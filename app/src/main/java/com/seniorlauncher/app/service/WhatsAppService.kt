package com.seniorlauncher.app.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.seniorlauncher.app.data.model.Contact
import kotlin.text.replace

object WhatsAppService {
    fun openWhatsAppToContact(context: Context, phone: String?) {
        if (phone.isNullOrBlank()) {
            openWhatsApp(context)
            return
        }
        val normalized = phone.replace(Regex("[^0-9+]"), "").trimStart('+')

        val uri = Uri.parse("smsto:" + normalized)
        val i = Intent(Intent.ACTION_SENDTO, uri)
        i.setPackage("com.whatsapp")
        context.startActivity(i)

    }

    private fun openWhatsApp(context: Context) {
        val i = Intent(Intent.ACTION_MAIN).apply {
            `package` = "com.whatsapp"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(i) }
    }
}