package com.seniorlauncher.app.service.sync.handlers

import android.util.Log
import com.seniorlauncher.app.data.preferences.AppPreferencesRepository
import com.seniorlauncher.app.service.sync.json.toStringList
import okhttp3.WebSocket
import org.json.JSONObject

class SetConfigHandler(
    private val prefs: AppPreferencesRepository,
    private val tag: String,
    private val sendResult: (WebSocket, String, Boolean, String) -> Unit,
    private val onConfigApplied: () -> Unit,
) {
    fun handle(json: JSONObject, ws: WebSocket) {
        val commandId = json.optString("commandId", "")
        val settings = json.optJSONObject("settings") ?: JSONObject()
        runCatching {
            applySettings(settings)
            sendResult(ws, commandId, true, "Config applied")
            onConfigApplied()
        }.onFailure { error ->
            Log.e(tag, "Error applying setConfig", error)
            sendResult(ws, commandId, false, error.message ?: "Error applying config")
        }
    }

    private fun applySettings(settings: JSONObject) {
        val stringSettings: Map<String, (String) -> Unit> = mapOf(
            "userName" to { v -> prefs.setUserName(v.ifBlank { prefs.getUserName() }) },
            "backendServerUrl" to { v -> prefs.setServerUrl(v) },
            "backendDeviceId" to { v -> prefs.setDeviceId(v) },
            "backendApiToken" to { v -> prefs.setApiToken(v) },
            "sosPhoneNumber" to { v -> prefs.setSosPhoneNumber(v) },
        )
        stringSettings.forEach { (key, setter) ->
            if (settings.has(key)) setter(settings.optString(key))
        }

        val boolSettings: Map<String, (Boolean) -> Unit> = mapOf(
            "useWhatsApp" to { v -> prefs.setUseWhatsApp(v) },
            "protectDndMode" to { v -> prefs.setProtectDndMode(v) },
            "lockDeviceVolume" to { v -> prefs.setLockDeviceVolume(v) },
            "backendSyncEnabled" to { v -> prefs.setSyncEnabled(v) },
            "navigationAnimationsEnabled" to { v -> prefs.setNavigationAnimationsEnabled(v) },
            "showSosButton" to { v -> prefs.setShowSosButton(v) },
        )
        boolSettings.forEach { (key, setter) ->
            if (settings.has(key)) setter(settings.optBoolean(key))
        }

        if (settings.has("lockedVolumePercent")) {
            prefs.setLockedVolumePercent(settings.optInt("lockedVolumePercent").coerceIn(0, 100))
        }

        val appIdsKey = listOf("selectedHomeAppIds", "orderedHomeAppIds", "enabledHomeAppIds")
            .firstOrNull { settings.has(it) }
        if (appIdsKey != null) {
            prefs.setSelectedHomeAppIds(settings.optJSONArray(appIdsKey).toStringList())
        }
    }
}
