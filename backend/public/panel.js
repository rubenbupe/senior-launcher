(() => {
  const byId = (id) => document.getElementById(id);

  const statusEl = byId("status");
  const eventsEl = byId("events");
  const deviceStateEl = byId("deviceState");
  const deviceInfoEl = byId("deviceInfo");
  const persistWarnEl = byId("persistWarn");
  const selectedAppsListEl = byId("selectedAppsList");
  const availableAppsListEl = byId("availableAppsList");
  const smsPreviewEl = byId("smsPreview");
  const actionTypeEl = byId("actionType");
  const actionPhoneGroupEl = byId("actionPhoneGroup");
  const actionNameGroupEl = byId("actionNameGroup");

  const deviceIdInput = byId("deviceId");
  const tokenInput = byId("token");
  const connectBtn = byId("connectBtn");
  const sendConfigBtn = byId("sendConfigBtn");
  const runActionBtn = byId("runActionBtn");
  const requestSmsBtn = byId("requestSmsBtn");

  if (!connectBtn || !sendConfigBtn || !runActionBtn || !requestSmsBtn || !actionTypeEl || !actionPhoneGroupEl || !actionNameGroupEl) {
    console.error("Panel no inicializado: faltan elementos HTML requeridos");
    return;
  }

  let ws = null;
  let connected = false;
  let deviceOnline = false;
  let hasPersistedConfig = false;
  let deviceApps = [];
  let selectedAppIds = [];
  let lastAppliedConfigSignature = "";
  let lastAppliedAppsSignature = "";

  function stableSignature(value) {
    const normalize = (input) => {
      if (Array.isArray(input)) return input.map(normalize);
      if (input && typeof input === "object") {
        const out = {};
        Object.keys(input)
          .sort()
          .forEach((key) => {
            const normalized = normalize(input[key]);
            if (normalized !== undefined) out[key] = normalized;
          });
        return out;
      }
      return input;
    };
    return JSON.stringify(normalize(value ?? {}));
  }

  function addEvent(payload) {
    const line = typeof payload === "string" ? payload : JSON.stringify(payload, null, 2);
    const current = eventsEl.textContent || "";
    eventsEl.textContent = (`${new Date().toISOString()} ${line}\n\n${current}`).slice(0, 20000);
  }

  function setStatus(text, ok) {
    statusEl.innerHTML = `<span class="pill ${ok ? "ok" : "bad"}">${text}</span>`;
  }

  function refreshConfigAvailability() {
    const canConfigure = connected && hasPersistedConfig;
    sendConfigBtn.disabled = !canConfigure;
    requestSmsBtn.disabled = !(connected && deviceOnline);
    persistWarnEl.style.display = canConfigure ? "none" : "block";
  }

  function refreshActionFields() {
    const actionType = actionTypeEl.value;
    if (actionType === "call") {
      actionPhoneGroupEl.style.display = "block";
      actionNameGroupEl.style.display = "none";
      return;
    }
    if (actionType === "createContact") {
      actionPhoneGroupEl.style.display = "block";
      actionNameGroupEl.style.display = "block";
      return;
    }
    actionPhoneGroupEl.style.display = "none";
    actionNameGroupEl.style.display = "none";
  }

  function wsUrl(ticket) {
    const protocol = location.protocol === "https:" ? "wss:" : "ws:";
    return `${protocol}//${location.host}/ws/admin?ticket=${encodeURIComponent(ticket)}`;
  }

  async function requestAdminWsTicket() {
    const token = tokenInput.value.trim();
    if (!token) {
      throw new Error("Token admin obligatorio");
    }

    const response = await fetch("/auth/ticket/admin", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "x-app-token": `${token}`
      }
    });

    if (!response.ok) {
      throw new Error(`No se pudo obtener ticket (${response.status})`);
    }

    const payload = await response.json().catch(() => ({}));
    const ticket = String(payload?.ticket || "").trim();
    if (!ticket) {
      throw new Error("Respuesta inválida al solicitar ticket");
    }
    return ticket;
  }

  function requireConnected() {
    if (!connected || !ws) {
      addEvent("No hay WebSocket conectado");
      return false;
    }
    return true;
  }

  function appById(id) {
    return deviceApps.find((item) => item.id === id);
  }

  function normalizeSelectedFromState(state) {
    deviceApps = Array.isArray(state?.availableApps) ? state.availableApps : [];
    const selectedFromConfig = Array.isArray(state?.currentConfig?.selectedHomeAppIds)
      ? state.currentConfig.selectedHomeAppIds.filter((id) => typeof id === "string" && id.trim().length > 0)
      : [];

    if (selectedFromConfig.length > 0) {
      selectedAppIds = Array.from(new Set(selectedFromConfig));
      return;
    }

    const enabled = new Set(deviceApps.filter((a) => a.enabled === true).map((a) => a.id));
    const ordered = [...deviceApps]
      .filter((a) => enabled.has(a.id))
      .sort((a, b) => (Number(a.order ?? 9999) - Number(b.order ?? 9999)) || String(a.label).localeCompare(String(b.label)))
      .map((a) => a.id);
    selectedAppIds = ordered.length > 0 ? ordered : [...enabled];
  }

  function moveSelected(id, direction) {
    const index = selectedAppIds.indexOf(id);
    if (index < 0) return;
    const target = index + direction;
    if (target < 0 || target >= selectedAppIds.length) return;
    const next = [...selectedAppIds];
    [next[index], next[target]] = [next[target], next[index]];
    selectedAppIds = next;
    renderAppLists();
  }

  function removeApp(id) {
    selectedAppIds = selectedAppIds.filter((entry) => entry !== id);
    renderAppLists();
  }

  function addApp(id) {
    if (selectedAppIds.includes(id)) return;
    selectedAppIds = [...selectedAppIds, id];
    renderAppLists();
  }

  function renderAppLists() {
    const selectedSet = new Set(selectedAppIds);

    const selectedHtml = selectedAppIds.map((id, index) => {
      const app = appById(id);
      if (!app) return "";
      return `<div class="app-item">
        <div class="app-meta">
          <div class="app-label">${app.label || id}</div>
          <div class="app-id">${id}</div>
        </div>
        <div class="app-actions">
          <button class="light" data-action="up" data-id="${id}" ${index === 0 ? "disabled" : ""}>↑</button>
          <button class="light" data-action="down" data-id="${id}" ${index === selectedAppIds.length - 1 ? "disabled" : ""}>↓</button>
          <button data-action="remove" data-id="${id}">✕</button>
        </div>
      </div>`;
    }).join("") || '<p style="margin:0;color:#64748b;">No hay apps seleccionadas</p>';

    const availableHtml = [...deviceApps]
      .filter((app) => !selectedSet.has(app.id))
      .sort((a, b) => (a.isMiniApp === b.isMiniApp ? String(a.label).localeCompare(String(b.label)) : a.isMiniApp ? -1 : 1))
      .map((app) => `<div class="app-item">
        <div class="app-meta">
          <div class="app-label">${app.label || app.id}</div>
          <div class="app-id">${app.id}</div>
        </div>
        <div class="app-actions"><button data-action="add" data-id="${app.id}">+</button></div>
      </div>`)
      .join("") || '<p style="margin:0;color:#64748b;">No hay más apps disponibles</p>';

    selectedAppsListEl.innerHTML = selectedHtml;
    availableAppsListEl.innerHTML = availableHtml;

    selectedAppsListEl.querySelectorAll("button").forEach((btn) => {
      btn.addEventListener("click", () => {
        const id = btn.getAttribute("data-id");
        const action = btn.getAttribute("data-action");
        if (!id || !action) return;
        if (action === "up") moveSelected(id, -1);
        if (action === "down") moveSelected(id, 1);
        if (action === "remove") removeApp(id);
      });
    });

    availableAppsListEl.querySelectorAll("button").forEach((btn) => {
      btn.addEventListener("click", () => {
        const id = btn.getAttribute("data-id");
        if (id) addApp(id);
      });
    });
  }

  function renderSmsPreview(list, conversations, errorMessage) {
    const items = Array.isArray(list) ? list : [];
    const fullConversations = Array.isArray(conversations) ? conversations : [];
    if (errorMessage) {
      smsPreviewEl.innerHTML = `<p style="margin:0;color:#b91c1c;">${errorMessage}</p>`;
      return;
    }

    if (fullConversations.length > 0) {
      smsPreviewEl.innerHTML = fullConversations.map((conversation, index) => {
        const titleDate = conversation.timestamp ? new Date(Number(conversation.timestamp)).toLocaleString() : "";
        const unread = Number(conversation.unreadCount || 0);
        const messages = Array.isArray(conversation.messages) ? conversation.messages : [];
        const messagesHtml = messages.length > 0
          ? messages
              .map((message) => {
                const messageDate = message.timestamp ? new Date(Number(message.timestamp)).toLocaleString() : "";
                const prefix = message.isSentByMe ? "Yo" : (conversation.address || "Contacto");
                return `<div style="padding:6px 8px;border:1px solid #e2e8f0;border-radius:8px;margin-top:6px;background:${message.isSentByMe ? "#eef2ff" : "#f8fafc"};">
                  <p style="margin:0 0 4px 0;font-size:12px;color:#475569;"><strong>${prefix}</strong> · ${messageDate}</p>
                  <p style="margin:0;white-space:pre-wrap;word-break:break-word;">${message.body || "(vacío)"}</p>
                </div>`;
              })
              .join("")
          : '<p style="margin:0;color:#64748b;">Sin mensajes en esta conversación.</p>';

        return `<div class="preview-item">
          <h4>#${index + 1} · ${conversation.address || "Desconocido"}${unread > 0 ? ` · ${unread} sin leer` : ""}</h4>
          <p style="margin-top:4px;font-size:12px;color:#64748b;">${titleDate}</p>
          <div style="margin-top:8px;">${messagesHtml}</div>
        </div>`;
      }).join("");
      return;
    }

    smsPreviewEl.innerHTML = items.length > 0
      ? items.slice(0, 50).map((sms, index) => {
          const date = sms.timestamp ? new Date(Number(sms.timestamp)).toLocaleString() : "";
          const unread = Number(sms.unreadCount || 0);
          return `<div class="preview-item">
            <h4>#${index + 1} · ${sms.address || "Desconocido"}${unread > 0 ? ` · ${unread} sin leer` : ""}</h4>
            <p>${sms.preview || "(sin texto)"}</p>
            <p style="margin-top:4px;font-size:12px;color:#64748b;">${date}</p>
          </div>`;
        }).join("")
      : '<p style="margin:0;color:#64748b;">Sin SMS recientes o sin permisos de SMS.</p>';
  }

  function renderDeviceInfo(info) {
    const data = info && typeof info === "object" ? info : {};
    const rows = [
      ["App", data.appVersionName ? `${data.appVersionName} (${data.appVersionCode ?? "-"})` : "-"],
      ["Paquete", data.packageName || "-"],
      ["Dispositivo", [data.deviceManufacturer, data.deviceModel].filter(Boolean).join(" ") || "-"],
      ["Marca / producto", [data.deviceBrand, data.deviceProduct].filter(Boolean).join(" / ") || "-"],
      ["Android", data.androidRelease ? `${data.androidRelease} (API ${data.androidApiLevel ?? "-"})` : "-"],
      [
        "Batería",
        typeof data.batteryLevelPercent === "number"
          ? `${data.batteryLevelPercent}% · ${data.batteryCharging ? "cargando" : (data.batteryStatus || "")}`
          : "-"
      ]
    ];

    deviceInfoEl.innerHTML = rows
      .map(([key, value]) => `<div class="device-info-row"><div class="device-info-key">${key}</div><div class="device-info-val">${value || "-"}</div></div>`)
      .join("");
  }

  function applyConfigToForm(config) {
    byId("userName").value = config.userName || "";
    byId("lockedVolumePercent").value = Number.isFinite(config.lockedVolumePercent) ? String(config.lockedVolumePercent) : "";
    byId("backendServerUrl").value = config.backendServerUrl || "";
    byId("backendDeviceId").value = config.backendDeviceId || "";
    byId("backendApiToken").value = config.backendApiToken || "";
    byId("useWhatsApp").checked = config.useWhatsApp !== false;
    byId("protectDndMode").checked = config.protectDndMode !== false;
    byId("lockDeviceVolume").checked = config.lockDeviceVolume !== false;
    byId("backendSyncEnabled").checked = config.backendSyncEnabled === true;
    byId("navigationAnimationsEnabled").checked = config.navigationAnimationsEnabled !== false;
    byId("showSosButton").checked = config.showSosButton !== false;
    byId("sosPhoneNumber").value = config.sosPhoneNumber || "112";
  }

  function applyStateToForm(state) {
    const config = state?.currentConfig || {};
    const configSignature = stableSignature(config);
    const apps = Array.isArray(state?.availableApps) ? state.availableApps : [];
    const appsSignature = stableSignature(apps);
    const configChanged = configSignature !== lastAppliedConfigSignature;
    const appsChanged = appsSignature !== lastAppliedAppsSignature;

    if (appsChanged) {
      deviceApps = apps;
      lastAppliedAppsSignature = appsSignature;
      const validIds = new Set(deviceApps.map((app) => app.id));
      selectedAppIds = selectedAppIds.filter((id) => validIds.has(id));
    }

    if (configChanged) {
      applyConfigToForm(config);
      lastAppliedConfigSignature = configSignature;
      normalizeSelectedFromState(state);
    }

    renderDeviceInfo(state?.deviceInfo || {});
    if (configChanged || appsChanged) {
      renderAppLists();
    }
  }

  connectBtn.addEventListener("click", async () => {
    if (ws) ws.close();

    let ticket = "";
    try {
      ticket = await requestAdminWsTicket();
    } catch (error) {
      addEvent(`Error solicitando ticket WS admin: ${String(error)}`);
      setStatus("Error de autenticación", false);
      return;
    }

    const url = wsUrl(ticket);
    addEvent("Conectando WebSocket admin...");

    try {
      ws = new WebSocket(url);
    } catch (error) {
      addEvent(`Error abriendo WebSocket: ${String(error)}`);
      return;
    }

    setStatus("Conectando...", false);
    deviceOnline = false;
    hasPersistedConfig = false;
    refreshConfigAvailability();

    ws.onopen = () => {
      connected = true;
      setStatus("Conectado", true);
      addEvent("WS conectado");
      ws.send(JSON.stringify({ type: "getState", deviceId: deviceIdInput.value.trim() }));
    };

    ws.onclose = (event) => {
      connected = false;
      deviceOnline = false;
      hasPersistedConfig = false;
      refreshConfigAvailability();
      setStatus("Desconectado", false);
      addEvent(`WS cerrado. code=${event.code} reason=${event.reason || "sin detalle"}`);
    };

    ws.onerror = () => addEvent("WS error (revisa token, URL y logs del backend)");

    ws.onmessage = (event) => {
      let data = event.data;
      try { data = JSON.parse(event.data); } catch {}
      if (data && data.type === "deviceState") {
        deviceStateEl.textContent = JSON.stringify(data, null, 2);
        deviceOnline = data.state?.connected === true;
        hasPersistedConfig = !!data.state?.currentConfig;
        refreshConfigAvailability();
        applyStateToForm(data.state || {});
      }
      if (data && data.type === "devicePresence") {
        if (String(data.deviceId || "") === deviceIdInput.value.trim()) {
          deviceOnline = data.connected === true;
          refreshConfigAvailability();
        }
      }
      if (data && data.type === "deviceData") {
        if (data.dataType === "sms") {
          renderSmsPreview(data.smsPreview || [], data.smsConversations || [], data.error || "");
        }
      }
      if (data && data.type === "error") {
        addEvent(`ERROR: ${data.message || "error desconocido"}`);
      }
      addEvent(data);
    };
  });

  sendConfigBtn.addEventListener("click", () => {
    if (!requireConnected()) return;
    if (!hasPersistedConfig) {
      addEvent("No se puede configurar: el device aún no ha persistido su config");
      return;
    }

    const settings = {
      userName: byId("userName").value.trim() || undefined,
      useWhatsApp: byId("useWhatsApp").checked,
      protectDndMode: byId("protectDndMode").checked,
      lockDeviceVolume: byId("lockDeviceVolume").checked,
      backendSyncEnabled: byId("backendSyncEnabled").checked,
      backendServerUrl: byId("backendServerUrl").value.trim() || undefined,
      backendDeviceId: byId("backendDeviceId").value.trim() || undefined,
      backendApiToken: byId("backendApiToken").value.trim() || undefined,
      navigationAnimationsEnabled: byId("navigationAnimationsEnabled").checked,
      showSosButton: byId("showSosButton").checked,
      sosPhoneNumber: byId("sosPhoneNumber").value.trim() || undefined,
      selectedHomeAppIds: selectedAppIds,
      lockedVolumePercent: (() => {
        const raw = byId("lockedVolumePercent").value.trim();
        if (raw === "") return undefined;
        const n = Number(raw);
        return Number.isNaN(n) ? undefined : n;
      })()
    };

    ws.send(JSON.stringify({ type: "setConfig", deviceId: deviceIdInput.value.trim(), settings }));
  });

  runActionBtn.addEventListener("click", () => {
    if (!requireConnected()) return;

    const actionType = actionTypeEl.value;
    const phone = byId("actionPhone").value.trim();
    const name = byId("actionName").value.trim();
    let action = null;
    if (actionType === "call") {
      action = { type: "call", phone };
    } else if (actionType === "createContact") {
      action = { type: "createContact", name: name || "Nuevo contacto", phone };
    } else if (actionType === "markAllSmsRead") {
      action = { type: "markAllSmsRead" };
    } else if (actionType === "deleteAllSms") {
      action = { type: "deleteAllSms" };
    } else if (actionType === "killApp") {
      action = { type: "killApp" };
    }

    if (!action) {
      addEvent("Acción no soportada");
      return;
    }

    ws.send(JSON.stringify({ type: "runAction", deviceId: deviceIdInput.value.trim(), action }));
  });

  actionTypeEl.addEventListener("change", refreshActionFields);

  requestSmsBtn.addEventListener("click", () => {
    if (!requireConnected()) return;
    if (!deviceOnline) {
      addEvent("El dispositivo está desconectado");
      return;
    }
    renderSmsPreview([], [], "Solicitando SMS...");
    ws.send(JSON.stringify({ type: "requestData", deviceId: deviceIdInput.value.trim(), dataType: "sms" }));
  });

  renderSmsPreview([], []);
  renderDeviceInfo({});
  refreshActionFields();
  refreshConfigAvailability();
})();
