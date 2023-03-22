package com.example.kotlinhelloworld.videoCall

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.kotlinhelloworld.R
import okhttp3.OkHttpClient
import okhttp3.Request
import org.webrtc.*
import org.webrtc.ContextUtils.getApplicationContext
import org.webrtc.VideoCapturer.CapturerObserver
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext


enum class VideoCallStatus(val label: Int, val color: Int) {
    UNKNOWN(R.string.status_unknown, R.color.colorUnknown),
    CONNECTING(R.string.status_connecting, R.color.colorConnecting),
    MATCHING(R.string.status_matching, R.color.colorMatching),
    FAILED(R.string.status_failed, R.color.colorFailed),
    CONNECTED(R.string.status_connected, R.color.colorConnected),
    FINISHED(R.string.status_finished, R.color.colorConnected),
    ON_GOING_CALL(R.string.status_finished, R.color.colorConnected);
}

data class VideoRenderers(
    private val localView: SurfaceViewRenderer?,
    private val remoteView: SurfaceViewRenderer?
) {
    val localRenderer: (VideoRenderer.I420Frame) -> Unit =
        if (localView == null) this::sink else { f -> localView.renderFrame(f) }
    val remoteRenderer: (VideoRenderer.I420Frame) -> Unit =
        if (remoteView == null) this::sink else { f -> remoteView.renderFrame(f) }

    private fun sink(frame: VideoRenderer.I420Frame) {
        Log.w("VideoRenderer", "Missing surface view, dropping frame")
        VideoRenderer.renderFrameDone(frame)
    }
}

class VideoCallSession(
    private val context: Context,
    private val onStatusChangedListener: (VideoCallStatus) -> Unit,
    private val signaler: SignalingWebSocket,
    private val videoRenderers: VideoRenderers
) {

    private var peerConnection: PeerConnection? = null
    private var factory: PeerConnectionFactory? = null
    private var isOfferingPeer = false
    private var videoSource: VideoSource? = null
    private var audioSource: AudioSource? = null
    private val eglBase = EglBase.create()
    private var videoCapturer: VideoCapturer? = null
    private lateinit var mediaRecorder: MediaRecorder

    val renderContext: EglBase.Context
        get() = eglBase.eglBaseContext

    class SimpleRTCEventHandler(
        private val onIceCandidateCb: (IceCandidate) -> Unit,
        private val onAddStreamCb: (MediaStream) -> Unit,
        private val onRemoveStreamCb: (MediaStream) -> Unit
    ) : PeerConnection.Observer {

        override fun onIceCandidate(candidate: IceCandidate?) {
            Log.d("abhishek", "candidate added");
            if (candidate != null) onIceCandidateCb(candidate)
        }

        override fun onAddStream(stream: MediaStream?) {
            Log.d("abhishek", "added : " + stream.toString());
            if (stream != null) onAddStreamCb(stream)
        }

        override fun onRemoveStream(stream: MediaStream?) {
            Log.d("abhishek", "removed : " + stream.toString());
            if (stream != null) onRemoveStreamCb(stream)
        }

        override fun onDataChannel(chan: DataChannel?) {
            Log.w(TAG, "onDataChannel: $chan")
        }

        override fun onIceConnectionReceivingChange(p0: Boolean) {
            Log.w(TAG, "onIceConnectionReceivingChange: $p0")
        }

        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
            Log.w(TAG, "onIceConnectionChange: $newState")
        }

        override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {
            Log.w(TAG, "onIceGatheringChange: $newState")
        }

        override fun onSignalingChange(newState: PeerConnection.SignalingState?) {
            Log.w(TAG, "onSignalingChange: $newState")
        }

        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
            Log.w(TAG, "onIceCandidatesRemoved: $candidates")
        }

        override fun onRenegotiationNeeded() {
            Log.w(TAG, "onRenegotiationNeeded")
        }

        override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
    }

    init {
        signaler.messageHandler = this::onMessage
        this.onStatusChangedListener(VideoCallStatus.MATCHING)
        executor.execute(this::init)
    }

    private fun init() {
        //PeerConnectionFactory.initializeAndroidGlobals(context, true)
        val initializeOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableVideoHwAcceleration(false).setEnableInternalTracer(false)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initializeOptions) //gialakias

        val opts = PeerConnectionFactory.Options()
        opts.networkIgnoreMask = 0

        factory = PeerConnectionFactory.builder().setOptions(opts).createPeerConnectionFactory()
        factory?.setVideoHwAccelerationOptions(eglBase.eglBaseContext, eglBase.eglBaseContext)

        val iceServers = arrayListOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )

        val constraints = MediaConstraints()
        constraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        constraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        val rtcCfg = PeerConnection.RTCConfiguration(iceServers)
        rtcCfg.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        val rtcEvents = SimpleRTCEventHandler(
            this::handleLocalIceCandidate,
            this::addRemoteStream,
            this::removeRemoteStream
        )
        peerConnection = factory?.createPeerConnection(rtcCfg, constraints, rtcEvents) //deprecated?
        setupMediaDevices()
    }

    private fun start() {
        executor.execute(this::maybeCreateOffer)
    }

    private fun maybeCreateOffer() {
        if (isOfferingPeer) {

            peerConnection?.createOffer(
                SDPCreateCallback(this::createDescriptorCallback),
                MediaConstraints()
            )
        }
    }

    private fun handleLocalIceCandidate(candidate: IceCandidate) {
        Log.w(TAG, "Local ICE candidate: $candidate")
        signaler.sendCandidate(candidate.sdpMLineIndex, candidate.sdpMid, candidate.sdp)
    }

    private fun addRemoteStream(stream: MediaStream) {
        onStatusChangedListener(VideoCallStatus.CONNECTED)
        Log.d("abhishek", "added stream :$stream");
        Log.i(TAG, "Got remote stream: $stream")
        executor.execute {
            if (stream.videoTracks.isNotEmpty()) {
                val remoteVideoTrack = stream.videoTracks.first()
                remoteVideoTrack.setEnabled(true)
                remoteVideoTrack.addRenderer(VideoRenderer(videoRenderers.remoteRenderer))

            }
            val remoteAudioTrack = stream.audioTracks.first()
            remoteAudioTrack.setEnabled(true)
            remoteAudioTrack.setVolume(1000.0)
            Log.d("abhishek", "audio streams : ${stream.audioTracks.first()} : ${stream.id}")
        }
    }

//    private fun mediaStreamToByte(){
//        val locator = MediaLocator
//    }

    private fun removeRemoteStream(@Suppress("UNUSED_PARAMETER") _stream: MediaStream) {
        // We lost the stream, lets finish
        Log.d("abhishek", "remove stream");
        Log.i(TAG, "Bye")
        onStatusChangedListener(VideoCallStatus.FINISHED)
    }

    private fun handleRemoteCandidate(label: Int, id: String, strCandidate: String) {
        Log.i(TAG, "Got remote ICE candidate $strCandidate")
        executor.execute {
            val candidate = IceCandidate(id, label, strCandidate)
            peerConnection?.addIceCandidate(candidate)
        }
    }

    private fun setupMediaDevices() {
        Log.d("abhishek", "media setup");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val camera2 = Camera2Enumerator(context)
            if (camera2.deviceNames.isNotEmpty()) {
                val selectedDevice = camera2.deviceNames.firstOrNull(camera2::isFrontFacing)
                    ?: camera2.deviceNames.first()
                videoCapturer = camera2.createCapturer(selectedDevice, null)
            }
        }
        if (videoCapturer == null) {
            val camera1 = Camera1Enumerator(true)
            val selectedDevice = camera1.deviceNames.firstOrNull(camera1::isFrontFacing)
                ?: camera1.deviceNames.first()
            videoCapturer = camera1.createCapturer(selectedDevice, null)
        }


        videoSource = factory?.createVideoSource(videoCapturer)
        val captureObserver = MyCapturerObserver()
//        var surfaceTextureHelper =
//            SurfaceTextureHelper.create("CaptureThread", eglBase.getEglBaseContext());
//        videoSource = factory?.createVideoSource(videoCapturer);
//        videoCapturer?.initialize(surfaceTextureHelper, getApplicationContext(),captureObserver);
        videoCapturer?.startCapture(640, 480, 24)

//        Thread.sleep(100)

//        val surfaceTextureHelper =
//            SurfaceTextureHelper.create("byteThread", eglBase.getEglBaseContext());
//        Thread(Runnable {
//            kotlin.run {
////                videoCapturer?.initialize(surfaceTextureHelper, getApplicationContext(), captureObserver);
//                videoCapturer?.startCapture(640, 480, 24)
//            }
//        })


        Log.d("abhishek", "video capture start");
        val stream = factory?.createLocalMediaStream(STREAM_LABEL)
        val videoTrack = factory?.createVideoTrack(VIDEO_TRACK_LABEL, videoSource)

        val videoRenderer = VideoRenderer(videoRenderers.localRenderer)
        videoTrack?.addRenderer(videoRenderer)
        stream?.addTrack(videoTrack)

        audioSource = factory?.createAudioSource(createAudioConstraints())
        val audioTrack = factory?.createAudioTrack(AUDIO_TRACK_LABEL, audioSource)

        stream?.addTrack(audioTrack)

        peerConnection?.addStream(stream)

        val videoTracksFetched = stream?.videoTracks?.get(0)
        videoTracksFetched?.addSink(object : VideoSink {
            override fun onFrame(frame: VideoFrame) {
                // Handle the video frame
                val i420Buffer = frame.buffer.toI420()
                val videoFrame = VideoFrame(i420Buffer, frame.rotation, frame.timestampNs)
//                Log.d("abhishek","videoframe data $videoFrame")
                captureObserver.testI420toNV21Conversion(i420Buffer,context)
            }
        })
    }

    private fun createAudioConstraints(): MediaConstraints {
        Log.d("abhishek", "audio consts");
        val audioConstraints = MediaConstraints()
        audioConstraints.mandatory.add(
            MediaConstraints.KeyValuePair(
                "googEchoCancellation",
                "false"
            )
        )
        audioConstraints.mandatory.add(
            MediaConstraints.KeyValuePair(
                "googAutoGainControl",
                "false"
            )
        )
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "false"))
        audioConstraints.mandatory.add(
            MediaConstraints.KeyValuePair(
                "googNoiseSuppression",
                "false"
            )
        )
        audioConstraints.mandatory.add(
            MediaConstraints.KeyValuePair(
                "googEchoCancellation",
                "true"
            )
        )
        audioConstraints.mandatory.add(
            MediaConstraints.KeyValuePair(
                "googEchoCancellation2",
                "true"
            )
        )
        audioConstraints.mandatory.add(
            MediaConstraints.KeyValuePair(
                "googDAEchoCancellation",
                "true"
            )
        )
        audioConstraints.mandatory.add(
            MediaConstraints.KeyValuePair(
                "googTypingNoiseDetection",
                "true"
            )
        )
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
        audioConstraints.mandatory.add(
            MediaConstraints.KeyValuePair(
                "googAutoGainControl2",
                "true"
            )
        )
        audioConstraints.mandatory.add(
            MediaConstraints.KeyValuePair(
                "googNoiseSuppression",
                "true"
            )
        )
        audioConstraints.mandatory.add(
            MediaConstraints.KeyValuePair(
                "googNoiseSuppression2",
                "true"
            )
        )
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googAudioMirroring", "false"))
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
        return audioConstraints
    }

    private fun handleRemoteDescriptor(sdp: String) {
        Log.e(TAG, "Error egw: $sdp")
        if (isOfferingPeer) {
            peerConnection?.setRemoteDescription(SDPSetCallback({ setError ->
                if (setError != null) {
                    Log.e(TAG, "setRemoteDescription failed: $setError")
                }
            }), SessionDescription(SessionDescription.Type.ANSWER, sdp))
        } else {
            peerConnection?.setRemoteDescription(SDPSetCallback({ setError ->
                if (setError != null) {
                    Log.e(TAG, "Receiving offer setRemoteDescription failed: $setError")
                    Log.i(TAG, "EDWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWW")
                    isOfferingPeer = true
                    start()
                } else {
                    peerConnection?.createAnswer(
                        SDPCreateCallback(this::createDescriptorCallback),
                        MediaConstraints()
                    )
                }
            }), SessionDescription(SessionDescription.Type.OFFER, sdp.toString()))
        }
    }

    private fun createDescriptorCallback(result: SDPCreateResult) {
        when (result) {
            is SDPCreateSuccess -> {
                peerConnection?.setLocalDescription(SDPSetCallback({ setResult ->
                    Log.i(TAG, "SetLocalDescription: $setResult")
                }), result.descriptor)
                signaler.sendSDP(result.descriptor.description)
            }
            is SDPCreateFailure -> Log.e(TAG, "Error creating offer: ${result.reason}")
        }
    }

    private fun onMessage(message: ClientMessage) {
        when (message) {
            is MatchMessage -> {
                onStatusChangedListener(VideoCallStatus.CONNECTING)
                isOfferingPeer = message.offer
                start()
            }
            is SDPMessage -> {

                handleRemoteDescriptor(message.sdp)
            }
            is AnswerMessage -> {
                handleRemoteDescriptor(message.sdp)
            }
            is ICEMessage -> {
                handleRemoteCandidate(message.label, message.id, message.candidate)
            }
            is PeerLeft -> {
                onStatusChangedListener(VideoCallStatus.FINISHED)
            }
            is OnGoingCall -> { //egw
                onStatusChangedListener(VideoCallStatus.ON_GOING_CALL)
            }
        }
    }

    fun terminate() {
        signaler.close()
        try {
            videoCapturer?.stopCapture()
        } catch (ex: Exception) {
        }

        videoCapturer?.dispose()
        videoSource?.dispose()

        audioSource?.dispose()

        peerConnection?.dispose()

        factory?.dispose()

        eglBase.release()
    }

    //    @RequiresApi(Build.VERSION_CODES.R)
    fun mediaStreamToBitmap(stream: MediaStream) {
//        val mediaStream: MediaStream = stream// your MediaStream object
//
//        val mediaRecorder = MediaRecorder()
//
//// add all tracks to the media recorder
////        mediaStream.videoTracks.forEach { track ->
////            mediaRecorder.addTrack(track)
////        }
//
//// configure the media recorder
//        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
//        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA)
//
//        // Set the output format and encoding
//        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
//        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
//        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
//
//// start recording
//        mediaRecorder.prepare()
//        mediaRecorder.start()
//
//// wait for the recording to finish
//        Thread.sleep(5000) // for example, record for 5 seconds
//
//        // Set the maximum duration of the recording to 10 seconds
////        mediaRecorder.setMaxDuration(10000)
//
//// stop recording and release resources
//        mediaRecorder.stop()
//        mediaRecorder.release()


// get the recorded data as a Blob
//        mediaRecorder.setOnInfoListener { mr, what, extra ->
//            when (what) {
//                MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED -> {
//                    // The maximum duration of the recording has been reached
//                    // Stop the recording and do something
//                    mr.stop()
//                }
//                MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED -> {
//                    // The maximum file size of the recording has been reached
//                    // Stop the recording and do something
//                    mr.stop()
//                }
//            }
//        }


    }


    companion object {

        fun connect(
            context: Context,
            url: String,
            videoRenderers: VideoRenderers,
            callback: (VideoCallStatus) -> Unit
        ): VideoCallSession {
            val websocketHandler = SignalingWebSocket()
            val session = VideoCallSession(context, callback, websocketHandler, videoRenderers)
            val client = OkHttpClient()
            val request = Request.Builder().url(url).build()
            Log.i(TAG, "Connecting to $url")
            client.newWebSocket(request, websocketHandler)
            client.dispatcher().executorService().shutdown()
            return session
        }

        private val STREAM_LABEL = "remoteStream"
        private val VIDEO_TRACK_LABEL = "remoteVideoTrack"
        private val AUDIO_TRACK_LABEL = "remoteAudioTrack"
        private val TAG = "VideoCallSession"
        private val executor = Executors.newSingleThreadExecutor()
    }

}