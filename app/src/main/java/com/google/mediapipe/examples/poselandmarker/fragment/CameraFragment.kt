package com.google.mediapipe.examples.poselandmarker.fragment

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.mediapipe.examples.poselandmarker.*
import com.google.mediapipe.examples.poselandmarker.databinding.FragmentCameraBinding
import com.google.mediapipe.examples.poselandmarker.databinding.InfoBottomSheetBinding
import com.google.mediapipe.examples.poselandmarker.stats.WorkoutStatsManager
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import com.google.mediapipe.examples.poselandmarker.R

class CameraFragment : Fragment(), PoseLandmarkerHelper.LandmarkerListener {

    companion object {
        private const val TAG = "Pose Landmarker"
    }

    private var _fragmentCameraBinding: FragmentCameraBinding? = null
    private val fragmentCameraBinding get() = _fragmentCameraBinding!!
    
    // Add a binding for the bottom sheet
    private var _bottomSheetBinding: InfoBottomSheetBinding? = null
    private val bottomSheetBinding get() = _bottomSheetBinding!!

    // Shared ViewModel from MainActivity
    private val viewModel: MainViewModel by activityViewModels()
    
    // Workout stats manager reference from MainActivity
    private var statsManager: WorkoutStatsManager? = null

    private lateinit var poseLandmarkerHelper: PoseLandmarkerHelper
    
    // Exercise feedback manager
    private lateinit var exerciseFeedbackManager: ExerciseFeedbackManager

    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null

    // Use front camera by default
    private var cameraFacing = CameraSelector.LENS_FACING_FRONT

    // Background executor for ML ops
    private lateinit var backgroundExecutor: ExecutorService

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentCameraBinding = FragmentCameraBinding.inflate(inflater, container, false)
        return fragmentCameraBinding.root
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize the bottom sheet binding properly
        val bottomSheetView = view.findViewById<View>(R.id.bottom_sheet_layout)
        if (bottomSheetView != null) {
            _bottomSheetBinding = InfoBottomSheetBinding.bind(bottomSheetView)
        } else {
            Log.e(TAG, "Bottom sheet layout not found!")
        }

        backgroundExecutor = Executors.newSingleThreadExecutor()

        // Get reference to the MainActivity's WorkoutStatsManager
        statsManager = (activity as? MainActivity)?.getWorkoutStatsManager()

        // Set up camera when view is ready
        fragmentCameraBinding.viewFinder.post {
            setUpCamera()
        }

        // Initialize PoseLandmarkerHelper on background thread
        backgroundExecutor.execute {
            poseLandmarkerHelper = PoseLandmarkerHelper(
                context = requireContext(),
                runningMode = RunningMode.LIVE_STREAM,
                minPoseDetectionConfidence = viewModel.currentMinPoseDetectionConfidence,
                minPoseTrackingConfidence = viewModel.currentMinPoseTrackingConfidence,
                minPosePresenceConfidence = viewModel.currentMinPosePresenceConfidence,
                currentDelegate = viewModel.currentDelegate,
                poseLandmarkerHelperListener = this
            )
        }

        // Initialize bottom sheet controls if binding is available
        if (_bottomSheetBinding != null) {
            initBottomSheetControls()
        }

        // Set up switch camera button
        fragmentCameraBinding.switchCamera.setOnClickListener {
            toggleCamera()
        }
        
        // Initialize the exercise stats overlay
        initExerciseOverlay()
        
        // Add FAB for workout stats
        fragmentCameraBinding.fabWorkoutStats.setOnClickListener {
            statsManager?.showWorkoutStats()
        }

        viewModel.settingsChanged.observe(viewLifecycleOwner) { changed ->
            if (changed) {
                updateCameraFromViewModel()
                viewModel.acknowledgeSettingsChanged()
            }
        }
        
        // Check if we need to toggle camera (from navigation arguments)
        arguments?.let {
            if (it.getBoolean("TOGGLE_CAMERA", false)) {
                toggleCamera()
                // Clear the argument after handling
                it.remove("TOGGLE_CAMERA")
            }
        }
    }

    // Make camera toggling public
    fun toggleCamera() {
        cameraFacing = if (cameraFacing == CameraSelector.LENS_FACING_FRONT)
            CameraSelector.LENS_FACING_BACK
        else
            CameraSelector.LENS_FACING_FRONT
        setUpCamera()
    }

    // Initialize the exercise stats overlay
    private fun initExerciseOverlay() {
        val exerciseStatsBinding = fragmentCameraBinding.exerciseStatsOverlay
        
        // Create the exercise feedback manager
        exerciseFeedbackManager = ExerciseFeedbackManager(
            overlayView = fragmentCameraBinding.overlay,
            repCountTextView = exerciseStatsBinding.tvRepCount,
            formFeedbackTextView = exerciseStatsBinding.tvFormFeedback,
            formTipTextView = exerciseStatsBinding.tvFormTip,
            angleTextView = exerciseStatsBinding.tvCurrentAngle,
            distanceTextView = exerciseStatsBinding.tvDistanceIndicator,
            repStageTextView = exerciseStatsBinding.tvRepStage,
            errorFlashView = exerciseStatsBinding.viewErrorFlash,
            cardFormFeedback = exerciseStatsBinding.cardFormFeedback
        )
        
        // Set the current exercise type
        exerciseFeedbackManager.exerciseType = viewModel.currentExerciseType
        
        // Update exercise name display
        exerciseStatsBinding.tvExerciseType.text = when(viewModel.currentExerciseType) {
            ExerciseType.BICEP -> "Bicep Curl"
            ExerciseType.SQUAT -> "Squat"
            ExerciseType.LATERAL_RAISE -> "Lateral Raise"
            ExerciseType.LUNGES -> "Lunges"
            ExerciseType.SHOULDER_PRESS -> "Shoulder Press"
        }
    }
    
    // Add this method to initialize bottom sheet controls
    private fun initBottomSheetControls() {
        // Safety check to ensure binding is available
        if (_bottomSheetBinding == null) return
        
        // Update the values in the bottom sheet
        bottomSheetBinding.detectionThresholdValue.text =
            String.format(Locale.US, "%.2f", viewModel.currentMinPoseDetectionConfidence)
        bottomSheetBinding.trackingThresholdValue.text =
            String.format(Locale.US, "%.2f", viewModel.currentMinPoseTrackingConfidence)
        bottomSheetBinding.presenceThresholdValue.text = 
            String.format(Locale.US, "%.2f", viewModel.currentMinPosePresenceConfidence)
            
        // Set model spinner
        bottomSheetBinding.spinnerModel.setSelection(viewModel.currentModel, false)
        bottomSheetBinding.spinnerModel.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    viewModel.setModel(position)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    // No-op
                }
            }
            
        // Set delegate spinner
        bottomSheetBinding.spinnerDelegate.setSelection(viewModel.currentDelegate, false)
        bottomSheetBinding.spinnerDelegate.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    viewModel.setDelegate(position)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    // No-op
                }
            }
            
        // Set exercise type spinner
        bottomSheetBinding.spinnerExercise.setSelection(viewModel.currentExerciseType.ordinal, false)
        bottomSheetBinding.spinnerExercise.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    viewModel.setExerciseType(ExerciseType.values()[position])
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    // No-op
                }
            }
            
        // Set threshold controllers
        bottomSheetBinding.detectionThresholdMinus.setOnClickListener {
            val threshold = viewModel.currentMinPoseDetectionConfidence - 0.1f
            if (threshold >= 0) {
                viewModel.setMinPoseDetectionConfidence(threshold)
                updateControlsUi()
            }
        }
        bottomSheetBinding.detectionThresholdPlus.setOnClickListener {
            val threshold = viewModel.currentMinPoseDetectionConfidence + 0.1f
            if (threshold <= 1) {
                viewModel.setMinPoseDetectionConfidence(threshold)
                updateControlsUi()
            }
        }
        bottomSheetBinding.trackingThresholdMinus.setOnClickListener {
            val threshold = viewModel.currentMinPoseTrackingConfidence - 0.1f
            if (threshold >= 0) {
                viewModel.setMinPoseTrackingConfidence(threshold)
                updateControlsUi()
            }
        }
        bottomSheetBinding.trackingThresholdPlus.setOnClickListener {
            val threshold = viewModel.currentMinPoseTrackingConfidence + 0.1f
            if (threshold <= 1) {
                viewModel.setMinPoseTrackingConfidence(threshold)
                updateControlsUi()
            }
        }
        bottomSheetBinding.presenceThresholdMinus.setOnClickListener {
            val threshold = viewModel.currentMinPosePresenceConfidence - 0.1f
            if (threshold >= 0) {
                viewModel.setMinPosePresenceConfidence(threshold)
                updateControlsUi()
            }
        }
        bottomSheetBinding.presenceThresholdPlus.setOnClickListener {
            val threshold = viewModel.currentMinPosePresenceConfidence + 0.1f
            if (threshold <= 1) {
                viewModel.setMinPosePresenceConfidence(threshold)
                updateControlsUi()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        backgroundExecutor.execute {
            if (this::poseLandmarkerHelper.isInitialized && poseLandmarkerHelper.isClose()) {
                poseLandmarkerHelper.setupPoseLandmarker()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (this::poseLandmarkerHelper.isInitialized) {
            viewModel.setMinPoseDetectionConfidence(poseLandmarkerHelper.minPoseDetectionConfidence)
            viewModel.setMinPoseTrackingConfidence(poseLandmarkerHelper.minPoseTrackingConfidence)
            viewModel.setMinPosePresenceConfidence(poseLandmarkerHelper.minPosePresenceConfidence)
            viewModel.setDelegate(poseLandmarkerHelper.currentDelegate)

            backgroundExecutor.execute { poseLandmarkerHelper.clearPoseLandmarker() }
        }
    }

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        _bottomSheetBinding = null
        super.onDestroyView()
        backgroundExecutor.shutdown()
        backgroundExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)
    }

    private fun updateControlsUi() {
        // Safety check to ensure binding is available
        if (_bottomSheetBinding == null) return
        
        bottomSheetBinding.detectionThresholdValue.text =
            String.format(Locale.US, "%.2f", poseLandmarkerHelper.minPoseDetectionConfidence)
        bottomSheetBinding.trackingThresholdValue.text =
            String.format(Locale.US, "%.2f", poseLandmarkerHelper.minPoseTrackingConfidence)
        bottomSheetBinding.presenceThresholdValue.text =
            String.format(Locale.US, "%.2f", poseLandmarkerHelper.minPosePresenceConfidence)

        backgroundExecutor.execute {
            poseLandmarkerHelper.clearPoseLandmarker()
            poseLandmarkerHelper.setupPoseLandmarker()
        }
        fragmentCameraBinding.overlay.clear()
        
        // Reset exercise feedback manager
        if (this::exerciseFeedbackManager.isInitialized) {
            exerciseFeedbackManager.resetCounters()
        }
    }

    private fun updateCameraFromViewModel() {
        if (this::poseLandmarkerHelper.isInitialized) {
            poseLandmarkerHelper.minPoseDetectionConfidence = viewModel.currentMinPoseDetectionConfidence
            poseLandmarkerHelper.minPoseTrackingConfidence = viewModel.currentMinPoseTrackingConfidence
            poseLandmarkerHelper.minPosePresenceConfidence = viewModel.currentMinPosePresenceConfidence
            poseLandmarkerHelper.currentDelegate = viewModel.currentDelegate
            poseLandmarkerHelper.currentModel = viewModel.currentModel
            
            if (this::exerciseFeedbackManager.isInitialized) {
                exerciseFeedbackManager.exerciseType = viewModel.currentExerciseType
            }
            
            // Restart the PoseLandmarkerHelper with new settings
            backgroundExecutor.execute {
                poseLandmarkerHelper.clearPoseLandmarker()
                poseLandmarkerHelper.setupPoseLandmarker()
            }
        }
    }

    private fun setUpCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return
        val cameraSelector = CameraSelector.Builder().requireLensFacing(cameraFacing).build()

        preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
            .build()

        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(backgroundExecutor) { imageProxy ->
                    detectPose(imageProxy)
                }
            }

        cameraProvider.unbindAll()
        try {
            camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageAnalyzer
            )
            preview?.setSurfaceProvider(fragmentCameraBinding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun detectPose(imageProxy: ImageProxy) {
        if (this::poseLandmarkerHelper.isInitialized) {
            poseLandmarkerHelper.detectLiveStream(
                imageProxy,
                cameraFacing == CameraSelector.LENS_FACING_FRONT
            )
        } else {
            imageProxy.close()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        imageAnalyzer?.targetRotation = fragmentCameraBinding.viewFinder.display.rotation
    }

    override fun onResults(resultBundle: PoseLandmarkerHelper.ResultBundle) {
        requireActivity().runOnUiThread {
            // Update inference time if bottom sheet binding exists
            if (_bottomSheetBinding != null) {
                bottomSheetBinding.inferenceTimeVal.text =
                    String.format("%d ms", resultBundle.inferenceTime)
            }
                
            // Set the exercise type for the overlay
            fragmentCameraBinding.overlay.setExerciseType(viewModel.currentExerciseType)
            
            // Update the overlay with pose landmarks
            fragmentCameraBinding.overlay.setResults(
                poseLandmarkerResults = resultBundle.results.first(),
                imageHeight = resultBundle.inputImageHeight,
                imageWidth = resultBundle.inputImageWidth,
                runningMode = RunningMode.LIVE_STREAM
            )
            
            // Process pose results with exercise feedback manager
            if (this@CameraFragment::exerciseFeedbackManager.isInitialized) {
                // Get proximity distance from overlay
                val proximityDistance = fragmentCameraBinding.overlay.getProximityDistance() 
                
                // Process pose results to update exercise feedback
                exerciseFeedbackManager.processPoseResults(
                    resultBundle.results.first(),
                    proximityDistance
                )
                
                // If a rep was completed, notify the ViewModel only if workout is active
                if (exerciseFeedbackManager.wasRepJustCompleted()) {
                    val angle = exerciseFeedbackManager.getCurrentAngle()
                    val hasErrors = exerciseFeedbackManager.getFormErrors().isNotEmpty()
                    
                    // Record the rep in the stats manager (which checks if workout is active)
                    statsManager?.recordRep(angle, hasErrors)
                }
            }
            
            fragmentCameraBinding.overlay.invalidate()
        }
    }

    override fun onError(error: String, errorCode: Int) {
        requireActivity().runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            if (errorCode == PoseLandmarkerHelper.GPU_ERROR && _bottomSheetBinding != null) {
                bottomSheetBinding.spinnerDelegate.setSelection(
                    PoseLandmarkerHelper.DELEGATE_CPU, false
                )
            }
        }
    }
}