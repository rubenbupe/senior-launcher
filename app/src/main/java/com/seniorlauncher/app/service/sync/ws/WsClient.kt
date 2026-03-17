package com.seniorlauncher.app.service.sync.ws

import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import java.util.concurrent.TimeUnit

class WsClient(
    private val scope: CoroutineScope,
    private val tag: String,
    private val baseDelayMs: Long,
    private val maxDelayMs: Long,
    private val pollIntervalMs: Long,
    private val onStatus: (String) -> Unit,
    private val onOpen: suspend (WebSocket) -> Unit,
    private val onTextMessage: suspend (WebSocket, String) -> Unit,
    private val onClosing: () -> Unit,
    private val onFailure: () -> Unit,
) {
    private var reconnectAttempts = 0
    private var connectJob: Job? = null
    var currentSocket: WebSocket? = null
        private set

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun launchConnectionLoop(wsConfigProvider: () -> WsConfig?) {
        if (connectJob?.isActive == true) return
        connectJob = scope.launch {
            while (isActive) {
                val cfg = wsConfigProvider()
                if (cfg == null) {
                    Log.d(tag, "Sync disabled")
                    onStatus("Sync paused")
                    delay(pollIntervalMs)
                    continue
                }

                try {
                    connectAndWait(cfg)
                    if (!isActive) break
                    reconnectAttempts++
                    val backoff = minOf(maxDelayMs, baseDelayMs * reconnectAttempts)
                    Log.d(tag, "Reconnecting in ${backoff}ms (attempt #$reconnectAttempts)")
                    delay(backoff)
                } catch (e: Exception) {
                    Log.e(tag, "Error connecting WebSocket", e)
                    delay(pollIntervalMs)
                }
            }
        }
    }

    private suspend fun connectAndWait(cfg: WsConfig) = suspendCancellableCoroutine<Unit> { cont ->
        cleanupSocket()
        Log.i(tag, "Connecting to: ${cfg.url}")

        val request = Request.Builder()
            .url(cfg.url)
            .apply {
                if (cfg.bearerToken.isNotBlank())
                    addHeader("Authorization", "Bearer ${cfg.bearerToken}")
            }
            .build()

        val socket = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(tag, "WebSocket connected")
                reconnectAttempts = 0
                onStatus("Connected and synced")
                scope.launch { onOpen(webSocket) }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(tag, "Received message: $text")
                scope.launch { onTextMessage(webSocket, text) }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(tag, "Websocket closing: code=$code, reason=$reason")
                webSocket.close(1000, "OK")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(tag, "Websocket closed: code=$code, reason=$reason")
                if (currentSocket === webSocket) currentSocket = null
                onClosing()
                if (cont.isActive) cont.resume(Unit) {}
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(tag, "WebSocket failed: ${t.message}", t)
                if (currentSocket === webSocket) currentSocket = null
                onStatus("Disconnected, reconnecting...")
                onClosing()
                onFailure()
                if (cont.isActive) cont.resume(Unit) {}
            }
        })

        currentSocket = socket

        cont.invokeOnCancellation {
            if (currentSocket === socket) {
                socket.close(1000, "Cancelled")
                currentSocket = null
            }
        }
    }

    fun cleanupSocket() {
        currentSocket?.close(1000, "Reconnecting")
        currentSocket = null
    }

    fun shutdownClient() {
        cleanupSocket()
        client.dispatcher.executorService.shutdown()
    }
}
