export type SettingsPatch = {
  userName?: string;
  useWhatsApp?: boolean;
  protectDndMode?: boolean;
  lockDeviceVolume?: boolean;
  lockedVolumePercent?: number;
  backendSyncEnabled?: boolean;
  backendServerUrl?: string;
  backendDeviceId?: string;
  backendApiToken?: string;
  navigationAnimationsEnabled?: boolean;
  showSosButton?: boolean;
  sosPhoneNumber?: string;
  selectedHomeAppIds?: string[];
};

export type DeviceAppInfo = {
  id: string;
  label: string;
  isMiniApp: boolean;
  packageName?: string;
  enabled?: boolean;
  order?: number;
};

export type SmsPreviewItem = {
  threadId: number;
  address: string;
  preview: string;
  timestamp: number;
  unreadCount: number;
};

export type SmsMessageItem = {
  id: number;
  body: string;
  timestamp: number;
  isSentByMe: boolean;
};

export type SmsConversationItem = {
  threadId: number;
  address: string;
  preview: string;
  timestamp: number;
  unreadCount: number;
  messages: SmsMessageItem[];
};

export type GalleryPreviewItem = {
  uri: string;
  mimeType?: string;
  timestamp?: number;
  thumbnailBase64?: string;
  imageBase64?: string;
};

export type DeviceInfo = {
  packageName?: string;
  appVersionName?: string;
  appVersionCode?: number;
  deviceManufacturer?: string;
  deviceBrand?: string;
  deviceModel?: string;
  deviceProduct?: string;
  androidRelease?: string;
  androidApiLevel?: number;
  batteryLevelPercent?: number;
  batteryCharging?: boolean;
  batteryStatus?: string;
};

export type DeviceAction =
  | { type: "call"; phone: string }
  | { type: "createContact"; name: string; phone: string }
  | { type: "markAllSmsRead" }
  | { type: "deleteAllSms" }
  | { type: "killApp" };

export type CommandEnvelope = {
  commandId: string;
  deviceId: string;
  createdAt: string;
};

export type ConfigCommand = CommandEnvelope & {
  type: "setConfig";
  settings: SettingsPatch;
};

export type ActionCommand = CommandEnvelope & {
  type: "runAction";
  action: DeviceAction;
};

export type DeviceRuntimeState = {
  deviceId: string;
  connected: boolean;
  lastSeenAt?: string;
  deviceInfo?: DeviceInfo;
  currentConfig?: SettingsPatch;
  availableApps?: DeviceAppInfo[];
};

export type PersistedStore = {
  devices: Record<string, DeviceRuntimeState>;
  pendingConfigByDevice: Record<string, ConfigCommand>;
  pendingActionsByDevice: Record<string, ActionCommand[]>;
};

export type WsRole = "device" | "web";

export type WsData = {
  role: WsRole;
  deviceId?: string;
};
