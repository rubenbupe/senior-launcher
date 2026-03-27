import { join } from "node:path";
import { timingSafeEqual } from "node:crypto";
import type { ServerWebSocket } from "bun";
import {
	hasAnySetting,
	parseAction,
	parseAvailableApps,
	parseDeviceInfo,
	parseSmsConversations,
	parseSettingsPatch,
	parseSmsPreview
} from "./parsers";
import { RuntimeStore } from "./store";
import type {
	ActionCommand,
	ConfigCommand,
	DeviceAction,
	SettingsPatch,
	SmsPreviewItem,
	WsData,
	WsRole
} from "./types";

const PORT = Number(Bun.env.PORT ?? 8080);
const HOST = Bun.env.HOST ?? "127.0.0.1";
const STORE_PATH = join(process.cwd(), "device-sync-store.json");
const MAX_HTTP_BODY_BYTES = Number(Bun.env.MAX_HTTP_BODY_BYTES ?? 262_144);

function requireEnv(name: string): string {
	const value = (Bun.env[name] ?? "").trim();
	if (!value) {
		console.error(`FATAL: Environment variable "${name}" is required and cannot be empty.`);
		process.exit(1);
	}
	return value;
}

const DEVICE_TOKEN = requireEnv("DEVICE_TOKEN");
const ADMIN_TOKEN = requireEnv("ADMIN_TOKEN");
const ALLOWED_ORIGIN = requireEnv("ALLOWED_ORIGIN");

const corsHeaders: HeadersInit = {
	"Access-Control-Allow-Origin": ALLOWED_ORIGIN,
	"Access-Control-Allow-Methods": "GET,POST,OPTIONS",
	"Access-Control-Allow-Headers": "Content-Type, Authorization",
	Vary: "Origin"
};

const deviceSockets = new Map<string, ServerWebSocket<WsData>>();
const webSockets = new Set<ServerWebSocket<WsData>>();
const store = new RuntimeStore(STORE_PATH);
const wsTickets = new Map<string, { role: WsRole; deviceId?: string; clientIp: string; expiresAt: number }>();

setInterval(() => {
	const now = Date.now();
	for (const [ticket, entry] of wsTickets) {
		if (now > entry.expiresAt) wsTickets.delete(ticket);
	}
}, 60_000);

function nowIso(): string {
	return new Date().toISOString();
}

function logInfo(message: string, details?: Record<string, unknown>) {
	if (details) {
		console.log(`[${nowIso()}] INFO ${message}`, details);
		return;
	}
	console.log(`[${nowIso()}] INFO ${message}`);
}

function logWarn(message: string, details?: Record<string, unknown>) {
	if (details) {
		console.warn(`[${nowIso()}] WARN ${message}`, details);
		return;
	}
	console.warn(`[${nowIso()}] WARN ${message}`);
}

function json(data: unknown, status = 200): Response {
	return new Response(JSON.stringify(data), {
		status,
		headers: {
			...corsHeaders,
			"Content-Type": "application/json; charset=utf-8"
		}
	});
}

function empty(status = 204): Response {
	return new Response(null, {
		status,
		headers: corsHeaders
	});
}

function unauthorized(): Response {
	return json({ error: "Unauthorized" }, 401);
}

function tokenFromRequest(request: Request): string {
	const authHeader = request.headers.get("x-app-token") ?? "";
	if (authHeader.startsWith("Bearer ")) {
		return authHeader.substring(7).trim();
	}
	return "";
}

function isTokenValid(provided: string, expected: string): boolean {
	if (!provided || provided.length !== expected.length) return false;
	return timingSafeEqual(Buffer.from(provided), Buffer.from(expected));
}

function isOriginAllowed(request: Request): boolean {
	const origin = request.headers.get("origin");
	if (!origin || origin === "null") return true;
	return origin === ALLOWED_ORIGIN;
}

function wsOriginRejected(request: Request): boolean {
	const origin = request.headers.get("origin");
	return !!origin && origin !== "null" && origin !== ALLOWED_ORIGIN;
}

function resolveClientIp(
	request: Request,
	serverInstance: { requestIP: (request: Request) => { address: string } | null }
): string {
	const forwardedFor = (request.headers.get("x-forwarded-for") ?? "").split(",")[0]?.trim();
	if (forwardedFor) return forwardedFor;

	const realIp = (request.headers.get("x-real-ip") ?? "").trim();
	if (realIp) return realIp;

	return serverInstance.requestIP(request)?.address ?? "unknown";
}

function createTicket(role: WsRole, clientIp: string, deviceId?: string): string {
	const ticket = crypto.randomUUID();
	wsTickets.set(ticket, { role, deviceId, clientIp, expiresAt: Date.now() + 30_000 });
	return ticket;
}

function consumeTicket(ticket: string, clientIp: string): { role: WsRole; deviceId?: string } | null {
	const entry = wsTickets.get(ticket);
	wsTickets.delete(ticket);
	if (!entry || Date.now() > entry.expiresAt) return null;
	if (entry.clientIp !== clientIp) return null;
	return { role: entry.role, deviceId: entry.deviceId };
}

function sendWs(ws: ServerWebSocket<WsData>, payload: unknown) {
	ws.send(JSON.stringify(payload));
}

function broadcastToWeb(payload: unknown) {
	const text = JSON.stringify(payload);
	for (const ws of webSockets) {
		ws.send(text);
	}
}

function queueOrSendConfig(deviceId: string, settings: SettingsPatch): ConfigCommand | null {
	if (!store.hasConfigChanges(deviceId, settings)) {
		return null;
	}

	const command: ConfigCommand = {
		type: "setConfig",
		commandId: crypto.randomUUID(),
		deviceId,
		settings,
		createdAt: nowIso()
	};

	store.queueConfig(command);
	const ws = deviceSockets.get(deviceId);
	if (ws) {
		sendWs(ws, { type: "setConfig", commandId: command.commandId, settings });
	}
	return command;
}

function queueOrSendAction(deviceId: string, action: DeviceAction): ActionCommand {
	const command: ActionCommand = {
		type: "runAction",
		commandId: crypto.randomUUID(),
		deviceId,
		action,
		createdAt: nowIso()
	};

	store.queueAction(command);

	const ws = deviceSockets.get(deviceId);
	if (ws) {
		sendWs(ws, { type: "runAction", commandId: command.commandId, action });
	}

	return command;
}

function flushPendingForDevice(deviceId: string) {
	const ws = deviceSockets.get(deviceId);
	if (!ws) return;

	const pendingConfig = store.pendingConfigByDevice.get(deviceId);
	if (pendingConfig) {
		sendWs(ws, { type: "setConfig", commandId: pendingConfig.commandId, settings: pendingConfig.settings });
	}

	const pendingActions = store.pendingActionsByDevice.get(deviceId) ?? [];
	for (const command of pendingActions) {
		sendWs(ws, { type: "runAction", commandId: command.commandId, action: command.action });
	}
}

async function parseJsonBody<T>(request: Request): Promise<T> {
	if (!request.body) return {} as T;

	const contentLengthHeader = request.headers.get("content-length");
	const declaredLength = Number(contentLengthHeader ?? "0");
	if (Number.isFinite(declaredLength) && declaredLength > MAX_HTTP_BODY_BYTES) {
		throw new Error("PAYLOAD_TOO_LARGE");
	}

	const rawBody = await request.text();
	if (Buffer.byteLength(rawBody, "utf8") > MAX_HTTP_BODY_BYTES) {
		throw new Error("PAYLOAD_TOO_LARGE");
	}

	if (!rawBody.trim()) return {} as T;
	return JSON.parse(rawBody) as T;
}

function jsonErrorFromBodyParse(error: unknown): Response {
	if (error instanceof Error && error.message === "PAYLOAD_TOO_LARGE") {
		return json({ error: "Payload too large" }, 413);
	}
	return json({ error: "Invalid request" }, 400);
}

function staticResponse(filePath: URL, contentType: string): Response {
	const file = Bun.file(filePath);
	return new Response(file, {
		headers: {
			...corsHeaders,
			"Content-Type": contentType,
			"Cache-Control": "no-store"
		}
	});
}

export function createBackendServer() {
	store.load(logWarn);
	logInfo("Store loaded", {
		devices: store.deviceState.size,
		pendingConfigs: store.pendingConfigByDevice.size,
		pendingActions: Array.from(store.pendingActionsByDevice.values()).reduce((sum, queue) => sum + queue.length, 0)
	});

	const indexHtmlUrl = new URL("../public/index.html", import.meta.url);
	const panelJsUrl = new URL("../public/panel.js", import.meta.url);

	const server = Bun.serve<WsData>({
		port: PORT,
		hostname: HOST,
		fetch(request, serverInstance) {
			const url = new URL(request.url);
			const { pathname } = url;

			if (request.method === "OPTIONS") {
				if (!isOriginAllowed(request)) return json({ error: "Origin not allowed" }, 403);
				return empty(204);
			}

			if (!isOriginAllowed(request)) return json({ error: "Origin not allowed" }, 403);

			if (request.method === "POST" && pathname === "/auth/ticket/admin") {
				if (!isTokenValid(tokenFromRequest(request), ADMIN_TOKEN)) return unauthorized();
				const clientIp = resolveClientIp(request, serverInstance);
				return json({ ticket: createTicket("web", clientIp) }, 201);
			}

			if (request.method === "POST" && pathname === "/auth/ticket/device") {
				if (!isTokenValid(tokenFromRequest(request), DEVICE_TOKEN)) return unauthorized();
				const clientIp = resolveClientIp(request, serverInstance);
				return parseJsonBody<{ deviceId?: string }>(request)
					.then((body) => {
						const deviceId = String(body.deviceId ?? "").trim();
						if (!deviceId) return json({ error: "deviceId is required" }, 400);
						return json({ ticket: createTicket("device", clientIp, deviceId) }, 201);
					})
					.catch((error) => jsonErrorFromBodyParse(error));
			}

			if (pathname === "/ws/device") {
				const deviceId = (url.searchParams.get("deviceId") ?? "").trim();
				const ticket = (url.searchParams.get("ticket") ?? "").trim();
				const clientIp = resolveClientIp(request, serverInstance);
				const consumed = ticket ? consumeTicket(ticket, clientIp) : null;
				if (wsOriginRejected(request)) return json({ error: "Origin not allowed" }, 403);

				const hasValidTicket = consumed?.role === "device" && consumed.deviceId === deviceId;

				if (!hasValidTicket) return unauthorized();
				if (!deviceId) {
					return json({ error: "deviceId is required" }, 400);
				}

				const upgraded = serverInstance.upgrade(request, { data: { role: "device", deviceId } });
				if (upgraded) return;
				return json({ error: "WebSocket upgrade failed" }, 400);
			}

			if (pathname === "/ws/admin") {
				const ticket = (url.searchParams.get("ticket") ?? "").trim();
				const clientIp = resolveClientIp(request, serverInstance);
				const consumed = ticket ? consumeTicket(ticket, clientIp) : null;
				if (wsOriginRejected(request)) return json({ error: "Origin not allowed" }, 403);

				if (consumed?.role !== "web") {
					return unauthorized();
				}

				const upgraded = serverInstance.upgrade(request, { data: { role: "web" } });
				if (upgraded) return;
				return json({ error: "WebSocket upgrade failed" }, 400);
			}

			if (request.method === "GET" && pathname === "/") {
				return staticResponse(indexHtmlUrl, "text/html; charset=utf-8");
			}
			if (request.method === "GET" && pathname === "/panel.js") {
				return staticResponse(panelJsUrl, "application/javascript; charset=utf-8");
			}

			if (!isTokenValid(tokenFromRequest(request), ADMIN_TOKEN)) return unauthorized();

			if (request.method === "GET" && pathname === "/health") {
				return json({ ok: true, time: nowIso() });
			}

			if (request.method === "GET" && pathname === "/api/state") {
				return json({
					devices: Array.from(store.deviceState.values()),
					pendingConfig: Array.from(store.pendingConfigByDevice.values()),
					pendingActions: Array.from(store.pendingActionsByDevice.entries()).map(([deviceId, list]) => ({
						deviceId,
						count: list.length
					}))
				});
			}

			if (request.method === "POST" && pathname === "/api/config/update") {
				return parseJsonBody<{ deviceId?: string; settings?: SettingsPatch }>(request)
					.then((body) => {
						const deviceId = String(body.deviceId ?? "").trim();
						const settings = parseSettingsPatch(body.settings);
						if (!deviceId) return json({ error: "deviceId is required" }, 400);
						if (!hasAnySetting(settings)) return json({ error: "settings is empty" }, 400);
						if (!store.hasPersistedConfig(deviceId)) {
							return json({ error: "The device has not persisted its configuration yet" }, 409);
						}
						const command = queueOrSendConfig(deviceId, settings);
						if (!command) {
							return json({ status: "noop", message: "No configuration changes detected" }, 200);
						}
						return json(command, 201);
					})
					.catch((error) => jsonErrorFromBodyParse(error));
			}

			if (request.method === "POST" && pathname === "/api/action/run") {
				return parseJsonBody<{ deviceId?: string; action?: DeviceAction }>(request)
					.then((body) => {
						const deviceId = String(body.deviceId ?? "").trim();
						if (!deviceId) return json({ error: "deviceId is required" }, 400);
						const action = parseAction(body.action);
						if (!action) return json({ error: "invalid action" }, 400);
						const command = queueOrSendAction(deviceId, action);
						return json(command, 201);
					})
					.catch((error) => jsonErrorFromBodyParse(error));
			}

			return json({ error: "Not found" }, 404);
		},
		websocket: {
			maxPayloadLength: 1024 * 512,
			idleTimeout: 120,
			open(ws) {
				const { role, deviceId } = ws.data;

				if (role === "web") {
					webSockets.add(ws);
					sendWs(ws, { type: "hello", role: "web", connectedAt: nowIso() });
					return;
				}

				if (!deviceId) {
					ws.close(1008, "missing deviceId");
					return;
				}

				deviceSockets.set(deviceId, ws);
				const state = store.updatePresence(deviceId, true);
				broadcastToWeb({ type: "devicePresence", deviceId, connected: true, lastSeenAt: state.lastSeenAt });
				sendWs(ws, { type: "hello", role: "device", deviceId, connectedAt: nowIso() });
				flushPendingForDevice(deviceId);
			},
			message(ws, message) {
				let payload: any;
				try {
					payload = JSON.parse(typeof message === "string" ? message : Buffer.from(message).toString("utf8"));
				} catch {
					return;
				}

				if (ws.data.role === "device") {
					const deviceId = ws.data.deviceId;
					if (!deviceId) return;

					if (payload?.type === "deviceState") {
						const currentConfig = parseSettingsPatch(payload.currentConfig);
						const availableApps = parseAvailableApps(payload.availableApps);
						const deviceInfo = parseDeviceInfo(payload.deviceInfo);
						const { state, changed } = store.upsertDeviceState(deviceId, currentConfig, availableApps, deviceInfo);
						if (changed) {
							broadcastToWeb({ type: "deviceState", deviceId, state });
						}
						return;
					}

					if (payload?.type === "deviceData") {
						const dataType = String(payload.dataType ?? "").trim();
						const requestId = String(payload.requestId ?? "").trim() || undefined;
						const error = String(payload.error ?? "").trim() || undefined;
						if (dataType === "sms") {
							const smsPreview: SmsPreviewItem[] = parseSmsPreview(payload.smsPreview);
							const smsConversations = parseSmsConversations(payload.smsConversations);
							broadcastToWeb({
								type: "deviceData",
								deviceId,
								dataType: "sms",
								requestId,
								smsPreview,
								smsConversations,
								error
							});
							return;
						}
					}

					if (payload?.type === "commandResult") {
						const commandId = String(payload.commandId ?? "").trim();
						const success = payload.success !== false;
						const messageText = String(payload.message ?? "").trim();
						const commandType = store.ackCommandResult(deviceId, commandId, success);
						if (!commandType) return;
						broadcastToWeb({
							type: "commandResult",
							deviceId,
							commandId,
							commandType,
							success,
							message: messageText
						});
					}
					return;
				}

				if (ws.data.role === "web") {
					const type = String(payload?.type ?? "").trim();
					const deviceId = String(payload?.deviceId ?? ws.data.deviceId ?? "").trim();
					if (!deviceId) {
						sendWs(ws, { type: "error", message: "deviceId is required" });
						return;
					}

					if (type === "getState") {
						const state = store.deviceState.get(deviceId) ?? { deviceId, connected: false };
						sendWs(ws, { type: "deviceState", deviceId, state });
						return;
					}

					if (type === "setConfig") {
						const settings = parseSettingsPatch(payload.settings);
						if (!hasAnySetting(settings)) {
							sendWs(ws, { type: "error", message: "settings is empty" });
							return;
						}
						if (!store.hasPersistedConfig(deviceId)) {
							sendWs(ws, { type: "error", message: "The device has not persisted its configuration yet" });
							return;
						}
						const command = queueOrSendConfig(deviceId, settings);
						if (!command) {
							sendWs(ws, { type: "noop", message: "No configuration changes detected", deviceId });
							return;
						}
						sendWs(ws, { type: "accepted", command });
						return;
					}

					if (type === "runAction") {
						const action = parseAction(payload.action);
						if (!action) {
							sendWs(ws, { type: "error", message: "invalid action" });
							return;
						}
						const command = queueOrSendAction(deviceId, action);
						sendWs(ws, { type: "accepted", command });
						return;
					}

					if (type === "requestData") {
						const dataType = String(payload?.dataType ?? "").trim();
						if (dataType !== "sms") {
							sendWs(ws, { type: "error", message: "invalid dataType" });
							return;
						}
						const deviceSocket = deviceSockets.get(deviceId);
						if (!deviceSocket) {
							sendWs(ws, { type: "error", message: "The device is not connected" });
							return;
						}
						const requestId = crypto.randomUUID();
						sendWs(deviceSocket, { type: "requestData", requestId, dataType });
						sendWs(ws, { type: "accepted", request: { type: "requestData", requestId, dataType, deviceId } });
					}
				}
			},
			close(ws) {
				if (ws.data.role === "web") {
					webSockets.delete(ws);
					return;
				}

				const deviceId = ws.data.deviceId;
				if (!deviceId) return;

				const active = deviceSockets.get(deviceId);
				if (active === ws) {
					deviceSockets.delete(deviceId);
				}

				const state = store.updatePresence(deviceId, false);
				broadcastToWeb({ type: "devicePresence", deviceId, connected: false, lastSeenAt: state.lastSeenAt });
			}
		}
	});

	logInfo("Backend started", {
		url: server.url.toString(),
		ticketTtlMs: 30_000,
		storePath: STORE_PATH
	});

	return server;
}
