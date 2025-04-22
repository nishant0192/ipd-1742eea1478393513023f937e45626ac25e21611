package com.google.mediapipe.examples.poselandmarker.recommend

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.mediapipe.examples.poselandmarker.ExerciseType
import com.google.mediapipe.examples.poselandmarker.R

class RecommendationAdapter(
    private val recommendations: List<String>,
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<RecommendationAdapter.RecommendationViewHolder>() {

    class RecommendationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val exerciseIcon: ImageView = view.findViewById(R.id.exercise_icon)
        val exerciseName: TextView = view.findViewById(R.id.exercise_name)
        val exerciseDescription: TextView = view.findViewById(R.id.exercise_description)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecommendationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recommendation, parent, false)
        return RecommendationViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecommendationViewHolder, position: Int) {
        val recommendation = recommendations[position]
        
        // Set exercise name
        holder.exerciseName.text = getDisplayName(recommendation)
        
        // Set exercise description
        holder.exerciseDescription.text = getDescription(recommendation)
        
        // Set exercise icon
        holder.exerciseIcon.setImageResource(getExerciseIcon(recommendation))
        
        // Set click listener
        holder.itemView.setOnClickListener {
            onItemClick(recommendation)
        }
    }

    override fun getItemCount() = recommendations.size
    
    private fun getDisplayName(recommendationId: String): String {
        return when (recommendationId) {
            "bicep" -> "Bicep Curl"
            "squat" -> "Squat"
            "lateral_raise" -> "Lateral Raise"
            "lunges" -> "Lunges"
            "shoulder_press" -> "Shoulder Press"
            else -> recommendationId.replaceFirstChar { it.uppercase() }
        }
    }
    
    private fun getDescription(recommendationId: String): String {
        return when (recommendationId) {
            "bicep" -> "Targets biceps. Builds arm strength."
            "squat" -> "Full-body exercise focusing on leg muscles."
            "lateral_raise" -> "Isolates shoulder muscles for strength and definition."
            "lunges" -> "Develops leg strength, balance and coordination."
            "shoulder_press" -> "Strengthens shoulders, triceps and core."
            else -> "Recommended based on your workout history."
        }
    }
    
    private fun getExerciseIcon(recommendationId: String): Int {
        return when (recommendationId) {
            "bicep" -> R.drawable.ic_bicep
            "squat" -> R.drawable.ic_squat
            "lateral_raise" -> R.drawable.ic_lateral_raise
            "lunges" -> R.drawable.ic_lunges
            "shoulder_press" -> R.drawable.ic_shoulder_press
            else -> R.drawable.ic_bicep // Default
        }
    }
    
    fun getExerciseType(recommendationId: String): ExerciseType {
        return when (recommendationId) {
            "bicep" -> ExerciseType.BICEP
            "squat" -> ExerciseType.SQUAT
            "lateral_raise" -> ExerciseType.LATERAL_RAISE
            "lunges" -> ExerciseType.LUNGES
            "shoulder_press" -> ExerciseType.SHOULDER_PRESS
            else -> ExerciseType.BICEP // Default
        }
    }
}