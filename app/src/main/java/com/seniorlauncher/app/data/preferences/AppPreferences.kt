package com.seniorlauncher.app.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.seniorlauncher.app.defaultEnabledMiniAppIds
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

private const val PREFS_NAME = "senior_launcher_prefs"
private const val KEY_USER_NAME = "user_name"
private const val KEY_USE_WHATSAPP = "use_whatsapp"
private const val KEY_PIN = "pin"
private const val KEY_PROTECT_DND_MODE = "protect_dnd_mode"
private const val KEY_LOCK_DEVICE_VOLUME = "lock_device_volume"
private const val KEY_LOCKED_VOLUME_PERCENT = "locked_volume_percent"
private const val KEY_BACKEND_SYNC_ENABLED = "backend_sync_enabled"
private const val KEY_BACKEND_SERVER_URL = "backend_server_url"
private const val KEY_BACKEND_DEVICE_ID = "backend_device_id"
private const val KEY_BACKEND_API_TOKEN = "backend_api_token"
private const val KEY_NAV_ANIMATIONS_ENABLED = "nav_animations_enabled"
private const val KEY_SELECTED_HOME_APPS = "selected_home_apps"
private const val KEY_SHOW_SOS_BUTTON = "show_sos_button"
private const val KEY_SOS_PHONE_NUMBER = "sos_phone_number"

private const val DEFAULT_USER_NAME = "Jane"
private const val DEFAULT_USE_WHATSAPP = true
private const val DEFAULT_PIN = "1111"
private const val DEFAULT_PROTECT_DND_MODE = true
private const val DEFAULT_LOCK_DEVICE_VOLUME = true
private const val DEFAULT_LOCKED_VOLUME_PERCENT = 70
private const val DEFAULT_BACKEND_SYNC_ENABLED = false
private const val DEFAULT_BACKEND_SERVER_URL = ""
private const val DEFAULT_BACKEND_DEVICE_ID = ""
private const val DEFAULT_BACKEND_API_TOKEN = ""
private const val DEFAULT_NAV_ANIMATIONS_ENABLED = true
private const val DEFAULT_SHOW_SOS_BUTTON = true
private const val DEFAULT_SOS_PHONE_NUMBER = "112"

fun getPreferences(context: Context): SharedPreferences =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

fun getSavedUserName(context: Context): String =
    getPreferences(context).getString(KEY_USER_NAME, DEFAULT_USER_NAME) ?: DEFAULT_USER_NAME

fun setSavedUserName(context: Context, name: String) {
    getPreferences(context).edit(commit = true) { putString(KEY_USER_NAME, name) }
}

fun getUseWhatsApp(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_USE_WHATSAPP, DEFAULT_USE_WHATSAPP)

fun setUseWhatsApp(context: Context, use: Boolean) {
    getPreferences(context).edit(commit = true) { putBoolean(KEY_USE_WHATSAPP, use) }
}

fun getPin(context: Context): String =
    getPreferences(context).getString(KEY_PIN, DEFAULT_PIN) ?: DEFAULT_PIN

fun setPin(context: Context, pin: String) {
    getPreferences(context).edit(commit = true) { putString(KEY_PIN, pin) }
}

fun checkPin(context: Context, entered: String): Boolean =
    entered == getPin(context)

fun getProtectDndMode(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_PROTECT_DND_MODE, DEFAULT_PROTECT_DND_MODE)

fun setProtectDndMode(context: Context, enabled: Boolean) {
    getPreferences(context).edit(commit = true) {
        putBoolean(
            KEY_PROTECT_DND_MODE,
            enabled
        )
    }
}

fun getLockDeviceVolume(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_LOCK_DEVICE_VOLUME, DEFAULT_LOCK_DEVICE_VOLUME)

fun setLockDeviceVolume(context: Context, enabled: Boolean) {
    getPreferences(context).edit(commit = true) {
        putBoolean(
            KEY_LOCK_DEVICE_VOLUME,
            enabled
        )
    }
}

fun getLockedVolumePercent(context: Context): Int =
    getPreferences(context)
        .getInt(KEY_LOCKED_VOLUME_PERCENT, DEFAULT_LOCKED_VOLUME_PERCENT)
        .coerceIn(0, 100)

fun setLockedVolumePercent(context: Context, percent: Int) {
    getPreferences(context)
        .edit(commit = true) {
            putInt(KEY_LOCKED_VOLUME_PERCENT, percent.coerceIn(0, 100))
        }
}

fun getBackendSyncEnabled(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_BACKEND_SYNC_ENABLED, DEFAULT_BACKEND_SYNC_ENABLED)

fun setBackendSyncEnabled(context: Context, enabled: Boolean) {
    getPreferences(context).edit(commit = true) {
        putBoolean(
            KEY_BACKEND_SYNC_ENABLED,
            enabled
        )
    }
}

fun getBackendServerUrl(context: Context): String =
    getPreferences(context)
        .getString(KEY_BACKEND_SERVER_URL, DEFAULT_BACKEND_SERVER_URL)
        .orEmpty()
        .trim()

fun setBackendServerUrl(context: Context, url: String) {
    getPreferences(context).edit(commit = true) {
        putString(
            KEY_BACKEND_SERVER_URL,
            url.trim()
        )
    }
}

fun getBackendDeviceId(context: Context): String =
    getPreferences(context)
        .getString(KEY_BACKEND_DEVICE_ID, DEFAULT_BACKEND_DEVICE_ID)
        .orEmpty()
        .trim()

fun setBackendDeviceId(context: Context, deviceId: String) {
    getPreferences(context).edit(commit = true) {
        putString(
            KEY_BACKEND_DEVICE_ID,
            deviceId.trim()
        )
    }
}

fun getBackendApiToken(context: Context): String =
    getPreferences(context)
        .getString(KEY_BACKEND_API_TOKEN, DEFAULT_BACKEND_API_TOKEN)
        .orEmpty()
        .trim()

fun setBackendApiToken(context: Context, token: String) {
    getPreferences(context).edit(commit = true) {
        putString(
            KEY_BACKEND_API_TOKEN,
            token.trim()
        )
    }
}

fun getNavigationAnimationsEnabled(context: Context): Boolean =
    getPreferences(context)
        .getBoolean(KEY_NAV_ANIMATIONS_ENABLED, DEFAULT_NAV_ANIMATIONS_ENABLED)

fun setNavigationAnimationsEnabled(context: Context, enabled: Boolean) {
    getPreferences(context).edit(commit = true) {
        putBoolean(
            KEY_NAV_ANIMATIONS_ENABLED,
            enabled
        )
    }
}

fun getSelectedHomeAppIds(context: Context): List<String> {
    val raw = getPreferences(context)
        .getString(KEY_SELECTED_HOME_APPS, "")
        .orEmpty()
    if (raw.isBlank()) return defaultEnabledMiniAppIds()
    return raw
        .split('|')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
}

fun setSelectedHomeAppIds(context: Context, ids: List<String>) {
    val normalized = ids
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString("|")
    getPreferences(context).edit(commit = true) {
        putString(
            KEY_SELECTED_HOME_APPS,
            normalized
        )
    }
}

fun getEnabledHomeAppIds(context: Context): Set<String> =
    getSelectedHomeAppIds(context).toSet()

fun setEnabledHomeAppIds(context: Context, ids: Set<String>) {
    val current = getSelectedHomeAppIds(context)
    val normalized = current.filter { it in ids } + ids.filterNot { it in current }
    setSelectedHomeAppIds(context, normalized)
}

fun getHomeAppOrderIds(context: Context): List<String> =
    getSelectedHomeAppIds(context)

fun setHomeAppOrderIds(context: Context, ids: List<String>) {
    setSelectedHomeAppIds(context, ids)
}

fun getShowSosButton(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_SHOW_SOS_BUTTON, DEFAULT_SHOW_SOS_BUTTON)

fun setShowSosButton(context: Context, enabled: Boolean) {
    getPreferences(context).edit(commit = true) { putBoolean(KEY_SHOW_SOS_BUTTON, enabled) }
}

fun getSosPhoneNumber(context: Context): String =
    getPreferences(context)
        .getString(KEY_SOS_PHONE_NUMBER, DEFAULT_SOS_PHONE_NUMBER)
        .orEmpty()
        .trim()
        .ifBlank { DEFAULT_SOS_PHONE_NUMBER }

fun setSosPhoneNumber(context: Context, phoneNumber: String) {
    val normalized = phoneNumber.trim().ifBlank { DEFAULT_SOS_PHONE_NUMBER }
    getPreferences(context).edit(commit = true) {
        putString(
            KEY_SOS_PHONE_NUMBER,
            normalized
        )
    }
}


data class AppSettingsSnapshot(
    val userName: String,
    val useWhatsApp: Boolean,
    val protectDndMode: Boolean,
    val lockDeviceVolume: Boolean,
    val lockedVolumePercent: Int,
    val backendSyncEnabled: Boolean,
    val backendServerUrl: String,
    val backendDeviceId: String,
    val backendApiToken: String,
    val navigationAnimationsEnabled: Boolean,
    val showSosButton: Boolean,
    val sosPhoneNumber: String,
    val selectedHomeAppIds: List<String>
)

object AppSettingsService {
    fun read(context: Context): AppSettingsSnapshot = AppSettingsSnapshot(
        userName = getSavedUserName(context),
        useWhatsApp = getUseWhatsApp(context),
        protectDndMode = getProtectDndMode(context),
        lockDeviceVolume = getLockDeviceVolume(context),
        lockedVolumePercent = getLockedVolumePercent(context),
        backendSyncEnabled = getBackendSyncEnabled(context),
        backendServerUrl = getBackendServerUrl(context),
        backendDeviceId = getBackendDeviceId(context),
        backendApiToken = getBackendApiToken(context),
        navigationAnimationsEnabled = getNavigationAnimationsEnabled(context),
        showSosButton = getShowSosButton(context),
        sosPhoneNumber = getSosPhoneNumber(context),
        selectedHomeAppIds = getSelectedHomeAppIds(context)
    )

    fun save(context: Context, snapshot: AppSettingsSnapshot) {
        setSavedUserName(context, snapshot.userName)
        setUseWhatsApp(context, snapshot.useWhatsApp)
        setProtectDndMode(context, snapshot.protectDndMode)
        setLockDeviceVolume(context, snapshot.lockDeviceVolume)
        setLockedVolumePercent(context, snapshot.lockedVolumePercent)
        setBackendSyncEnabled(context, snapshot.backendSyncEnabled)
        setBackendServerUrl(context, snapshot.backendServerUrl)
        setBackendDeviceId(context, snapshot.backendDeviceId)
        setBackendApiToken(context, snapshot.backendApiToken)
        setNavigationAnimationsEnabled(context, snapshot.navigationAnimationsEnabled)
        setShowSosButton(context, snapshot.showSosButton)
        setSosPhoneNumber(context, snapshot.sosPhoneNumber)
        setSelectedHomeAppIds(context, snapshot.selectedHomeAppIds)
    }

    fun observe(context: Context): Flow<AppSettingsSnapshot> = callbackFlow {
        val prefs = getPreferences(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(read(context))
        }

        trySend(read(context))
        prefs.registerOnSharedPreferenceChangeListener(listener)

        awaitClose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }.distinctUntilChanged()
}
