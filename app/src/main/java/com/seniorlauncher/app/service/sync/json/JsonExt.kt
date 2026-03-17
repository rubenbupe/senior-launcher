package com.seniorlauncher.app.service.sync.json

import org.json.JSONArray

fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { optString(it).trim().ifEmpty { null } }
}

