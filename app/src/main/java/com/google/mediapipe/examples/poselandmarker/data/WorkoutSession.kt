package com.google.mediapipe.examples.poselandmarker.data

/**
 * Represents a workout session with its statistics
 */
data class WorkoutSession(
    val id: String,
    val timestamp: Long,
    val exerciseType: com.google.mediapipe.examples.poselandmarker.ExerciseType,
    val totalReps: Int,
    val perfectReps: Int,
    val avgAngle: Float,
    val difficulty: Int,
    val duration: Int // in minutes
)