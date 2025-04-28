package com.google.mediapipe.examples.poselandmarker.stats

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.mediapipe.examples.poselandmarker.ExerciseType
import com.google.mediapipe.examples.poselandmarker.MainActivity
import com.google.mediapipe.examples.poselandmarker.MainViewModel
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.ResultAnalysisActivity
import com.google.mediapipe.examples.poselandmarker.recommend.RecommendationAdapter
import kotlinx.coroutines.Job
import java.util.UUID

/**
 * Enhanced workout statistics manager that integrates RL/RS models into the UI
 * and controls workout start/stop functionality
 */
class WorkoutStatsManager(
    private val context: Context,
    private val viewModel: MainViewModel,
    private val lifecycleOwner: LifecycleOwner,
    private val onExerciseSelected: (ExerciseType) -> Unit
) {
    // Generate a unique ID for this workout session
    private val workoutId = UUID.randomUUID().toString()
    
    // Workout state
    private var isWorkoutActive = false
    private var targetReps = 0
    private var collectingJob: Job? = null
    private var workoutStartTime = 0L
    
    // Dialog for displaying workout statistics
    private var statsDialog: Dialog? = null

    private var uiUpdateHandler: Handler? = null
    
    // Handler for syncing rep count
    private var syncHandler: Handler? = null
    private val syncRunnable = object : Runnable {
        override fun run() {
            if (isWorkoutActive) {
                updateFromExerciseFeedbackManager()
                syncHandler?.postDelayed(this, 500) // Update every 500ms
            }
        }
    }
    
    /**
     * Directly update the rep counts from ExerciseFeedbackManager
     */
    private fun updateFromExerciseFeedbackManager() {
        try {
            val mainActivity = context as? MainActivity
            if (mainActivity != null) {
                val cameraFragment = mainActivity.getCameraFragment()
                val feedbackManager = cameraFragment?.getExerciseFeedbackManager()
                
                if (feedbackManager != null) {
                    // Get the EXACT rep count from ExerciseFeedbackManager
                    val actualRepCount = feedbackManager.getCurrentRepCount()
                    
                    // Force the ViewModel to match this value
                    viewModel.setTotalReps(actualRepCount)
                    
                    // Directly update the UI
                    statsDialog?.findViewById<TextView>(R.id.value_total_reps)?.text = actualRepCount.toString()
                    
                    // Update progress bar
                    val progressBar = statsDialog?.findViewById<ProgressBar>(R.id.rep_progress)
                    if (progressBar != null && targetReps > 0) {
                        val progress = (actualRepCount * 100) / targetReps
                        progressBar.max = 100
                        progressBar.progress = progress.coerceAtMost(100)
                    }
                    
                    // Check if target reached
                    if (targetReps > 0 && actualRepCount >= targetReps && isWorkoutActive) {
                        stopWorkout()
                        Toast.makeText(context, "Target reps reached! Great job!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("WorkoutStatsManager", "Error syncing rep count: ${e.message}")
        }
    }
    
    /**
     * Start periodic syncing of rep count
     */
    private fun startRepCountSync() {
        syncHandler = Handler(Looper.getMainLooper())
        syncHandler?.postDelayed(syncRunnable, 500)
    }
    
    /**
     * Stop periodic syncing of rep count
     */
    private fun stopRepCountSync() {
        syncHandler?.removeCallbacks(syncRunnable)
        syncHandler = null
    }
    
    /**
     * Record a completed rep with its form quality
     * This method is completely disabled to prevent duplicate counting
     */
    fun recordRep(angle: Float, hasErrors: Boolean): Boolean {
        // Do absolutely nothing - all rep counting now happens via direct sync
        return false
    }
    
    /**
     * Start a new workout
     */
    private fun startWorkout() {
        if (isWorkoutActive) return
        
        isWorkoutActive = true
        workoutStartTime = System.currentTimeMillis()
        viewModel.resetWorkoutStats()
        
        // Get access to the ExerciseFeedbackManager from the MainActivity
        try {
            val mainActivity = context as? MainActivity
            if (mainActivity != null) {
                val cameraFragment = mainActivity.getCameraFragment()
                cameraFragment?.getExerciseFeedbackManager()?.resetCounters()
            }
        } catch (e: Exception) {
            Log.e("WorkoutStatsManager", "Error resetting feedback manager: ${e.message}")
        }
        
        // Start syncing rep count from ExerciseFeedbackManager
        startRepCountSync()
        
        // Update UI if dialog is showing
        statsDialog?.findViewById<TextView>(R.id.workout_status)?.text = "Workout in progress"
        statsDialog?.findViewById<Button>(R.id.btn_start_workout)?.isEnabled = false
        statsDialog?.findViewById<Button>(R.id.btn_stop_workout)?.isEnabled = true
        statsDialog?.findViewById<View>(R.id.target_reps_layout)?.visibility = View.GONE
    }
    
    /**
     * Stop the current workout
     */
    private fun stopWorkout() {
        if (!isWorkoutActive) return
        
        isWorkoutActive = false
        
        // Stop syncing rep count
        stopRepCountSync()
        
        // Update UI if dialog is showing
        statsDialog?.findViewById<TextView>(R.id.workout_status)?.text = "Workout completed"
        statsDialog?.findViewById<Button>(R.id.btn_start_workout)?.isEnabled = true
        statsDialog?.findViewById<Button>(R.id.btn_stop_workout)?.isEnabled = false
        statsDialog?.findViewById<View>(R.id.target_reps_layout)?.visibility = View.VISIBLE
        
        try {
            // Launch the Result Analysis screen
            val intent = Intent(context, ResultAnalysisActivity::class.java)
            intent.putExtra("WORKOUT_ID", workoutId)
            context.startActivity(intent)
        } catch (e: Exception) {
            // Log the error and show a toast - don't crash
            Log.e("WorkoutStatsManager", "Error launching analysis: ${e.message}")
            Toast.makeText(context, "Could not show analysis: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Set target number of reps
     */
    private fun setTargetReps(target: Int) {
        targetReps = target
        updateProgressBar()
    }
    
    /**
     * Update the progress bar based on current/target reps
     */
    private fun updateProgressBar() {
        val dialog = statsDialog ?: return
        if (!dialog.isShowing) return
        
        val progressBar = dialog.findViewById<ProgressBar>(R.id.rep_progress) ?: return
        val totalReps = viewModel.totalReps.value ?: 0
        
        if (targetReps > 0) {
            val progress = (totalReps * 100) / targetReps
            progressBar.max = 100
            progressBar.progress = progress.coerceAtMost(100)
            
            dialog.findViewById<TextView>(R.id.value_target_reps)?.text = targetReps.toString()
        } else {
            progressBar.max = 100
            progressBar.progress = 0
            dialog.findViewById<TextView>(R.id.value_target_reps)?.text = "--"
        }
    }
    
    /**
     * Show workout statistics dialog
     */
    fun showWorkoutStats() {
        if (statsDialog?.isShowing == true) {
            // Dialog already showing, just update it
            updateFromExerciseFeedbackManager()
            return
        }
        
        try {
            // Create new dialog
            val builder = AlertDialog.Builder(context)
            val dialogView = View.inflate(context, R.layout.workout_stats_dialog, null)
            
            // Initialize UI elements
            setupDialogControls(dialogView)
            
            // Create and show dialog
            builder.setView(dialogView)
                .setTitle("Workout Dashboard")
                
            val dialog = builder.create()
            dialog.setOnDismissListener {
                // Clean up any observers when dialog is dismissed
                collectingJob?.cancel()
                collectingJob = null
            }
            
            statsDialog = dialog
            dialog.show()
            
            // If workout is active, immediately start syncing
            if (isWorkoutActive) {
                updateFromExerciseFeedbackManager()
            }

            dialog.setOnDismissListener {
                // Stop any UI update handlers when dialog is dismissed
                uiUpdateHandler?.removeCallbacksAndMessages(null)
                uiUpdateHandler = null
                
                // Clean up any observers when dialog is dismissed
                collectingJob?.cancel()
                collectingJob = null
            }
        } catch (e: Exception) {
            Log.e("WorkoutStatsManager", "Error showing workout stats dialog: ${e.message}")
            Toast.makeText(context, "Failed to show workout statistics", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Setup all the controls in the dialog
     */
    private fun setupDialogControls(dialogView: View) {
        try {
            // Get references to UI elements
            val exerciseTypeText = dialogView.findViewById<TextView>(R.id.value_exercise_type)
            val totalRepsText = dialogView.findViewById<TextView>(R.id.value_total_reps)
            val perfectRepsText = dialogView.findViewById<TextView>(R.id.value_perfect_reps)
            val avgAngleText = dialogView.findViewById<TextView>(R.id.value_avg_angle)
            val difficultyText = dialogView.findViewById<TextView>(R.id.value_difficulty)
            val targetRepsInput = dialogView.findViewById<EditText>(R.id.edit_target_reps)
            val setTargetButton = dialogView.findViewById<Button>(R.id.btn_set_target)
            val startButton = dialogView.findViewById<Button>(R.id.btn_start_workout)
            val stopButton = dialogView.findViewById<Button>(R.id.btn_stop_workout)
            val statusText = dialogView.findViewById<TextView>(R.id.workout_status)
            val recommendationsList = dialogView.findViewById<RecyclerView>(R.id.recommendation_list)
            val ratingBar = dialogView.findViewById<RatingBar>(R.id.workout_rating)
            val submitButton = dialogView.findViewById<Button>(R.id.btn_submit_rating)
            
            // Configure recommendations recycler view
            recommendationsList.layoutManager = LinearLayoutManager(context)
            
            // Set initial values
            exerciseTypeText.text = getExerciseTypeName(viewModel.currentExerciseType)
            
            // Try to get the EXACT rep count TextView from the camera overlay
            try {
                val mainActivity = context as? MainActivity
                val cameraFragment = mainActivity?.getCameraFragment()
                
                if (cameraFragment != null) {
                    // Get the actual rep count TextView from the camera overlay
                    val actualRepCountTextView = cameraFragment.getRepCountTextView()
                    
                    if (actualRepCountTextView != null) {
                        // Initial value
                        totalRepsText.text = actualRepCountTextView.text
                        
                        // Setup a handler to continuously update this text view
                        val handler = Handler(Looper.getMainLooper())
                        val updateRunnable = object : Runnable {
                            override fun run() {
                                if (statsDialog?.isShowing == true) {
                                    // Directly copy the text from the camera overlay
                                    totalRepsText.text = actualRepCountTextView.text
                                    
                                    // Also update progress bar if target is set
                                    if (targetReps > 0) {
                                        val repCount = actualRepCountTextView.text.toString().toIntOrNull() ?: 0
                                        val progress = (repCount * 100) / targetReps
                                        val progressBar = statsDialog?.findViewById<ProgressBar>(R.id.rep_progress)
                                        progressBar?.progress = progress.coerceAtMost(100)
                                        
                                        // Check if target reached
                                        if (repCount >= targetReps && isWorkoutActive) {
                                            stopWorkout()
                                            Toast.makeText(context, "Target reps reached! Great job!", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    
                                    // Continue updating
                                    handler.postDelayed(this, 100) // Update every 100ms
                                }
                            }
                        }
                        
                        // Start the continuous update
                        handler.post(updateRunnable)
                    } else {
                        // Fallback to viewModel if we couldn't get the actual TextView
                        totalRepsText.text = viewModel.totalReps.value?.toString() ?: "0"
                    }
                } else {
                    // Fallback to viewModel if we couldn't get the CameraFragment
                    totalRepsText.text = viewModel.totalReps.value?.toString() ?: "0"
                }
            } catch (e: Exception) {
                Log.e("WorkoutStatsManager", "Error setting up real-time rep count: ${e.message}")
                // Fallback to viewModel
                totalRepsText.text = viewModel.totalReps.value?.toString() ?: "0"
            }
            
            perfectRepsText.text = viewModel.perfectReps.value?.toString() ?: "0"
            avgAngleText.text = viewModel.avgAngle.value?.let { String.format("%.1f°", it) } ?: "0°"
            difficultyText.text = viewModel.difficulty.value?.toString() ?: "1"
            statusText.text = if (isWorkoutActive) "Workout in progress" else "Ready to start"
            
            // Set initial button states
            startButton.isEnabled = !isWorkoutActive
            stopButton.isEnabled = isWorkoutActive
            
            // Setup observers for live data
            viewModel.perfectReps.observe(lifecycleOwner) { reps ->
                perfectRepsText.text = reps.toString()
            }
            
            viewModel.avgAngle.observe(lifecycleOwner) { angle ->
                avgAngleText.text = String.format("%.1f°", angle)
            }
            
            viewModel.difficulty.observe(lifecycleOwner) { difficulty ->
                difficultyText.text = difficulty.toString()
            }
            
            // Load recommendations
            updateRecommendations(recommendationsList)
            
            // Setup button listeners
            setTargetButton.setOnClickListener {
                val targetInput = targetRepsInput.text.toString()
                if (targetInput.isNotEmpty()) {
                    val target = targetInput.toIntOrNull() ?: 0
                    if (target > 0) {
                        setTargetReps(target)
                        Toast.makeText(context, "Target set to $target reps", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Please enter a valid number", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            
            startButton.setOnClickListener {
                startWorkout()
            }
            
            stopButton.setOnClickListener {
                // End the workout
                stopWorkout()
            }
            
            // Setup submit rating button
            submitButton.setOnClickListener {
                val rating = ratingBar.rating.toInt()
                if (rating > 0) {
                    viewModel.submitRating(workoutId, rating)
                    ratingBar.rating = 0f
                    submitButton.text = "Thank you!"
                    submitButton.isEnabled = false
                    
                    // Reload recommendations after submitting rating
                    updateRecommendations(recommendationsList)
                } else {
                    Toast.makeText(context, "Please select a rating", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e("WorkoutStatsManager", "Error setting up dialog controls: ${e.message}")
        }
    }
    
    /**
     * Update the stats display if dialog is showing
     */
    private fun updateStatsDisplay() {
        updateFromExerciseFeedbackManager()
    }
    
    /**
     * Update the recommendations list
     */
    private fun updateRecommendations(recyclerView: RecyclerView) {
        try {
            // Get recommendations from ViewModel
            val recommendations = viewModel.recommendations.value
            
            // Create adapter with recommendations
            val adapter = RecommendationAdapter(recommendations) { recommendationId ->
                // Handle recommendation click
                val adapter = recyclerView.adapter as? RecommendationAdapter ?: return@RecommendationAdapter
                val exerciseType = adapter.getExerciseType(recommendationId)
                
                // Notify callback
                onExerciseSelected(exerciseType)
                
                // Update exercise type display
                statsDialog?.findViewById<TextView>(R.id.value_exercise_type)?.text = 
                    getExerciseTypeName(exerciseType)
            }
            
            // Set adapter
            recyclerView.adapter = adapter
        } catch (e: Exception) {
            Log.e("WorkoutStatsManager", "Error updating recommendations: ${e.message}")
        }
    }
    
    /**
     * Get display name for exercise type
     */
    private fun getExerciseTypeName(exerciseType: ExerciseType): String {
        return when (exerciseType) {
            ExerciseType.BICEP -> "Bicep Curl"
            ExerciseType.SQUAT -> "Squat"
            ExerciseType.LATERAL_RAISE -> "Lateral Raise"
            ExerciseType.LUNGES -> "Lunges"
            ExerciseType.SHOULDER_PRESS -> "Shoulder Press"
        }
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        uiUpdateHandler?.removeCallbacksAndMessages(null)
        uiUpdateHandler = null
        
        stopRepCountSync()
        statsDialog?.dismiss()
        statsDialog = null
        collectingJob?.cancel()
        collectingJob = null
    }
    
    /**
     * Check if workout is active
     */
    fun isWorkoutActive(): Boolean {
        return isWorkoutActive
    }
}