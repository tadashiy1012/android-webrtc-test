package jp.yama.webrtctest001

import android.Manifest
import android.app.Activity
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.ActivityCompat
import org.webrtc.*

class MainActivity : AppCompatActivity() {

    private val REQUEST_CODE: Int = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val permissions = listOf(Manifest.permission.CAMERA).toTypedArray()
        ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_CODE) return
        Toast.makeText(this.applicationContext,"permission ok!", Toast.LENGTH_LONG).show()
        val webRTC = WebRTC(this)
        webRTC.startCapture()
    }

}

class WebRTC(private val activity: Activity) {

    private var videoCapturer: VideoCapturer?
    private var renderCtx: EglBase.Context?

    init {
        val eglBase = EglBase.create()
        renderCtx = eglBase.eglBaseContext
        val initOptionBuilder = PeerConnectionFactory.InitializationOptions.builder(activity.applicationContext)
        PeerConnectionFactory.initialize(initOptionBuilder.createInitializationOptions())
        val options = PeerConnectionFactory.Options()
        val factoryBuilder = PeerConnectionFactory.builder()
        factoryBuilder.setOptions(options)
        val factory = factoryBuilder.createPeerConnectionFactory()
        val localRenderer = setupRenderer()
        val localStream = factory.createLocalMediaStream("local_stream")
        val localVideoSrc = factory.createVideoSource(false)
        val localVideoTrack = factory.createVideoTrack("android_local_videotrack", localVideoSrc)
        localVideoTrack.addSink(localRenderer)
        localStream.addTrack(localVideoTrack)
        val helper = SurfaceTextureHelper.create(Thread.currentThread().name, eglBase.eglBaseContext)
        videoCapturer = createCameraCapture(Camera2Enumerator(activity))
        videoCapturer?.initialize(helper, activity.application, localVideoSrc.capturerObserver)
    }

    fun startCapture() {
        val displayMetrics = DisplayMetrics()
        val windowManager = activity.application.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getRealMetrics(displayMetrics)
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels
        videoCapturer?.startCapture(width, height, 30)
    }

    private fun createCameraCapture(enumerator: Camera2Enumerator): VideoCapturer? {
        return createBackCameraCapture(enumerator)
    }

    private fun createBackCameraCapture(enumerator: Camera2Enumerator): VideoCapturer? {
        val deviceNames = enumerator.deviceNames
        deviceNames.forEach {
            if (!enumerator.isFrontFacing(it)) {
                val capturer = enumerator.createCapturer(it, null)
                if (capturer != null) return capturer
            }
        }
        return null
    }

    private fun setupRenderer(): SurfaceViewRenderer {
        val renderer = activity.findViewById<SurfaceViewRenderer>(R.id.localRenderer)
        renderer.init(renderCtx, null)
        renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
        renderer.setZOrderMediaOverlay(true)
        renderer.setEnableHardwareScaler(true)
        return renderer
    }

}