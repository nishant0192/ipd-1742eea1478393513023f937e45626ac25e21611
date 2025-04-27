package com.google.mediapipe.examples.poselandmarker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.mediapipe.examples.poselandmarker.data.*
import com.google.mediapipe.examples.poselandmarker.rl.QLearningAgent
import com.google.mediapipe.examples.poselandmarker.rl.State
import com.google.mediapipe.examples.poselandmarker.recommend.ItemBasedRecommender
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt
import android.util.Log

class MainViewModel(app: Application) : AndroidViewModel(app) {

  // ─── Pose‑landmarker settings ─────────────────────────────────
  private var _model = 0
  private var _delegate = 0
  private var _minPoseDetectionConfidence = 0.5f
  private var _minPoseTrackingConfidence   = 0.5f
  private var _minPosePresenceConfidence   = 0.5f
  private var _exerciseType = ExerciseType.BICEP

  val currentModel: Int get() = _model
  val currentDelegate: Int get() = _delegate
  val currentMinPoseDetectionConfidence: Float get() = _minPoseDetectionConfidence
  val currentMinPoseTrackingConfidence: Float get() = _minPoseTrackingConfidence
  val currentMinPosePresenceConfidence: Float get() = _minPosePresenceConfidence
  val currentExerciseType: ExerciseType get() = _exerciseType

  // ─── Settings change notification ────────────────────────────────────────
  private val _settingsChanged = MutableLiveData<Boolean>(false)
  val settingsChanged: LiveData<Boolean> = _settingsChanged

  // ─── Client‑side RL/RS fields ────────────────────────────────────────────
  private val db          = AppDatabase.getInstance(app)
  private val rlAgent     = QLearningAgent(app)
  private val recommender = ItemBasedRecommender(db.ratingDao())
  private val gson        = Gson()

  // ─── RL Values exposed to UI ────────────────────────────────────────────
  private val _difficulty = MutableLiveData(1)
  val difficulty: LiveData<Int> = _difficulty
  
  // ─── RS Values exposed to UI ────────────────────────────────────────────
  private val _recommendations = MutableStateFlow<List<String>>(emptyList())
  val recommendations: StateFlow<List<String>> = _recommendations.asStateFlow()
  
  // ─── Workout statistics ────────────────────────────────────────────────
  private val _totalReps = MutableLiveData(0)
  val totalReps: LiveData<Int> = _totalReps
  
  private val _perfectReps = MutableLiveData(0)
  val perfectReps: LiveData<Int> = _perfectReps
  
  private val _avgAngle = MutableLiveData(0f)
  val avgAngle: LiveData<Float> = _avgAngle
  
  private var totalAngle = 0f

  // Setter methods with notification of changes
  fun setModel(m: Int) {
    _model = m
    _settingsChanged.value = true
  }

  // In MainViewModel.kt
  fun setTotalReps(count: Int) {
    _totalReps.value = count
  }
  
  fun setDelegate(d: Int) {
    _delegate = d
    _settingsChanged.value = true
  }
  
  fun setMinPoseDetectionConfidence(c: Float) {
    _minPoseDetectionConfidence = c
    _settingsChanged.value = true
  }
  
  fun setMinPoseTrackingConfidence(c: Float) {
    _minPoseTrackingConfidence = c
    _settingsChanged.value = true
  }
  
  fun setMinPosePresenceConfidence(c: Float) {
    _minPosePresenceConfidence = c
    _settingsChanged.value = true
  }
  
  fun setExerciseType(t: ExerciseType) {
    _exerciseType = t
    _settingsChanged.value = true
  }
  
  fun acknowledgeSettingsChanged() {
    _settingsChanged.value = false
  }

  companion object {
    private const val BATCH = 5
    private const val TARGET_ANGLE = 45
  }

  /**
   * On each rep: store sample then tune difficulty
   */
  fun recordRep(angle: Float, errors: List<String>) {
    Log.d("RepCounter", "Recording rep with angle $angle")
    viewModelScope.launch {
      // Update local stats
      val currentTotal = _totalReps.value ?: 0
      _totalReps.value = currentTotal + 1
      
      if (errors.isEmpty()) {
        val currentPerfect = _perfectReps.value ?: 0
        _perfectReps.value = currentPerfect + 1
      }
      
      totalAngle += angle
      _avgAngle.value = totalAngle / (_totalReps.value ?: 1)
      
      // Store in database
      withContext(Dispatchers.IO) {
        db.sampleDao().insert(
          SampleEntity(
            timestamp = System.currentTimeMillis(),
            reps = 1,
            avgAngle = angle,
            errorsJson = gson.toJson(errors)
          )
        )
      }
      
      // Update RL model
      updateRL()
      
      // Update recommendations
      updateRecommendations()
    }
  }

  /**
   * Update RL model with recent samples
   */
  private fun updateRL() = viewModelScope.launch {
    val recents = withContext(Dispatchers.IO) {
      db.sampleDao().getRecent(BATCH)
    }
    if (recents.size < BATCH) return@launch

    val avg = recents.map { it.avgAngle }.average().roundToInt()
    val errs = recents.sumOf {
      gson.fromJson(it.errorsJson, Array<String>::class.java).size
    }

    val state = State(avg, errs)
    val action = rlAgent.selectAction(state)
    _difficulty.value = (_difficulty.value ?: 1) + action.coerceAtLeast(1)

    val reward = if (abs(avg - TARGET_ANGLE) < 5 && errs == 0) 1f else -1f
    rlAgent.update(state, action, reward, state)
  }

  /**
   * When user rates a workout
   */
  fun submitRating(workoutId: String, rating: Int) {
    viewModelScope.launch {
      withContext(Dispatchers.IO) {
        db.ratingDao().insert(RatingEntity(workoutId = workoutId, rating = rating))
      }
      updateRecommendations()
    }
  }

  /**
   * Update recommendations from the RS model
   */
  private fun updateRecommendations() = viewModelScope.launch {
    val recs = withContext(Dispatchers.Default) {
      recommender.recommend()
    }
    _recommendations.value = recs
  }
  
  /**
   * Initialize by loading recommendations
   */
  init {
    updateRecommendations()
  }
  
  /**
   * Reset statistics for a new workout
   */
  fun resetWorkoutStats() {
    _totalReps.value = 0
    _perfectReps.value = 0
    totalAngle = 0f
    _avgAngle.value = 0f
  }
}