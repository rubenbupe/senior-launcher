

package com.seniorlauncher.app.service.sync.payload

import com.seniorlauncher.app.data.model.SmsConversation
import com.seniorlauncher.app.data.model.SmsItem
import org.json.JSONObject

fun SmsConversation.toPreviewJson(): JSONObject = JSONObject().apply {
    put("threadId", threadId)
    put("address", address)
    put("preview", preview)
    put("timestamp", timestamp)
    put("unreadCount", unreadCount)
}

fun SmsItem.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("body", body)
    put("timestamp", timestamp)
    put("isSentByMe", isSentByMe)
}
