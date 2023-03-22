package com.example.kotlinhelloworld.classifiers

import com.example.kotlinhelloworld.classifiers.InterpreterImageParams.getInputImageWidth
import com.example.kotlinhelloworld.classifiers.InterpreterImageParams.getInputImageHeight
import android.content.res.AssetManager
import com.example.kotlinhelloworld.classifiers.TFLiteClassifier
import com.example.kotlinhelloworld.classifiers.behaviors.TFLiteImageClassification
import android.graphics.Bitmap
import com.example.kotlinhelloworld.classifiers.InterpreterImageParams
import com.example.kotlinhelloworld.utils.ImageUtils
import java.lang.IllegalArgumentException
import java.util.*

// Image classifier that uses tflite format
class TFLiteImageClassifier(
    assetManager: AssetManager?,
    modelFileName: String?,
    labels: Array<String?>?
) : TFLiteClassifier(
    assetManager!!, modelFileName, labels!!
) {
    init {
        classifyBehavior = TFLiteImageClassification(interpreter!!)
    }

    fun classify(imageBitmap: Bitmap, useFilter: Boolean): Map<String, Float> {
        val preprocessedImage = preprocessImage(imageBitmap, useFilter)
        return classify(preprocessedImage)
    }

    private fun classify(input: FloatArray): Map<String, Float> {
        val outputArr = classifyBehavior!!.classify(input)

        // Checked compliance with the array of strings specified in the constructor
        if (labels.size !== outputArr?.get(0)?.size) {
            val formatter = Formatter()
            throw IllegalArgumentException(
                formatter.format(
                    "labels array length must be equal to %1\$d, but actual length is %2\$d",
                    outputArr?.get(0)?.size,
                    labels.size
                ).toString()
            )
        }
        val outputMap: MutableMap<String, Float> = HashMap()
        var predictedLabel: String
        var probability: Float
        for (i in outputArr[0]?.indices!!) {
            predictedLabel = labels[i]
            probability = outputArr[0]!![i]
            outputMap[predictedLabel] = probability
        }
        return outputMap
    }

    private fun preprocessImage(imageBitmap: Bitmap, useFilter: Boolean): FloatArray {
        // Scale an image
        val scaledImage = Bitmap.createScaledBitmap(
            imageBitmap,
            getInputImageWidth(interpreter!!),
            getInputImageHeight(interpreter!!),
            useFilter
        )

        // Translate an image to greyscale format
        val greyScaleImage = ImageUtils.toGreyScale(scaledImage)

        // Translate an image to normalized float format [0f, 1f]
        val preprocessedImage = FloatArray(greyScaleImage.size)
        for (i in preprocessedImage.indices) {
            preprocessedImage[i] = greyScaleImage[i] / 255.0f
        }
        return preprocessedImage
    }
}