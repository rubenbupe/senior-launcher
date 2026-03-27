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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.WebSocket
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

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
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

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
                val apiToken = prefs.getApiToken().trim()
                if (apiToken.isBlank()) {
                    Log.e(TAG, "Missing API token for sync")
                    return@launchConnectionLoop null
                }
                val wsBaseUrl = toWebSocketBaseUrl(prefs.getServerUrl()) ?: run {
                    Log.e(TAG, "Invalid URL: ${prefs.getServerUrl()}")
                    return@launchConnectionLoop null
                }
                val ticket = requestDeviceWsTicket(
                    wsBaseUrl = wsBaseUrl,
                    deviceId = prefs.getDeviceId(),
                    apiToken = apiToken
                ) ?: return@launchConnectionLoop null
                val encodedId = URLEncoder.encode(prefs.getDeviceId(), Charsets.UTF_8.name())
                WsConfig(
                    url = "$wsBaseUrl/ws/device?deviceId=$encodedId&ticket=${URLEncoder.encode(ticket, Charsets.UTF_8.name())}",
                    bearerToken = ""
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

    private suspend fun requestDeviceWsTicket(
        wsBaseUrl: String,
        deviceId: String,
        apiToken: String
    ): String? = withContext(Dispatchers.IO) {
        val httpBaseUrl = when {
            wsBaseUrl.startsWith("wss://") -> "https://${wsBaseUrl.removePrefix("wss://")}"
            wsBaseUrl.startsWith("ws://") -> "http://${wsBaseUrl.removePrefix("ws://")}"
            else -> return@withContext null
        }

        val body = JSONObject().apply {
            put("deviceId", deviceId)
        }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url("$httpBaseUrl/auth/ticket/device")
            .addHeader("x-app-token", "$apiToken")
            .post(body)
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Ticket request failed: HTTP ${response.code}")
                    return@use null
                }
                val responseBody = response.body?.string().orEmpty()
                val ticket = runCatching { JSONObject(responseBody).optString("ticket", "") }
                    .getOrDefault("")
                    .trim()
                if (ticket.isBlank()) {
                    Log.e(TAG, "Ticket response missing ticket")
                    return@use null
                }
                ticket
            }
        }.onFailure {
            Log.e(TAG, "Ticket request error", it)
        }.getOrNull()
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
