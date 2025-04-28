package com.google.mediapipe.examples.poselandmarker

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.github.mikephil.charting.utils.ColorTemplate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Activity for displaying comprehensive workout analysis with professional-grade charts
 * This is designed for investor presentations to showcase data visualization capabilities
 */
class ResultAnalysisActivity : AppCompatActivity() {

    private lateinit var formQualityChart: PieChart
    private lateinit var repProgressChart: LineChart
    private lateinit var performanceChart: LineChart
    
    private var workoutId: String? = null
    private var exerciseType: ExerciseType = ExerciseType.BICEP

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.workout_analysis_dialog)
        
        // Set up toolbar
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = "Workout Analysis"
        
        // Get workout ID from intent
        workoutId = intent.getStringExtra("WORKOUT_ID")
        
        // Get exercise type - default to bicep if not provided
        val exerciseTypeOrdinal = intent.getIntExtra("EXERCISE_TYPE", 0)
        exerciseType = ExerciseType.values()[exerciseTypeOrdinal]
        
        // Initialize charts
        formQualityChart = findViewById(R.id.chart_form_quality)
        repProgressChart = findViewById(R.id.chart_progress)
        performanceChart = findViewById(R.id.chart_metrics)
        
        // Setup charts with realistic data for investors
        setupAnalysisUI()
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_analysis, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            R.id.action_share -> {
                shareAnalysis()
                true
            }
            R.id.action_save -> {
                saveAnalysis()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun setupAnalysisUI() {
        try {
            // Setup workout info header
            setupHeader()
            
            // Setup summary metrics
            setupSummaryMetrics()
            
            // Setup visualization charts
            setupFormQualityChart()
            setupRepProgressChart()
            setupPerformanceChart()
            
            // Setup recommendations
            setupRecommendations()
            
            // Setup coach commentary
            setupCoachCommentary()
        } catch (e: Exception) {
            Log.e("ResultAnalysisActivity", "Error setting up analysis UI: ${e.message}")
            Toast.makeText(this, "Error setting up analysis", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupHeader() {
        // Set exercise icon and name
        val exerciseIcon: View = findViewById(R.id.exercise_icon)
        val exerciseNameText: TextView = findViewById(R.id.text_exercise_type)
        val workoutDateText: TextView = findViewById(R.id.text_workout_date)
        val gradeText: TextView = findViewById(R.id.text_grade)
        
        // Set exercise type name
        exerciseNameText.text = getExerciseDisplayName(exerciseType)
        
        // Set current date and time
        val dateFormat = SimpleDateFormat("MMMM d, yyyy • h:mm a", Locale.getDefault())
        workoutDateText.text = dateFormat.format(Date())
        
        // Set grade - generate a good score for investor demo
        val grade = "A"
        gradeText.text = grade
        
        // Set grade background color
        val gradeBackground = gradeText.background
        gradeBackground.setTint(ContextCompat.getColor(this, R.color.green_grade))
    }
    
    private fun setupSummaryMetrics() {
        val durationText: TextView = findViewById(R.id.text_duration)
        val totalRepsText: TextView = findViewById(R.id.text_total_reps)
        val perfectFormText: TextView = findViewById(R.id.text_perfect_form)
        val scoreText: TextView = findViewById(R.id.text_score)
        
        // Set realistic values for investor demo
        durationText.text = "06:42"
        
        // Generate total reps based on exercise type
        val totalReps = when (exerciseType) {
            ExerciseType.BICEP -> 15
            ExerciseType.SQUAT -> 12
            ExerciseType.LATERAL_RAISE -> 14
            ExerciseType.LUNGES -> 10
            ExerciseType.SHOULDER_PRESS -> 12
        }
        totalRepsText.text = totalReps.toString()
        
        // Generate perfect form percentage
        val perfectPercentage = 85
        perfectFormText.text = "$perfectPercentage%"
        
        // Generate overall score
        val score = 92
        scoreText.text = score.toString()
    }
    
    private fun setupFormQualityChart() {
        // Create entries
        val entries = ArrayList<PieEntry>()
        
        // Generate realistic form quality distribution based on exercise type
        when (exerciseType) {
            ExerciseType.BICEP -> {
                entries.add(PieEntry(45f, "Perfect"))
                entries.add(PieEntry(35f, "Good"))
                entries.add(PieEntry(15f, "Fair"))
                entries.add(PieEntry(5f, "Needs Work"))
            }
            ExerciseType.SQUAT -> {
                entries.add(PieEntry(35f, "Perfect"))
                entries.add(PieEntry(40f, "Good"))
                entries.add(PieEntry(20f, "Fair"))
                entries.add(PieEntry(5f, "Needs Work"))
            }
            ExerciseType.LATERAL_RAISE -> {
                entries.add(PieEntry(30f, "Perfect"))
                entries.add(PieEntry(45f, "Good"))
                entries.add(PieEntry(20f, "Fair"))
                entries.add(PieEntry(5f, "Needs Work"))
            }
            ExerciseType.LUNGES -> {
                entries.add(PieEntry(25f, "Perfect"))
                entries.add(PieEntry(40f, "Good"))
                entries.add(PieEntry(25f, "Fair"))
                entries.add(PieEntry(10f, "Needs Work"))
            }
            ExerciseType.SHOULDER_PRESS -> {
                entries.add(PieEntry(30f, "Perfect"))
                entries.add(PieEntry(45f, "Good"))
                entries.add(PieEntry(20f, "Fair"))
                entries.add(PieEntry(5f, "Needs Work"))
            }
        }
        
        // Create dataset
        val dataSet = PieDataSet(entries, "Form Quality")
        
        // Set colors
        val colors = ArrayList<Int>()
        colors.add(ContextCompat.getColor(this, R.color.green_grade))
        colors.add(ContextCompat.getColor(this, R.color.blue_grade))
        colors.add(ContextCompat.getColor(this, R.color.yellow_grade))
        colors.add(ContextCompat.getColor(this, R.color.orange_grade))
        dataSet.colors = colors
        
        // Styling
        dataSet.sliceSpace = 3f
        dataSet.selectionShift = 8f
        dataSet.valueTextSize = 14f
        dataSet.valueTextColor = Color.WHITE
        
        // Create pie data
        val data = PieData(dataSet)
        data.setValueFormatter(PercentFormatter(formQualityChart))
        
        // Configure chart
        formQualityChart.data = data
        formQualityChart.description.isEnabled = false
        formQualityChart.setExtraOffsets(20f, 20f, 20f, 20f)
        formQualityChart.dragDecelerationFrictionCoef = 0.95f
        
        // Set center text
        formQualityChart.setDrawCenterText(true)
        formQualityChart.centerText = "Form\nQuality"
        formQualityChart.setCenterTextSize(16f)
        
        // Configure legend
        val legend = formQualityChart.legend
        legend.verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
        legend.horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
        legend.orientation = Legend.LegendOrientation.HORIZONTAL
        legend.textSize = 12f
        
        // Animate the chart
        formQualityChart.animateY(1400)
        
        // Hide text on slices for cleaner look
        formQualityChart.setDrawEntryLabels(false)
        
        // Refresh chart
        formQualityChart.invalidate()
    }
    
    private fun setupRepProgressChart() {
        // Create entries
        val repEntries = ArrayList<Entry>()
        val targetEntries = ArrayList<Entry>()
        
        // Generate realistic angle data based on exercise type
        val repCount = when (exerciseType) {
            ExerciseType.BICEP -> 15
            ExerciseType.SQUAT -> 12
            ExerciseType.LATERAL_RAISE -> 14
            ExerciseType.LUNGES -> 10
            ExerciseType.SHOULDER_PRESS -> 12
        }
        
        // Target angle for perfect form
        val targetAngle = when (exerciseType) {
            ExerciseType.BICEP -> 45f  // Target elbow angle at top of curl
            ExerciseType.SQUAT -> 90f  // Target knee angle at bottom of squat
            ExerciseType.LATERAL_RAISE -> 90f  // Target shoulder abduction angle
            ExerciseType.LUNGES -> 90f  // Target front knee angle
            ExerciseType.SHOULDER_PRESS -> 175f  // Target arm extension angle
        }
        
        // Generate realistic angle data with slight degradation pattern (fatigue effect)
        // Initial angle is close to perfect, gradual deviation occurs
        for (i in 1..repCount) {
            val repIndex = i.toFloat()
            
            // More deviation as workout progresses (fatigue effect)
            val fatigueFactor = min(i * 0.4f, 6f) 
            
            // Add some randomness for realism
            val randomVariation = (Random.nextFloat() * 10f - 5f)
            
            // Calculate angle with fatigue and random components
            val angle = when (exerciseType) {
                ExerciseType.BICEP -> {
                    // Bicep angles tend to increase (worse) as fatigue sets in
                    targetAngle + fatigueFactor + randomVariation
                }
                ExerciseType.SQUAT -> {
                    // Squat depth decreases (angle increases) with fatigue
                    targetAngle + fatigueFactor * 1.5f + randomVariation
                }
                ExerciseType.LATERAL_RAISE -> {
                    // Lateral raise height decreases (angle decreases) with fatigue
                    targetAngle - fatigueFactor * 1.2f + randomVariation
                }
                ExerciseType.LUNGES -> {
                    // Lunge depth decreases (angle increases) with fatigue
                    targetAngle + fatigueFactor * 1.7f + randomVariation
                }
                ExerciseType.SHOULDER_PRESS -> {
                    // Shoulder press extension decreases with fatigue
                    targetAngle - fatigueFactor * 2f + randomVariation
                }
            }
            
            repEntries.add(Entry(repIndex, angle))
            targetEntries.add(Entry(repIndex, targetAngle))
        }
        
        // Create datasets for actual angles and target line
        val datasets = ArrayList<ILineDataSet>()
        
        // Actual angle dataset with professional styling
        val repDataSet = LineDataSet(repEntries, "Actual Angles")
        repDataSet.color = ContextCompat.getColor(this, R.color.mp_color_primary)
        repDataSet.lineWidth = 3f
        repDataSet.setCircleColor(ContextCompat.getColor(this, R.color.mp_color_primary))
        repDataSet.circleRadius = 5f
        repDataSet.setDrawCircleHole(true)
        repDataSet.circleHoleRadius = 2.5f
        repDataSet.valueTextSize = 12f
        repDataSet.setDrawValues(false) // No values to avoid clutter
        
        // Target angle dataset with dashed line styling
        val targetDataSet = LineDataSet(targetEntries, "Target Angle")
        targetDataSet.color = ContextCompat.getColor(this, R.color.mp_color_secondary)
        targetDataSet.lineWidth = 2f
        targetDataSet.enableDashedLine(10f, 5f, 0f) // Dashed line for target
        targetDataSet.setDrawCircles(false) // No circles for target line
        targetDataSet.setDrawValues(false) // No values for target line
        
        // Add both datasets
        datasets.add(repDataSet)
        datasets.add(targetDataSet)
        
        // Create line data with all datasets
        val lineData = LineData(datasets)
        
        // Configure chart with professional styling
        repProgressChart.data = lineData
        repProgressChart.description.isEnabled = false
        repProgressChart.setExtraOffsets(10f, 20f, 25f, 10f)
        repProgressChart.setDrawGridBackground(false)
        repProgressChart.setDrawBorders(false)
        repProgressChart.setScaleEnabled(true)
        repProgressChart.setPinchZoom(true)
        
        // Configure X axis
        val xAxis = repProgressChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.granularity = 1f
        // Custom formatter to show "Rep X" on x-axis
        xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return "Rep ${value.toInt()}"
            }
        }
        xAxis.textSize = 12f
        xAxis.textColor = Color.BLACK
        
        // Configure Y axis
        val leftAxis = repProgressChart.axisLeft
        leftAxis.setDrawGridLines(true)
        leftAxis.gridColor = Color.LTGRAY
        leftAxis.gridLineWidth = 0.5f
        
        // Set Y axis range appropriate for the exercise
        when (exerciseType) {
            ExerciseType.BICEP -> {
                leftAxis.axisMinimum = 30f
                leftAxis.axisMaximum = 70f
            }
            ExerciseType.SQUAT -> {
                leftAxis.axisMinimum = 70f
                leftAxis.axisMaximum = 120f
            }
            ExerciseType.LATERAL_RAISE -> {
                leftAxis.axisMinimum = 70f
                leftAxis.axisMaximum = 100f
            }
            ExerciseType.LUNGES -> {
                leftAxis.axisMinimum = 75f
                leftAxis.axisMaximum = 125f
            }
            ExerciseType.SHOULDER_PRESS -> {
                leftAxis.axisMinimum = 140f
                leftAxis.axisMaximum = 180f
            }
        }
        
        // Format Y axis values to show degrees
        leftAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return "${value.toInt()}°"
            }
        }
        leftAxis.textSize = 12f
        leftAxis.textColor = Color.BLACK
        
        // Hide right Y axis
        val rightAxis = repProgressChart.axisRight
        rightAxis.isEnabled = false
        
        // Configure legend
        val legend = repProgressChart.legend
        legend.form = Legend.LegendForm.LINE
        legend.formSize = 12f
        legend.textSize = 12f
        legend.textColor = Color.BLACK
        legend.verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
        legend.horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
        legend.orientation = Legend.LegendOrientation.HORIZONTAL
        legend.setDrawInside(false)
        
        // Animate chart
        repProgressChart.animateX(1500)
        
        // Refresh chart
        repProgressChart.invalidate()
    }
    
    private fun setupPerformanceChart() {
        // Create multiple performance metric datasets for a comprehensive view
        
        // Lists to hold entries for each metric
        val formEntries = ArrayList<Entry>()
        val speedEntries = ArrayList<Entry>()
        val stabilityEntries = ArrayList<Entry>()
        
        // Number of reps
        val repCount = when (exerciseType) {
            ExerciseType.BICEP -> 15
            ExerciseType.SQUAT -> 12
            ExerciseType.LATERAL_RAISE -> 14
            ExerciseType.LUNGES -> 10
            ExerciseType.SHOULDER_PRESS -> 12
        }
        
        // Generate realistic data with appropriate trends
        // Form quality typically decreases with fatigue
        // Speed typically increases (people rush) with fatigue
        // Stability typically decreases with fatigue
        for (i in 1..repCount) {
            val repIndex = i.toFloat()
            
            // Calculate fatigue factors - more pronounced as workout progresses
            val formDecline = min(0.8f * i, 15f)
            val speedIncrease = min(0.6f * i, 12f)
            val stabilityDecline = min(1f * i, 20f)
            
            // Add random variation for natural appearance
            val formVariation = (Random.nextFloat() * 8 - 4)
            val speedVariation = (Random.nextFloat() * 6 - 3)
            val stabilityVariation = (Random.nextFloat() * 10 - 5)
            
            // Calculate metrics (0-100 scale)
            
            // Form starts high and gradually declines
            val formBase = when (exerciseType) {
                ExerciseType.BICEP -> 95f
                ExerciseType.SQUAT -> 90f
                ExerciseType.LATERAL_RAISE -> 92f
                ExerciseType.LUNGES -> 88f
                ExerciseType.SHOULDER_PRESS -> 93f
            }
            val formScore = max(min(formBase - formDecline + formVariation, 100f), 0f)
            
            // Speed starts optimal and gets worse (too fast)
            val speedBase = 90f
            val speedScore = max(min(speedBase - speedIncrease + speedVariation, 100f), 0f)
            
            // Stability starts high and gradually declines
            val stabilityBase = when (exerciseType) {
                ExerciseType.BICEP -> 95f
                ExerciseType.SQUAT -> 88f
                ExerciseType.LATERAL_RAISE -> 90f
                ExerciseType.LUNGES -> 85f
                ExerciseType.SHOULDER_PRESS -> 92f
            }
            val stabilityScore = max(min(stabilityBase - stabilityDecline + stabilityVariation, 100f), 0f)
            
            // Add entries
            formEntries.add(Entry(repIndex, formScore))
            speedEntries.add(Entry(repIndex, speedScore))
            stabilityEntries.add(Entry(repIndex, stabilityScore))
        }
        
        // Create datasets
        val datasets = ArrayList<ILineDataSet>()
        
        // Form quality dataset with professional styling
        val formDataSet = LineDataSet(formEntries, "Form Quality")
        formDataSet.color = ContextCompat.getColor(this, R.color.green_grade)
        formDataSet.lineWidth = 3f
        formDataSet.setCircleColor(ContextCompat.getColor(this, R.color.green_grade))
        formDataSet.circleRadius = 4f
        formDataSet.setDrawCircleHole(true)
        formDataSet.circleHoleRadius = 2f
        formDataSet.valueTextSize = 0f
        formDataSet.setDrawValues(false)
        
        // Speed dataset
        val speedDataSet = LineDataSet(speedEntries, "Movement Speed")
        speedDataSet.color = ContextCompat.getColor(this, R.color.blue_grade)
        speedDataSet.lineWidth = 3f
        speedDataSet.setCircleColor(ContextCompat.getColor(this, R.color.blue_grade))
        speedDataSet.circleRadius = 4f
        speedDataSet.setDrawCircleHole(true)
        speedDataSet.circleHoleRadius = 2f
        speedDataSet.valueTextSize = 0f
        speedDataSet.setDrawValues(false)
        
        // Stability dataset
        val stabilityDataSet = LineDataSet(stabilityEntries, "Movement Stability")
        stabilityDataSet.color = ContextCompat.getColor(this, R.color.mp_color_secondary)
        stabilityDataSet.lineWidth = 3f
        stabilityDataSet.setCircleColor(ContextCompat.getColor(this, R.color.mp_color_secondary))
        stabilityDataSet.circleRadius = 4f
        stabilityDataSet.setDrawCircleHole(true)
        stabilityDataSet.circleHoleRadius = 2f
        stabilityDataSet.valueTextSize = 0f
        stabilityDataSet.setDrawValues(false)
        
        // Add all datasets
        datasets.add(formDataSet)
        datasets.add(speedDataSet)
        datasets.add(stabilityDataSet)
        
        // Create line data
        val lineData = LineData(datasets)
        
        // Configure chart with professional styling
        performanceChart.data = lineData
        performanceChart.description.isEnabled = false
        performanceChart.setExtraOffsets(10f, 20f, 25f, 10f)
        performanceChart.setDrawGridBackground(false)
        performanceChart.setDrawBorders(false)
        performanceChart.setScaleEnabled(true)
        performanceChart.setPinchZoom(true)
        
        // Configure X axis
        val xAxis = performanceChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.granularity = 1f
        // Custom formatter to show "Rep X" on x-axis
        xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return "Rep ${value.toInt()}"
            }
        }
        xAxis.textSize = 12f
        xAxis.textColor = Color.BLACK
        
        // Configure Y axis (percentage scale 0-100)
        val leftAxis = performanceChart.axisLeft
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
        
        // Hide right Y axis
        val rightAxis = performanceChart.axisRight
        rightAxis.isEnabled = false
        
        // Configure legend
        val legend = performanceChart.legend
        legend.form = Legend.LegendForm.LINE
        legend.formSize = 12f
        legend.textSize = 12f
        legend.textColor = Color.BLACK
        legend.verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
        legend.horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
        legend.orientation = Legend.LegendOrientation.HORIZONTAL
        legend.setDrawInside(false)
        
        // Animate chart
        performanceChart.animateX(1500)
        
        // Refresh chart
        performanceChart.invalidate()
    }
    
    private fun setupRecommendations() {
        // Primary recommendation
        val primaryRecommendation = findViewById<TextView>(R.id.text_primary_recommendation)
        // Secondary recommendation
        val secondaryRecommendation = findViewById<TextView>(R.id.text_secondary_recommendation)
        // Tertiary recommendation (if available)
        val tertiaryRecommendation = findViewById<TextView>(R.id.text_tertiary_recommendation)
        
        // Set exercise-specific recommendations
        when (exerciseType) {
            ExerciseType.BICEP -> {
                primaryRecommendation.text = "Focus on maintaining your elbow position close to your torso throughout the entire movement to maximize bicep activation."
                secondaryRecommendation.text = "Try slowing down the eccentric (lowering) phase to 3-4 seconds per rep for increased time under tension."
                tertiaryRecommendation?.text = "Consider increasing resistance by 5-10% in your next workout while maintaining proper form."
            }
            ExerciseType.SQUAT -> {
                primaryRecommendation.text = "Keep your weight centered over the middle of your foot - avoid shifting too far forward onto your toes."
                secondaryRecommendation.text = "Work on maintaining consistent depth on each repetition, especially as fatigue increases."
                tertiaryRecommendation?.text = "Try adding a brief 1-second pause at the bottom of each rep to improve stability and form awareness."
            }
            ExerciseType.LATERAL_RAISE -> {
                primaryRecommendation.text = "Maintain a slight bend in your elbows throughout the movement to reduce stress on the joint."
                secondaryRecommendation.text = "Focus on raising both arms at exactly the same height to avoid muscular imbalances."
                tertiaryRecommendation?.text = "Consider using a lighter weight and focusing on perfect form for your next session."
            }
            ExerciseType.LUNGES -> {
                primaryRecommendation.text = "Focus on keeping your front knee directly above your ankle, not extending past your toes."
                secondaryRecommendation.text = "Maintain an upright torso position throughout the movement to properly engage your core."
                tertiaryRecommendation?.text = "Try adding alternating legs to improve balance and coordination."
            }
            ExerciseType.SHOULDER_PRESS -> {
                primaryRecommendation.text = "Engage your core throughout the movement to prevent excessive arching in your lower back."
                secondaryRecommendation.text = "Ensure you're achieving full extension at the top of each rep for maximum muscle activation."
                tertiaryRecommendation?.text = "Consider incorporating unilateral (one-arm) shoulder presses in your next workout to address any imbalances."
            }
        }
    }
    
    private fun setupCoachCommentary() {
        // Coach commentary
        val coachCommentary = findViewById<TextView>(R.id.text_coach_commentary)
        
        // Set exercise-specific professional coach commentary
        when (exerciseType) {
            ExerciseType.BICEP -> {
                coachCommentary.text = "Your bicep curl form shows excellent technique in maintaining fixed elbow position during most reps. Your peak contraction angle is consistently good, and you're controlling the tempo well. As fatigue sets in around rep #10, your elbows begin to drift slightly forward - this is normal but try to focus on maintaining that position even in later reps. Overall, this was an excellent set with high-quality execution on the majority of repetitions."
            }
            ExerciseType.SQUAT -> {
                coachCommentary.text = "Your squat mechanics demonstrate solid fundamentals with good knee tracking and consistent depth on most repetitions. Your hip-to-knee alignment is excellent through the first 8 reps. As fatigue develops, there's a slight tendency to rise onto your toes and reduce depth in the final 3-4 reps. Consider focusing on driving through your heels and maintaining depth even as fatigue builds. The timing of your eccentric phase is very consistent, showing good control throughout the movement pattern."
            }
            ExerciseType.LATERAL_RAISE -> {
                coachCommentary.text = "Your lateral raise technique shows good shoulder positioning with minimal momentum usage. Your peak height is consistent through most repetitions, though there's a slight asymmetry with your right arm reaching about 5° higher than your left in the middle reps. Your tempo is controlled, which is excellent for maximizing deltoid engagement. As fatigue increases in the final reps, focus on maintaining the same peak height rather than reducing range of motion. Overall, this was a well-executed set with good time under tension."
            }
            ExerciseType.LUNGES -> {
                coachCommentary.text = "Your lunge form demonstrates good stability and knee control throughout most repetitions. Your step length is consistent, creating proper 90° angles at both knees. There's occasional torso lean on the deeper reps - focus on keeping your chest upright by engaging your core more actively. Your balance is excellent, particularly for a unilateral exercise. The tempo of your repetitions is also consistent, though consider adding a brief pause at the bottom position to increase stability training. Overall, very good execution with minor adjustments needed."
            }
            ExerciseType.SHOULDER_PRESS -> {
              coachCommentary.text = "Your shoulder press mechanics show excellent shoulder-to-elbow alignment and proper scapular positioning. Your lockout at the top is complete on most repetitions, though it diminishes slightly in the final 3-4 reps as fatigue builds. There's minimal lumbar extension (back arching), demonstrating good core engagement. Your bilateral symmetry is excellent with both arms moving at identical speeds. As you continue to train, focus on maintaining that full extension even during the final repetitions. This was a very well-executed set overall with strong technical proficiency."
          }
      }
  }
  
  /**
   * Share workout analysis results
   */
  private fun shareAnalysis() {
      // Create shareable content
      val exerciseName = getExerciseDisplayName(exerciseType)
      val shareText = "I just completed a $exerciseName workout with Exercise Form Assistant!\n\n" +
              "Score: 92/100\n" +
              "Perfect Form: 85%\n" +
              "Form Analysis: Excellent technique with minimal deviations\n\n" +
              "Try Exercise Form Assistant for real-time form correction and analytics!"
              
      // Create share intent
      val shareIntent = Intent(Intent.ACTION_SEND).apply {
          type = "text/plain"
          putExtra(Intent.EXTRA_SUBJECT, "My $exerciseName Workout Analysis")
          putExtra(Intent.EXTRA_TEXT, shareText)
      }
      
      // Launch share dialog
      startActivity(Intent.createChooser(shareIntent, "Share Workout Analysis"))
  }
  
  /**
   * Save workout analysis to history
   */
  private fun saveAnalysis() {
      // For demo purposes, just show a toast
      Toast.makeText(this, "Workout saved to history", Toast.LENGTH_SHORT).show()
  }
  
  /**
   * Get display name for exercise type
   */
  private fun getExerciseDisplayName(exerciseType: ExerciseType): String {
      return when (exerciseType) {
          ExerciseType.BICEP -> "Bicep Curl"
          ExerciseType.SQUAT -> "Squat"
          ExerciseType.LATERAL_RAISE -> "Lateral Raise"
          ExerciseType.LUNGES -> "Lunges"
          ExerciseType.SHOULDER_PRESS -> "Shoulder Press"
      }
  }
}