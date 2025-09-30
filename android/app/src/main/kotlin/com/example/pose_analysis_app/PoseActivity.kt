package com.example.pose_analysis_app

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Process
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.example.pose_analysis_app.Data.Device
import com.example.pose_analysis_app.camera.CameraSource
import com.example.pose_analysis_app.ml.ModelType
import com.example.pose_analysis_app.ml.MoveNet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PoseActivity : AppCompatActivity(), SensorEventListener {
    companion object {
        private const val FRAGMENT_DIALOG = "dialog"
    }

    /** A [SurfaceView] for camera preview.   */
    private lateinit var surfaceView: SurfaceView

    /** Default device is CPU */
    private var device = Device.CPU
    /** A [TextView] for Value preview.   */
    private lateinit var tvScore: TextView
    private lateinit var tvFPS: TextView
    private lateinit var topCircle: View
    private lateinit var rightCircle: View
    private lateinit var topBar: View
    private lateinit var rightBar: View

    private var topCirclePosX = 0f
    private var rightCirclePosY = 0f

    private lateinit var sensorManager: SensorManager
    private var gyroSensor: Sensor? = null
    private var cameraSource: CameraSource? = null
    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                openCamera()
            } else {
                ErrorDialog.newInstance(getString(R.string.tfe_pe_request_permission))
                    .show(supportFragmentManager, FRAGMENT_DIALOG)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // keep screen on while app is running
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        tvScore = findViewById(R.id.tvScore)
        tvFPS = findViewById(R.id.tvFps)
        surfaceView = findViewById(R.id.surfaceView)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        topCircle = findViewById(R.id.top_circle)
        rightCircle = findViewById(R.id.right_circle)
        topBar = findViewById(R.id.top_bar)
        rightBar = findViewById(R.id.right_bar)

        if (!isCameraPermissionGranted()) {
            requestPermission()
        }
    }

    override fun onStart() {
        super.onStart()
        openCamera()
    }

    override fun onResume() {
        cameraSource?.resume()
        super.onResume()
        gyroSensor?.let{
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        cameraSource?.close()
        cameraSource = null
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    private fun isCameraPermissionGranted(): Boolean {
        return checkPermission(
            Manifest.permission.CAMERA,
            Process.myPid(),
            Process.myUid()
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun openCamera() {
        if (isCameraPermissionGranted()) {
            if (cameraSource == null) {
                cameraSource =
                    CameraSource(surfaceView, object : CameraSource.CameraSourceListener {
                        override fun onFPSListener(fps: Int) {
                            runOnUiThread {
                                tvFPS.text = getString(R.string.tfe_pe_tv_fps, fps)
                            }
                        }

                        override fun onDetectedInfo(
                            personScore: Float?,
                            poseLabels: List<Pair<String, Float>>?
                        ) {
                            runOnUiThread {
                                tvScore.text = getString(R.string.tfe_pe_tv_score, personScore ?: 0f)
                            }
                        }

                    }).apply {
                        prepareCamera()
                    }
                lifecycleScope.launch(Dispatchers.Main) {
                    cameraSource?.initCamera()
                }
            }
            createPoseEstimator()
        }
    }

    private fun createPoseEstimator() {
        val poseDetector = MoveNet.create(this, device, ModelType.Thunder)
        poseDetector.let { detector ->
            cameraSource?.setDetector(detector)
        }
    }

    private fun requestPermission() {
        when (PackageManager.PERMISSION_GRANTED) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) -> {
                openCamera()
            }
            else -> {
                requestPermissionLauncher.launch(
                    Manifest.permission.CAMERA
                )
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_GYROSCOPE) return

        val rotX = event.values[0]   // pitch (위/아래)
        val rotY = event.values[1]   // roll (좌/우)

        // 이동 속도 계수
        val moveFactor = 5f

        // 이동 거리 계산
        topCirclePosX -= rotY * moveFactor
        rightCirclePosY += rotX * moveFactor

        // 경계 제한 (top_bar 너비 내에서만 이동)
        val topBarWidth = topBar.width
        val maxTopX = (topBarWidth - topCircle.width).toFloat().coerceAtLeast(0f)
        topCirclePosX = topCirclePosX.coerceIn(0f, maxTopX)

        // 경계 제한 (right_bar 높이 내에서만 이동)
        val rightBarHeight = rightBar.height
        val maxRightY = (rightBarHeight - rightCircle.height).toFloat().coerceAtLeast(0f)
        rightCirclePosY = rightCirclePosY.coerceIn(0f, maxRightY)

        // 이동 적용
        topCircle.translationX = topCirclePosX
        rightCircle.translationY = rightCirclePosY
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    class ErrorDialog : DialogFragment() {

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
            AlertDialog.Builder(activity)
                .setMessage(requireArguments().getString(ARG_MESSAGE))
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    // do nothing
                }
                .create()

        companion object {

            @JvmStatic
            private val ARG_MESSAGE = "message"

            @JvmStatic
            fun newInstance(message: String): ErrorDialog = ErrorDialog().apply {
                arguments = Bundle().apply { putString(ARG_MESSAGE, message) }
            }
        }
    }
}