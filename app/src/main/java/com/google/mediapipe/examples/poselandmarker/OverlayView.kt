package com.google.mediapipe.examples.poselandmarker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaPlayer
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.acos
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// Wrapper to extract x, y, and z coordinates using reflection.
private class LandmarkWrapper(val landmark: Any) {
    val x: Float get() = landmark.javaClass.getMethod("x").invoke(landmark) as Float
    val y: Float get() = landmark.javaClass.getMethod("y").invoke(landmark) as Float
    val z: Float
        get() = try {
            landmark.javaClass.getMethod("z").invoke(landmark) as Float
        } catch (e: Exception) {
            0f
        }
}

class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs), SensorEventListener {

    companion object {
        private const val PI_F = 3.14159265f
    }

    // --- Pose results ---
    private var results: PoseLandmarkerResult? = null

    // --- Paints for drawing ---
    private var pointPaint = Paint()
    private var linePaint = Paint()
    private var textPaint = Paint()

    // --- Dimensions, scaling & offsets ---
    private var scaleFactor = 1f
    private var imageWidth = 1
    private var imageHeight = 1
    private var offsetX = 0f
    private var offsetY = 0f

    // --- Current exercise type ---
    private var exerciseType: ExerciseType = ExerciseType.BICEP
    fun setExerciseType(exerciseType: ExerciseType) {
        this.exerciseType = exerciseType
    }

    // --- MediaPlayer for error feedback (error.mp3) ---
    private var mediaPlayer: MediaPlayer? = null

    // --- Proximity sensor for distance measurement ---
    private var sensorManager: SensorManager? = null
    private var proximitySensor: Sensor? = null
    private var proximityDistance: Float = 0f

    // ===== BICEP LOGIC =====
    private val BICEP_ELBOW_EXTENDED = 150f  
    private val BICEP_ELBOW_CONTRACTED = 50f   
    private var repCountLeftBicep = 0
    private var repCountRightBicep = 0
    private var stageLeftBicep: String? = null
    private var stageRightBicep: String? = null

    // ===== SQUAT LOGIC =====
    private val SQUAT_DOWN_THRESHOLD = 90f
    private val SQUAT_UP_THRESHOLD = 170f
    private var repCountSquatLeft = 0
    private var repCountSquatRight = 0
    private var stageSquatLeft: String? = null
    private var stageSquatRight: String? = null

    // ===== LATERAL RAISE LOGIC =====
    private val LATERAL_RAISE_MIN = 75f
    private val LATERAL_RAISE_MAX = 95f
    private val LATERAL_RAISE_UP_THRESHOLD = 90f
    private val LATERAL_RAISE_DOWN_THRESHOLD = 50f
    private var repCountLateralLeft = 0
    private var repCountLateralRight = 0
    private var stageLateralLeft: String? = null
    private var stageLateralRight: String? = null

    // ===== LUNGES LOGIC =====
    private val LUNGES_DOWN_THRESHOLD = 100f
    private val LUNGES_UP_THRESHOLD = 160f
    private var repCountLungesLeft = 0
    private var repCountLungesRight = 0
    private var stageLungesLeft: String? = null
    private var stageLungesRight: String? = null

    // ===== SHOULDER PRESS LOGIC =====
    private var repCountShoulderLeft = 0
    private var repCountShoulderRight = 0
    private var stageShoulderLeft: String? = null
    private var stageShoulderRight: String? = null

    init {
        initPaints()
        sensorManager = context?.getSystemService(Context.SENSOR_SERVICE) as SensorManager?
        proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        proximitySensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        sensorManager?.unregisterListener(this)
    }

    private fun initPaints() {
        linePaint.color = ContextCompat.getColor(context!!, R.color.mp_color_primary)
        linePaint.strokeWidth = 12f
        linePaint.style = Paint.Style.STROKE

        pointPaint.color = Color.YELLOW
        pointPaint.strokeWidth = 12f
        pointPaint.style = Paint.Style.FILL

        // Set text for reps and distance to be black, bold and larger.
        textPaint.color = Color.BLACK
        textPaint.textSize = 48f
        textPaint.isFakeBoldText = true
        textPaint.style = Paint.Style.FILL
    }

    fun clear() {
        results = null

        repCountLeftBicep = 0
        repCountRightBicep = 0
        stageLeftBicep = null
        stageRightBicep = null

        repCountSquatLeft = 0
        repCountSquatRight = 0
        stageSquatLeft = null
        stageSquatRight = null

        repCountLateralLeft = 0
        repCountLateralRight = 0
        stageLateralLeft = null
        stageLateralRight = null

        repCountLungesLeft = 0
        repCountLungesRight = 0
        stageLungesLeft = null
        stageLungesRight = null

        repCountShoulderLeft = 0
        repCountShoulderRight = 0
        stageShoulderLeft = null
        stageShoulderRight = null

        pointPaint.reset()
        linePaint.reset()
        textPaint.reset()
        initPaints()
        invalidate()
    }

    // Add this method to retrieve the proximity distance
    fun getProximityDistance(): Float {
        return proximityDistance / 100f // Convert to meters
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val poseLandmarkerResult = results ?: return
        if (poseLandmarkerResult.landmarks().isEmpty()) return

        val personLandmarks = poseLandmarkerResult.landmarks()[0]
        
        // Draw points for landmarks
        for (lm in personLandmarks) {
            val lw = LandmarkWrapper(lm)
            canvas.drawPoint(
                offsetX + lw.x * imageWidth * scaleFactor,
                offsetY + lw.y * imageHeight * scaleFactor,
                pointPaint
            )
        }
        
        // Draw connections between landmarks
        drawPoseConnections(canvas, personLandmarks)
        
        // Process exercise data but don't draw text
        when (exerciseType) {
            ExerciseType.BICEP -> processBicepInternal(personLandmarks, canvas)
            ExerciseType.SQUAT -> processSquatInternal(personLandmarks, canvas)
            ExerciseType.LATERAL_RAISE -> processLateralRaiseInternal(personLandmarks, canvas)
            ExerciseType.LUNGES -> processLungesInternal(personLandmarks, canvas)
            ExerciseType.SHOULDER_PRESS -> processShoulderPressInternal(personLandmarks, canvas)
        }
    }

    private fun drawPoseConnections(canvas: Canvas, landmarks: List<Any>) {
        // Draw lines between connected landmarks
        val connections = listOf(
            // Face oval
            Pair(0, 1), Pair(1, 2), Pair(2, 3), Pair(3, 4), Pair(4, 5),
            Pair(5, 6), Pair(6, 7), Pair(7, 8), Pair(8, 9), Pair(9, 10),
            
            // Arms
            Pair(11, 13), Pair(13, 15), // Left arm
            Pair(12, 14), Pair(14, 16), // Right arm
            
            // Torso
            Pair(11, 12), // Shoulders
            Pair(11, 23), Pair(12, 24), // Shoulders to hips
            Pair(23, 24), // Hips
            
            // Legs
            Pair(23, 25), Pair(25, 27), Pair(27, 29), Pair(29, 31), // Left leg
            Pair(24, 26), Pair(26, 28), Pair(28, 30), Pair(30, 32)  // Right leg
        )
        
        for ((start, end) in connections) {
            if (landmarks.size <= maxOf(start, end)) continue
            
            val startLandmark = LandmarkWrapper(landmarks[start])
            val endLandmark = LandmarkWrapper(landmarks[end])
            
            canvas.drawLine(
                offsetX + startLandmark.x * imageWidth * scaleFactor,
                offsetY + startLandmark.y * imageHeight * scaleFactor,
                offsetX + endLandmark.x * imageWidth * scaleFactor,
                offsetY + endLandmark.y * imageHeight * scaleFactor,
                linePaint
            )
        }
    }

    // ----- BICEP PROCESSING (internal version without text drawing) -----
    private fun processBicepInternal(landmarks: List<Any>, canvas: Canvas) {
        if (landmarks.size <= 16) return

        val leftShoulder = landmarks[11]
        val leftElbow = landmarks[13]
        val leftWrist = landmarks[15]
        val rightShoulder = landmarks[12]
        val rightElbow = landmarks[14]
        val rightWrist = landmarks[16]

        val leftElbowAngle = calculateAngle(leftShoulder, leftElbow, leftWrist)
        val rightElbowAngle = calculateAngle(rightShoulder, rightElbow, rightWrist)

        if (leftElbowAngle > BICEP_ELBOW_EXTENDED) {
            stageLeftBicep = "down"
        }
        if (stageLeftBicep == "down" && leftElbowAngle < BICEP_ELBOW_CONTRACTED) {
            repCountLeftBicep++
            stageLeftBicep = "up"
            playAudioFeedback(R.raw.error)
        }
        if (rightElbowAngle > BICEP_ELBOW_EXTENDED) {
            stageRightBicep = "down"
        }
        if (stageRightBicep == "down" && rightElbowAngle < BICEP_ELBOW_CONTRACTED) {
            repCountRightBicep++
            stageRightBicep = "up"
            playAudioFeedback(R.raw.error)
        }

        val leftColor = if (leftElbowAngle in (BICEP_ELBOW_CONTRACTED - 10f)..(BICEP_ELBOW_EXTENDED + 10f))
            Color.GREEN else Color.RED
        val rightColor = if (rightElbowAngle in (BICEP_ELBOW_CONTRACTED - 10f)..(BICEP_ELBOW_EXTENDED + 10f))
            Color.GREEN else Color.RED

        drawLimb(canvas, leftShoulder, leftElbow, leftWrist, leftColor)
        drawLimb(canvas, rightShoulder, rightElbow, rightWrist, rightColor)
    }

    // ----- SQUAT PROCESSING (internal version without text drawing) -----
    private fun processSquatInternal(landmarks: List<Any>, canvas: Canvas) {
        if (landmarks.size <= 28) return

        val leftHip = landmarks[23]
        val leftKnee = landmarks[25]
        val leftAnkle = landmarks[27]
        val leftKneeAngle = calculateAngle(leftHip, leftKnee, leftAnkle)
        if (leftKneeAngle <= SQUAT_DOWN_THRESHOLD) {
            stageSquatLeft = "down"
        }
        if (stageSquatLeft == "down" && leftKneeAngle >= SQUAT_UP_THRESHOLD) {
            repCountSquatLeft++
            stageSquatLeft = "up"
            playAudioFeedback(R.raw.error)
        }

        val rightHip = landmarks[24]
        val rightKnee = landmarks[26]
        val rightAnkle = landmarks[28]
        val rightKneeAngle = calculateAngle(rightHip, rightKnee, rightAnkle)
        if (rightKneeAngle <= SQUAT_DOWN_THRESHOLD) {
            stageSquatRight = "down"
        }
        if (stageSquatRight == "down" && rightKneeAngle >= SQUAT_UP_THRESHOLD) {
            repCountSquatRight++
            stageSquatRight = "up"
            playAudioFeedback(R.raw.error)
        }

        val leftColor = if (leftKneeAngle <= SQUAT_DOWN_THRESHOLD || leftKneeAngle >= SQUAT_UP_THRESHOLD)
            Color.GREEN else Color.RED
        val rightColor = if (rightKneeAngle <= SQUAT_DOWN_THRESHOLD || rightKneeAngle >= SQUAT_UP_THRESHOLD)
            Color.GREEN else Color.RED

        drawLimb(canvas, leftHip, leftKnee, leftAnkle, leftColor)
        drawLimb(canvas, rightHip, rightKnee, rightAnkle, rightColor)
    }

    // ----- LATERAL RAISE PROCESSING (internal version without text drawing) -----
    private fun processLateralRaiseInternal(landmarks: List<Any>, canvas: Canvas) {
        if (landmarks.size <= 24) return

        val leftShoulder = landmarks[11]
        val leftElbow = landmarks[13]
        val leftHip = landmarks[23]
        val rightShoulder = landmarks[12]
        val rightElbow = landmarks[14]
        val rightHip = landmarks[24]

        val leftAbductionAngle = calculateAngle(leftHip, leftShoulder, leftElbow)
        val rightAbductionAngle = calculateAngle(rightHip, rightShoulder, rightElbow)

        if (leftAbductionAngle > LATERAL_RAISE_UP_THRESHOLD) {
            stageLateralLeft = "up"
        }
        if (stageLateralLeft == "up" && leftAbductionAngle < LATERAL_RAISE_DOWN_THRESHOLD) {
            repCountLateralLeft++
            stageLateralLeft = "down"
            playAudioFeedback(R.raw.error)
        }
        if (rightAbductionAngle > LATERAL_RAISE_UP_THRESHOLD) {
            stageLateralRight = "up"
        }
        if (stageLateralRight == "up" && rightAbductionAngle < LATERAL_RAISE_DOWN_THRESHOLD) {
            repCountLateralRight++
            stageLateralRight = "down"
            playAudioFeedback(R.raw.error)
        }

        val leftColor = if (leftAbductionAngle in (LATERAL_RAISE_MIN - 10f)..(LATERAL_RAISE_MAX + 10f))
            Color.GREEN else Color.RED
        val rightColor = if (rightAbductionAngle in (LATERAL_RAISE_MIN - 10f)..(LATERAL_RAISE_MAX + 10f))
            Color.GREEN else Color.RED

        if (leftAbductionAngle !in LATERAL_RAISE_MIN..LATERAL_RAISE_MAX ||
            rightAbductionAngle !in LATERAL_RAISE_MIN..LATERAL_RAISE_MAX) {
            playAudioFeedback(R.raw.error)
        }

        val leftWrist = landmarks[15]
        val rightWrist = landmarks[16]
        drawLimb(canvas, leftShoulder, leftElbow, leftWrist, leftColor)
        drawLimb(canvas, rightShoulder, rightElbow, rightWrist, rightColor)
    }

    // ----- LUNGES PROCESSING (internal version without text drawing) -----
    private fun processLungesInternal(landmarks: List<Any>, canvas: Canvas) {
        if (landmarks.size <= 28) return

        val leftHip = landmarks[23]
        val leftKnee = landmarks[25]
        val leftAnkle = landmarks[27]
        val leftKneeAngle = calculateAngle(leftHip, leftKnee, leftAnkle)
        if (leftKneeAngle <= LUNGES_DOWN_THRESHOLD) {
            stageLungesLeft = "down"
        }
        if (stageLungesLeft == "down" && leftKneeAngle >= LUNGES_UP_THRESHOLD) {
            repCountLungesLeft++
            stageLungesLeft = "up"
            playAudioFeedback(R.raw.error)
        }

        val rightHip = landmarks[24]
        val rightKnee = landmarks[26]
        val rightAnkle = landmarks[28]
        val rightKneeAngle = calculateAngle(rightHip, rightKnee, rightAnkle)
        if (rightKneeAngle <= LUNGES_DOWN_THRESHOLD) {
            stageLungesRight = "down"
        }
        if (stageLungesRight == "down" && rightKneeAngle >= LUNGES_UP_THRESHOLD) {
            repCountLungesRight++
            stageLungesRight = "up"
            playAudioFeedback(R.raw.error)
        }

        val leftColor = if (leftKneeAngle <= LUNGES_DOWN_THRESHOLD || leftKneeAngle >= LUNGES_UP_THRESHOLD)
            Color.GREEN else Color.RED
        val rightColor = if (rightKneeAngle <= LUNGES_DOWN_THRESHOLD || rightKneeAngle >= LUNGES_UP_THRESHOLD)
            Color.GREEN else Color.RED

        drawLimb(canvas, leftHip, leftKnee, leftAnkle, leftColor)
        drawLimb(canvas, rightHip, rightKnee, rightAnkle, rightColor)
    }

    // ----- SHOULDER PRESS PROCESSING (internal version without text drawing) -----
    private fun processShoulderPressInternal(landmarks: List<Any>, canvas: Canvas) {
        if (landmarks.size <= 16) return

        val leftShoulder = landmarks[11]
        val leftWrist = landmarks[15]
        val rightShoulder = landmarks[12]
        val rightWrist = landmarks[16]

        val leftPressAngle = computeAngleWithVertical(leftShoulder, leftWrist)
        val rightPressAngle = computeAngleWithVertical(rightShoulder, rightWrist)

        if (leftPressAngle > 60f) {
            stageShoulderLeft = "down"
        }
        if (stageShoulderLeft == "down" && leftPressAngle < 15f) {
            repCountShoulderLeft++
            stageShoulderLeft = "up"
            playAudioFeedback(R.raw.error)
        }
        if (rightPressAngle > 60f) {
            stageShoulderRight = "down"
        }
        if (stageShoulderRight == "down" && rightPressAngle < 15f) {
            repCountShoulderRight++
            stageShoulderRight = "up"
            playAudioFeedback(R.raw.error)
        }

        val leftColor = if (leftPressAngle < 15f) Color.GREEN else Color.RED
        val rightColor = if (rightPressAngle < 15f) Color.GREEN else Color.RED

        drawLimb(canvas, leftShoulder, leftWrist, leftWrist, leftColor)
        drawLimb(canvas, rightShoulder, rightWrist, rightWrist, rightColor)
    }

    // Helper to compute the angle between vector (shoulder→wrist) and vertical.
    private fun computeAngleWithVertical(shoulder: Any, wrist: Any): Float {
        val s = LandmarkWrapper(shoulder)
        val w = LandmarkWrapper(wrist)
        val dx = w.x - s.x
        val dy = w.y - s.y
        val mag = sqrt(dx * dx + dy * dy)
        if (mag == 0f) return 0f
        val dot = (0f * dx) + ((-1f) * dy) // vertical vector (0, -1)
        val angleRad = acos(dot / mag)
        return Math.toDegrees(angleRad.toDouble()).toFloat()
    }

    private fun drawLimb(canvas: Canvas, first: Any, second: Any, third: Any, color: Int) {
        linePaint.color = color
        pointPaint.color = color

        val f = LandmarkWrapper(first)
        val s = LandmarkWrapper(second)
        val t = LandmarkWrapper(third)

        val fx = offsetX + f.x * imageWidth * scaleFactor
        val fy = offsetY + f.y * imageHeight * scaleFactor
        val sx = offsetX + s.x * imageWidth * scaleFactor
        val sy = offsetY + s.y * imageHeight * scaleFactor
        val tx = offsetX + t.x * imageWidth * scaleFactor
        val ty = offsetY + t.y * imageHeight * scaleFactor

        canvas.drawLine(fx, fy, sx, sy, linePaint)
        canvas.drawLine(sx, sy, tx, ty, linePaint)
        canvas.drawCircle(fx, fy, 8f, pointPaint)
        canvas.drawCircle(sx, sy, 8f, pointPaint)
        canvas.drawCircle(tx, ty, 8f, pointPaint)
    }

    // Calculates the angle at point b (between lines ab and bc).
    private fun calculateAngle(a: Any, b: Any, c: Any): Float {
        val A = LandmarkWrapper(a)
        val B = LandmarkWrapper(b)
        val C = LandmarkWrapper(c)
        val radians = atan2(C.y - B.y, C.x - B.x) - atan2(A.y - B.y, A.x - B.x)
        var angle = abs(radians * 180f / PI_F)
        if (angle > 180f) angle = 360f - angle
        return angle
    }

    // Computes an approximate distance using the average z value from landmarks.
    private fun computeDistance(landmarks: List<Any>): Float {
        var sum = 0f
        var count = 0
        for (lm in landmarks) {
            try {
                val zVal = lm.javaClass.getMethod("z").invoke(lm) as Float
                sum += zVal
                count++
            } catch (e: Exception) { }
        }
        return if (count > 0) - (sum / count) else 0f
    }

    fun setResults(
        poseLandmarkerResults: PoseLandmarkerResult,
        imageHeight: Int,
        imageWidth: Int,
        runningMode: RunningMode = RunningMode.IMAGE
    ) {
        results = poseLandmarkerResults
        this.imageHeight = imageHeight
        this.imageWidth = imageWidth

        scaleFactor = when (runningMode) {
            RunningMode.IMAGE, RunningMode.VIDEO -> min(width * 1f / imageWidth, height * 1f / imageHeight)
            RunningMode.LIVE_STREAM -> max(width * 1f / imageWidth, height * 1f / imageHeight)
        }
        offsetX = (width - imageWidth * scaleFactor) / 2f
        offsetY = (height - imageHeight * scaleFactor) / 2f

        invalidate()
    }

    // Plays an audio file given its resource ID.
    private fun playAudioFeedback(audioResId: Int) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(context, audioResId)
        mediaPlayer?.start()
    }

    // SensorEventListener implementation.
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_PROXIMITY) {
            proximityDistance = event.values[0]
            invalidate()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op.
    }
}