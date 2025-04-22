package com.google.mediapipe.examples.poselandmarker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "samples")
data class SampleEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val timestamp: Long,
  val reps: Int,
  val avgAngle: Float,
  val errorsJson: String
)
