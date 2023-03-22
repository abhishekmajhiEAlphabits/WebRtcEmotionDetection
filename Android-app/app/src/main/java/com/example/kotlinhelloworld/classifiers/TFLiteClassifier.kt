package com.example.kotlinhelloworld.classifiers

import android.content.res.AssetManager
import com.example.kotlinhelloworld.classifiers.behaviors.ClassifyBehavior
import org.tensorflow.lite.gpu.GpuDelegate

import android.content.res.AssetFileDescriptor
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.lang.Exception
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.*

// Abstract classifier using tflite format
abstract class TFLiteClassifier(
    protected var mAssetManager: AssetManager,
    modelFileName: String?,
    labels: Array<String?>
) {
    var interpreter: Interpreter? = null
        protected set
    protected var mTFLiteInterpreterOptions: Interpreter.Options
    var labels: List<String>
    @JvmField
    protected var classifyBehavior: ClassifyBehavior? = null

    init {
        val delegate = GpuDelegate()
        mTFLiteInterpreterOptions = Interpreter.Options().addDelegate(delegate)
        try {
            interpreter = Interpreter(loadModel(modelFileName), mTFLiteInterpreterOptions)
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        this.labels = ArrayList(Arrays.asList<String>(*labels))
    }


    fun loadModel(modelFileName: String?): MappedByteBuffer {
        val fileDescriptor = mAssetManager.openFd(modelFileName!!)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    // Close the interpreter to avoid memory leaks
    fun close() {
        interpreter!!.close()
    }
}