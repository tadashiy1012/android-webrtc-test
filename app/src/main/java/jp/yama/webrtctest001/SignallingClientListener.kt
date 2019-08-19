package jp.yama.webrtctest001

import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

interface SignallingClientListener {
    fun onConnectionEstablished()
    fun onOfferReceived(description: SessionDescription)
    fun onAnswerReceived(description: SessionDescription)
    fun onIceCandidateReceived(iceCandidate: IceCandidate)
}