package com.seniorlauncher.app.service.sync

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.seniorlauncher.app.data.preferences.AppSettingsService
import com.seniorlauncher.app.data.preferences.AppPreferencesRepository
import com.seniorlauncher.app.data.repository.SmsRepository
import com.seniorlauncher.app.service.sync.handlers.RequestDataHandler
import com.seniorlauncher.app.service.sync.handlers.RunActionHandler
import com.seniorlauncher.app.service.sync.handlers.SetConfigHandler
import com.seniorlauncher.app.service.sync.notifications.SyncNotifier
import com.seniorlauncher.app.service.sync.payload.DeviceStatePayloadBuilder
import com.seniorlauncher.app.service.sync.protocol.SyncProtocol
import com.seniorlauncher.app.service.sync.ws.WsClient
import com.seniorlauncher.app.service.sync.ws.WsConfig
import com.seniorlauncher.app.service.sync.ws.toWebSocketBaseUrl
import kotlinx.coroutines.*
import okhttp3.WebSocket
import org.json.JSONObject
import java.net.URLEncoder

class SyncService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "senior_launcher_sync"
        private const val TAG = "SyncForegroundService"
        private const val RECONNECT_BASE_DELAY_MS = 5_000L
        private const val RECONNECT_MAX_DELAY_MS = 30_000L
        private const val PERIODIC_STATE_INTERVAL_MS = 60_000L
        private const val POLL_INTERVAL_MS = 10_000L

        fun start(context: Context) {
            val intent = Intent(context, SyncService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SyncService::class.java))
        }

        fun startIfConfigured(context: Context) {
            val prefs = AppPreferencesRepository(context)
            if (prefs.getSyncEnabled() && prefs.getServerUrl().isNotBlank() && prefs.getDeviceId().isNotBlank())
                start(context)
            else stop(context)
        }

        fun restart(context: Context) {
            val appContext = context.applicationContext
            stop(appContext)
            Handler(Looper.getMainLooper()).postDelayed({ start(appContext) }, 250L)
        }

        fun restartIfConfigured(context: Context) {
            val prefs = AppPreferencesRepository(context)
            if (prefs.getSyncEnabled() && prefs.getServerUrl().isNotBlank() && prefs.getDeviceId().isNotBlank()) {
                restart(context)
            } else {
                stop(context)
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var periodicStateJob: Job? = null
    private var syncSettingsObserverJob: Job? = null

    private lateinit var notifier: SyncNotifier
    private lateinit var payloadBuilder: DeviceStatePayloadBuilder
    private lateinit var wsClient: WsClient
    private lateinit var protocol: SyncProtocol
    private lateinit var prefs: AppPreferencesRepository

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate")

        prefs = AppPreferencesRepository(this)
        notifier = SyncNotifier(this, CHANNEL_ID, NOTIFICATION_ID).apply { ensureChannel() }
        payloadBuilder = DeviceStatePayloadBuilder(this)

        val smsRepo = SmsRepository
        val setConfigHandler = SetConfigHandler(
            prefs = prefs,
            tag = TAG,
            sendResult = ::sendCommandResult,
            onConfigApplied = {
                wsClient.currentSocket?.let { ws ->
                    serviceScope.launch { sendCurrentDeviceState(ws) }
                }
            }
        )
        val runActionHandler = RunActionHandler(
            context = this,
            smsRepo = smsRepo,
            tag = TAG,
            sendResult = ::sendCommandResult,
        )
        val requestDataHandler = RequestDataHandler(
            context = this,
            smsRepo = smsRepo,
            tag = TAG,
        )

        protocol = SyncProtocol(
            tag = TAG,
            onSetConfig = setConfigHandler::handle,
            onRunAction = runActionHandler::handle,
            onRequestData = { json, ws ->
                withContext(Dispatchers.IO) { requestDataHandler.handle(json, ws) }
            },
        )

        wsClient = WsClient(
            scope = serviceScope,
            tag = TAG,
            baseDelayMs = RECONNECT_BASE_DELAY_MS,
            maxDelayMs = RECONNECT_MAX_DELAY_MS,
            pollIntervalMs = POLL_INTERVAL_MS,
            onStatus = { notifier.update(it) },
            onOpen = { ws ->
                sendCurrentDeviceState(ws)
                periodicStateJob?.cancel()
                periodicStateJob = serviceScope.launch {
                    while (isActive) {
                        delay(PERIODIC_STATE_INTERVAL_MS)
                        sendCurrentDeviceState(ws)
                    }
                }
            },
            onTextMessage = { ws, text -> protocol.onMessage(ws, text) },
            onClosing = {
                periodicStateJob?.cancel()
                periodicStateJob = null
            },
            onFailure = {},
        )

        observeSyncSettingsChanges()
    }

    private fun observeSyncSettingsChanges() {
        syncSettingsObserverJob?.cancel()
        syncSettingsObserverJob = serviceScope.launch {
            var lastSignature: String? = null
            AppSettingsService.observe(this@SyncService).collect { snapshot ->
                val currentSignature = listOf(
                    snapshot.backendSyncEnabled.toString(),
                    snapshot.backendServerUrl,
                    snapshot.backendDeviceId,
                    snapshot.backendApiToken
                ).joinToString("|")

                val previous = lastSignature
                lastSignature = currentSignature

                if (previous != null && previous != currentSignature) {
                    Log.i(TAG, "Sync config changed, restarting sync service")
                    restartIfConfigured(this@SyncService)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val isAlreadyRunning = wsClient.currentSocket != null
        val notifText = if (isAlreadyRunning) "Connected and syncing" else "Connecting to server..."
        startForeground(NOTIFICATION_ID, notifier.build(notifText))

        if (!isAlreadyRunning) {
            wsClient.launchConnectionLoop {
                if (!prefs.getSyncEnabled() || prefs.getServerUrl().isBlank() || prefs.getDeviceId().isBlank()) {
                    return@launchConnectionLoop null
                }
                val wsBaseUrl = toWebSocketBaseUrl(prefs.getServerUrl()) ?: run {
                    Log.e(TAG, "Invalid URL: ${prefs.getServerUrl()}")
                    return@launchConnectionLoop null
                }
                val encodedId = URLEncoder.encode(prefs.getDeviceId(), Charsets.UTF_8.name())
                WsConfig(
                    url = "$wsBaseUrl/ws?role=device&deviceId=$encodedId",
                    bearerToken = prefs.getApiToken()
                )
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.i(TAG, "onDestroy")
        syncSettingsObserverJob?.cancel()
        periodicStateJob?.cancel()
        periodicStateJob = null
        wsClient.shutdownClient()
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun sendCurrentDeviceState(ws: WebSocket) {
        runCatching {
            val payload = withContext(Dispatchers.IO) { payloadBuilder.build() }
            ws.send(payload)
            Log.d(TAG, "Device state sent")
        }.onFailure { Log.e(TAG, "Error sending device state", it) }
    }

    private fun sendCommandResult(ws: WebSocket, commandId: String, success: Boolean, message: String) {
        ws.send(JSONObject().apply {
            put("type", "commandResult")
            put("commandId", commandId)
            put("success", success)
            put("message", message)
        }.toString())
    }
}
