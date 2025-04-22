package com.google.mediapipe.examples.poselandmarker.stats

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
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
    
    // Rep tracking to avoid false counts
    private var lastRepTime = 0L
    private val minTimeBetweenReps = 750L // Minimum 0.75 second between reps
    
    // Dialog for displaying workout statistics
    private var statsDialog: Dialog? = null
    
    /**
     * Record a completed rep with its form quality
     * Only counts if workout is active and sufficient time has passed since last rep
     */
    fun recordRep(angle: Float, hasErrors: Boolean): Boolean {
        if (!isWorkoutActive) {
            return false // Workout not started, don't count rep
        }
        
        // Check if sufficient time has passed since the last rep
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastRepTime < minTimeBetweenReps) {
            // Too soon after last rep, likely a false count
            return false 
        }
        
        // Update last rep time
        lastRepTime = currentTime
        
        // Record rep in ViewModel
        val errors = if (hasErrors) listOf("form_error") else emptyList()
        viewModel.recordRep(angle, errors)
        
        // Update any currently open dialog
        updateStatsDisplay()
        
        // Check if target reached
        if (targetReps > 0 && (viewModel.totalReps.value ?: 0) >= targetReps) {
            stopWorkout()
            Toast.makeText(context, "Target reps reached! Great job!", Toast.LENGTH_SHORT).show()
        }
        
        return true
    }
    
    /**
     * Start a new workout
     */
    private fun startWorkout() {
        if (isWorkoutActive) return
        
        isWorkoutActive = true
        viewModel.resetWorkoutStats()
        lastRepTime = 0L
        
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
            android.util.Log.e("WorkoutStatsManager", "Error launching analysis: ${e.message}")
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
            updateStatsDisplay()
            return
        }
        
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
    }
    
    /**
     * Setup all the controls in the dialog
     */
    private fun setupDialogControls(dialogView: View) {
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
        totalRepsText.text = viewModel.totalReps.value?.toString() ?: "0"
        perfectRepsText.text = viewModel.perfectReps.value?.toString() ?: "0"
        avgAngleText.text = viewModel.avgAngle.value?.let { String.format("%.1f°", it) } ?: "0°"
        difficultyText.text = viewModel.difficulty.value?.toString() ?: "1"
        statusText.text = if (isWorkoutActive) "Workout in progress" else "Ready to start"
        
        // Set initial button states
        startButton.isEnabled = !isWorkoutActive
        stopButton.isEnabled = isWorkoutActive
        
        // Setup observers for live data
        viewModel.totalReps.observe(lifecycleOwner) { reps ->
            totalRepsText.text = reps.toString()
            updateProgressBar()
        }
        
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
    }
    
    /**
     * Update the stats display if dialog is showing
     */
    private fun updateStatsDisplay() {
        val dialog = statsDialog ?: return
        if (!dialog.isShowing) return
        
        // Update progress bar
        updateProgressBar()
    }
    
    /**
     * Update the recommendations list
     */
    private fun updateRecommendations(recyclerView: RecyclerView) {
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