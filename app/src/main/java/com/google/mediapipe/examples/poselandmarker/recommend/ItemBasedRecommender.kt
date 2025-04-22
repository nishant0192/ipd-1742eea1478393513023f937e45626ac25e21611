// File: app/src/main/java/com/google/mediapipe/examples/poselandmarker/recommend/ItemBasedRecommender.kt
package com.google.mediapipe.examples.poselandmarker.recommend

import com.google.mediapipe.examples.poselandmarker.data.RatingDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

class ItemBasedRecommender(private val dao: RatingDao) {

  suspend fun recommend(k: Int = 3): List<String> = withContext(Dispatchers.Default) {
    val ratings = dao.allRatings()
    if (ratings.size < 2) return@withContext emptyList()

    // workout → List<Float> of ratings
    val byWorkout = ratings.groupBy { it.workoutId }
     .mapValues { it.value.map { r -> r.rating.toFloat() } }

    // cosine similarity using fold for Floats
    fun cosine(a: List<Float>, b: List<Float>): Float {
      val dot  = a.zip(b).fold(0f) { acc, (x, y) -> acc + x * y }
      val magA = sqrt(a.fold(0f) { acc, x -> acc + x * x })
      val magB = sqrt(b.fold(0f) { acc, y -> acc + y * y })
      return if (magA > 0 && magB > 0) dot / (magA * magB) else 0f
    }

    val last = ratings.last().workoutId
    return@withContext byWorkout
      .filterKeys { it != last }
      .mapValues { cosine(byWorkout[last]!!, it.value) }
      .entries
      .sortedByDescending { it.value }
      .take(k)
      .map { it.key }
  }
}
