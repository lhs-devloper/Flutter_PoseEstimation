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
import android.widget.SeekBar
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
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
import com.example.pose_analysis_app.Data.Person
import android.util.Log
import android.graphics.Bitmap
import android.graphics.PointF
import com.example.pose_analysis_app.Data.BodyPart
import com.example.pose_analysis_app.Data.KeyPoint
import com.example.pose_analysis_app.VisualizationUtils
import com.google.gson.Gson
import android.content.Intent
import android.app.Activity

private enum class CaptureStep(val instruction: String) {
    FRONT("정면을 촬영하세요"),
    LEFT_SIDE("왼쪽 측면을 촬영하세요"),
    RIGHT_SIDE("오른쪽 측면을 촬영하세요"),
    RESULT("분석이 완료되었습니다.")
}

class PoseActivity : AppCompatActivity(), SensorEventListener {
    companion object {
        private const val TAG = "PoseActivity"
        private const val FRAGMENT_DIALOG = "dialog"
    }

    /** A [SurfaceView] for camera preview.   */
    private lateinit var surfaceView: SurfaceView

    /** Default device is CPU */
    private var device = Device.CPU
    /** A [TextView] for Value preview.   */
    private lateinit var tvScore: TextView
    private lateinit var tvFPS: TextView
    private lateinit var tvDebug: TextView
    private lateinit var horizontalSeek: SeekBar
    private lateinit var verticalSeek: SeekBar
    private lateinit var btnShot: Button
    private lateinit var resultImageView: ImageView
    private lateinit var btnCloseResult: Button
    private lateinit var btnAnalyze: Button
    private lateinit var instructionText: TextView
    private lateinit var thumbnailContainer: LinearLayout
    private lateinit var thumbFront: ImageView
    private lateinit var thumbLeft: ImageView
    private lateinit var thumbRight: ImageView


    private lateinit var sensorManager: SensorManager
    private var accelerometerSensor: Sensor? = null
    private var cameraSource: CameraSource? = null
    private var averagePersonResult: Person? = null // This will now hold the last computed person
    private var currentStep = CaptureStep.FRONT
    private val capturedResults = mutableMapOf<CaptureStep, Pair<Person, Bitmap>>()
    private val resultBitmaps = mutableMapOf<CaptureStep, Bitmap>()


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
        tvDebug = findViewById(R.id.tvDebug)
        tvDebug.visibility = View.GONE // Hide debug text view for now
        surfaceView = findViewById(R.id.surfaceView)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        horizontalSeek = findViewById(R.id.horizontalSeek)
        verticalSeek = findViewById(R.id.verticalSeek)
        btnShot = findViewById(R.id.btnShot)
        btnShot.isEnabled = false // Start as disabled
        resultImageView = findViewById(R.id.resultImageView)
        btnCloseResult = findViewById(R.id.btnCloseResult)
        instructionText = findViewById(R.id.instructionText)
        btnAnalyze = findViewById(R.id.btnAnalyze)
        thumbnailContainer = findViewById(R.id.thumbnailContainer)
        thumbFront = findViewById(R.id.thumbFront)
        thumbLeft = findViewById(R.id.thumbLeft)
        thumbRight = findViewById(R.id.thumbRight)
        updateUiForStep(currentStep)

        btnShot.setOnClickListener {
            cameraSource?.let {
                if (!it.isCollectingFrames()) {
                    it.startFrameCollection()
                    btnShot.text = "촬영 중... (5초)"
                }
            }
        }

        btnCloseResult.setOnClickListener {
            // Reset everything to the first step
            capturedResults.clear()
            resultBitmaps.clear()
            currentStep = CaptureStep.FRONT
            updateUiForStep(currentStep)

            resultImageView.visibility = View.GONE
            btnCloseResult.visibility = View.GONE
            btnAnalyze.visibility = View.GONE
            thumbnailContainer.visibility = View.GONE

            surfaceView.visibility = View.VISIBLE
            btnShot.visibility = View.VISIBLE
            // Re-evaluate button state
            onSensorChanged(null)
        }

        btnAnalyze.setOnClickListener {
            sendResultToFlutter()
        }

        thumbFront.setOnClickListener { selectThumbnail(CaptureStep.FRONT) }
        thumbLeft.setOnClickListener { selectThumbnail(CaptureStep.LEFT_SIDE) }
        thumbRight.setOnClickListener { selectThumbnail(CaptureStep.RIGHT_SIDE) }

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
        accelerometerSensor?.let{
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
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

                        override fun onFrameCollectionFinished(persons: List<Person>, lastFrame: Bitmap?) {
                            runOnUiThread {
                                if (persons.isNotEmpty() && lastFrame != null) {
                                    showToast("${persons.size}개의 프레임 수집 완료!")
                                    processFramesWithMeanShift(persons, lastFrame)
                                } else {
                                    showToast("프레임 수집에 실패했습니다.")
                                    btnShot.text = "정면 촬영"
                                    // Re-evaluate button state based on current gyro
                                    onSensorChanged(null)
                                }
                            }
                        }

                        override fun onCountdown(secondsRemaining: Int) {
                            runOnUiThread {
                                btnShot.text = "촬영 중... (${secondsRemaining}초)"
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
        // Allow null event to re-evaluate button state
        val values = event?.values
        if (event != null && event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = -(values?.get(0) ?: 0f) // Invert for left-right intuitive control
        val y = values?.get(1) ?: 9.8f  // Keep positive for upright-to-flat mapping

        // Horizontal (Roll) mapping for x-axis
        val horizontal = ((x / 9.8f) * 10).coerceIn(-10f, 10f)
        val horizontalProgress = (horizontal + 10).toInt()
        horizontalSeek.progress = horizontalProgress

        // Vertical (Pitch) mapping for y-axis
        val verticalProgress = ((-10 / 9.8f) * y + 20f).coerceIn(0f, 20f).toInt()
        verticalSeek.progress = verticalProgress

        // --- Gyro-based Button Logic ---
        val center = 10
        val threshold = 1
        val isCentered = (horizontalProgress in (center - threshold)..(center + threshold)) &&
                (verticalProgress in (center - threshold)..(center + threshold))

        if (currentStep != CaptureStep.RESULT && cameraSource?.isCollectingFrames() == true) {
            if (!isCentered) {
                // Cancel collection if gyro moves out of range
                cameraSource?.cancelFrameCollection()
                showToast("자세가 벗어나 촬영을 취소했습니다.")
                updateUiForStep(currentStep) // Reset button text
            }
        } else if (currentStep != CaptureStep.RESULT) {
            // Enable/disable button only when not collecting
            btnShot.isEnabled = isCentered
        } else {
            btnShot.isEnabled = false
        }

        // Update colors based on progress
        updateSeekBarColor(horizontalSeek, horizontalProgress)
        updateSeekBarColor(verticalSeek, verticalProgress)
    }

    private fun processFramesWithMeanShift(persons: List<Person>, lastFrame: Bitmap) {
        // --- Simplified MeanShift Clustering ---
        // 1. Flatten all keypoints into a single list of 34-dimensional vectors (17 keypoints * 2 coordinates)
        val poses = persons.map { person ->
            person.keyPoints.flatMap { keyPoint ->
                listOf(keyPoint.coordinate.x, keyPoint.coordinate.y)
            }.toFloatArray()
        }

        if (poses.isEmpty()) {
            showToast("분석할 유효한 자세가 없습니다.")
            return
        }

        // 2. Run a simplified clustering (find the densest point's neighborhood average)
        val bandwidth = 20f // Pixel distance threshold for neighbors
        var bestCenter: FloatArray? = null
        var maxNeighbors = -1
        var bestScores: FloatArray? = null

        for (currentPose in poses) {
            val neighbors = poses.filter { otherPose ->
                // Calculate Euclidean distance between currentPose and otherPose
                val distance = Math.sqrt(currentPose.zip(otherPose).sumOf { (a, b) -> ((a - b) * (a - b)).toDouble() })
                distance < bandwidth
            }

            if (neighbors.size > maxNeighbors) {
                maxNeighbors = neighbors.size
                // Calculate the centroid of this neighborhood
                val newCenter = FloatArray(currentPose.size)
                // Also calculate the average score for each keypoint
                val newScores = FloatArray(currentPose.size / 2)

                for (neighbor in neighbors) {
                    for (i in newCenter.indices) {
                        newCenter[i] += neighbor[i]
                    }
                    // Sum up scores for each keypoint from the original Person objects
                    // We need to find the original Person object that corresponds to this neighbor float array
                    val originalPerson = persons.find { p ->
                        p.keyPoints.flatMap { k -> listOf(k.coordinate.x, k.coordinate.y) }.toFloatArray().contentEquals(neighbor)
                    }
                    originalPerson?.keyPoints?.forEachIndexed { index, keyPoint ->
                        newScores[index] += keyPoint.score
                    }
                }
                for (i in newCenter.indices) {
                    newCenter[i] /= neighbors.size
                }
                for (i in newScores.indices) {
                    newScores[i] /= neighbors.size
                }
                bestCenter = newCenter
                bestScores = newScores
            }
        }

        // 3. Convert the center vector back to a Person object
        val averageKeyPoints = bestCenter?.toList()?.chunked(2)?.mapIndexed { index, coords ->
            val bodyPart = BodyPart.fromInt(index) // Assuming BodyPart enum has a mapping
            val score = bestScores?.get(index) ?: 0.0f
            KeyPoint(bodyPart, PointF(coords[0], coords[1]), score)
        }

        if (averageKeyPoints != null) {
            // Calculate the overall score as the average of all keypoint scores
            val overallScore = averageKeyPoints.map { it.score }.average().toFloat()
            val averagePerson = Person(keyPoints = averageKeyPoints, score = overallScore)
            averagePersonResult = averagePerson // Save the result

            // Store the result for the current step
            capturedResults[currentStep] = Pair(averagePerson, lastFrame)

            // --- Advance to the next step or show results ---
            when (currentStep) {
                CaptureStep.FRONT -> {
                    currentStep = CaptureStep.LEFT_SIDE
                    updateUiForStep(currentStep)
                    onSensorChanged(null) // Re-evaluate button state for the new step
                }
                CaptureStep.LEFT_SIDE -> {
                    currentStep = CaptureStep.RIGHT_SIDE
                    updateUiForStep(currentStep)
                    onSensorChanged(null) // Re-evaluate button state
                }
                CaptureStep.RIGHT_SIDE -> {
                    currentStep = CaptureStep.RESULT
                    updateUiForStep(currentStep)
                    showFinalResults()
                }
                CaptureStep.RESULT -> {
                    // Do nothing
                }
            }
        } else {
            showToast("평균 자세 계산에 실패했습니다.")
            updateUiForStep(currentStep) // Reset button text
        }
    }

    private fun showFinalResults() {
        // Generate and store all result bitmaps first
        for (step in listOf(CaptureStep.FRONT, CaptureStep.LEFT_SIDE, CaptureStep.RIGHT_SIDE)) {
            val result = capturedResults[step]
            if (result != null) {
                val (person, frame) = result
                val resultBitmap = frame.copy(Bitmap.Config.ARGB_8888, true)
                resultBitmaps[step] = VisualizationUtils.drawBodyKeypoints(resultBitmap, listOf(person))
            }
        }

        // Set images to thumbnails
        thumbFront.setImageBitmap(resultBitmaps[CaptureStep.FRONT])
        thumbLeft.setImageBitmap(resultBitmaps[CaptureStep.LEFT_SIDE])
        thumbRight.setImageBitmap(resultBitmaps[CaptureStep.RIGHT_SIDE])

        // Show UI
        resultImageView.visibility = View.VISIBLE
        btnCloseResult.visibility = View.VISIBLE
        btnAnalyze.visibility = View.VISIBLE
        thumbnailContainer.visibility = View.VISIBLE

        surfaceView.visibility = View.GONE
        btnShot.visibility = View.GONE
        instructionText.visibility = View.VISIBLE // Keep instruction visible

        // Select front as default
        selectThumbnail(CaptureStep.FRONT)
    }

    private fun selectThumbnail(step: CaptureStep) {
        resultImageView.setImageBitmap(resultBitmaps[step])

        thumbFront.isSelected = (step == CaptureStep.FRONT)
        thumbLeft.isSelected = (step == CaptureStep.LEFT_SIDE)
        thumbRight.isSelected = (step == CaptureStep.RIGHT_SIDE)
    }

    private fun sendResultToFlutter() {
        if (capturedResults.size == 3) {
            val resultsToSend = mapOf(
                "FRONT" to capturedResults[CaptureStep.FRONT]!!.first,
                "LEFT_SIDE" to capturedResults[CaptureStep.LEFT_SIDE]!!.first,
                "RIGHT_SIDE" to capturedResults[CaptureStep.RIGHT_SIDE]!!.first
            )
            val gson = Gson()
            val resultJson = gson.toJson(resultsToSend)
            val resultIntent = Intent()
            resultIntent.putExtra("poseData", resultJson)
            setResult(Activity.RESULT_OK, resultIntent)
        } else {
            setResult(Activity.RESULT_CANCELED)
        }
        finish()
    }

    private fun updateSeekBarColor(seekBar: SeekBar, progress: Int) {
        val center = 10
        val threshold = 1 // +-1 from center is green zone
        val context = this

        if (progress > center + threshold || progress < center - threshold) {
            // Out of range - Red
            seekBar.progressDrawable.setTint(ContextCompat.getColor(context, R.color.gyro_vertical_track))
            seekBar.thumb.setTint(ContextCompat.getColor(context, R.color.gyro_vertical_thumb))
        } else {
            // In range - Green
            seekBar.progressDrawable.setTint(ContextCompat.getColor(context, R.color.gyro_horizontal_track))
            seekBar.thumb.setTint(ContextCompat.getColor(context, R.color.gyro_horizontal_thumb))
        }
    }

    private fun updateUiForStep(step: CaptureStep) {
        instructionText.text = step.instruction
        when (step) {
            CaptureStep.FRONT -> btnShot.text = "정면 촬영"
            CaptureStep.LEFT_SIDE -> btnShot.text = "좌측 촬영"
            CaptureStep.RIGHT_SIDE -> btnShot.text = "우측 촬영"
            CaptureStep.RESULT -> {
                // Handled in showFinalResults
            }
        }
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