package com.google.mediapipe.examples.poselandmarker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ratings")
data class RatingEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val workoutId: String,
  val rating: Int
)
