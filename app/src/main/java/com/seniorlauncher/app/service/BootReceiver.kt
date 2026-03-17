package com.seniorlauncher.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.seniorlauncher.app.service.sync.SyncService

/**
 * Receiver que inicia el servicio de sincronización al arrancar el dispositivo
 * si la sincronización está habilitada en las preferencias
 */
class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }
        
        Log.i(TAG, "Device booted, checking if syncing is needed")

        SyncService.startIfConfigured(context)
    }
}
