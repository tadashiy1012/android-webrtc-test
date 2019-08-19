package jp.yama.webrtctest001

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.json.GsonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.websocket.WebSockets
import io.ktor.client.features.websocket.ws
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.readText
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import kotlin.coroutines.CoroutineContext

@ExperimentalCoroutinesApi
@KtorExperimentalAPI
class SignallingClient(
    private val listener: SignallingClientListener
): CoroutineScope {

    companion object {
        private const val HOST_ADDRESS = "localhost"
        private const val PORT_NUMBER = 8080
    }

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + job

    private val gson = Gson()

    private val client = HttpClient(CIO) {
        install(WebSockets)
        install(JsonFeature) {
            serializer = GsonSerializer()
        }
    }

    private val sendChannel = ConflatedBroadcastChannel<String>()

    init {
        connect()
    }

    private fun connect() = launch {
        client.ws(host = HOST_ADDRESS, port = PORT_NUMBER, path = "/") {
            listener.onConnectionEstablished()
            val sendData = sendChannel.openSubscription()
            try {
                while (true) {
                    sendData.poll()?.let {
                        Log.v("yama", "sending: $it")
                        outgoing.send(Frame.Text(it))
                    }
                    incoming.poll()?.let { frame ->
                        if (frame is Frame.Text) {
                            val data = frame.readText()
                            Log.v("yama", "received: $data")
                            val json = gson.fromJson(data, JsonObject::class.java)
                            withContext(Dispatchers.Main) {
                                if (json.has("serverUrl")) {
                                    listener.onIceCandidateReceived(gson.fromJson(json, IceCandidate::class.java))
                                } else if (json.has("type") && json.get("type").asString == "OFFER") {
                                    listener.onOfferReceived(gson.fromJson(json, SessionDescription::class.java))
                                } else if (json.has("type") && json.get("type").asString == "ANSWER") {
                                    listener.onAnswerReceived(gson.fromJson(json, SessionDescription::class.java))
                                }
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.e("yama", "error!", e)
            }
        }
    }

    fun send(dataObject: Any?) = runBlocking {
        sendChannel.send(gson.toJson(dataObject))
    }

    fun destroy() {
        client.close()
        job.complete()
    }

}
