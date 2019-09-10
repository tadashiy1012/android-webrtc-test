package jp.yama.webrtctest001

import android.Manifest
import android.app.AlertDialog
import android.app.Application
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.android.synthetic.main.activity_main.*
import okhttp3.*
import org.webrtc.*
import java.util.*

class MainActivity : AppCompatActivity() {

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

    private lateinit var ws: WebSocket

    private var desc: SessionDescription? = null
    private var isOpen = false
    private var timer: Timer? = null

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
                                Log.v("yama", "send sdp")
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
                                Log.v("yama", "pending...")
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
    }

    override fun onDestroy() {
        super.onDestroy()
        if (timer != null) {
            timer?.cancel()
            timer = null
        }
        peerConnection?.close()
        ws.close(1000, "bye")
    }

}

