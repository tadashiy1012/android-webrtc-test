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
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_main.*
import org.webrtc.*
import java.util.*

class MainActivity : AppCompatActivity() {

    companion object {
        private const val CAMERA_PERMISSION_REQ_CODE = 1
        private const val CAMERA_PERMISSION = Manifest.permission.CAMERA
    }

    private val uuid = UUID.randomUUID()
    private val key = "default"

    private lateinit var rtcClient: WebRtcClient
    private lateinit var signalingClient: SignalingClient

    private val gson = Gson()

    private var isWsOpen = false;
    private var desc: SessionDescription? = null

    private val sdpObserver = object : AppSdpObserver() {
        override fun onCreateSuccess(p0: SessionDescription?) {
            super.onCreateSuccess(p0)
            Log.v("yama", "sdpObserver onCreateSuccess")
            desc = p0
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
                rtcClient.addIceCandidate(p0)
            }

            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {
                super.onIceGatheringChange(p0)
                if (p0 == PeerConnection.IceGatheringState.COMPLETE) {
                    signalingClient.send(mapOf(
                        Pair("to", "default@890"),
                        Pair("type", "consume"),
                        Pair("uuid", uuid.toString()),
                        Pair("key", key),
                        Pair("sdp", desc?.description!!)
                    ));
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
            if (isWsOpen) {
                rtcClient.offer(sdpObserver)
            }
        }
    }

    private fun createSignalingClientListener(): SignalingClientListener = object : SignalingClientListener {
        override fun onConnectionEstablished() {
            isWsOpen = true
            rtcClient.offer(sdpObserver)
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

