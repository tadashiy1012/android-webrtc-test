package jp.yama.webrtctest001

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import okhttp3.*
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import java.lang.reflect.Type
import java.net.URL
import kotlin.coroutines.CoroutineContext

class SignalingClient(
    private val listener: SignalingClientListener
) {

    companion object {
        private const val HOST_ADDRESS = "cloud.achex.ca"
        private const val PORT_NUMBER = 443
        private const val USER_NAME = "consume@890"
        private const val PASSWORD = "0749637637"
    }

    private lateinit var ws: WebSocket
    private val gson = Gson()
    private val type : Type = object : TypeToken<MutableMap<String, String>>() {}.type

    init {
        connect()
    }

    private fun connect() {
        try {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("wss://${HOST_ADDRESS}:${PORT_NUMBER}")
                .build()
            Log.v("yama", request.url.toString())
            val wsListener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.v("yama", "onOpen:${response.message}")
                    val json = gson.toJson(mapOf(Pair("auth", USER_NAME), Pair("password", PASSWORD)))
                    webSocket.send(json)
                }
                override fun onMessage(webSocket: WebSocket, text: String) {
                    Log.v("yama", "onMessage:${text}")
                    val json = gson.fromJson(text, JsonObject::class.java)
                    Log.v("yama", json.toString());
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.v("yama", response?.message ?: "no message")
                    Log.e("yama", "error!", t)
                }
            }
            ws = client.newWebSocket(request, wsListener)
            client.dispatcher.executorService.shutdown()
        } catch (e: Throwable) {
            Log.e("yama", "error!", e)
        }
    }

    fun send(data: Map<String, String>) {
        val json = gson.toJson(data);
        Log.v("yama", "ws send:${json}")
        ws.send(json)
    }

}
