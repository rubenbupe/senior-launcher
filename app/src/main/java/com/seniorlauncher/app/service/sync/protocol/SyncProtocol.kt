package com.seniorlauncher.app.service.sync.protocol

import android.util.Log
import okhttp3.WebSocket
import org.json.JSONObject

class SyncProtocol(
    private val tag: String,
    private val onSetConfig: (JSONObject, WebSocket) -> Unit,
    private val onRunAction: (JSONObject, WebSocket) -> Unit,
    private val onRequestData: suspend (JSONObject, WebSocket) -> Unit,
) {
    suspend fun onMessage(ws: WebSocket, text: String) {
        runCatching {
            val json = JSONObject(text)
            when (json.optString("type")) {
                "setConfig"   -> onSetConfig(json, ws)
                "runAction"   -> onRunAction(json, ws)
                "requestData" -> onRequestData(json, ws)
            }
        }.onFailure { Log.e(tag, "Error processing message", it) }
    }
}
