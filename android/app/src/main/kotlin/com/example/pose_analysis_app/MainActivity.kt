package com.example.pose_analysis_app

import android.content.Intent
import androidx.annotation.NonNull
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity: FlutterActivity() {
    private val CHANNEL = "com.example.pose_analysis_app/pose"

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler {
                call, result ->
            println("Call: ${call.method}")
            if (call.method == "openPoseEstimation") {
                val intent = Intent(this, PoseActivity::class.java)
                startActivity(intent)
                result.success(null)
            } else {
                result.notImplemented()
            }
        }
    }
}
