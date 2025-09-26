package com.example.pose_analysis_app.ml

import android.graphics.Bitmap
import com.example.pose_analysis_app.Data.Person

interface PoseDetector : AutoCloseable {

    fun estimatePoses(bitmap: Bitmap): List<Person>

    fun lastInferenceTimeNanos(): Long
}