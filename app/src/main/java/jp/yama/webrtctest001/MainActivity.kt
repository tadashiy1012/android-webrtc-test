package jp.yama.webrtctest001

import android.Manifest
import android.app.AlertDialog
import android.app.Application
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.*
import org.webrtc.*
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity(), CoroutineScope by MainScope() {

    companion object {
        private const val CAMERA_PERMISSION_REQ_CODE = 1
        private const val CAMERA_PERMISSION = Manifest.permission.CAMERA
        private const val HOST_ADDRESS = "cloud.achex.ca"
        private const val PORT_NUMBER = 443
        private const val USER_NAME = "consume@890"
        private const val PASSWORD = "0749637637"
        private const val KEY = "default"
        private const val LOCAL_TRACK_ID = "local_track"
        private const val LOCAL_STREAM_ID = "local_track"
        private const val DATA_CHANNEL_LABEL = "data_channel_1"
    }

    private val uuid = UUID.randomUUID()
    private val gson = Gson()
    private val eglBase = EglBase.create()

    private val iceServer = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
    )
    private val peerConnectionFactory by lazy { buildPeerConnectionFactory() }
    private val videoCapturer by lazy { getVideoCapturer(this.application) }
    private val localVideoSrc by lazy { peerConnectionFactory.createVideoSource(false) }
    private val peerConnection by lazy { buildPeerConnection() }
    private val dcPeerConnection by lazy { buildDataChannelPeerConnection() }

    private lateinit var ws: WebSocket
    private var dataChannel: DataChannel? = null

    private var desc: SessionDescription? = null
    private var isOpen = false
    private var timer: Timer? = null
    private var timer2: Timer? = null
    private lateinit var data: MutableLiveData<List<Media>>
    private var chunk: ArrayList<ByteBuffer>? = null

    private fun buildPeerConnectionFactory(): PeerConnectionFactory {
        val options = PeerConnectionFactory.InitializationOptions.builder(this)
            .setEnableInternalTracer(true)
            .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
        return PeerConnectionFactory
            .builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setOptions(PeerConnectionFactory.Options().apply {
                disableNetworkMonitor = true
            }).createPeerConnectionFactory()
    }

    private fun buildPeerConnection(): PeerConnection? = peerConnectionFactory.createPeerConnection(
        iceServer,
        object: AppPeerConnectionObserver() {
            override fun onIceCandidate(p0: IceCandidate?) {
                super.onIceCandidate(p0)
                peerConnection?.addIceCandidate(p0)
            }
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {
                super.onIceGatheringChange(p0)
                if (p0 == PeerConnection.IceGatheringState.COMPLETE) {
                    timer = Timer()
                    timer?.schedule(object: TimerTask() {
                        override fun run() {
                            if (isOpen && desc != null) {
                                Log.v("yama", "sending sdp")
                                send(mapOf(
                                    Pair("to", "default@890"),
                                    Pair("type", "consume"),
                                    Pair("uuid", uuid.toString()),
                                    Pair("key", KEY),
                                    Pair("sdp", desc?.description!!)
                                ));
                                timer?.cancel()
                                timer = null;
                            } else {
                                Log.v("yama", "sdp send pending...")
                            }
                        }
                    }, 0, 3000)
                    timer2 = Timer()
                    timer2?.schedule(object : TimerTask() {
                        override fun run() {
                            val dcdesc = dcPeerConnection?.localDescription
                            if (isOpen && dcdesc != null) {
                                Log.v("yama", "sending dc sdp")
                                send(mapOf(
                                    Pair("to", "default@890"),
                                    Pair("type", "consume_dc"),
                                    Pair("uuid", uuid.toString()),
                                    Pair("key", KEY),
                                    Pair("sdp", dcdesc?.description),
                                    Pair("env", "chrome")
                                ))
                                timer2?.cancel()
                                timer2 = null;
                            } else {
                                Log.v("yama", "dc sdp send pending...")
                            }
                        }
                    }, 0, 3000)
                }
            }
            override fun onAddStream(p0: MediaStream?) {
                super.onAddStream(p0)
                p0?.videoTracks?.get(0)?.addSink(remoteRenderer)
            }
        }
    )

    private fun buildDataChannelPeerConnection(): PeerConnection? = peerConnectionFactory.createPeerConnection(
        iceServer,
        object: AppPeerConnectionObserver() {
            override fun onRenegotiationNeeded() {
                super.onRenegotiationNeeded()
                Log.v("yama", "dc onRenegotiationNeeded")
            }
            override fun onIceCandidate(p0: IceCandidate?) {
                super.onIceCandidate(p0)
                Log.v("yama", "dc onIceCandidate")
                dcPeerConnection?.addIceCandidate(p0)
            }
        }
    )

    private fun buildDataChannel(): DataChannel? = dcPeerConnection?.createDataChannel(
            DATA_CHANNEL_LABEL, DataChannel.Init()).apply {
        Log.v("yama", "build data channel")
        this?.registerObserver(object: DataChannel.Observer {
            override fun onMessage(p0: DataChannel.Buffer?) {
                Log.v("yama", "data channel on message")
                if (p0?.binary!!) {
                    Log.v("yama", "message type binary")
                    val buffer = p0.data
                    Log.v("yama", buffer[0].toString())
                    if (buffer[0] == byteArrayOf(0)[0]) {
                        Log.v("yama", "bingo!")
                    }
                } else {
                    Log.v("yama", "message type string")
                    val buffer = p0.data
                    val byteAry = ByteArray(buffer?.remaining()!!)
                    buffer.get(byteAry)
                    val str = String(byteAry)
                    val json = gson.fromJson(str, JsonObject::class.java)
                    launch {
                        val ls = data.value?.toMutableList()
                        ls?.add(0, Media("text", json.get("message").asString, null))
                        data.value = ls?.toList()
                    }
                }
            }
            override fun onBufferedAmountChange(p0: Long) {}
            override fun onStateChange() {
                Log.v("yama","data channel state change")
            }
        })
    }

    private fun getVideoCapturer(ctx: Application) = Camera2Enumerator(ctx).run {
        deviceNames.find {
            isFrontFacing(it)
        }?.let {
            createCapturer(it, null)
        } ?: throw IllegalStateException()
    }

    private fun checkPermission() {
        if (ContextCompat.checkSelfPermission(this, CAMERA_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission()
        } else {
            onCameraPermissionGranted()
        }
    }

    private fun requestCameraPermission(dialogShown: Boolean = false) {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, CAMERA_PERMISSION) && !dialogShown) {
            showPermissionRationaleDialog()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(CAMERA_PERMISSION), CAMERA_PERMISSION_REQ_CODE)
        }
    }

    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Camera Permission Required")
            .setMessage("this app need the camera to function")
            .setPositiveButton("Grant") { dialog, _ ->
                dialog.dismiss()
                requestCameraPermission(true)
            }
            .setNegativeButton("Deny") { dialog, _ ->
                dialog.dismiss()
                onCameraPermissionDenied()
            }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQ_CODE && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            onCameraPermissionGranted()
        } else {
            onCameraPermissionDenied()
        }
    }

    private fun onCameraPermissionGranted() {
        initSurfaceView(remoteRenderer)
        initSurfaceView(localRenderer)
        startLocalVideoCapture(localRenderer)
        connect()
    }

    private fun onCameraPermissionDenied() {
        Toast.makeText(this, "Camera Permission Denied", Toast.LENGTH_LONG).show()
    }


    private fun makeOffer() {
        peerConnection?.offer(object : SdpObserver {
            override fun onSetFailure(p0: String?) {}
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {
                Log.v("yama", "sdpObserver onCreateFail:${p0}")
            }
            override fun onCreateSuccess(p0: SessionDescription?) {
                Log.v("yama", "sdpObserver onCreateSuccess")
                desc = p0
            }
        })
    }

    private fun PeerConnection.offer(sdpObserver: SdpObserver) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        createOffer(object : SdpObserver by sdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {
                Log.v("yama", "create offer")
                setLocalDescription(object : SdpObserver {
                    override fun onSetFailure(p0: String?) {}
                    override fun onSetSuccess() {
                        Log.v("yama", "set my offer")
                    }
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                }, p0)
                sdpObserver.onCreateSuccess(p0)
            }
        }, constraints)
    }

    private fun onRemoteSessionReceived(sessionDesc: SessionDescription) {
        Log.v("yama", sessionDesc.type.toString())
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetFailure(p0: String?) {
                Log.v("yama", "remote desc set fail! " + p0)
            }
            override fun onSetSuccess() {
                Log.v("yama", "remote desc set success!")
            }
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
        }, sessionDesc)
    }

    private fun makeDcOffer() {
        dataChannel = buildDataChannel()
        val constraints = MediaConstraints()
        dcPeerConnection?.createOffer(object: SdpObserver {
            override fun onSetFailure(p0: String?) {}
            override fun onSetSuccess() {}
            override fun onCreateSuccess(p0: SessionDescription?) {
                Log.v("yama", "dc offer create success")
                dcPeerConnection?.setLocalDescription(object: SdpObserver {
                    override fun onSetFailure(p0: String?) {
                        Log.e("yama", "dc offer set fail:${p0}")
                    }
                    override fun onSetSuccess() {
                        Log.v("yama", "dc offer set success")
                    }
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                }, p0)
            }
            override fun onCreateFailure(p0: String?) {
                Log.e("yama", "dc offer create fail:${p0}")
            }
        }, constraints)
    }

    private fun onDcRemoteSessionReceived(desc: SessionDescription) {
        Log.v("yama", desc.type.toString())
        dcPeerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetFailure(p0: String?) {
                Log.v("yama", "remote desc set fail! " + p0)
            }
            override fun onSetSuccess() {
                Log.v("yama", "remote desc set success!")
            }
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
        }, desc)
    }

    private fun initSurfaceView(view: SurfaceViewRenderer) = view.run {
        setMirror(true)
        setEnableHardwareScaler(true)
        init(eglBase.eglBaseContext, null)
    }

    private fun startLocalVideoCapture(localVideoOutput: SurfaceViewRenderer) {
        val helper = SurfaceTextureHelper.create(Thread.currentThread().name, eglBase.eglBaseContext)
        (videoCapturer as VideoCapturer).initialize(helper, localVideoOutput.context, localVideoSrc.capturerObserver)
        videoCapturer.startCapture(320, 240, 30)
        val videoTrack = peerConnectionFactory.createVideoTrack(LOCAL_TRACK_ID, localVideoSrc)
        videoTrack.addSink(localVideoOutput)
        val stream = peerConnectionFactory.createLocalMediaStream(LOCAL_STREAM_ID)
        stream.addTrack(videoTrack)
        stream.videoTracks.forEach {
            peerConnection?.addTrack(it)
        }
    }

    private fun connect() {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("wss://${HOST_ADDRESS}:${PORT_NUMBER}")
            .build()
        val wsListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.v("yama", "onOpen:${response.message}")
                isOpen = true;
                val auth = gson.toJson(mapOf(Pair("auth", USER_NAME), Pair("password", PASSWORD)))
                webSocket.send(auth)
                makeOffer()
                makeDcOffer()
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.v("yama", "onMessage:${text}")
                val json = gson.fromJson(text, JsonObject::class.java)
                Log.v("yama", json.toString());
                if (json.has("type") && json.get("type").asString == "produce"
                        && json.has("destination") && json.get("destination").asString == uuid.toString()) {
                    Log.v("yama", "answer received")
                    val answer = SessionDescription(SessionDescription.Type.ANSWER, json.get("sdp").asString)
                    onRemoteSessionReceived(answer)
                } else if (json.has("type") && json.get("type").asString == "produce_dc"
                        && json.has("destination") && json.get("destination").asString == uuid.toString()) {
                    Log.v("yama", "dc answer received")
                    val answer = SessionDescription(SessionDescription.Type.ANSWER, json.get("sdp").asString)
                    onDcRemoteSessionReceived(answer)
                }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.v("yama", response?.message ?: "no message")
                Log.e("yama", "error!", t)
            }
        }
        ws = client.newWebSocket(request, wsListener)
        client.dispatcher.executorService.shutdown()
    }

    private fun send(data: Map<String, String>) {
        val json = gson.toJson(data);
        Log.v("yama", "ws send:${json}")
        ws.send(json)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        checkPermission()
        val rview = findViewById<RecyclerView>(R.id.rview)
        val adapter = MyAdapter(this)
        rview.setHasFixedSize(true)
        rview.layoutManager = LinearLayoutManager(this)
        rview.adapter = adapter
        data = MutableLiveData<List<Media>>().also {
            it.value = listOf(Media("text", "hoge", null))
        }
        data.observe(this, androidx.lifecycle.Observer {
            adapter.submitList(it)
            rview.scrollToPosition(0)
        })
        val inText = findViewById<EditText>(R.id.inText)
        val button = findViewById<Button>(R.id.button1)
        button.setOnClickListener {
            val str = inText.text.toString()
            if (!str.isEmpty()) {
                val ls = data.value?.toMutableList()
                ls?.add(0, Media("text", "[me]:${str}", null))
                data.value = ls?.toList()
                val json = gson.toJson(mapOf<String, String>(
                    Pair("id", uuid.toString()),
                    Pair("type", "plane"),
                    Pair("message", str)
                ))
                val charset = Charset.defaultCharset()
                val buffer = charset.encode(json)
                dataChannel?.send(DataChannel.Buffer(buffer, false))
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (timer != null) {
            timer?.cancel()
            timer = null
        }
        if (timer2 != null) {
            timer2?.cancel()
            timer = null
        }
        peerConnection?.close()
        ws.close(1000, "bye")
        cancel()
    }

}

