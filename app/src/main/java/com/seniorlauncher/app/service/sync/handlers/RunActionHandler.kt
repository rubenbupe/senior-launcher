package com.seniorlauncher.app.service.sync.handlers

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import com.seniorlauncher.app.data.repository.ContactsRepository
import com.seniorlauncher.app.data.repository.SmsRepository
import com.seniorlauncher.app.service.PhoneService
import com.seniorlauncher.app.service.sms.SmsService
import okhttp3.WebSocket
import org.json.JSONObject
import kotlin.system.exitProcess

class RunActionHandler(
    private val context: Context,
    private val smsRepo: SmsRepository,
    private val tag: String,
    private val sendResult: (WebSocket, String, Boolean, String) -> Unit,
) {
    fun handle(json: JSONObject, ws: WebSocket) {
        val commandId = json.optString("commandId", "")
        val actionObject = json.optJSONObject("action")
        val action = actionObject?.optString("type").orEmpty().trim()
            .ifEmpty { json.optString("action").trim() }

        Log.i(tag, "Action received: $action")

        when (action) {
            "call" -> handleCall(actionObject, commandId, ws)
            "createContact" -> handleCreateContact(actionObject, commandId, ws)
            "markAllSmsRead" -> handleMarkAllSmsRead(commandId, ws)
            "deleteAllSms" -> handleDeleteAllSms(commandId, ws)
            "killApp" -> handleKillApp(commandId, ws)
            else -> {
                Log.w(tag, "Action not supported: $action")
                sendResult(ws, commandId, false, "Action not supported: $action")
            }
        }
    }

    private fun handleCall(actionObject: JSONObject?, commandId: String, ws: WebSocket) {
        val phone = actionObject?.optString("phone").orEmpty().trim()
        if (phone.isBlank()) {
            sendResult(ws, commandId, false, "Empty phone number")
            return
        }
        runCatching { PhoneService.call(context, phone) }
            .onSuccess { sendResult(ws, commandId, true, "Call started") }
            .onFailure { e ->
                Log.e(tag, "Could not start call", e)
                sendResult(ws, commandId, false, e.message ?: "Error starting call")
            }
    }

    private fun handleCreateContact(actionObject: JSONObject?, commandId: String, ws: WebSocket) {
        if (context.checkSelfPermission(Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            sendResult(ws, commandId, false, "Missing permission WRITE_CONTACTS")
            return
        }
        val name = actionObject?.optString("name").orEmpty().trim().ifBlank { "Nuevo contacto" }
        val phone = actionObject?.optString("phone").orEmpty().trim()
        if (phone.isBlank()) {
            sendResult(ws, commandId, false, "Empty phone number")
            return
        }
        val inserted = runCatching {
            ContactsRepository.insert(context, name, phone, photoBytes = null)
        }.getOrDefault(false)

        if (inserted) sendResult(ws, commandId, true, "Contact created")
        else sendResult(ws, commandId, false, "Could not create contact")
    }

    private fun handleMarkAllSmsRead(commandId: String, ws: WebSocket) {
        if (context.checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            sendResult(ws, commandId, false, "Missing permission READ_SMS")
            return
        }
        if (!SmsService.canModify(context)) {
            sendResult(ws, commandId, false, "App is not default SMS app")
            return
        }
        val updated = runCatching { smsRepo.markAllAsRead(context) }.getOrDefault(0)
        sendResult(ws, commandId, true, "Marked as read: $updated")
    }

    private fun handleDeleteAllSms(commandId: String, ws: WebSocket) {
        if (context.checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            sendResult(ws, commandId, false, "Missing permission READ_SMS")
            return
        }
        if (!SmsService.canModify(context)) {
            sendResult(ws, commandId, false, "App is not default SMS app")
            return
        }
        val deleted = runCatching { smsRepo.deleteAll(context) }.getOrDefault(0)
        sendResult(ws, commandId, true, "Deleted SMS: $deleted")
    }

    private fun handleKillApp(commandId: String, ws: WebSocket) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (launchIntent == null) {
            sendResult(ws, commandId, false, "Launch intent not found")
            return
        }

        sendResult(ws, commandId, true, "App restart requested")

        Handler(Looper.getMainLooper()).postDelayed({
            runCatching {
                launchIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
                context.startActivity(launchIntent)
            }
            Handler(Looper.getMainLooper()).postDelayed({
                Process.killProcess(Process.myPid())
                exitProcess(0)
            }, 600L)
        }, 300L)
    }
}
