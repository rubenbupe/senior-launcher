package com.seniorlauncher.app.service.sync.ws

import android.util.Log

private const val TAG = "WsUrlUtils"

fun toWebSocketBaseUrl(serverUrl: String): String? {
    val raw = serverUrl.trim().trimEnd('/')
    if (raw.isBlank()) return null

    val normalizedInput = if (raw.contains("://")) raw else "http://$raw"
    val uri = runCatching { java.net.URI(normalizedInput) }.getOrNull() ?: return null
    val host = uri.host?.trim().orEmpty()
    val port = if (uri.port > 0) ":${uri.port}" else ""

    if (host.isBlank()) return null
    if (host == "0.0.0.0") {
        Log.e(TAG, "Host 0.0.0.0 no es válido desde Android. Usa la IP LAN del servidor")
        return null
    }

    val wsScheme = when (uri.scheme?.lowercase()) {
        "https", "wss" -> "wss"
        "http", "ws", null -> "ws"
        else -> return null
    }

    return "$wsScheme://$host$port"
}
