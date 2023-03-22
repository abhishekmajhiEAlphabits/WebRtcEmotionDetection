package com.example.kotlinhelloworld.detectors

import android.content.Context
import android.content.res.AssetManager
import android.graphics.*
import android.util.Log
import android.util.Pair
import com.example.kotlinhelloworld.R
import com.example.kotlinhelloworld.classifiers.TFLiteImageClassifier
import com.example.kotlinhelloworld.utils.SortingHelper
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.*

class ImageProcess {
    private var mClassifier: TFLiteImageClassifier? = null
    private val MODEL_FILE_NAME = "simple_classifier.tflite"
    private lateinit var assets:AssetManager

    // Function to handle successful new image acquisition
    fun processImageRequestResult(scaledResultImageBitmap: Bitmap,context: Context) {
        mClassifier = TFLiteImageClassifier(
            context.assets,
            MODEL_FILE_NAME,
            context.resources.getStringArray(R.array.emotions)
        )
//        Bitmap scaledResultImageBitmap = getScaledImageBitmap(resultImageUri);
        detectFaces(scaledResultImageBitmap)
    }

    private fun detectFaces(imageBitmap: Bitmap) {
        val faceDetectorOptions = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.1f)
            .build()
        val faceDetector = FaceDetection.getClient(faceDetectorOptions)
        val firebaseImage = InputImage.fromBitmap(imageBitmap, 0)
        val result = faceDetector.process(firebaseImage)
            .addOnSuccessListener { faces ->

                // When the search for faces was successfully completed
                val imageBitmap = firebaseImage.bitmapInternal
                // Temporary Bitmap for drawing
                val tmpBitmap = Bitmap.createBitmap(
                    imageBitmap!!.width,
                    imageBitmap.height,
                    imageBitmap.config
                )

                // Create an image-based canvas
                val tmpCanvas = Canvas(tmpBitmap)
                tmpCanvas.drawBitmap(
                    imageBitmap, 0f, 0f,
                    null
                )
                val paint = Paint()
                paint.color = Color.GREEN
                paint.strokeWidth = 2f
                paint.textSize = 48f

                // Coefficient for indentation of face number
                val textIndentFactor = 0.1f

                // If at least one face was found
                if (!faces.isEmpty()) {
                    // faceId ~ face text number
                    var faceId = 1
                    for (face in faces) {
                        val faceRect = getInnerRect(
                            face.boundingBox,
                            imageBitmap.width,
                            imageBitmap.height
                        )

                        // Draw a rectangle around a face
//                                                paint.setStyle(Paint.Style.STROKE);
//                                                tmpCanvas.drawRect(faceRect, paint);

                        // Draw a face number in a rectangle
//                                                paint.setStyle(Paint.Style.FILL);
//                                                tmpCanvas.drawText(
//                                                        Integer.toString(faceId),
//                                                        faceRect.left +
//                                                                faceRect.width() * textIndentFactor,
//                                                        faceRect.bottom -
//                                                                faceRect.height() * textIndentFactor,
//                                                        paint);

                        // Get subarea with a face
                        val faceBitmap = Bitmap.createBitmap(
                            imageBitmap,
                            faceRect.left,
                            faceRect.top,
                            faceRect.width(),
                            faceRect.height()
                        )
                        classifyEmotions(faceBitmap, faceId)
                        faceId++
                    }


                    // If single face, then immediately open the list
                    if (faces.size == 1) {
                        Log.d("abhishekmajhi", "faces = 1")
                    }
                    // If no faces are found
                } else {
                    Log.d("abhishekmajhi", "No faces")
                }
            }
            .addOnFailureListener { e -> e.printStackTrace() }
    }

    private fun classifyEmotions(imageBitmap: Bitmap, faceId: Int) {
        val result: Map<String, Float> = mClassifier!!.classify(imageBitmap, true)

        // Sort by increasing probability
        val sortedResult = SortingHelper.sortByValues(result) as LinkedHashMap<String?, Float>
        val reversedKeys = ArrayList(sortedResult.keys)
        // Change the order to get a decrease in probabilities
        Collections.reverse(reversedKeys)
        val faceGroup = ArrayList<Pair<String?, String>>()
        for (key in reversedKeys) {
            val percentage = String.format("%.1f%%", sortedResult[key]!! * 100)
            faceGroup.add(Pair(key, percentage))
        }

//        String groupName = getString(R.string.face) + " " + faceId;
//        mClassificationResult.put(groupName, faceGroup);
        Log.d(
            "abhishekmajhi",
            "analysed data : faceId => " + faceId + "and facedata : " + faceGroup.toString()
        )
    }

    // Get a rectangle that lies inside the image area
    private fun getInnerRect(rect: Rect, areaWidth: Int, areaHeight: Int): Rect {
        val innerRect = Rect(rect)
        if (innerRect.top < 0) {
            innerRect.top = 0
        }
        if (innerRect.left < 0) {
            innerRect.left = 0
        }
        if (rect.bottom > areaHeight) {
            innerRect.bottom = areaHeight
        }
        if (rect.right > areaWidth) {
            innerRect.right = areaWidth
        }
        return innerRect
    }

//    private fun analyseData(faceId: Int,faceGroup:ArrayList<Pair<String?, String>>) {
//        if (faceGroup.equals(Pair("Happy","80%")).also {  }) {
//            if (faceGroup){
//
//            }
//        }
//    }
}