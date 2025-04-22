package com.google.mediapipe.examples.poselandmarker

import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Manages exercise feedback, form evaluation, and rep counting based on pose landmarks
 * Improved with more robust rep counting logic
 */
class ExerciseFeedbackManager(
    private val overlayView: OverlayView,
    private val repCountTextView: TextView,
    private val formFeedbackTextView: TextView,
    private val formTipTextView: TextView,
    private val angleTextView: TextView,
    private val distanceTextView: TextView,
    private val repStageTextView: TextView,
    private val errorFlashView: View,
    private val cardFormFeedback: CardView
) {
    // Current exercise details
    var exerciseType: ExerciseType = ExerciseType.BICEP
        set(value) {
            field = value
            resetCounters()
            updateExerciseSpecificTips()
        }

    // Rep counting
    private var repCount = 0
    private var repStage: RepStage = RepStage.WAITING
    private var lastGoodFormTimestamp = System.currentTimeMillis()
    
    // Form evaluation
    private var currentAngle: Float = 0f
    private var formErrors = mutableListOf<FormError>()
    private var consecutiveGoodReps = 0
    private var distance: Float = 0f

    private var lastAngle: Float = 0f
    private var minMovementThreshold = 15f // Minimum angle change required to start counting
    
    // Movement detection
    private var isMoving = false
    private var lastMovementTime = 0L
    private val movementTimeoutMs = 1000 // 1 second timeout for movement detection
    
    // Rep cycle tracking for bilateral exercises
    private var leftSideComplete = false
    private var rightSideComplete = false
    private var lastSignificantAngleChange = 0L
    private val significantAngleChangeTimeoutMs = 1500 // 1.5 seconds to complete both sides
    
    // Time throttling for rep counting
    private var lastRepTime = 0L
    private val MIN_TIME_BETWEEN_REPS = 1500L // 1.5 seconds between reps

    // Handler for UI updates
    private val handler = Handler(Looper.getMainLooper())
    private var errorFlashRunnable: Runnable? = null
    
    // Track if a rep was just completed
    private var repJustCompleted = false
    
    // Angle history for movement detection
    private val angleHistory = ArrayDeque<Float>(5)
    private val angleChangeThreshold = 5f // Minimum change to consider as movement

    // Exercise-specific parameters
    private val exerciseParams = mapOf(
        ExerciseType.BICEP to ExerciseParams(
            minAngle = 30f,
            maxAngle = 160f,
            perfectFormMinAngle = 45f,
            perfectFormMaxAngle = 150f,
            repCompletionThreshold = 140f,
            repStartThreshold = 60f,
            primaryTip = "Keep elbows close to body"
        ),
        ExerciseType.SQUAT to ExerciseParams(
            minAngle = 70f,
            maxAngle = 170f, 
            perfectFormMinAngle = 80f,
            perfectFormMaxAngle = 160f,
            repCompletionThreshold = 150f,
            repStartThreshold = 90f,
            primaryTip = "Keep knees aligned with toes"
        ),
        ExerciseType.LATERAL_RAISE to ExerciseParams(
            minAngle = 10f,
            maxAngle = 100f,
            perfectFormMinAngle = 20f,
            perfectFormMaxAngle = 90f,
            repCompletionThreshold = 80f,
            repStartThreshold = 30f,
            primaryTip = "Keep slight bend in elbows"
        ),
        ExerciseType.LUNGES to ExerciseParams(
            minAngle = 70f,
            maxAngle = 170f,
            perfectFormMinAngle = 90f,
            perfectFormMaxAngle = 160f,
            repCompletionThreshold = 150f,
            repStartThreshold = 100f,
            primaryTip = "Front knee should not extend past toes"
        ),
        ExerciseType.SHOULDER_PRESS to ExerciseParams(
            minAngle = 10f,
            maxAngle = 170f,
            perfectFormMinAngle = 20f,
            perfectFormMaxAngle = 160f,
            repCompletionThreshold = 150f,
            repStartThreshold = 60f,
            primaryTip = "Keep core engaged and avoid arching back"
        )
    )

    init {
        updateExerciseSpecificTips()
    }

    fun resetCounters() {
        repCount = 0
        repStage = RepStage.WAITING
        consecutiveGoodReps = 0
        formErrors.clear()
        isMoving = false
        lastMovementTime = 0L
        leftSideComplete = false
        rightSideComplete = false
        angleHistory.clear()
        lastRepTime = 0L
        updateUI()
    }

    private fun updateExerciseSpecificTips() {
        val params = exerciseParams[exerciseType] ?: return
        formTipTextView.text = params.primaryTip
    }

    /**
     * Process new pose detection results
     */
    fun processPoseResults(
        result: PoseLandmarkerResult,
        proximityDistance: Float
    ) {
        if (result.landmarks().isEmpty()) return
        
        // Update distance measurement
        distance = proximityDistance
        
        // Process exercise based on type
        when (exerciseType) {
            ExerciseType.BICEP -> processBicepCurl(result)
            ExerciseType.SQUAT -> processSquat(result)
            ExerciseType.LATERAL_RAISE -> processLateralRaise(result)
            ExerciseType.LUNGES -> processLunges(result)
            ExerciseType.SHOULDER_PRESS -> processShoulderPress(result)
        }
        
        // Update UI
        updateUI()
    }

    /**
     * Count a rep only if movement is detected and enough time has passed
     */
    private fun countRep() {
        // Only count if we're moving and enough time has passed
        val currentTime = System.currentTimeMillis()
        if (!isMoving || currentTime - lastRepTime < MIN_TIME_BETWEEN_REPS) {
            return
        }
        
        repCount++
        repJustCompleted = true
        lastRepTime = currentTime
        
        // Check if this was a good rep (form errors)
        if (formErrors.isEmpty()) {
            consecutiveGoodReps++
        } else {
            consecutiveGoodReps = 0
            showErrorFlash()
        }
    }

    private fun processBicepCurl(result: PoseLandmarkerResult) {
        val landmarks = result.landmarks()[0]
        if (landmarks.size <= 16) return

        // Calculate both arm angles
        val leftShoulderVisible = isLandmarkConfident(landmarks[11])
        val rightShoulderVisible = isLandmarkConfident(landmarks[12])
        
        var leftAngle = 0f
        var rightAngle = 0f
        var dominantAngle = 0f
        
        if (leftShoulderVisible) {
            val leftShoulder = landmarks[11]
            val leftElbow = landmarks[13]
            val leftWrist = landmarks[15]
            
            leftAngle = calculateAngle(leftShoulder, leftElbow, leftWrist)
            // Check form errors for left arm
            if (isLandmarkConfident(landmarks[23])) { // If left hip is visible
                val leftHip = landmarks[23]
                val shoulderHipElbowAngle = calculateAngle(leftShoulder, leftHip, leftElbow)
                if (shoulderHipElbowAngle > 30f) {
                    formErrors.add(FormError.ELBOW_AWAY_FROM_BODY)
                }
            }
        }
        
        if (rightShoulderVisible) {
            val rightShoulder = landmarks[12]
            val rightElbow = landmarks[14]
            val rightWrist = landmarks[16]
            
            rightAngle = calculateAngle(rightShoulder, rightElbow, rightWrist)
            // Check form errors for right arm
            if (isLandmarkConfident(landmarks[24])) { // If right hip is visible
                val rightHip = landmarks[24]
                val shoulderHipElbowAngle = calculateAngle(rightShoulder, rightHip, rightElbow)
                if (shoulderHipElbowAngle > 30f) {
                    formErrors.add(FormError.ELBOW_AWAY_FROM_BODY)
                }
            }
        }
        
        // Use the average angle if both arms are visible, otherwise use the visible one
        dominantAngle = if (leftShoulderVisible && rightShoulderVisible) {
            (leftAngle + rightAngle) / 2f
        } else if (leftShoulderVisible) {
            leftAngle
        } else if (rightShoulderVisible) {
            rightAngle
        } else {
            0f // No landmarks visible
        }
        
        currentAngle = dominantAngle
        
        // Track left and right arm progress for complete rep
        if (leftShoulderVisible) {
            // Check if left arm completed its part of the rep
            checkArmCompletion(leftAngle, isLeft = true)
        }
        
        if (rightShoulderVisible) {
            // Check if right arm completed its part of the rep
            checkArmCompletion(rightAngle, isLeft = false)
        }
        
        // Check if both arms have completed their parts to count as a full rep
        checkBilateralRepCompletion()
        
        // Update movement detection
        detectMovement(dominantAngle)
    }

    private fun processSquat(result: PoseLandmarkerResult) {
        val landmarks = result.landmarks()[0]
        if (landmarks.size <= 28) return

        // Calculate knee angles
        val leftHip = landmarks[23]
        val leftKnee = landmarks[25]
        val leftAnkle = landmarks[27]
        val leftKneeAngle = calculateAngle(leftHip, leftKnee, leftAnkle)
        
        val rightHip = landmarks[24]
        val rightKnee = landmarks[26]
        val rightAnkle = landmarks[28]
        val rightKneeAngle = calculateAngle(rightHip, rightKnee, rightAnkle)
        
        // Use the average of both knees if visible, otherwise use whichever is available
        val kneeAngle = if (isLandmarkConfident(leftKnee) && isLandmarkConfident(rightKnee)) {
            (leftKneeAngle + rightKneeAngle) / 2
        } else if (isLandmarkConfident(leftKnee)) {
            leftKneeAngle
        } else if (isLandmarkConfident(rightKnee)) {
            rightKneeAngle
        } else {
            return // No reliable knee detected
        }
        
        currentAngle = kneeAngle

        // Check form errors
        formErrors.clear()
        
        // Error: Knees too far forward (over toes)
        val leftToe = landmarks[31]
        val rightToe = landmarks[32]
        
        if (isLandmarkConfident(leftKnee) && isLandmarkConfident(leftToe)) {
            val knee = LandmarkWrapper(leftKnee)
            val toe = LandmarkWrapper(leftToe)
            if (knee.x > toe.x + 0.05f) { // Knee is significantly in front of toe
                formErrors.add(FormError.KNEES_OVER_TOES)
            }
        } else if (isLandmarkConfident(rightKnee) && isLandmarkConfident(rightToe)) {
            val knee = LandmarkWrapper(rightKnee)
            val toe = LandmarkWrapper(rightToe)
            if (knee.x < toe.x - 0.05f) { // For right side, coordinates are flipped
                formErrors.add(FormError.KNEES_OVER_TOES)
            }
        }
        
        // Error: Back not straight (check alignment of shoulders-hip-knees)
        if (isLandmarkConfident(leftHip) && isLandmarkConfident(leftKnee) && 
            isLandmarkConfident(landmarks[11])) { // left shoulder
            val shoulderHipKneeAngle = calculateAngle(landmarks[11], leftHip, leftKnee)
            if (abs(shoulderHipKneeAngle - 180f) > 25f) {
                formErrors.add(FormError.BACK_NOT_STRAIGHT)
            }
        }
        
        // For squats, we'll track as a single movement (not bilateral)
        // Process rep counting
        processSquatRep(kneeAngle)
        
        // Update movement detection
        detectMovement(kneeAngle)
    }

    private fun processLateralRaise(result: PoseLandmarkerResult) {
        val landmarks = result.landmarks()[0]
        if (landmarks.size <= 24) return

        val leftShoulder = landmarks[11]
        val leftElbow = landmarks[13]
        val leftHip = landmarks[23]
        val rightShoulder = landmarks[12]
        val rightElbow = landmarks[14]
        val rightHip = landmarks[24]
        
        // Calculate angles for both arms
        val leftArmAngle = calculateAngle(leftHip, leftShoulder, leftElbow)
        val rightArmAngle = calculateAngle(rightHip, rightShoulder, rightElbow)
        
        // Use average of both sides if visible
        val armAngle = if (isLandmarkConfident(leftShoulder) && isLandmarkConfident(rightShoulder)) {
            (leftArmAngle + rightArmAngle) / 2
        } else if (isLandmarkConfident(leftShoulder)) {
            leftArmAngle
        } else if (isLandmarkConfident(rightShoulder)) {
            rightArmAngle
        } else {
            return // No reliable shoulder detected
        }
        
        currentAngle = armAngle
        
        // Check form errors
        formErrors.clear()
        
        // Error: Arms not at same height (asymmetry)
        if (isLandmarkConfident(leftElbow) && isLandmarkConfident(rightElbow)) {
            val leftElbowWrapper = LandmarkWrapper(leftElbow)
            val rightElbowWrapper = LandmarkWrapper(rightElbow)
            if (abs(leftElbowWrapper.y - rightElbowWrapper.y) > 0.05f) {
                formErrors.add(FormError.ASYMMETRIC_MOVEMENT)
            }
        }
        
        // Error: Elbows too straight or too bent
        if (isLandmarkConfident(leftShoulder) && isLandmarkConfident(leftElbow) && isLandmarkConfident(landmarks[15])) {
            val elbowAngle = calculateAngle(leftShoulder, leftElbow, landmarks[15])
            if (elbowAngle < 150f) {
                formErrors.add(FormError.ELBOWS_TOO_BENT)
            }
        }
        
        // Track left and right arm progress for complete rep
        if (isLandmarkConfident(leftShoulder)) {
            checkArmCompletion(leftArmAngle, isLeft = true)
        }
        
        if (isLandmarkConfident(rightShoulder)) {
            checkArmCompletion(rightArmAngle, isLeft = false)
        }
        
        // Check if both arms have completed their parts to count as a full rep
        checkBilateralRepCompletion()
        
        // Update movement detection
        detectMovement(armAngle)
    }

    private fun processLunges(result: PoseLandmarkerResult) {
        val landmarks = result.landmarks()[0]
        if (landmarks.size <= 28) return

        // Determine which leg is forward by comparing ankle positions
        val leftAnkle = LandmarkWrapper(landmarks[27])
        val rightAnkle = LandmarkWrapper(landmarks[28])
        
        // Process the front leg (lower knee angle indicates deeper lunge)
        val (hipLandmark, kneeLandmark, ankleLandmark) = if (leftAnkle.y < rightAnkle.y) {
            // Left leg is forward
            Triple(landmarks[23], landmarks[25], landmarks[27])
        } else {
            // Right leg is forward
            Triple(landmarks[24], landmarks[26], landmarks[28])
        }
        
        if (!isLandmarkConfident(hipLandmark) || !isLandmarkConfident(kneeLandmark) || 
            !isLandmarkConfident(ankleLandmark)) {
            return
        }
        
        val kneeAngle = calculateAngle(hipLandmark, kneeLandmark, ankleLandmark)
        currentAngle = kneeAngle
        
        // Check form errors
        formErrors.clear()
        
        // Error: Front knee over toes
        if (kneeAngle < 80f) {
            val knee = LandmarkWrapper(kneeLandmark)
            val ankle = LandmarkWrapper(ankleLandmark)
            
            // Check if knee is too far forward (depends on which side is forward)
            val kneeOverToes = if (leftAnkle.y < rightAnkle.y) {
                knee.x > ankle.x + 0.05f // Left leg forward
            } else {
                knee.x < ankle.x - 0.05f // Right leg forward
            }
            
            if (kneeOverToes) {
                formErrors.add(FormError.KNEES_OVER_TOES)
            }
        }
        
        // Error: Back not straight
        val shoulderLandmark = if (leftAnkle.y < rightAnkle.y) landmarks[11] else landmarks[12]
        if (isLandmarkConfident(shoulderLandmark)) {
            val shoulderHipKneeAngle = calculateAngle(shoulderLandmark, hipLandmark, kneeLandmark)
            if (abs(shoulderHipKneeAngle - 180f) > 25f) {
                formErrors.add(FormError.BACK_NOT_STRAIGHT)
            }
        }
        
        // For lunges, track left and right leg separately
        val isLeftLeg = leftAnkle.y < rightAnkle.y
        checkLegCompletion(kneeAngle, isLeftLeg)
        
        // Check if both legs have completed their parts
        checkBilateralRepCompletion()
        
        // Update movement detection
        detectMovement(kneeAngle)
    }

    private fun processShoulderPress(result: PoseLandmarkerResult) {
        val landmarks = result.landmarks()[0]
        if (landmarks.size <= 16) return

        // Calculate vertical angles for both arms
        val leftShoulderLandmark = landmarks[11]  // Renamed to avoid conflict
        val leftWristLandmark = landmarks[15]     // Renamed to avoid conflict
        val rightShoulderLandmark = landmarks[12] // Renamed to avoid conflict 
        val rightWristLandmark = landmarks[16]    // Renamed to avoid conflict
        
        // Calculate angle with vertical
        val leftPressAngle = if (isLandmarkConfident(leftShoulderLandmark) && 
                                isLandmarkConfident(leftWristLandmark)) {
            computeAngleWithVertical(leftShoulderLandmark, leftWristLandmark)
        } else {
            null
        }
        
        val rightPressAngle = if (isLandmarkConfident(rightShoulderLandmark) && 
                                isLandmarkConfident(rightWristLandmark)) {
            computeAngleWithVertical(rightShoulderLandmark, rightWristLandmark)
        } else {
            null
        }
        
        // Use average of both sides if visible
        val pressAngle = when {
            leftPressAngle != null && rightPressAngle != null -> (leftPressAngle + rightPressAngle) / 2
            leftPressAngle != null -> leftPressAngle
            rightPressAngle != null -> rightPressAngle
            else -> return // No reliable shoulder/wrist detected
        }
        
        currentAngle = pressAngle
        
        // Check form errors
        formErrors.clear()
        
        // Error: Asymmetric movement (arms not at same height)
        if (leftPressAngle != null && rightPressAngle != null) {
            if (abs(leftPressAngle - rightPressAngle) > 15f) {
                formErrors.add(FormError.ASYMMETRIC_MOVEMENT)
            }
        }
        
        // Error: Back arching
        val leftHipLandmark = landmarks[23]  // Renamed to avoid conflict
        if (isLandmarkConfident(leftHipLandmark) && isLandmarkConfident(leftShoulderLandmark)) {
            val hip = LandmarkWrapper(leftHipLandmark)
            val shoulder = LandmarkWrapper(leftShoulderLandmark)
            // If shoulder is significantly behind hip horizontally, back is likely arching
            if (shoulder.x < hip.x - 0.05f) {
                formErrors.add(FormError.BACK_ARCHING)
            }
        }
        
        // Track left and right arm progress for complete rep
        if (leftPressAngle != null) {
            // Convert to the format used by our rep counter (90f - pressAngle)
            checkArmCompletion(90f - leftPressAngle, isLeft = true)
        }
        
        if (rightPressAngle != null) {
            // Convert to the format used by our rep counter (90f - pressAngle)
            checkArmCompletion(90f - rightPressAngle, isLeft = false)
        }
        
        // Check if both arms have completed their parts to count as a full rep
        checkBilateralRepCompletion()
        
        // Update movement detection
        detectMovement(90f - pressAngle) // Convert to make the logic consistent
    }
    
    /**
     * Check if an arm has completed its part of a rep
     */
    private fun checkArmCompletion(angle: Float, isLeft: Boolean) {
        val params = exerciseParams[exerciseType] ?: return
        
        if (isLeft) {
            if (repStage == RepStage.WAITING || repStage == RepStage.UP) {
                if (angle <= params.repStartThreshold) {
                    // Arm has moved to the DOWN position
                    leftSideComplete = true
                    lastSignificantAngleChange = System.currentTimeMillis()
                }
            } else if (repStage == RepStage.DOWN) {
                if (angle >= params.repCompletionThreshold) {
                    // Arm has moved to the UP position
                    leftSideComplete = true
                    lastSignificantAngleChange = System.currentTimeMillis()
                }
            }
        } else { // Right arm
            if (repStage == RepStage.WAITING || repStage == RepStage.UP) {
                if (angle <= params.repStartThreshold) {
                    // Arm has moved to the DOWN position
                    rightSideComplete = true
                    lastSignificantAngleChange = System.currentTimeMillis()
                }
            } else if (repStage == RepStage.DOWN) {
                if (angle >= params.repCompletionThreshold) {
                    // Arm has moved to the UP position
                    rightSideComplete = true
                    lastSignificantAngleChange = System.currentTimeMillis()
                }
            }
        }
    }
    
    /**
     * Check if a leg has completed its part of a rep (for lunges)
     */
    private fun checkLegCompletion(angle: Float, isLeft: Boolean) {
        val params = exerciseParams[exerciseType] ?: return
        
        if (repStage == RepStage.WAITING || repStage == RepStage.UP) {
            if (angle <= params.repStartThreshold) {
                // Leg has moved to the DOWN position
                if (isLeft) {
                    leftSideComplete = true
                } else {
                    rightSideComplete = true
                }
                lastSignificantAngleChange = System.currentTimeMillis()
                repStage = RepStage.DOWN
            }
        } else if (repStage == RepStage.DOWN) {
            if (angle >= params.repCompletionThreshold) {
                // Leg has moved to the UP position
                if (isLeft) {
                    leftSideComplete = true
                } else {
                    rightSideComplete = true
                }
                lastSignificantAngleChange = System.currentTimeMillis()
                repStage = RepStage.UP
            }
        }
    }
    
    /**
     * Check if both sides have completed to count a full rep
     */
    private fun checkBilateralRepCompletion() {
        // Only count a rep if both sides have completed within the time window
        // and the user is actively moving
        val now = System.currentTimeMillis()
        val withinTimeWindow = (now - lastSignificantAngleChange) < significantAngleChangeTimeoutMs
        
        // Check if we should count a rep - using the improved counting logic
        if (isMoving && leftSideComplete && rightSideComplete && withinTimeWindow) {
            // Both sides completed within time window
            if (repStage == RepStage.DOWN) {
                // Use the countRep method instead of directly incrementing
                countRep()
                
                if (repJustCompleted) {
                    repStage = RepStage.UP
                }
            } else if (repStage == RepStage.UP) {
                // Reset for next rep
                repStage = RepStage.DOWN
            } else if (repStage == RepStage.WAITING) {
                // First rep - set to DOWN to prepare for the next cycle
                repStage = RepStage.DOWN
            }
            
            // Reset completion flags
            leftSideComplete = false
            rightSideComplete = false
        } else if (!withinTimeWindow) {
            // If too much time passed, reset the completion flags
            leftSideComplete = false
            rightSideComplete = false
        }
    }
    
    /**
     * Process rep counting specifically for squats (non-bilateral exercise)
     */
    private fun processSquatRep(angle: Float) {
        val params = exerciseParams[exerciseType] ?: return
        
        // Reset rep completed flag
        repJustCompleted = false
        
        // Only process rep if the user is moving
        if (!isMoving) {
            return
        }
        
        // Check time between reps
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastRepTime < MIN_TIME_BETWEEN_REPS) {
            return // Too soon for another rep
        }
        
        when (repStage) {
            RepStage.WAITING -> {
                if (angle <= params.repStartThreshold) {
                    repStage = RepStage.DOWN
                    lastGoodFormTimestamp = System.currentTimeMillis()
                }
            }
            RepStage.DOWN -> {
                if (angle >= params.repCompletionThreshold) {
                    // Use countRep instead of direct incrementing
                    countRep()
                    
                    if (repJustCompleted) {
                        repStage = RepStage.UP
                    }
                }
            }
            RepStage.UP -> {
                if (angle <= params.repStartThreshold) {
                    repStage = RepStage.DOWN
                    lastGoodFormTimestamp = System.currentTimeMillis()
                }
            }
        }
    }
    
    /**
     * Detect if the user is actually moving by analyzing angle changes over time
     */
    private fun detectMovement(angle: Float) {
        // Add current angle to history
        angleHistory.add(angle)
        if (angleHistory.size > 5) {
            angleHistory.removeFirst()
        }
        
        // Calculate if there's significant movement
        if (angleHistory.size >= 3) {
            val oldest = angleHistory.first()
            val newest = angleHistory.last()
            val absoluteChange = abs(newest - oldest)
            
            // Check if the change is significant enough to be considered movement
            if (absoluteChange > angleChangeThreshold * 2) { // Increased threshold for robustness
                isMoving = true
                lastMovementTime = System.currentTimeMillis()
            } else {
                // If no significant movement in a while, consider as not moving
                val now = System.currentTimeMillis()
                if (now - lastMovementTime > movementTimeoutMs) {
                    isMoving = false
                }
            }
        }
    }

    private fun showErrorFlash() {
        // Cancel any previous flash
        errorFlashRunnable?.let { handler.removeCallbacks(it) }
        
        // Show error flash
        errorFlashView.visibility = View.VISIBLE
        
        // Hide after delay
        errorFlashRunnable = Runnable {
            errorFlashView.visibility = View.GONE
            errorFlashRunnable = null
        }
        handler.postDelayed(errorFlashRunnable!!, 300)
    }

    private fun updateUI() {
        // Update rep count
        repCountTextView.text = repCount.toString()
        
        // Update angle display
        angleTextView.text = "Angle: ${currentAngle.toInt()}°"
        
        // Update distance
        distanceTextView.text = "Distance: ${String.format("%.2f", distance)}m"
        
        // Update rep stage indicator
        val stageText = when (repStage) {
            RepStage.WAITING -> "READY"
            RepStage.DOWN -> "DOWN"
            RepStage.UP -> "UP"
        }
        repStageTextView.text = stageText
        
        // Update form feedback
        if (formErrors.isEmpty()) {
            if (consecutiveGoodReps >= 3) {
                formFeedbackTextView.text = "Perfect Form!"
                formFeedbackTextView.setTextColor(0xFF4CAF50.toInt()) // Green
            } else {
                formFeedbackTextView.text = "Good Form"
                formFeedbackTextView.setTextColor(0xFF4CAF50.toInt()) // Green
            }
        } else {
            val errorText = when (formErrors.first()) {
                FormError.ELBOW_AWAY_FROM_BODY -> "Keep elbows close to body"
                FormError.KNEES_OVER_TOES -> "Knees going past toes"
                FormError.BACK_NOT_STRAIGHT -> "Straighten your back"
                FormError.ASYMMETRIC_MOVEMENT -> "Keep movement even"
                FormError.ELBOWS_TOO_BENT -> "Straighten elbows slightly"
                FormError.BACK_ARCHING -> "Avoid arching your back"
            }
            formFeedbackTextView.text = errorText
            formFeedbackTextView.setTextColor(0xFFFF5722.toInt()) // Orange
        }
    }

    /**
     * Returns true if a rep was just completed in the last processing cycle
     */
    fun wasRepJustCompleted(): Boolean = repJustCompleted
    
    /**
     * Returns the current angle being tracked
     */
    fun getCurrentAngle(): Float = currentAngle
    
    /**
     * Returns the current form errors
     */
    fun getFormErrors(): List<FormError> = formErrors.toList()

    private fun isLandmarkConfident(landmark: Any): Boolean {
        // This is a simplistic check - in a real app you might check visibility score
        return true
    }

    // Helper to compute angle between vector (shoulder→wrist) and vertical
    private fun computeAngleWithVertical(shoulder: Any, wrist: Any): Float {
        val s = LandmarkWrapper(shoulder)
        val w = LandmarkWrapper(wrist)
        val dx = w.x - s.x
        val dy = w.y - s.y
        val angleRad = atan2(dx, -dy) // vertical vector (0, -1)
        return Math.toDegrees(angleRad.toDouble()).toFloat().let { 
            if (it < 0) it + 180 else it 
        }
    }

    // Calculates angle between 3 points (angle at point b)
    private fun calculateAngle(a: Any, b: Any, c: Any): Float {
        val A = LandmarkWrapper(a)
        val B = LandmarkWrapper(b)
        val C = LandmarkWrapper(c)
        val radians = atan2(C.y - B.y, C.x - B.x) - 
                      atan2(A.y - B.y, A.x - B.x)
        var angle = abs(radians * 180f / kotlin.math.PI.toFloat())
        if (angle > 180f) angle = 360f - angle
        return angle
    }

    // Wrapper class to extract x, y coordinates
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

    /**
     * Parameters class for exercise-specific thresholds
     */
    data class ExerciseParams(
        val minAngle: Float,
        val maxAngle: Float,
        val perfectFormMinAngle: Float,
        val perfectFormMaxAngle: Float,
        val repCompletionThreshold: Float,
        val repStartThreshold: Float,
        val primaryTip: String
    )

    /**
     * Enum representing common form errors during exercises
     */
    enum class FormError {
        ELBOW_AWAY_FROM_BODY,
        KNEES_OVER_TOES,
        BACK_NOT_STRAIGHT,
        ASYMMETRIC_MOVEMENT,
        ELBOWS_TOO_BENT,
        BACK_ARCHING
    }

    /**
     * Enum representing stages of a repetition
     */
    enum class RepStage {
        WAITING, // Waiting to start rep
        DOWN,    // Eccentric phase (going down)
        UP       // Concentric phase (going up/complete)
    }
}