package jp.yama.webrtctest001

import android.util.Log
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

open class AppSdpObserver: SdpObserver {
    override fun onSetFailure(p0: String?) {
        Log.v("yama", "SdpObserver onSet fail:${p0}")
    }
    override fun onSetSuccess() {
        Log.v("yama", "SdpObserver onSet success")
    }
    override fun onCreateSuccess(p0: SessionDescription?) {
        Log.v("yama", "SdpObserver onCreate success:${p0}")
    }
    override fun onCreateFailure(p0: String?) {
        Log.v("yama", "SdpObserver onCreate fail:${p0}")
    }
}
