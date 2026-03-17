package com.seniorlauncher.app.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri

object PhoneService {

    fun sanitizePhone(raw: String): String =
        raw.replace(Regex("[^0-9+]"), "").trim()

    fun hasCallPermission(context: Context): Boolean =
        context.checkSelfPermission(android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

    fun call(context: Context, rawPhone: String) {
        val phone = sanitizePhone(rawPhone)
        val uri = "tel:$phone".toUri()
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        if (telecomManager != null) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CALL_PHONE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                throw UnsupportedOperationException("Missing permission CALL_PHONE")
            }
            telecomManager.placeCall(uri, Bundle())
        } else {
            context.startActivity(Intent(Intent.ACTION_CALL).apply {
                data = uri
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        }
    }
}
