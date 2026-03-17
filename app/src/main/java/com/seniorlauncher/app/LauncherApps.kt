package com.seniorlauncher.app

data class AllowedAppOption(
    val id: String,
    val label: String,
    val isMiniApp: Boolean,
    val packageName: String? = null
)

const val MINI_APP_PHONE_ID = "mini:phone"
const val MINI_APP_CAMERA_ID = "mini:camera"
const val MINI_APP_GALLERY_ID = "mini:gallery"
const val MINI_APP_SMS_ID = "mini:sms"
const val MINI_APP_FLASHLIGHT_ID = "mini:flashlight"

fun defaultEnabledMiniAppIds(): List<String> = listOf(
    MINI_APP_PHONE_ID,
    MINI_APP_SMS_ID,
    MINI_APP_CAMERA_ID,
    MINI_APP_GALLERY_ID,
    MINI_APP_FLASHLIGHT_ID
)

fun getMiniAppOptions(): List<AllowedAppOption> = listOf(
    AllowedAppOption(MINI_APP_PHONE_ID, "Teléfono", isMiniApp = true),
    AllowedAppOption(MINI_APP_CAMERA_ID, "Cámara", isMiniApp = true),
    AllowedAppOption(MINI_APP_GALLERY_ID, "Galería", isMiniApp = true),
    AllowedAppOption(MINI_APP_SMS_ID, "Mensajes", isMiniApp = true),
    AllowedAppOption(MINI_APP_FLASHLIGHT_ID, "Linterna", isMiniApp = true)
)
