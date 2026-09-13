package com.remotecamera.viewer.signaling

import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import okhttp3.*

data class SignalingMessage(
    val type: String,
    val senderDeviceId: String,
    val targetDeviceId: String? = null,
    val payload: Any? = null
)

class SignalingClient(
    private val deviceId: String,
    private val clientType: String = "VIEWER",
    private val client: OkHttpClient = OkHttpClient()
) {
    private var webSocket: WebSocket? = null
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _incomingMessages = MutableSharedFlow<SignalingMessage>()
    val incomingMessages: SharedFlow<SignalingMessage> = _incomingMessages

    fun connect(serverUrl: String) {
        val request = Request.Builder().url(serverUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                println("[SignalingClient] Connected to $serverUrl. Registering...")
                val registerMessage = SignalingMessage(
                    type = "REGISTER",
                    senderDeviceId = deviceId,
                    payload = mapOf("clientType" to clientType)
                )
                webSocket.send(gson.toJson(registerMessage))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val msg = gson.fromJson(text, SignalingMessage::class.java)
                    scope.launch {
                        _incomingMessages.emit(msg)
                    }
                } catch (e: Exception) {
                    println("[SignalingClient] Parse error: ${e.message}")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                println("[SignalingClient] Failure: ${t.message}")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                println("[SignalingClient] Closed: $reason")
            }
        })
    }

    fun sendMessage(targetDeviceId: String, type: String, payload: Any) {
        val message = SignalingMessage(
            type = type,
            senderDeviceId = deviceId,
            targetDeviceId = targetDeviceId,
            payload = payload
        )
        webSocket?.send(gson.toJson(message))
    }

    fun disconnect() {
        webSocket?.close(1000, "Client disconnect")
    }
}
