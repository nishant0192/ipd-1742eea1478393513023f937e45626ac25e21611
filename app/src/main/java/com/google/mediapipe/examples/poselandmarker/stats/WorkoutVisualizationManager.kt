package com.google.mediapipe.examples.poselandmarker.stats

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.ExerciseType

/**
 * Utility class for creating enhanced, investor-ready data visualizations
 * for workout analysis charts
 */
class WorkoutVisualizationManager(private val context: Context) {

    // Define FormRating here to replace the missing EnhancedFormFeedback.FormRating
    enum class FormRating(val displayName: String, val color: Int) {
        PERFECT("Perfect Form", COLOR_PERFECT),
        GOOD("Good Form", COLOR_GOOD),
        FAIR("Fair Form", COLOR_FAIR),
        NEEDS_WORK("Needs Work", COLOR_NEEDS_WORK),
        POOR("Poor Form", COLOR_POOR)
    }

    // Define SpeedRating here to replace the missing EnhancedFormFeedback.SpeedRating
    enum class SpeedRating(val displayName: String) {
        TOO_FAST("Too Fast"),
        GOOD_PACE("Good Pace"),
        TOO_SLOW("Too Slow")
    }

    // Define FormIssue here to replace the missing EnhancedFormFeedback.FormIssue
    enum class FormIssue(val description: String, val tip: String) {
        ELBOW_AWAY_FROM_BODY("Elbow swinging during curl", "Keep your elbows fixed to your sides"),
        WRIST_ROTATION("Excessive wrist rotation", "Keep wrists neutral throughout movement"),
        BACK_ARCHING("Arching back during exercise", "Maintain neutral spine, avoid leaning back"),
        KNEES_OVER_TOES("Knees extending past toes", "Shift weight to heels, knees behind toes"),
        ASYMMETRIC_MOVEMENT("Uneven arm movement", "Keep both arms moving at the same height"),
        ELBOWS_TOO_BENT("Excessive elbow bending", "Maintain slight elbow bend throughout")
    }

    // Define RepFeedback data class to replace the missing EnhancedFormFeedback.RepFeedback
    data class RepFeedback(
        val angle: Float,
        val issues: List<FormIssue>,
        val rating: FormRating,
        val speedRating: SpeedRating,
        val tipMessage: String
    )

    companion object {
        val COLOR_PERFECT = Color.parseColor("#4CAF50") // Green
        val COLOR_GOOD = Color.parseColor("#8BC34A") // Light Green
        val COLOR_FAIR = Color.parseColor("#FFEB3B") // Yellow
        val COLOR_NEEDS_WORK = Color.parseColor("#FF9800") // Orange
        val COLOR_POOR = Color.parseColor("#F44336") // Red
    }

    /**
     * Setup an enhanced form quality pie chart with realistic data
     */
    fun setupFormQualityChart(pieChart: PieChart, formFeedback: List<RepFeedback>, exerciseType: ExerciseType) {
        // Count occurrences of each form rating
        val ratingCounts = formFeedback.groupBy { it.rating }
            .mapValues { it.value.size }
        
        // Create entries with realistic distribution based on exercise type
        val entries = ArrayList<PieEntry>()
        
        // If we have actual data, use it
        if (ratingCounts.isNotEmpty()) {
            FormRating.values().forEach { rating ->
                val count = ratingCounts[rating] ?: 0
                if (count > 0) {
                    entries.add(PieEntry(count.toFloat(), rating.displayName))
                }
            }
        } else {
            // Generate realistic demo data if we don't have real data
            // Different exercises have different typical form quality distributions
            when (exerciseType) {
                ExerciseType.BICEP -> {
                    entries.add(PieEntry(45f, FormRating.PERFECT.displayName))
                    entries.add(PieEntry(30f, FormRating.GOOD.displayName))
                    entries.add(PieEntry(15f, FormRating.FAIR.displayName))
                    entries.add(PieEntry(10f, FormRating.NEEDS_WORK.displayName))
                }
                ExerciseType.SQUAT -> {
                    entries.add(PieEntry(30f, FormRating.PERFECT.displayName))
                    entries.add(PieEntry(40f, FormRating.GOOD.displayName))
                    entries.add(PieEntry(20f, FormRating.FAIR.displayName))
                    entries.add(PieEntry(10f, FormRating.NEEDS_WORK.displayName))
                }
                ExerciseType.LATERAL_RAISE -> {
                    entries.add(PieEntry(35f, FormRating.PERFECT.displayName))
                    entries.add(PieEntry(35f, FormRating.GOOD.displayName))
                    entries.add(PieEntry(20f, FormRating.FAIR.displayName))
                    entries.add(PieEntry(10f, FormRating.NEEDS_WORK.displayName))
                }
                ExerciseType.LUNGES -> {
                    entries.add(PieEntry(25f, FormRating.PERFECT.displayName))
                    entries.add(PieEntry(35f, FormRating.GOOD.displayName))
                    entries.add(PieEntry(25f, FormRating.FAIR.displayName))
                    entries.add(PieEntry(15f, FormRating.NEEDS_WORK.displayName))
                }
                ExerciseType.SHOULDER_PRESS -> {
                    entries.add(PieEntry(30f, FormRating.PERFECT.displayName))
                    entries.add(PieEntry(35f, FormRating.GOOD.displayName))
                    entries.add(PieEntry(25f, FormRating.FAIR.displayName))
                    entries.add(PieEntry(10f, FormRating.NEEDS_WORK.displayName))
                }
            }
        }
        
        // Create dataset with enhanced styling
        val dataSet = PieDataSet(entries, "Form Quality Distribution")
        
        // Set colors for each rating
        val colors = ArrayList<Int>()
        // Either use colors from the actual data
        if (ratingCounts.isNotEmpty()) {
            FormRating.values().forEach { rating ->
                if (ratingCounts[rating] != null && ratingCounts[rating]!! > 0) {
                    colors.add(rating.color)
                }
            }
        } else {
            // Or set colors for demo data
            colors.add(COLOR_PERFECT)
            colors.add(COLOR_GOOD)
            colors.add(COLOR_FAIR)
            colors.add(COLOR_NEEDS_WORK)
        }
        dataSet.colors = colors
        
        // Enhanced styling
        dataSet.sliceSpace = 3f
        dataSet.selectionShift = 8f
        dataSet.valueTextSize = 14f
        dataSet.valueTextColor = Color.WHITE
        dataSet.valueTypeface = Typeface.DEFAULT_BOLD
        
        // Create pie data with percentage formatting
        val data = PieData(dataSet)
        data.setValueFormatter(PercentFormatter(pieChart))
        
        // Configure chart with professional styling
        pieChart.data = data
        pieChart.description.isEnabled = false
        pieChart.setExtraOffsets(20f, 20f, 20f, 20f) // Add padding
        pieChart.dragDecelerationFrictionCoef = 0.95f
        pieChart.rotationAngle = 0f
        pieChart.isRotationEnabled = true
        pieChart.isHighlightPerTapEnabled = true
        pieChart.animateY(1400)
        pieChart.setEntryLabelColor(Color.WHITE)
        pieChart.setEntryLabelTextSize(12f)
        pieChart.setDrawEntryLabels(false) // Don't draw labels on slices, use legend instead
        
        // Setup center text
        pieChart.setDrawCenterText(true)
        pieChart.centerText = "Form\nQuality"
        pieChart.setCenterTextSize(16f)
        pieChart.setCenterTextTypeface(Typeface.DEFAULT_BOLD)
        
        // Setup legend with enhanced styling
        val legend = pieChart.legend
        legend.verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
        legend.horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
        legend.orientation = Legend.LegendOrientation.HORIZONTAL
        legend.textSize = 12f
        legend.formSize = 12f
        legend.formToTextSpace = 5f
        legend.xEntrySpace = 10f
        legend.yEntrySpace = 5f
        legend.textColor = ContextCompat.getColor(context, R.color.black)
        
        // Refresh chart
        pieChart.invalidate()
    }
    
    /**
     * Setup enhanced progress line chart showing rep angles over time
     */
    fun setupProgressChart(lineChart: LineChart, repFeedback: List<RepFeedback>, exerciseType: ExerciseType) {
        // Create entries for rep angles - either use real data or generate realistic data
        val angleEntries = ArrayList<Entry>()
        val targetAngleEntries = ArrayList<Entry>()
        
        // Add reference ideal angle for the exercise type based on realistic biomechanics
        val targetAngle = when (exerciseType) {
            ExerciseType.BICEP -> 45f // Ideal bicep curl peak angle
            ExerciseType.SQUAT -> 90f // Ideal squat depth angle
            ExerciseType.LATERAL_RAISE -> 90f // Ideal lateral raise height
            ExerciseType.LUNGES -> 90f // Ideal lunge knee angle
            ExerciseType.SHOULDER_PRESS -> 180f // Ideal shoulder press height
        }
        
        // If we have real data, use it
        if (repFeedback.isNotEmpty()) {
            repFeedback.forEachIndexed { index, feedback ->
                angleEntries.add(Entry(index.toFloat() + 1, feedback.angle))
                targetAngleEntries.add(Entry(index.toFloat() + 1, targetAngle))
            }
        } else {
            // Generate realistic demo data based on exercise type
            val repCount = 15 // Demo with 15 reps
            val angleData = when (exerciseType) {
                ExerciseType.BICEP -> generateRealisticBicepData(repCount)
                ExerciseType.SQUAT -> generateRealisticSquatData(repCount)
                ExerciseType.LATERAL_RAISE -> generateRealisticLateralRaiseData(repCount)
                ExerciseType.LUNGES -> generateRealisticLungesData(repCount)
                ExerciseType.SHOULDER_PRESS -> generateRealisticShoulderPressData(repCount)
            }
            
            angleData.forEachIndexed { index, angle ->
                angleEntries.add(Entry(index.toFloat() + 1, angle))
                targetAngleEntries.add(Entry(index.toFloat() + 1, targetAngle))
            }
        }
        
        // Create multiple datasets for enhanced visualization
        val datasets = ArrayList<ILineDataSet>()
        
        // Actual angle dataset with enhanced styling
        val angleDataSet = LineDataSet(angleEntries, "Actual Angles")
        angleDataSet.color = ContextCompat.getColor(context, R.color.mp_color_primary)
        angleDataSet.lineWidth = 3f
        angleDataSet.setCircleColor(ContextCompat.getColor(context, R.color.mp_color_primary))
        angleDataSet.circleRadius = 5f
        angleDataSet.setDrawCircleHole(true)
        angleDataSet.circleHoleRadius = 2.5f
        angleDataSet.valueTextSize = 12f
        angleDataSet.valueTypeface = Typeface.DEFAULT_BOLD
        angleDataSet.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return "${value.toInt()}°"
            }
        }
        // Only show values for every other point to avoid crowding
        angleDataSet.setDrawValues(false)
        datasets.add(angleDataSet)
        
        // Target angle dataset with dashed line
        val targetDataSet = LineDataSet(targetAngleEntries, "Target Angle")
        targetDataSet.color = ContextCompat.getColor(context, R.color.mp_color_secondary)
        targetDataSet.lineWidth = 2f
        targetDataSet.enableDashedLine(10f, 5f, 0f)
        targetDataSet.setDrawCircles(false)
        targetDataSet.setDrawValues(false)
        datasets.add(targetDataSet)
        
        // Create line data with multiple datasets
        val lineData = LineData(datasets)
        
        // Configure chart with professional styling
        lineChart.data = lineData
        lineChart.description.isEnabled = false
        lineChart.setExtraOffsets(10f, 20f, 25f, 10f)
        lineChart.setDrawGridBackground(false)
        lineChart.setDrawBorders(false)
        lineChart.setScaleEnabled(true)
        lineChart.setPinchZoom(true)
        lineChart.animateX(1500)
        
        // Enhanced X axis
        val xAxis = lineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.granularity = 1f
        xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return "Rep ${value.toInt()}"
            }
        }
        xAxis.textSize = 12f
        xAxis.textColor = Color.BLACK
        
        // Enhanced Y axis
        val leftAxis = lineChart.axisLeft
        leftAxis.setDrawGridLines(true)
        leftAxis.gridColor = Color.LTGRAY
        leftAxis.gridLineWidth = 0.5f
        leftAxis.axisMinimum = 0f
        leftAxis.axisMaximum = when (exerciseType) {
            ExerciseType.BICEP -> 180f
            ExerciseType.SQUAT -> 180f
            ExerciseType.LATERAL_RAISE -> 180f
            ExerciseType.LUNGES -> 180f
            ExerciseType.SHOULDER_PRESS -> 200f
        }
        leftAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return "${value.toInt()}°"
            }
        }
        leftAxis.textSize = 12f
        leftAxis.textColor = Color.BLACK
        
        // Hide right axis
        val rightAxis = lineChart.axisRight
        rightAxis.isEnabled = false
        
        // Enhanced legend
        val legend = lineChart.legend
        legend.form = Legend.LegendForm.LINE
        legend.formSize = 12f
        legend.textSize = 12f
        legend.textColor = Color.BLACK
        legend.verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
        legend.horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
        legend.orientation = Legend.LegendOrientation.HORIZONTAL
        legend.setDrawInside(false)
        
        // Refresh chart
        lineChart.invalidate()
    }
    
    /**
     * Setup multiple performance metrics chart
     */
    fun setupPerformanceMetricsChart(lineChart: LineChart, repFeedback: List<RepFeedback>, exerciseType: ExerciseType) {
        // Create entries for various performance metrics
        val formQualityEntries = ArrayList<Entry>()
        val speedEntries = ArrayList<Entry>()
        val stabilityEntries = ArrayList<Entry>()
        
        // If we have real data, derive metrics from it
        if (repFeedback.isNotEmpty()) {
            repFeedback.forEachIndexed { index, feedback ->
                // Map form rating to a numerical score (0-100)
                val formScore = when (feedback.rating) {
                    FormRating.PERFECT -> 100f
                    FormRating.GOOD -> 80f
                    FormRating.FAIR -> 60f
                    FormRating.NEEDS_WORK -> 40f
                    FormRating.POOR -> 20f
                }
                formQualityEntries.add(Entry(index.toFloat() + 1, formScore))
                
                // Map speed rating to a numerical score (0-100)
                val speedScore = when (feedback.speedRating) {
                    SpeedRating.GOOD_PACE -> 90f
                    SpeedRating.TOO_FAST -> 60f
                    SpeedRating.TOO_SLOW -> 70f
                }
                speedEntries.add(Entry(index.toFloat() + 1, speedScore))
                
                // Calculate stability score based on form issues
                val stabilityScore = 100f - (10f * feedback.issues.size)
                stabilityEntries.add(Entry(index.toFloat() + 1, stabilityScore.coerceAtLeast(0f)))
            }
        } else {
            // Generate realistic demo data based on exercise type
            val repCount = 15 // Demo with 15 reps
            
            // Generate quality, speed, and stability data with realistic trends
            // Form typically decreases slightly as workout progresses
            for (i in 1..repCount) {
                val repIndex = i.toFloat()
                
                // Form score decreases slightly over time (fatigue)
                val formBase = when (exerciseType) {
                    ExerciseType.BICEP -> 90f
                    ExerciseType.SQUAT -> 85f
                    ExerciseType.LATERAL_RAISE -> 80f
                    ExerciseType.LUNGES -> 75f
                    ExerciseType.SHOULDER_PRESS -> 85f
                }
                val formDecline = 0.8f * i
                val formNoise = (Math.random() * 10 - 5).toFloat()
                val formScore = (formBase - formDecline + formNoise).coerceIn(0f, 100f)
                formQualityEntries.add(Entry(repIndex, formScore))
                
                // Speed typically increases (people rush as they fatigue)
                val speedBase = 85f
                val speedIncrease = 0.5f * i
                val speedNoise = (Math.random() * 8 - 4).toFloat()
                val speedScore = (speedBase - speedIncrease + speedNoise).coerceIn(0f, 100f)
                speedEntries.add(Entry(repIndex, speedScore))
                
                // Stability typically decreases
                val stabilityBase = when (exerciseType) {
                    ExerciseType.BICEP -> 88f
                    ExerciseType.SQUAT -> 83f
                    ExerciseType.LATERAL_RAISE -> 85f
                    ExerciseType.LUNGES -> 80f
                    ExerciseType.SHOULDER_PRESS -> 82f
                }
                val stabilityDecline = 1f * i
                val stabilityNoise = (Math.random() * 8 - 4).toFloat()
                val stabilityScore = (stabilityBase - stabilityDecline + stabilityNoise).coerceIn(0f, 100f)
                stabilityEntries.add(Entry(repIndex, stabilityScore))
            }
        }
        
        // Create multiple datasets for performance metrics
        val datasets = ArrayList<ILineDataSet>()
        
        // Form quality dataset
        val formDataSet = LineDataSet(formQualityEntries, "Form Quality")
        formDataSet.color = ContextCompat.getColor(context, R.color.green_grade)
        formDataSet.lineWidth = 3f
        formDataSet.setCircleColor(ContextCompat.getColor(context, R.color.green_grade))
        formDataSet.circleRadius = 4f
        formDataSet.setDrawCircleHole(true)
        formDataSet.circleHoleRadius = 2f
        formDataSet.valueTextSize = 0f // Don't show values
        formDataSet.setDrawValues(false)
        datasets.add(formDataSet)
        
        // Speed dataset
        val speedDataSet = LineDataSet(speedEntries, "Movement Speed")
        speedDataSet.color = ContextCompat.getColor(context, R.color.blue_grade)
        speedDataSet.lineWidth = 3f
        speedDataSet.setCircleColor(ContextCompat.getColor(context, R.color.blue_grade))
        speedDataSet.circleRadius = 4f
        speedDataSet.setDrawCircleHole(true)
        speedDataSet.circleHoleRadius = 2f
        speedDataSet.valueTextSize = 0f
        speedDataSet.setDrawValues(false)
        datasets.add(speedDataSet)
        
        // Stability dataset
        val stabilityDataSet = LineDataSet(stabilityEntries, "Movement Stability")
        stabilityDataSet.color = ContextCompat.getColor(context, R.color.mp_color_secondary)
        stabilityDataSet.lineWidth = 3f
        stabilityDataSet.setCircleColor(ContextCompat.getColor(context, R.color.mp_color_secondary))
        stabilityDataSet.circleRadius = 4f
        stabilityDataSet.setDrawCircleHole(true)
        stabilityDataSet.circleHoleRadius = 2f
        stabilityDataSet.valueTextSize = 0f
        stabilityDataSet.setDrawValues(false)
        datasets.add(stabilityDataSet)
        
        // Create line data with multiple datasets
        val lineData = LineData(datasets)
        
        // Configure chart with professional styling
        lineChart.data = lineData
        lineChart.description.isEnabled = false
        lineChart.setExtraOffsets(10f, 20f, 25f, 10f)
        lineChart.setDrawGridBackground(false)
        lineChart.setDrawBorders(false)
        lineChart.setScaleEnabled(true)
        lineChart.setPinchZoom(true)
        lineChart.animateX(1500)
        
        // Enhanced X axis
        val xAxis = lineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.granularity = 1f
        xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return "Rep ${value.toInt()}"
            }
        }
        xAxis.textSize = 12f
        xAxis.textColor = Color.BLACK
        
        // Enhanced Y axis
        val leftAxis = lineChart.axisLeft
        leftAxis.setDrawGridLines(true)
        leftAxis.gridColor = Color.LTGRAY
        leftAxis.gridLineWidth = 0.5f
        leftAxis.axisMinimum = 0f
        leftAxis.axisMaximum = 100f
        leftAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return "${value.toInt()}"
            }
        }
        leftAxis.textSize = 12f
        leftAxis.textColor = Color.BLACK
        
        // Hide right axis
        val rightAxis = lineChart.axisRight
        rightAxis.isEnabled = false
        
        // Enhanced legend
        val legend = lineChart.legend
        legend.form = Legend.LegendForm.LINE
        legend.formSize = 12f
        legend.textSize = 12f
        legend.textColor = Color.BLACK
        legend.verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
        legend.horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
        legend.orientation = Legend.LegendOrientation.HORIZONTAL
        legend.setDrawInside(false)
        
        // Refresh chart
        lineChart.invalidate()
    }
    
    /**
     * Generate realistic bicep curl angle data
     */
    private fun generateRealisticBicepData(repCount: Int): List<Float> {
        val angles = mutableListOf<Float>()
        
        // Bicep curl typically starts around 150° (extended) and goes to around 45° (contracted)
        // Most people's form deteriorates slightly as they fatigue
        for (i in 1..repCount) {
            // Calculate fatigue factor (form gets slightly worse as reps increase)
            val fatigueFactor = i * 0.5f
            
            // Add realistic variation to target angles
            val targetPeakAngle = 45f + (Math.random() * 10 - 5).toFloat() + fatigueFactor
            angles.add(targetPeakAngle)
        }
        
        return angles
    }
    
    /**
     * Generate realistic squat angle data
     */
    private fun generateRealisticSquatData(repCount: Int): List<Float> {
        val angles = mutableListOf<Float>()
        
        // Squat typically involves knee angles from 170° (standing) to 90° (proper depth)
        // Most people's depth decreases as they fatigue
        for (i in 1..repCount) {
            // Calculate fatigue factor (depth decreases as reps increase)
            val fatigueFactor = i * 0.8f
            
            // Add realistic variation to target angles
            val targetDepthAngle = 90f + (Math.random() * 10 - 5).toFloat() + fatigueFactor
            angles.add(targetDepthAngle)
        }
        
        return angles
    }
    
    /**
     * Generate realistic lateral raise angle data
     */
    private fun generateRealisticLateralRaiseData(repCount: Int): List<Float> {
        val angles = mutableListOf<Float>()
        
        // Lateral raise typically involves shoulder abduction to 90° (arms parallel to ground)
        // Most people's height decreases as they fatigue
        for (i in 1..repCount) {
            // Calculate fatigue factor (height decreases as reps increase)
            val fatigueFactor = i * 0.6f
            
            // Add realistic variation to target angles
            val targetHeightAngle = 90f - (Math.random() * 8 - 4).toFloat() - fatigueFactor
            angles.add(targetHeightAngle)
        }
        
        return angles
    }
    
    /**
     * Generate realistic lunges angle data
     */
    private fun generateRealisticLungesData(repCount: Int): List<Float> {
        val angles = mutableListOf<Float>()
        
        // Lunges typically involve front knee angles around 90° (proper depth)
        // Most people's depth and stability decreases as they fatigue
        for (i in 1..repCount) {
            // Calculate fatigue factor (depth decreases as reps increase)
            val fatigueFactor = i * 0.7f
            
            // Add realistic variation to target angles
            val targetDepthAngle = 90f + (Math.random() * 15 - 5).toFloat() + fatigueFactor
            angles.add(targetDepthAngle)
        }
        
        return angles
    }
    
    /**
     * Generate realistic shoulder press angle data
     */
    private fun generateRealisticShoulderPressData(repCount: Int): List<Float> {
        val angles = mutableListOf<Float>()
        
        // Shoulder press typically involves arm extension from 90° to 180° (fully extended overhead)
        // Most people's extension decreases as they fatigue
        for (i in 1..repCount) {
            // Calculate fatigue factor (extension decreases as reps increase)
            val fatigueFactor = i * 1.0f
            
            // Add realistic variation to target angles
            val targetExtensionAngle = 180f - (Math.random() * 10 - 5).toFloat() - fatigueFactor
            angles.add(targetExtensionAngle)
        }
        
        return angles
    }
}