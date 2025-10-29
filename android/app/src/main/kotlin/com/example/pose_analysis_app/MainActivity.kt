package com.example.pose_analysis_app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity: FlutterActivity() {
    private val CHANNEL = "com.example.pose_analysis_app/pose"
    private val POSE_ESTIMATION_REQUEST = 1
    private var channelResult: MethodChannel.Result? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler {
                call, result ->
            if (call.method == "openPoseEstimation") {
                channelResult = result
                val intent = Intent(this, PoseActivity::class.java)
                startActivityForResult(intent, POSE_ESTIMATION_REQUEST)
            } else {
                result.notImplemented()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == POSE_ESTIMATION_REQUEST) {
            if (resultCode == Activity.RESULT_OK) {
                val poseData = data?.getStringExtra("poseData")
                channelResult?.success(poseData)
            } else {
                channelResult?.success(null)
            }
            channelResult = null
        }
    }
}
