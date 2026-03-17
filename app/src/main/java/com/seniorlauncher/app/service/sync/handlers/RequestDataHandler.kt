package com.seniorlauncher.app.service.sync.handlers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.seniorlauncher.app.data.repository.SmsRepository
import com.seniorlauncher.app.service.sync.payload.toJson
import com.seniorlauncher.app.service.sync.payload.toPreviewJson
import okhttp3.WebSocket
import org.json.JSONArray
import org.json.JSONObject

class RequestDataHandler(
    private val context: Context,
    private val smsRepo: SmsRepository,
    private val tag: String,
) {
    suspend fun handle(json: JSONObject, ws: WebSocket) {
        val dataType = json.optString("dataType", "")
        val requestId = json.optString("requestId")
        Log.i(tag, "Data request: $dataType")
        when (dataType) {
            "sms" -> sendSmsData(ws, requestId)
            else -> Log.w(tag, "Data type not supported: $dataType")
        }
    }

    private fun sendSmsData(ws: WebSocket, requestId: String?) {
        fun basePayload(extra: JSONObject.() -> Unit): String =
            JSONObject().apply {
                put("type", "deviceData")
                put("dataType", "sms")
                if (!requestId.isNullOrBlank()) put("requestId", requestId)
                extra()
            }.toString()

        if (context.checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            ws.send(basePayload {
                put("smsPreview", JSONArray())
                put("smsConversations", JSONArray())
                put("error", "Missing permission READ_SMS")
            })
            return
        }

        runCatching {
            val conversations = smsRepo.loadConversations(context)
            val previewArray = JSONArray().apply {
                conversations.forEach { put(it.toPreviewJson()) }
            }
            val conversationsArray = JSONArray().apply {
                conversations.forEach { conv ->
                    val messages = smsRepo.loadThreadMessages(context, conv.threadId)
                    val convJson = conv.toPreviewJson().apply {
                        put("messages", JSONArray().apply {
                            messages.forEach { msg -> put(msg.toJson()) }
                        })
                    }
                    put(convJson)
                }
            }
            ws.send(basePayload {
                put("smsPreview", previewArray)
                put("smsConversations", conversationsArray)
            })
            Log.d(tag, "Sent (${conversations.size} SMS threads)")
        }.onFailure { e ->
            Log.e(tag, "Error sending SMS threads", e)
            ws.send(basePayload {
                put("smsPreview", JSONArray())
                put("smsConversations", JSONArray())
                put("error", e.message ?: "Error reading SMS")
            })
        }
    }
}
