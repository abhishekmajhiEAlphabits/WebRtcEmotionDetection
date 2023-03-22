package com.example.kotlinhelloworld.classifiers.behaviors

import com.example.kotlinhelloworld.classifiers.InterpreterImageParams.getInputImageWidth
import com.example.kotlinhelloworld.classifiers.InterpreterImageParams.getInputImageHeight
import com.example.kotlinhelloworld.classifiers.InterpreterImageParams.getInputColorDimLength
import com.example.kotlinhelloworld.classifiers.InterpreterImageParams.getOutputLength
import com.example.kotlinhelloworld.classifiers.behaviors.ClassifyBehavior
import com.example.kotlinhelloworld.classifiers.InterpreterImageParams
import org.tensorflow.lite.Interpreter
import java.lang.IllegalArgumentException
import java.util.*

class TFLiteImageClassification(private val mInterpreter: Interpreter) : ClassifyBehavior {
    private var mImageHeight = 0
    private var mImageWidth = 0
    private var mImageColorLength = 0
    private var mOutputLength = 0

    init {
        setImageParameters()
    }

    override fun classify(input: FloatArray?): Array<FloatArray?>? {
        // Check the size of the input array for compliance
        if (input!!.size != mImageHeight * mImageWidth * mImageColorLength) {
            val formatter = Formatter()
            throw IllegalArgumentException(
                formatter.format(
                    "input array length must be equal to %1\$d * %2\$d * %3\$d = %4\$d," +
                            " but actual length is %5\$d",
                    mImageHeight,
                    mImageWidth,
                    mImageColorLength,
                    mImageHeight * mImageWidth * mImageColorLength,
                    input.size
                ).toString()
            )
        }
        val formattedInput =
            Array(1) { Array(mImageHeight) { Array(mImageWidth) { FloatArray(mImageColorLength) } } }

        // Translate the array into the matrix
        for (y in 0 until mImageHeight) {
            for (x in 0 until mImageWidth) {
                for (c in 0 until mImageColorLength) {
                    formattedInput[0][y][x][c] = input[y * mImageHeight + x * mImageColorLength + c]
                }
            }
        }
        val outputArr = Array<FloatArray?>(1) { FloatArray(mOutputLength) }
        mInterpreter.run(formattedInput, outputArr)
        return outputArr
    }

    private fun setImageParameters() {
        // Get an input image params from the interpreter
        mImageWidth = getInputImageWidth(mInterpreter)
        mImageHeight = getInputImageHeight(mInterpreter)
        mImageColorLength = getInputColorDimLength(mInterpreter)

        // Get an output length from the interpreter
        mOutputLength = getOutputLength(mInterpreter)
    }
}