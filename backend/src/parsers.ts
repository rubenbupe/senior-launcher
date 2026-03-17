import type {
  DeviceInfo,
  DeviceAction,
  DeviceAppInfo,
  GalleryPreviewItem,
  SmsConversationItem,
  SmsMessageItem,
  SettingsPatch,
  SmsPreviewItem
} from "./types";

export function parseSettingsPatch(input: unknown): SettingsPatch {
  const raw = (input ?? {}) as Record<string, unknown>;

  const parseStringArray = (value: unknown): string[] | undefined => {
    if (!Array.isArray(value)) return undefined;
    const normalized = value
      .map((entry) => (typeof entry === "string" ? entry.trim() : ""))
      .filter((entry) => entry.length > 0);
    return normalized.length > 0 ? Array.from(new Set(normalized)) : [];
  };

  const selectedHomeAppIds = (() => {
    const selected = parseStringArray(raw.selectedHomeAppIds);
    if (selected !== undefined) return selected;

    const ordered = parseStringArray(raw.orderedHomeAppIds);
    const enabled = parseStringArray(raw.enabledHomeAppIds);

    if (ordered !== undefined && enabled !== undefined) {
      return [...ordered.filter((id) => enabled.includes(id)), ...enabled.filter((id) => !ordered.includes(id))];
    }
    return ordered ?? enabled;
  })();

  return {
    userName:
      typeof raw.userName === "string" && raw.userName.trim().length > 0
        ? raw.userName.trim()
        : undefined,
    useWhatsApp: typeof raw.useWhatsApp === "boolean" ? raw.useWhatsApp : undefined,
    protectDndMode: typeof raw.protectDndMode === "boolean" ? raw.protectDndMode : undefined,
    lockDeviceVolume: typeof raw.lockDeviceVolume === "boolean" ? raw.lockDeviceVolume : undefined,
    lockedVolumePercent:
      typeof raw.lockedVolumePercent === "number"
        ? Math.max(0, Math.min(100, Math.round(raw.lockedVolumePercent)))
        : undefined,
    backendSyncEnabled:
      typeof raw.backendSyncEnabled === "boolean" ? raw.backendSyncEnabled : undefined,
    backendServerUrl:
      typeof raw.backendServerUrl === "string" ? raw.backendServerUrl.trim() : undefined,
    backendDeviceId:
      typeof raw.backendDeviceId === "string" ? raw.backendDeviceId.trim() : undefined,
    backendApiToken:
      typeof raw.backendApiToken === "string" ? raw.backendApiToken.trim() : undefined,
    navigationAnimationsEnabled:
      typeof raw.navigationAnimationsEnabled === "boolean"
        ? raw.navigationAnimationsEnabled
        : undefined,
    showSosButton: typeof raw.showSosButton === "boolean" ? raw.showSosButton : undefined,
    sosPhoneNumber:
      typeof raw.sosPhoneNumber === "string" && raw.sosPhoneNumber.trim().length > 0
        ? raw.sosPhoneNumber.trim()
        : undefined,
    selectedHomeAppIds
  };
}

export function parseAvailableApps(input: unknown): DeviceAppInfo[] {
  if (!Array.isArray(input)) return [];
  const result: DeviceAppInfo[] = [];

  for (const raw of input) {
    const entry = raw as Record<string, unknown>;
    const id = typeof entry.id === "string" ? entry.id.trim() : "";
    if (!id) continue;

    result.push({
      id,
      label: typeof entry.label === "string" ? entry.label.trim() || id : id,
      isMiniApp: entry.isMiniApp === true,
      packageName:
        typeof entry.packageName === "string" ? entry.packageName.trim() || undefined : undefined,
      enabled: typeof entry.enabled === "boolean" ? entry.enabled : undefined,
      order: typeof entry.order === "number" ? entry.order : undefined
    });
  }

  return result;
}

export function parseSmsPreview(input: unknown): SmsPreviewItem[] {
  if (!Array.isArray(input)) return [];
  const result: SmsPreviewItem[] = [];

  for (const raw of input) {
    const entry = raw as Record<string, unknown>;
    const threadId = typeof entry.threadId === "number" ? entry.threadId : Number(entry.threadId ?? 0);
    if (!Number.isFinite(threadId) || threadId <= 0) continue;

    result.push({
      threadId,
      address: typeof entry.address === "string" ? entry.address.trim() || "Desconocido" : "Desconocido",
      preview: typeof entry.preview === "string" ? entry.preview.trim() : "",
      timestamp: typeof entry.timestamp === "number" ? entry.timestamp : Number(entry.timestamp ?? 0),
      unreadCount:
        typeof entry.unreadCount === "number"
          ? Math.max(0, Math.round(entry.unreadCount))
          : Math.max(0, Number(entry.unreadCount ?? 0) || 0)
    });
  }

  return result.slice(0, 40);
}

export function parseSmsConversations(input: unknown): SmsConversationItem[] {
  if (!Array.isArray(input)) return [];
  const result: SmsConversationItem[] = [];

  for (const raw of input) {
    const entry = raw as Record<string, unknown>;
    const threadId = typeof entry.threadId === "number" ? entry.threadId : Number(entry.threadId ?? 0);
    if (!Number.isFinite(threadId) || threadId <= 0) continue;

    const messagesRaw = Array.isArray(entry.messages) ? entry.messages : [];
    const messages: SmsMessageItem[] = [];
    for (const messageRaw of messagesRaw) {
      const messageEntry = messageRaw as Record<string, unknown>;
      const id = typeof messageEntry.id === "number" ? messageEntry.id : Number(messageEntry.id ?? 0);
      if (!Number.isFinite(id) || id <= 0) continue;
      messages.push({
        id,
        body: typeof messageEntry.body === "string" ? messageEntry.body : "",
        timestamp:
          typeof messageEntry.timestamp === "number"
            ? messageEntry.timestamp
            : Number(messageEntry.timestamp ?? 0),
        isSentByMe: messageEntry.isSentByMe === true
      });
    }

    result.push({
      threadId,
      address: typeof entry.address === "string" ? entry.address.trim() || "Desconocido" : "Desconocido",
      preview: typeof entry.preview === "string" ? entry.preview : "",
      timestamp: typeof entry.timestamp === "number" ? entry.timestamp : Number(entry.timestamp ?? 0),
      unreadCount:
        typeof entry.unreadCount === "number"
          ? Math.max(0, Math.round(entry.unreadCount))
          : Math.max(0, Number(entry.unreadCount ?? 0) || 0),
      messages
    });
  }

  return result.slice(0, 40);
}

export function parseGalleryPreview(input: unknown): GalleryPreviewItem[] {
  if (!Array.isArray(input)) return [];
  const result: GalleryPreviewItem[] = [];

  for (const raw of input) {
    const entry = raw as Record<string, unknown>;
    const uri = typeof entry.uri === "string" ? entry.uri.trim() : "";
    if (!uri) continue;

    const thumbnailBase64 =
      typeof entry.thumbnailBase64 === "string" ? entry.thumbnailBase64.trim() || undefined : undefined;
    const imageBase64 =
      typeof entry.imageBase64 === "string" ? entry.imageBase64.trim() || undefined : undefined;

    result.push({
      uri,
      mimeType: typeof entry.mimeType === "string" ? entry.mimeType.trim() || undefined : undefined,
      timestamp: typeof entry.timestamp === "number" ? entry.timestamp : Number(entry.timestamp ?? 0) || undefined,
      thumbnailBase64,
      imageBase64
    });
  }

  return result.slice(0, 60);
}

export function parseDeviceInfo(input: unknown): DeviceInfo {
  const raw = (input ?? {}) as Record<string, unknown>;

  const num = (value: unknown): number | undefined => {
    if (typeof value === "number" && Number.isFinite(value)) return value;
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : undefined;
  };

  return {
    packageName: typeof raw.packageName === "string" ? raw.packageName.trim() || undefined : undefined,
    appVersionName:
      typeof raw.appVersionName === "string" ? raw.appVersionName.trim() || undefined : undefined,
    appVersionCode: num(raw.appVersionCode),
    deviceManufacturer:
      typeof raw.deviceManufacturer === "string"
        ? raw.deviceManufacturer.trim() || undefined
        : undefined,
    deviceBrand: typeof raw.deviceBrand === "string" ? raw.deviceBrand.trim() || undefined : undefined,
    deviceModel: typeof raw.deviceModel === "string" ? raw.deviceModel.trim() || undefined : undefined,
    deviceProduct:
      typeof raw.deviceProduct === "string" ? raw.deviceProduct.trim() || undefined : undefined,
    androidRelease:
      typeof raw.androidRelease === "string" ? raw.androidRelease.trim() || undefined : undefined,
    androidApiLevel: num(raw.androidApiLevel),
    batteryLevelPercent: num(raw.batteryLevelPercent),
    batteryCharging: typeof raw.batteryCharging === "boolean" ? raw.batteryCharging : undefined,
    batteryStatus:
      typeof raw.batteryStatus === "string" ? raw.batteryStatus.trim() || undefined : undefined
  };
}

export function parseAction(input: unknown): DeviceAction | null {
  const action = (input ?? {}) as Record<string, unknown>;
  const type = String(action.type ?? "").trim();

  if (type === "call") {
    const phone = String(action.phone ?? "").trim();
    return phone ? { type: "call", phone } : null;
  }

  if (type === "createContact") {
    const phone = String(action.phone ?? "").trim();
    if (!phone) return null;
    const name = String(action.name ?? "").trim() || "Nuevo contacto";
    return { type: "createContact", name, phone };
  }

  if (type === "markAllSmsRead") {
    return { type: "markAllSmsRead" };
  }

  if (type === "deleteAllSms") {
    return { type: "deleteAllSms" };
  }

  if (type === "killApp") {
    return { type: "killApp" };
  }

  return null;
}

export function hasAnySetting(settings: SettingsPatch): boolean {
  return Object.keys(settings).length > 0;
}
