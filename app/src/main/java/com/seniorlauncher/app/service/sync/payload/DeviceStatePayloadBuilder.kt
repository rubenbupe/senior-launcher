package com.seniorlauncher.app.service.sync.payload

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import com.seniorlauncher.app.data.preferences.AppPreferencesRepository
import com.seniorlauncher.app.service.AppsService
import org.json.JSONArray
import org.json.JSONObject

class DeviceStatePayloadBuilder(ctx: Context) {

    private val prefs = AppPreferencesRepository(ctx)
    private val context = ctx

    fun build(): String {
        val selectedHomeAppIds = prefs.getSelectedHomeAppIds()
        val selectedSet = selectedHomeAppIds.toSet()
        val availableApps = AppsService.getAvailableApps(context)

        val appsJson = JSONArray().apply {
            availableApps.forEach { app ->
                put(JSONObject().apply {
                    put("id", app.id)
                    put("label", app.label)
                    put("isMiniApp", app.isMiniApp)
                    put("packageName", app.packageName)
                    put("enabled", selectedSet.contains(app.id))
                    put("order", selectedHomeAppIds.indexOf(app.id).takeIf { it >= 0 } ?: 999)
                })
            }
        }

        val configJson = JSONObject().apply {
            put("userName", prefs.getUserName())
            put("useWhatsApp", prefs.getUseWhatsApp())
            put("protectDndMode", prefs.getProtectDndMode())
            put("lockDeviceVolume", prefs.getLockDeviceVolume())
            put("lockedVolumePercent", prefs.getLockedVolumePercent())
            put("backendSyncEnabled", prefs.getSyncEnabled())
            put("backendServerUrl", prefs.getServerUrl())
            put("backendDeviceId", prefs.getDeviceId())
            put("backendApiToken", prefs.getApiToken())
            put("navigationAnimationsEnabled", prefs.getNavigationAnimationsEnabled())
            put("showSosButton", prefs.getShowSosButton())
            put("sosPhoneNumber", prefs.getSosPhoneNumber())
            put("selectedHomeAppIds", JSONArray(selectedHomeAppIds))
        }

        return JSONObject().apply {
            put("type", "deviceState")
            put("deviceInfo", buildDeviceInfoJson(context))
            put("currentConfig", configJson)
            put("availableApps", appsJson)
        }.toString()
    }

    private fun buildDeviceInfoJson(context: Context): JSONObject {
        val packageInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
        }.getOrNull()

        val batteryIntent = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()

        val batteryLevel = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val batteryScale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (batteryLevel >= 0 && batteryScale > 0) {
            ((batteryLevel * 100f) / batteryScale).toInt().coerceIn(0, 100)
        } else null

        val batteryStatusCode = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val batteryStatus = when (batteryStatusCode) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
            BatteryManager.BATTERY_STATUS_FULL -> "full"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not_charging"
            else -> "unknown"
        }
        val batteryCharging = batteryStatusCode == BatteryManager.BATTERY_STATUS_CHARGING ||
                batteryStatusCode == BatteryManager.BATTERY_STATUS_FULL

        val longVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) packageInfo?.longVersionCode else 0L

        return JSONObject().apply {
            put("packageName", context.packageName)
            put("appVersionName", packageInfo?.versionName ?: "desconocida")
            put("appVersionCode", longVersionCode)
            put("deviceManufacturer", Build.MANUFACTURER ?: "")
            put("deviceBrand", Build.BRAND ?: "")
            put("deviceModel", Build.MODEL ?: "")
            put("deviceProduct", Build.PRODUCT ?: "")
            put("androidRelease", Build.VERSION.RELEASE ?: "")
            put("androidApiLevel", Build.VERSION.SDK_INT)
            if (batteryPct != null) put("batteryLevelPercent", batteryPct)
            put("batteryCharging", batteryCharging)
            put("batteryStatus", batteryStatus)
        }
    }
}
