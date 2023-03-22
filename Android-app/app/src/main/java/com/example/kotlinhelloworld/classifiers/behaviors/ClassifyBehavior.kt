package com.example.kotlinhelloworld.classifiers.behaviors

interface ClassifyBehavior {
    fun classify(input: FloatArray?): Array<FloatArray?>?
}