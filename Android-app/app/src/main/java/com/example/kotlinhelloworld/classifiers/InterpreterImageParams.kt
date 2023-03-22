package com.example.kotlinhelloworld.classifiers

import com.example.kotlinhelloworld.classifiers.InterpreterImageParams
import org.tensorflow.lite.Interpreter

object InterpreterImageParams {
    // Tensor indices of an image parameters
    private const val IMAGE_INPUT_TENSOR_INDEX = 0
    private const val IMAGE_OUTPUT_TENSOR_INDEX = 0

    // Indices of an input image parameters
    private const val MODEL_INPUT_WIDTH_INDEX = 1
    private const val MODEL_INPUT_HEIGHT_INDEX = 2
    private const val MODEL_INPUT_COLOR_DIM_INDEX = 3

    // Index of an output result array
    private const val MODEL_OUTPUT_LENGTH_INDEX = 1
    @JvmStatic
    fun getInputImageWidth(interpreter: Interpreter): Int {
        return interpreter.getInputTensor(IMAGE_INPUT_TENSOR_INDEX).shape()[MODEL_INPUT_WIDTH_INDEX]
    }

    @JvmStatic
    fun getInputImageHeight(interpreter: Interpreter): Int {
        return interpreter.getInputTensor(IMAGE_INPUT_TENSOR_INDEX)
            .shape()[MODEL_INPUT_HEIGHT_INDEX]
    }

    @JvmStatic
    fun getInputColorDimLength(interpreter: Interpreter): Int {
        return interpreter.getInputTensor(IMAGE_INPUT_TENSOR_INDEX)
            .shape()[MODEL_INPUT_COLOR_DIM_INDEX]
    }

    @JvmStatic
    fun getOutputLength(interpreter: Interpreter): Int {
        return interpreter.getOutputTensor(IMAGE_OUTPUT_TENSOR_INDEX)
            .shape()[MODEL_OUTPUT_LENGTH_INDEX]
    }
}