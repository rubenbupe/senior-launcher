# Senior Launcher Backend

WebSocket + HTTP backend to sync Senior Launcher devices and execute remote actions from the web panel.

## IMPORTANT NOTE
This backend is not provided as a production-ready solution. It's a reference implementation to demonstrate the communication protocol and basic features. For production use, you should implement your own backend with proper security, scalability, and reliability considerations.

## What it does

- Keeps a real-time connection with each device (`role=device`).
- Exposes a web panel (`/`) to send config and remote actions.
- Persists state and command queues in `device-sync-store.json`.
- Replays pending commands when a device reconnects.
- Blocks config updates if no persisted device state exists yet.

## Structure

- `server.ts`: entry point.
- `src/serverApp.ts`: HTTP/WS server.
- `src/store.ts`: persistence + in-memory runtime state.
- `src/parsers.ts`: payload validation/parsing.
- `src/types.ts`: shared types.
- `public/index.html`: web panel.
- `public/panel.js`: panel logic.

## Requirements

- Bun 1.1 or higher.

## Run locally

```bash
cd backend
bun install
bun run start
```

Required env vars:

- `HOST` (default `0.0.0.0`)
- `PORT` (default `8080`)
- `DEVICE_TOKEN` (token para autenticación de dispositivos)
- `ADMIN_TOKEN` (token para autenticación de panel/API)
- `ALLOWED_ORIGIN` (origen permitido para panel, por ejemplo `https://panel.tudominio.com`)

Example:

```bash
cd backend
DEVICE_TOKEN="device_secret" ADMIN_TOKEN="admin_secret" ALLOWED_ORIGIN="http://localhost:8080" bun run start
```

## Web panel

1. Open `http://localhost:8080/`.
2. Enter `Device ID` (same one configured in Android app).
3. Fill `Token` with `ADMIN_TOKEN`.
4. Click `Conectar WebSocket`.

From the panel you can:

- View current device state.
- Manage allowed apps without manually typing package names.
- Update config (`setConfig`).
- Execute actions (`runAction`, e.g. call or create contact).
- Request SMS data reported by the device.

## WebSocket protocol

Endpoints:

- `ws://<host>:<port>/ws/device?deviceId=<id>&ticket=<one-time-ticket>`
- `ws://<host>:<port>/ws/admin?ticket=<one-time-ticket>`

WebSocket tickets:

- `POST /auth/ticket/admin` with header `x-app-token: <ADMIN_TOKEN>` → `{ ticket }` (TTL 30s, single-use, IP-bound)
- `POST /auth/ticket/device` with header `x-app-token: <DEVICE_TOKEN>` and body `{ "deviceId": "..." }` → `{ ticket }` (TTL 30s, single-use, IP-bound)

Messages from `device` to backend:

- `deviceState`
  - `currentConfig`
  - `availableApps`
  - `deviceInfo`
- `deviceData`
  - `sms` payload on demand
- `commandResult`

Messages from backend to `device`:

- `setConfig`
- `runAction`
- `requestData`

Messages from backend to `web`:

- `hello`
- `devicePresence`
- `deviceState`
- `deviceData`
- `commandResult`
- `accepted`
- `error`

## HTTP API

- `GET /health`
- `GET /api/state`
- `POST /api/config/update`
- `POST /api/action/run`

All protected HTTP endpoints (`/auth/*`, `/health`, `/api/*`) require:

- `x-app-token: <ADMIN_TOKEN>` for admin panel/API
- `x-app-token: <DEVICE_TOKEN>` for `/auth/ticket/device`

## Docker

### Docker Compose (example)

```bash
cd backend
docker compose up --build
```

Included file: `docker-compose.yml`.

## Quick validation

1. Start backend.
2. Open web panel.
3. Connect with a valid `deviceId`.
4. Confirm `deviceState` arrives.
5. Send `setConfig` and verify `commandResult`.
