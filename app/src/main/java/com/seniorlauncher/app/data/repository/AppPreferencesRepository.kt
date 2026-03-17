package com.seniorlauncher.app.data.preferences

import android.content.Context

class AppPreferencesRepository(private val ctx: Context) {

    fun getUserName(): String = getSavedUserName(ctx)
    fun getUseWhatsApp(): Boolean = com.seniorlauncher.app.data.preferences.getUseWhatsApp(ctx)
    fun getProtectDndMode(): Boolean = com.seniorlauncher.app.data.preferences.getProtectDndMode(ctx)
    fun getLockDeviceVolume(): Boolean = com.seniorlauncher.app.data.preferences.getLockDeviceVolume(ctx)
    fun getLockedVolumePercent(): Int = com.seniorlauncher.app.data.preferences.getLockedVolumePercent(ctx)
    fun getSyncEnabled(): Boolean = getBackendSyncEnabled(ctx)
    fun getServerUrl(): String = getBackendServerUrl(ctx)
    fun getDeviceId(): String = getBackendDeviceId(ctx)
    fun getApiToken(): String = getBackendApiToken(ctx)
    fun getNavigationAnimationsEnabled(): Boolean = com.seniorlauncher.app.data.preferences.getNavigationAnimationsEnabled(ctx)
    fun getShowSosButton(): Boolean = com.seniorlauncher.app.data.preferences.getShowSosButton(ctx)
    fun getSosPhoneNumber(): String = com.seniorlauncher.app.data.preferences.getSosPhoneNumber(ctx)
    fun getSelectedHomeAppIds(): List<String> = com.seniorlauncher.app.data.preferences.getSelectedHomeAppIds(ctx)

    fun setUserName(v: String): Unit = setSavedUserName(ctx, v)
    fun setUseWhatsApp(v: Boolean): Unit = com.seniorlauncher.app.data.preferences.setUseWhatsApp(ctx, v)
    fun setProtectDndMode(v: Boolean): Unit = com.seniorlauncher.app.data.preferences.setProtectDndMode(ctx, v)
    fun setLockDeviceVolume(v: Boolean): Unit = com.seniorlauncher.app.data.preferences.setLockDeviceVolume(ctx, v)
    fun setLockedVolumePercent(v: Int): Unit = com.seniorlauncher.app.data.preferences.setLockedVolumePercent(ctx, v)
    fun setSyncEnabled(v: Boolean): Unit = setBackendSyncEnabled(ctx, v)
    fun setServerUrl(v: String): Unit = setBackendServerUrl(ctx, v)
    fun setDeviceId(v: String): Unit = setBackendDeviceId(ctx, v)
    fun setApiToken(v: String): Unit = setBackendApiToken(ctx, v)
    fun setNavigationAnimationsEnabled(v: Boolean): Unit = com.seniorlauncher.app.data.preferences.setNavigationAnimationsEnabled(ctx, v)
    fun setShowSosButton(v: Boolean): Unit = com.seniorlauncher.app.data.preferences.setShowSosButton(ctx, v)
    fun setSosPhoneNumber(v: String): Unit = com.seniorlauncher.app.data.preferences.setSosPhoneNumber(ctx, v)
    fun setSelectedHomeAppIds(v: List<String>): Unit = com.seniorlauncher.app.data.preferences.setSelectedHomeAppIds(ctx, v)
}
