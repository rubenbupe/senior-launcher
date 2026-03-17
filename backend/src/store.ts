import { existsSync, readFileSync, writeFileSync } from "node:fs";
import type {
  ActionCommand,
  ConfigCommand,
  DeviceInfo,
  DeviceRuntimeState,
  PersistedStore,
  SettingsPatch,
  DeviceAppInfo
} from "./types";

export class RuntimeStore {
  readonly deviceState = new Map<string, DeviceRuntimeState>();
  readonly pendingConfigByDevice = new Map<string, ConfigCommand>();
  readonly pendingActionsByDevice = new Map<string, ActionCommand[]>();

  constructor(private readonly storePath: string) {}

  private static normalizeForCompare(value: unknown): unknown {
    if (Array.isArray(value)) {
      return value.map((entry) => RuntimeStore.normalizeForCompare(entry));
    }
    if (value && typeof value === "object") {
      const input = value as Record<string, unknown>;
      const output: Record<string, unknown> = {};
      for (const key of Object.keys(input).sort()) {
        const normalized = RuntimeStore.normalizeForCompare(input[key]);
        if (normalized !== undefined) output[key] = normalized;
      }
      return output;
    }
    return value;
  }

  private static stableStringify(value: unknown): string {
    return JSON.stringify(RuntimeStore.normalizeForCompare(value));
  }

  private static isSame(a: unknown, b: unknown): boolean {
    return RuntimeStore.stableStringify(a) === RuntimeStore.stableStringify(b);
  }

  load(logWarn: (message: string, details?: Record<string, unknown>) => void): void {
    if (!existsSync(this.storePath)) return;

    try {
      const raw = readFileSync(this.storePath, "utf8");
      const parsed = JSON.parse(raw) as PersistedStore;

      for (const [deviceId, state] of Object.entries(parsed.devices ?? {})) {
        this.deviceState.set(deviceId, { ...state, connected: false });
      }
      for (const [deviceId, command] of Object.entries(parsed.pendingConfigByDevice ?? {})) {
        this.pendingConfigByDevice.set(deviceId, command);
      }
      for (const [deviceId, queue] of Object.entries(parsed.pendingActionsByDevice ?? {})) {
        this.pendingActionsByDevice.set(deviceId, Array.isArray(queue) ? queue : []);
      }
    } catch (error) {
      logWarn("Failed loading store", { error: (error as Error).message });
    }
  }

  save(): void {
    const store: PersistedStore = {
      devices: Object.fromEntries(this.deviceState.entries()),
      pendingConfigByDevice: Object.fromEntries(this.pendingConfigByDevice.entries()),
      pendingActionsByDevice: Object.fromEntries(this.pendingActionsByDevice.entries())
    };
    writeFileSync(this.storePath, JSON.stringify(store, null, 2), "utf8");
  }

  hasPersistedConfig(deviceId: string): boolean {
    return !!this.deviceState.get(deviceId)?.currentConfig;
  }

  updatePresence(deviceId: string, connected: boolean): DeviceRuntimeState {
    const current = this.deviceState.get(deviceId) ?? { deviceId, connected: false };
    const nextState: DeviceRuntimeState = {
      ...current,
      connected,
      lastSeenAt: new Date().toISOString()
    };
    this.deviceState.set(deviceId, nextState);
    this.save();
    return nextState;
  }

  upsertDeviceState(
    deviceId: string,
    currentConfig: SettingsPatch,
    availableApps: DeviceAppInfo[],
    deviceInfo: DeviceInfo
  ): { state: DeviceRuntimeState; changed: boolean } {
    const current = this.deviceState.get(deviceId) ?? { deviceId, connected: true };
    const previousComparable = {
      currentConfig: current.currentConfig ?? {},
      availableApps: current.availableApps ?? [],
      deviceInfo: current.deviceInfo ?? {}
    };

    const nextState: DeviceRuntimeState = {
      ...current,
      connected: true,
      lastSeenAt: new Date().toISOString(),
      deviceInfo: {
        ...(current.deviceInfo ?? {}),
        ...deviceInfo
      },
      currentConfig: {
        ...(current.currentConfig ?? {}),
        ...currentConfig
      },
      availableApps: availableApps.length > 0 ? availableApps : current.availableApps
    };

    const nextComparable = {
      currentConfig: nextState.currentConfig ?? {},
      availableApps: nextState.availableApps ?? [],
      deviceInfo: nextState.deviceInfo ?? {}
    };
    const changed = !RuntimeStore.isSame(previousComparable, nextComparable);

    this.deviceState.set(deviceId, nextState);
    this.save();
    return { state: nextState, changed };
  }

  hasConfigChanges(deviceId: string, settings: SettingsPatch): boolean {
    const currentConfig = this.deviceState.get(deviceId)?.currentConfig ?? {};
    const pendingSettings = this.pendingConfigByDevice.get(deviceId)?.settings ?? {};
    const effectiveConfig = { ...currentConfig, ...pendingSettings };
    const mergedConfig = { ...effectiveConfig, ...settings };
    return !RuntimeStore.isSame(effectiveConfig, mergedConfig);
  }

  queueConfig(command: ConfigCommand): void {
    this.pendingConfigByDevice.set(command.deviceId, command);
    this.save();
  }

  queueAction(command: ActionCommand): void {
    const queue = this.pendingActionsByDevice.get(command.deviceId) ?? [];
    queue.push(command);
    this.pendingActionsByDevice.set(command.deviceId, queue);
    this.save();
  }

  ackCommandResult(deviceId: string, commandId: string, success: boolean): "setConfig" | "runAction" | null {
    const pendingConfig = this.pendingConfigByDevice.get(deviceId);
    if (pendingConfig && pendingConfig.commandId === commandId) {
      if (success) {
        this.pendingConfigByDevice.delete(deviceId);
        this.save();
      }
      return "setConfig";
    }

    const queue = this.pendingActionsByDevice.get(deviceId) ?? [];
    const index = queue.findIndex((item) => item.commandId === commandId);
    if (index >= 0) {
      queue.splice(index, 1);
      this.pendingActionsByDevice.set(deviceId, queue);
      this.save();
      return "runAction";
    }

    return null;
  }
}
