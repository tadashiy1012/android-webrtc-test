package jp.yama.webrtctest001

import android.app.Application
import org.webrtc.*

class WebRtcCLient(
    ctx: Application,
    observer: PeerConnection.Observer
) {

    companion object {
        private const val LOCAL_TRACK_ID = "local_track"
        private const val LOCAL_STREAM_ID = "local_track"
    }

    private val eglBase: EglBase = EglBase.create()

    init {
        initPeerConnectionFactory(ctx)
    }

    private val iceServer = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
    )

    private val peerConnectionFactory by lazy { buildPeerConnectionFactory() }
    private val videoCapturer by lazy { getVideoCapurer(ctx) }
    private val localVideoSrc by lazy { peerConnectionFactory.createVideoSource(false) }
    private val peerConnection by lazy { buildPeerConnection(observer) }

    private fun buildPeerConnection(observer: PeerConnection.Observer) = peerConnectionFactory.createPeerConnection(
        iceServer,
        observer
    )

    private fun getVideoCapurer(ctx: Application) = Camera2Enumerator(ctx).run {
        deviceNames.find {
            isFrontFacing(it)
        }?.let {
            createCapturer(it, null)
        } ?: throw IllegalStateException()
    }

    private fun buildPeerConnectionFactory(): PeerConnectionFactory {
        return PeerConnectionFactory
            .builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setOptions(PeerConnectionFactory.Options().apply {
                disableEncryption = true
                disableNetworkMonitor = true
            }).createPeerConnectionFactory()
    }

    private fun initPeerConnectionFactory(context: Application) {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
    }

    fun initSurfaceView(view: SurfaceViewRenderer) = view.run {
        setMirror(true)
        setEnableHardwareScaler(true)
        init(eglBase.eglBaseContext, null)
    }

    fun startLocalVideoCapture(localVideoOutput: SurfaceViewRenderer) {
        val helper = SurfaceTextureHelper.create(Thread.currentThread().name, eglBase.eglBaseContext)
        (videoCapturer as VideoCapturer).initialize(helper, localVideoOutput.context, localVideoSrc.capturerObserver)
        videoCapturer.startCapture(320, 240, 30)
        val videoTrack = peerConnectionFactory.createVideoTrack(LOCAL_TRACK_ID, localVideoSrc)
        videoTrack.addSink(localVideoOutput)
        val stream = peerConnectionFactory.createLocalMediaStream(LOCAL_STREAM_ID)
        stream.addTrack(videoTrack)
        peerConnection?.addStream(stream)
    }

    private fun PeerConnection.call(sdpObserver: SdpObserver) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        createOffer(object : SdpObserver by sdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {
                setLocalDescription(object : SdpObserver {
                    override fun onSetFailure(p0: String?) {}
                    override fun onSetSuccess() {}
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                }, p0)
            }
        }, constraints)
    }

    private fun PeerConnection.answer(sdpObserver: SdpObserver) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        createAnswer(object : SdpObserver by sdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {
                setLocalDescription(object : SdpObserver {
                    override fun onSetFailure(p0: String?) {}
                    override fun onSetSuccess() {}
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                }, p0)
            }
        }, constraints)
    }

    fun call(sdpObserver: SdpObserver) = peerConnection?.call(sdpObserver)

    fun ansswer(sdpObserver: SdpObserver) = peerConnection?.answer(sdpObserver)

    fun onRemoteSessionReceived(sessionDesc: SessionDescription) {
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetFailure(p0: String?) {}
            override fun onSetSuccess() {}
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
        }, sessionDesc)
    }

    fun addIceCandidate(iceCandidate: IceCandidate?) {
        peerConnection?.addIceCandidate(iceCandidate)
    }

}


