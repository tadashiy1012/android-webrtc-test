package jp.yama.webrtctest001

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import org.webrtc.*
import java.util.*

class MainActivity : AppCompatActivity() {

    companion object {
        private const val CAMERA_PERMISSION_REQ_CODE = 1
        private const val CAMERA_PERMISSION = Manifest.permission.CAMERA
    }

    private val uuid = UUID.randomUUID()

    private lateinit var rtcClient: WebRtcClient
    private lateinit var signalingClient: SignalingClient

    private var isWsOpen = false;
    private var isCdateNull = false;

    private val sdpObserver = object : AppSdpObserver() {
        override fun onCreateSuccess(p0: SessionDescription?) {
            Log.v("yama", "sdpObserver onCreateSuccess")
            super.onCreateSuccess(p0)
            val obj = SendingData(sdp = p0?.description!!, uuid = uuid.toString())
            //signalingClient.send(obj)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        checkPermission()
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
        rtcClient = WebRtcClient(application, object : AppPeerConnectionObserver() {
            override fun onIceCandidate(p0: IceCandidate?) {
                super.onIceCandidate(p0)
                Log.v("yama", "onIceCandidate > " + p0.toString())
                //signalingClient.send(p0)
                rtcClient.addIceCandidate(p0)
                if (p0 == null) {
                    isCdateNull = true;
                }
            }
            override fun onAddStream(p0: MediaStream?) {
                super.onAddStream(p0)
                Log.v("yama", "onAddStream")
                p0?.videoTracks?.get(0)?.addSink(remoteRenderer)
            }
        })
        rtcClient.initSurfaceView(remoteRenderer)
        rtcClient.initSurfaceView(localRenderer)
        rtcClient.startLocalVideoCapture(localRenderer)
        signalingClient = SignalingClient(createSignalingClientListener())
        callBtn.setOnClickListener {
            if (isWsOpen && isCdateNull) {
                rtcClient.call(sdpObserver)
            }
        }
    }

    private fun createSignalingClientListener(): SignalingClientListener = object : SignalingClientListener {
        override fun onConnectionEstablished() {
            isWsOpen = true
            //signalingClient.send(WebSocketAuth("consume@890", "0749637637"))
        }
        override fun onOfferReceived(description: SessionDescription) {
            Log.v("yama", "offer received")
            rtcClient.onRemoteSessionReceived(description)
            rtcClient.answer(sdpObserver)
        }
        override fun onAnswerReceived(description: SessionDescription) {
            Log.v("yama", "answer received")
            rtcClient.onRemoteSessionReceived(description)
        }
        override fun onIceCandidateReceived(iceCandidate: IceCandidate) {
            Log.v("yama", " iceCandidate received")
            rtcClient.addIceCandidate(iceCandidate)
        }
    }

    private fun onCameraPermissionDenied() {
        Toast.makeText(this, "Camera Permission Denied", Toast.LENGTH_LONG).show()
    }

}

