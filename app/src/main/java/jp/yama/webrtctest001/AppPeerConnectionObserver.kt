package jp.yama.webrtctest001

import android.util.Log
import org.webrtc.*

open class AppPeerConnectionObserver: PeerConnection.Observer {
    override fun onIceCandidate(p0: IceCandidate?) {
        Log.v("yama", "onIceCandidate:${p0}")
    }
    override fun onDataChannel(p0: DataChannel?) {}
    override fun onIceConnectionReceivingChange(p0: Boolean) {
        Log.v("yama", "onIceConnectionReceivingChange:" + p0.toString())
    }
    override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {
        Log.v("yama", "onIceConnectionChange:" + p0.toString())
    }
    override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {
        Log.v("yama", "onIceGatheringChange:" + p0.toString())
    }
    override fun onAddStream(p0: MediaStream?) {}
    override fun onSignalingChange(p0: PeerConnection.SignalingState?) {
        Log.v("yama", "onSignalingChange:" + p0.toString())
    }
    override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
    override fun onRemoveStream(p0: MediaStream?) {}
    override fun onRenegotiationNeeded() {
        Log.v("yama", "onRenegotiationNeeded")}
    override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {
        Log.v("yama", "onAddTrack:${p1}")
    }
}

