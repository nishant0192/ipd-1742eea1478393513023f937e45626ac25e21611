package com.google.mediapipe.examples.poselandmarker

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.charts.RadarChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.gson.Gson
import com.google.mediapipe.examples.poselandmarker.adapters.ChartPagerAdapter
import com.google.mediapipe.examples.poselandmarker.data.AppDatabase
import com.google.mediapipe.examples.poselandmarker.data.SampleEntity
import com.google.mediapipe.examples.poselandmarker.data.WorkoutSession
import kotlinx.coroutines.*
import kotlin.math.min
import kotlin.random.Random

class ResultAnalysisActivity : AppCompatActivity() {
  private val mainScope = MainScope()
  private lateinit var db: AppDatabase
  private val gson = Gson()
  
  // UI Components
  private lateinit var tabLayout: TabLayout
  private lateinit var viewPager: ViewPager2
  
  // Simulated data for prototype - in a real app, this would come from the database
  private val exerciseNames = listOf("Bicep Curl", "Squat", "Lateral Raise", "Lunges", "Shoulder Press")
  private val recentSessions = mutableListOf<WorkoutSession>()
  
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_result_analysis)

    try {
      // Initialize database
      db = AppDatabase.getInstance(this)

      // Initialize UI components
      tabLayout = findViewById(R.id.tab_layout)
      viewPager = findViewById(R.id.view_pager)
      
      // Get workoutId if passed
      val workoutId = intent.getStringExtra("WORKOUT_ID") ?: ""

      // Generate some mock data for our prototype
      generateMockWorkoutSessions()
      
      // Load real data and setup UI
      mainScope.launch {
        try {
          val samples = withContext(Dispatchers.IO) {
            db.sampleDao().getRecent(1000)
          }
          
          if (samples.isNotEmpty()) {
            setupSummaryCards(samples)
            setupViewPager(samples)
          } else {
            // Use mock data for prototype demonstration
            setupSummaryCards(emptyList())
            setupViewPager(emptyList())
          }
        } catch (e: Exception) {
          Log.e("ResultAnalysis", "Error loading data: ${e.message}")
          e.printStackTrace()
          
          // Setup with empty data as fallback
          setupSummaryCards(emptyList())
          setupViewPager(emptyList())
        }
      }

      findViewById<Button>(R.id.btnClose).setOnClickListener {
        finish()
      }
    } catch (e: Exception) {
      Log.e("ResultAnalysis", "Error in onCreate: ${e.message}")
      e.printStackTrace()
      // Show a simple error toast and finish to avoid crashing
      android.widget.Toast.makeText(this, "Could not show analysis: ${e.message}", 
                                  android.widget.Toast.LENGTH_SHORT).show()
      finish()
    }
  }
  
  private fun generateMockWorkoutSessions() {
    try {
      // Generate 5 mock workout sessions for visualization
      for (i in 0 until 5) {
        val totalReps = Random.nextInt(10, 50)
        val perfectReps = Random.nextInt(totalReps / 2, totalReps)
        val avgAngle = 45f + Random.nextFloat() * 45f
        
        recentSessions.add(
          WorkoutSession(
            id = "session_$i",
            timestamp = System.currentTimeMillis() - (i * 86400000), // 1 day ago per session
            exerciseType = ExerciseType.values()[i % ExerciseType.values().size],
            totalReps = totalReps,
            perfectReps = perfectReps,
            avgAngle = avgAngle,
            difficulty = Random.nextInt(1, 5),
            duration = Random.nextInt(5, 30) // In minutes
          )
        )
      }
    } catch (e: Exception) {
      Log.e("ResultAnalysis", "Error generating mock data: ${e.message}")
    }
  }
  
  private fun setupSummaryCards(samples: List<SampleEntity>) {
    try {
      // Calculate stats from samples
      val totalReps = if (samples.isNotEmpty()) samples.maxOf { it.reps } else recentSessions.sumOf { it.totalReps }
      val perfectReps = if (samples.isNotEmpty()) {
        samples.count { 
          try {
            gson.fromJson(it.errorsJson, Array<String>::class.java).isEmpty()
          } catch (e: Exception) {
            Log.e("ResultAnalysis", "Error parsing errors: ${e.message}")
            true // Default to no errors if parsing fails
          }
        }
      } else {
        recentSessions.sumOf { it.perfectReps }
      }
      val avgAngle = if (samples.isNotEmpty()) {
        samples.map { it.avgAngle }.average()
      } else {
        recentSessions.map { it.avgAngle }.average()
      }
      
      // Calculate accuracy percentage
      val accuracy = if (totalReps > 0) (perfectReps.toFloat() / totalReps) * 100 else 0f
      
      // Update UI elements
      findViewById<TextView>(R.id.tvTotalReps).text = "Total Reps: $totalReps"
      findViewById<TextView>(R.id.tvPerfectReps).text = "Perfect Reps: $perfectReps"
      findViewById<TextView>(R.id.tvAvgAngle).text = String.format("Average Angle: %.1f°", avgAngle)
      findViewById<TextView>(R.id.tvAccuracy)?.text = String.format("Accuracy: %.1f%%", accuracy)
      
      // Update progress circle with accuracy
      findViewById<CircularProgressView>(R.id.progressAccuracy)?.apply {
        setProgress(accuracy.toInt())
        setText("${accuracy.toInt()}%")
      }
      
      // Display recommendation confidence (mock data for prototype)
      findViewById<TextView>(R.id.tvRecommendationConfidence)?.text = "Recommendation Confidence: 78%"
      
      // Update exercise breakdown card
      updateExerciseBreakdownCard()
    } catch (e: Exception) {
      Log.e("ResultAnalysis", "Error setting up summary cards: ${e.message}")
    }
  }
  
  private fun setupViewPager(samples: List<SampleEntity>) {
    try {
      // Create adapter for ViewPager
      val adapter = ChartPagerAdapter(this)
      
      // Add different chart fragments
      adapter.addChart("Performance", createPerformanceLineChart(samples))
      adapter.addChart("Errors", createErrorPieChart(samples))
      adapter.addChart("Exercise Comparison", createExerciseComparisonChart())
      adapter.addChart("RL Model", createRLModelChart())
      adapter.addChart("Form Analysis", createFormAnalysisRadarChart())
      
      // Set adapter
      viewPager.adapter = adapter
      
      // Connect TabLayout with ViewPager
      TabLayoutMediator(tabLayout, viewPager) { tab, position ->
        tab.text = adapter.getChartTitle(position)
      }.attach()
    } catch (e: Exception) {
      Log.e("ResultAnalysis", "Error setting up ViewPager: ${e.message}")
    }
  }
  
  private fun createPerformanceLineChart(samples: List<SampleEntity>): LineChart {
    val lineChart = LineChart(this)
    
    try {
      // If we have real samples, use them. Otherwise use mock data
      val entries = if (samples.isNotEmpty()) {
        samples
          .map { Entry(it.reps.toFloat(), it.avgAngle) }
          .sortedBy { it.x }
      } else {
        // Mock data showing angle over time
        (0 until 20).map { 
          Entry(it.toFloat(), 45f + Random.nextFloat() * 30f - 15f)
        }
      }
      
      // Create dataset
      val dataSet = LineDataSet(entries, "Angle per Rep").apply {
        setDrawValues(false)
        lineWidth = 2f
        color = ContextCompat.getColor(this@ResultAnalysisActivity, R.color.mp_color_primary)
        setCircleColor(ContextCompat.getColor(this@ResultAnalysisActivity, R.color.mp_color_primary))
        circleRadius = 4f
        setDrawCircleHole(false)
      }
      
      // Add a second dataset for "ideal" angle
      val idealEntries = entries.map { Entry(it.x, 45f) }
      val idealDataSet = LineDataSet(idealEntries, "Ideal Angle").apply {
        setDrawValues(false)
        lineWidth = 1f
        color = ContextCompat.getColor(this@ResultAnalysisActivity, R.color.mp_color_secondary)
        enableDashedLine(10f, 5f, 0f)
        setDrawCircles(false)
      }
      
      // Configure chart
      lineChart.apply {
        data = LineData(dataSet, idealDataSet)
        description = Description().apply { text = "" }
        axisRight.isEnabled = false
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        axisLeft.axisMinimum = 0f
        
        // Animate chart
        animateX(1000)
      }
    } catch (e: Exception) {
      Log.e("ResultAnalysis", "Error creating performance chart: ${e.message}")
    }
    
    return lineChart
  }
  
  private fun createErrorPieChart(samples: List<SampleEntity>): PieChart {
    val pieChart = PieChart(this)
    
    try {
      // Either use real samples or mock data for errors
      val errorCounts = if (samples.isNotEmpty()) {
        try {
          samples
            .flatMap { 
              try {
                gson.fromJson(it.errorsJson, Array<String>::class.java).toList()
              } catch (e: Exception) {
                emptyList<String>()
              }
            }
            .groupingBy { it }
            .eachCount()
        } catch (e: Exception) {
          Log.e("ResultAnalysis", "Error processing errors: ${e.message}")
          emptyMap<String, Int>()
        }
      } else {
        // Mock error data
        mapOf(
          "ELBOW_AWAY_FROM_BODY" to 8,
          "KNEES_OVER_TOES" to 5,
          "BACK_NOT_STRAIGHT" to 12,
          "ASYMMETRIC_MOVEMENT" to 3,
          "ELBOWS_TOO_BENT" to 6
        )
      }
      
      // If no errors, add a "No errors" entry
      val entries = if (errorCounts.isEmpty()) {
        listOf(PieEntry(1f, "No Errors"))
      } else {
        errorCounts.map { PieEntry(it.value.toFloat(), formatErrorName(it.key)) }
      }
      
      // Create dataset
      val dataSet = PieDataSet(entries, "Error Distribution").apply {
        sliceSpace = 3f
        selectionShift = 5f
        
        // Set colors
        val colors = listOf(
          ContextCompat.getColor(this@ResultAnalysisActivity, R.color.mp_color_primary),
          ContextCompat.getColor(this@ResultAnalysisActivity, R.color.mp_color_secondary),
          ContextCompat.getColor(this@ResultAnalysisActivity, R.color.mp_color_primary_variant),
          ContextCompat.getColor(this@ResultAnalysisActivity, R.color.mp_color_secondary_variant),
          ContextCompat.getColor(this@ResultAnalysisActivity, R.color.mp_color_primary_dark)
        )
        setColors(colors)
        
        valueFormatter = object : ValueFormatter() {
          override fun getFormattedValue(value: Float): String = value.toInt().toString()
        }
      }
      
      // Configure chart
      pieChart.apply {
        data = PieData(dataSet)
        description = Description().apply { text = "" }
        setUsePercentValues(false)
        setEntryLabelColor(ContextCompat.getColor(this@ResultAnalysisActivity, android.R.color.white))
        legend.textColor = ContextCompat.getColor(this@ResultAnalysisActivity, android.R.color.black)
        
        // Animate chart
        animateY(1200)
      }
    } catch (e: Exception) {
      Log.e("ResultAnalysis", "Error creating error pie chart: ${e.message}")
    }
    
    return pieChart
  }
  
  private fun createExerciseComparisonChart(): BarChart {
    val barChart = BarChart(this)
    
    try {
      // Create entries for each exercise type
      val entries = exerciseNames.mapIndexed { index, name ->
        // Get data for this exercise type from our mock sessions
        val sessionForExercise = recentSessions.find { it.exerciseType.ordinal == index }
        val accuracy = sessionForExercise?.let { 
          (it.perfectReps.toFloat() / it.totalReps) * 100 
        } ?: (Random.nextFloat() * 40f + 60f) // 60-100% accuracy
        
        BarEntry(index.toFloat(), accuracy)
      }
      
      // Create dataset
      val dataSet = BarDataSet(entries, "Exercise Accuracy (%)").apply {
        setColors(
          ContextCompat.getColor(this@ResultAnalysisActivity, R.color.mp_color_primary),
          ContextCompat.getColor(this@ResultAnalysisActivity, R.color.mp_color_secondary),
          ContextCompat.getColor(this@ResultAnalysisActivity, R.color.mp_color_primary_variant),
          ContextCompat.getColor(this@ResultAnalysisActivity, R.color.mp_color_secondary_variant),
          ContextCompat.getColor(this@ResultAnalysisActivity, R.color.mp_color_primary_dark)
        )
        valueTextSize = 10f
      }
      
      // Configure chart
      barChart.apply {
        data = BarData(dataSet)
        description = Description().apply { text = "" }
        xAxis.valueFormatter = IndexAxisValueFormatter(exerciseNames)
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.granularity = 1f
        xAxis.setCenterAxisLabels(false)
        xAxis.setDrawGridLines(false)
        
        // Y-axis settings
        axisLeft.axisMinimum = 0f
        axisLeft.axisMaximum = 100f
        axisRight.isEnabled = false
        
        // Animate chart
        animateY(1200)
        
        // Set label count to match exercises
        xAxis.labelCount = exerciseNames.size
        
        // Set label rotation for better readability
        xAxis.labelRotationAngle = 45f
        
        // Extra space for rotated labels
        extraBottomOffset = 20f
      }
    } catch (e: Exception) {
      Log.e("ResultAnalysis", "Error creating exercise comparison chart: ${e.message}")
    }
    
    return barChart
  }
  
  private fun createRLModelChart(): LineChart {
    val lineChart = LineChart(this)
    
    try {
      // Mock data showing RL model learning over time
      val rewardEntries = (0 until 25).map { i ->
        val reward = if (i < 5) {
          Random.nextFloat() * 0.5f - 0.25f // Initial low, possibly negative rewards
        } else if (i < 15) {
          Random.nextFloat() * 0.5f + 0.2f // Learning phase
        } else {
          Random.nextFloat() * 0.3f + 0.6f // Converged rewards
        }
        Entry(i.toFloat(), reward)
      }
      
      // Mock data for difficulty adaptation
      val difficultyEntries = (0 until 25).map { i ->
        val difficulty = when {
          i < 5 -> 1f + (i / 5f) // Starting at 1, slowly increasing
          i < 15 -> 2f + (i - 5) / 5f // Gradually increasing as model learns
          else -> min(5f, 3f + (i - 15) / 10f) // Plateauing at an appropriate difficulty
        }
        Entry(i.toFloat(), difficulty)
      }
      
      // Create datasets
      val rewardDataSet = LineDataSet(rewardEntries, "Training Reward").apply {
        setDrawValues(false)
        lineWidth = 2f
        color = ContextCompat.getColor(this@ResultAnalysisActivity, R.color.mp_color_primary)
        setCircleColor(ContextCompat.getColor(this@ResultAnalysisActivity, R.color.mp_color_primary))
        circleRadius = 3f
        setDrawCircleHole(false)
        axisDependency = YAxis.AxisDependency.LEFT
      }
      
      val difficultyDataSet = LineDataSet(difficultyEntries, "Difficulty Level").apply {
        setDrawValues(false)
        lineWidth = 2f
        color = ContextCompat.getColor(this@ResultAnalysisActivity, R.color.mp_color_secondary)
        setCircleColor(ContextCompat.getColor(this@ResultAnalysisActivity, R.color.mp_color_secondary))
        circleRadius = 3f
        setDrawCircleHole(false)
        axisDependency = YAxis.AxisDependency.RIGHT
      }
      
      // Configure chart
      lineChart.apply {
        data = LineData(rewardDataSet, difficultyDataSet)
        description = Description().apply { text = "" }
        
        // X-axis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.granularity = 5f
        xAxis.labelCount = 5
        
        // Left Y-axis (reward)
        axisLeft.axisMinimum = -0.5f
        axisLeft.axisMaximum = 1.0f
        axisLeft.textColor = ContextCompat.getColor(this@ResultAnalysisActivity, R.color.mp_color_primary)
        
        // Right Y-axis (difficulty)
        axisRight.isEnabled = true
        axisRight.axisMinimum = 0f
        axisRight.axisMaximum = 5f
        axisRight.textColor = ContextCompat.getColor(this@ResultAnalysisActivity, R.color.mp_color_secondary)
        
        // Animate chart
        animateXY(1000, 1000)
        
        // Legend
        legend.textSize = 12f
      }
    } catch (e: Exception) {
      Log.e("ResultAnalysis", "Error creating RL model chart: ${e.message}")
    }
    
    return lineChart
  }
  
  private fun createFormAnalysisRadarChart(): RadarChart {
    val radarChart = RadarChart(this)
    
    try {
      // Form analysis metrics 
      val metrics = listOf(
        "Angle Accuracy",
        "Movement Consistency",
        "Rep Pace",
        "Range of Motion",
        "Posture"
      )
      
      // Mock scores for current form
      val currentScores = listOf(
        Random.nextFloat() * 30f + 70f, // 70-100%
        Random.nextFloat() * 30f + 70f,
        Random.nextFloat() * 30f + 70f,
        Random.nextFloat() * 30f + 70f,
        Random.nextFloat() * 30f + 70f
      )
      
      // Mock scores for typical beginner
      val beginnerScores = listOf(
        Random.nextFloat() * 20f + 40f, // 40-60%
        Random.nextFloat() * 20f + 40f,
        Random.nextFloat() * 20f + 40f,
        Random.nextFloat() * 20f + 40f,
        Random.nextFloat() * 20f + 40f
      )
      
      // Create entries for each dataset
      val currentEntries = currentScores.mapIndexed { index, score ->
        RadarEntry(score)
      }
      
      val beginnerEntries = beginnerScores.mapIndexed { index, score ->
        RadarEntry(score)
      }
      
      // Create datasets
      val currentDataSet = RadarDataSet(currentEntries, "Your Form").apply {
        color = ContextCompat.getColor(this@ResultAnalysisActivity, R.color.mp_color_primary)
        fillColor = ContextCompat.getColor(this@ResultAnalysisActivity, R.color.mp_color_primary)
        setDrawFilled(true)
        fillAlpha = 150
        lineWidth = 2f
      }
      
      val beginnerDataSet = RadarDataSet(beginnerEntries, "Beginner Average").apply {
        color = ContextCompat.getColor(this@ResultAnalysisActivity, R.color.mp_color_secondary)
        fillColor = ContextCompat.getColor(this@ResultAnalysisActivity, R.color.mp_color_secondary)
        setDrawFilled(true)
        fillAlpha = 150
        lineWidth = 2f
      }
      
      // Configure chart
      radarChart.apply {
        data = RadarData(currentDataSet, beginnerDataSet)
        description = Description().apply { text = "" }
        
        // Set labels
        xAxis.valueFormatter = IndexAxisValueFormatter(metrics)
        
        // Set minimum and maximum values
        yAxis.axisMinimum = 0f
        yAxis.axisMaximum = 100f
        
        // Web lines
        webLineWidth = 1f
        webColor = ContextCompat.getColor(this@ResultAnalysisActivity, android.R.color.darker_gray)
        webLineWidthInner = 1f
        webColorInner = ContextCompat.getColor(this@ResultAnalysisActivity, android.R.color.darker_gray)
        webAlpha = 100
        
        // Animate chart
        animateXY(1000, 1000)
      }
    } catch (e: Exception) {
      Log.e("ResultAnalysis", "Error creating form analysis chart: ${e.message}")
    }
    
    return radarChart
  }
  
  private fun updateExerciseBreakdownCard() {
    try {
      // Get the breakdown card
      val breakdownCard = findViewById<CardView>(R.id.cardExerciseBreakdown)
      
      // We would populate this with real data from the database
      // For prototype, we're using mock data from recentSessions
      
      // Find the breakdown container
      val container = findViewById<LinearLayout>(R.id.exerciseBreakdownContainer)
      container?.removeAllViews()
      
      // Add a row for each exercise type
      for (i in exerciseNames.indices) {
        val sessionForExercise = recentSessions.find { it.exerciseType.ordinal == i }
        
        // Skip if no data for this exercise
        if (sessionForExercise == null) continue
        
        // Calculate accuracy
        val accuracy = (sessionForExercise.perfectReps.toFloat() / sessionForExercise.totalReps) * 100
        
        // Create and add row view
        val rowView = layoutInflater.inflate(R.layout.item_exercise_breakdown, container, false)
        
        // Set exercise name
        rowView.findViewById<TextView>(R.id.exerciseName).text = exerciseNames[i]
        
        // Set total reps
        rowView.findViewById<TextView>(R.id.exerciseReps).text = "${sessionForExercise.totalReps} reps"
        
        // Set accuracy
        rowView.findViewById<TextView>(R.id.exerciseAccuracy).text = String.format("%.1f%%", accuracy)
        
        // Add to container
        container?.addView(rowView)
      }
    } catch (e: Exception) {
      Log.e("ResultAnalysis", "Error updating exercise breakdown: ${e.message}")
    }
  }
  
  private fun formatErrorName(errorName: String): String {
    return when (errorName) {
      "ELBOW_AWAY_FROM_BODY" -> "Elbow Away"
      "KNEES_OVER_TOES" -> "Knees Over Toes"
      "BACK_NOT_STRAIGHT" -> "Back Not Straight"
      "ASYMMETRIC_MOVEMENT" -> "Asymmetric"
      "ELBOWS_TOO_BENT" -> "Elbows Too Bent"
      "BACK_ARCHING" -> "Back Arching"
      else -> errorName.replace("_", " ").lowercase().capitalize()
    }
  }
  
  private fun String.capitalize(): String {
    return this.split(" ").joinToString(" ") { word ->
      word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
  }
  
  override fun onDestroy() {
    super.onDestroy()
    mainScope.cancel()
  }
}