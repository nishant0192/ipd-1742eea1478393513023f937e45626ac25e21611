package com.google.mediapipe.examples.poselandmarker

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.gson.Gson
import com.google.mediapipe.examples.poselandmarker.data.AppDatabase
import com.google.mediapipe.examples.poselandmarker.data.SampleEntity
import kotlinx.coroutines.*

class ResultAnalysisActivity : AppCompatActivity() {
  private val mainScope = MainScope()
  private lateinit var db: AppDatabase
  private val gson = Gson()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_result_analysis)

    // initialize DB
    db = AppDatabase.getInstance(this)

    // fetch workoutId if you're tagging samples per workout
    val workoutId = intent.getStringExtra("WORKOUT_ID") ?: ""

    // load data and render
    mainScope.launch {
      val samples = withContext(Dispatchers.IO) {
        // if you stored workoutId in SampleEntity, filter by it:
        // db.sampleDao().getByWorkoutId(workoutId)
        db.sampleDao().getRecent(1000)
      }
      if (samples.isNotEmpty()) {
        renderSummary(samples)
        renderLineChart(samples)
        renderPieChart(samples)
      }
    }

    findViewById<Button>(R.id.btnClose).setOnClickListener {
      finish()
    }
  }

  private fun renderSummary(samples: List<SampleEntity>) {
    // Total reps is the max 'reps' value
    val totalReps = samples.maxOf { it.reps }
    // Perfect reps = samples with empty errors
    val perfectReps = samples.count {
      gson.fromJson(it.errorsJson, Array<String>::class.java).isEmpty()
    }
    // Average of all avgAngle
    val avgAngle = samples.map { it.avgAngle }.average()

    findViewById<TextView>(R.id.tvTotalReps).text = "Total Reps: $totalReps"
    findViewById<TextView>(R.id.tvPerfectReps).text = "Perfect Reps: $perfectReps"
    findViewById<TextView>(R.id.tvAvgAngle).text =
      String.format("Average Angle: %.1f\u00B0", avgAngle)
  }

  private fun renderLineChart(samples: List<SampleEntity>) {
    val entries = samples
      .map { Entry(it.reps.toFloat(), it.avgAngle) }
      .sortedBy { it.x }

    val set = LineDataSet(entries, "Angle per Rep").apply {
      lineWidth = 2f
      setDrawCircles(false)
    }
    val chart = findViewById<LineChart>(R.id.lineChart)
    chart.data = LineData(set)
    chart.description = Description().apply { text = "" }
    chart.axisRight.isEnabled = false
    chart.invalidate()
  }

  private fun renderPieChart(samples: List<SampleEntity>) {
    // count all error types
    val errorCounts = samples
      .flatMap { gson.fromJson(it.errorsJson, Array<String>::class.java).toList() }
      .groupingBy { it }
      .eachCount()

    val entries = errorCounts.map { PieEntry(it.value.toFloat(), it.key) }
    val set = PieDataSet(entries, "Error Distribution").apply {
      valueFormatter = object : ValueFormatter() {
        override fun getFormattedValue(value: Float): String =
          value.toInt().toString()
      }
      sliceSpace = 2f
    }
    val chart = findViewById<PieChart>(R.id.pieChart)
    chart.data = PieData(set)
    chart.description = Description().apply { text = "" }
    chart.invalidate()
  }

  override fun onDestroy() {
    super.onDestroy()
    mainScope.cancel()
  }
}
