package com.example.kotlinhelloworld.videoCall

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Handler
import android.util.Log
import com.example.kotlinhelloworld.detectors.ImageProcess
import org.webrtc.VideoCapturer.CapturerObserver
import org.webrtc.VideoFrame
import org.webrtc.VideoFrame.I420Buffer
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer


class MyCapturerObserver {

    private lateinit var imageProcessor: ImageProcess
    public lateinit var map: Bitmap
    private var count = 0

//    override fun onCapturerStarted(p0: Boolean) {
//        Log.d("abhishek", "byte data start capture....")
//    }
//
//    override fun onCapturerStopped() {
//        Log.d("abhishek", "capture stopped....")
//    }
//
//    override fun onFrameCaptured(p0: VideoFrame?) {
//        val byteBuffer = p0?.buffer?.toI420()
//        testI420toNV21Conversion(byteBuffer!!)
////        Log.d("abhishek","byteBuffer data $byteBuffer")
//    }
//
//    override fun onByteBufferFrameCaptured(
//        data: ByteArray?,
//        width: Int,
//        height: Int,
//        rotation: Int,
//        timeStamp: Long
//    ) {
//        super.onByteBufferFrameCaptured(data, width, height, rotation, timeStamp)
//    }

//    fun AndroidVideoTrackSourceObserver(nativeSource: Long) {
//        nativeSource = nativeSource
//    }

//    override fun onCapturerStarted(success: Boolean) {
//        nativeCapturerStarted(this.nativeSource, success)
//    }
//
//    override fun onCapturerStopped() {
//        nativeCapturerStopped(this.nativeSource)
//    }
//
//    override fun onByteBufferFrameCaptured(
//        data: ByteArray,
//        width: Int,
//        height: Int,
//        rotation: Int,
//        timeStamp: Long
//    ) {
//        nativeOnByteBufferFrameCaptured(
//            this.nativeSource,
//            data,
//            data.size,
//            width,
//            height,
//            rotation,
//            timeStamp
//        )
//    }
//
//    override fun onTextureFrameCaptured(
//        width: Int,
//        height: Int,
//        oesTextureId: Int,
//        transformMatrix: FloatArray,
//        rotation: Int,
//        timestamp: Long
//    ) {
//        nativeOnTextureFrameCaptured(
//            this.nativeSource,
//            width,
//            height,
//            oesTextureId,
//            transformMatrix,
//            rotation,
//            timestamp
//        )
//    }
//
//    override fun onFrameCaptured(frame: VideoFrame) {
//        nativeOnFrameCaptured(
//            this.nativeSource,
//            frame.buffer.width,
//            frame.buffer.height,
//            frame.rotation,
//            frame.timestampNs,
//            frame.buffer
//        )
//    }
//
//    private external fun nativeCapturerStarted(var0: Long, var2: Boolean)
//
//    private external fun nativeCapturerStopped(var0: Long)
//
//    private external fun nativeOnByteBufferFrameCaptured(
//        var0: Long,
//        var2: ByteArray,
//        var3: Int,
//        var4: Int,
//        var5: Int,
//        var6: Int,
//        var7: Long
//    )
//
//    private external fun nativeOnTextureFrameCaptured(
//        var0: Long,
//        var2: Int,
//        var3: Int,
//        var4: Int,
//        var5: FloatArray,
//        var6: Int,
//        var7: Long
//    )
//
//    private external fun nativeOnFrameCaptured(
//        var0: Long,
//        var2: Int,
//        var3: Int,
//        var4: Int,
//        var5: Long,
//        var7: VideoFrame.Buffer
//    )

    fun testI420toNV21Conversion(data: I420Buffer, context: Context) {
        // create a test I420 frame. Instead of this you can use the actual frame
        val i420Buffer: I420Buffer = data
        val width = i420Buffer.width
        val height = i420Buffer.height
        //convert to nv21, this is the same as byte[] from onPreviewCallback
        val nv21Data: ByteArray? = createNV21Data(i420Buffer)

        //let's test the conversion by converting the NV21 data to jpg and showing it in a bitmap.
        val yuvImage = YuvImage(nv21Data, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, out)
        val imageBytes: ByteArray = out.toByteArray()
        Log.d("abhishek", "byte data captured.... $imageBytes")
        val image = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        imageProcessor = ImageProcess()
        count++

        Thread(Runnable {
            kotlin.run {
                if (count % 50 == 0) {
                    imageProcessor.processImageRequestResult(image, context)
                }
            }
        }).start()
    }


    /** Create an NV21Buffer with the same pixel content as the given I420 buffer.  */
    fun createNV21Data(i420Buffer: I420Buffer): ByteArray? {
        val width = i420Buffer.width
        val height = i420Buffer.height
        val chromaWidth = (width + 1) / 2
        val chromaHeight = (height + 1) / 2
        val ySize = width * height
        val nv21Buffer: ByteBuffer = ByteBuffer.allocateDirect(ySize + width * chromaHeight)
        // We don't care what the array offset is since we only want an array that is direct.
        val nv21Data: ByteArray = nv21Buffer.array()
        for (y in 0 until height) {
            for (x in 0 until width) {
                val yValue = i420Buffer.dataY[y * i420Buffer.strideY + x]
                nv21Data[y * width + x] = yValue
            }
        }
        for (y in 0 until chromaHeight) {
            for (x in 0 until chromaWidth) {
                val uValue = i420Buffer.dataU[y * i420Buffer.strideU + x]
                val vValue = i420Buffer.dataV[y * i420Buffer.strideV + x]
                nv21Data[ySize + y * width + 2 * x + 0] = vValue
                nv21Data[ySize + y * width + 2 * x + 1] = uValue
            }
        }
        return nv21Data
    }

    /** Convert a byte array to a direct ByteBuffer.  */
    private fun toByteBuffer(array: IntArray): ByteBuffer? {
        val buffer: ByteBuffer = ByteBuffer.allocateDirect(array.size)
        buffer.put(toByteArray(array))
        buffer.rewind()
        return buffer
    }


    /**
     * Convert an int array to a byte array and make sure the values are within the range [0, 255].
     */
    private fun toByteArray(array: IntArray): ByteArray? {
        val res = ByteArray(array.size)
        for (i in array.indices) {
            val value = array[i]
            res[i] = value.toByte()
        }
        return res
    }
}