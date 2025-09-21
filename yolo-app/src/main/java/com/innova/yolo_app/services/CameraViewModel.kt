package com.innova.yolo_app.services

// CameraViewModel.kt
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import android.graphics.Bitmap

data class CameraState(
    val bitmap: Bitmap? = null,
    val detections: List<Detection> = emptyList(),
    val isProcessing: Boolean = false,
    val processingTime: Long = 0L
)

class CameraViewModel : ViewModel() {
    private val _state = MutableStateFlow(CameraState())
    val state: StateFlow<CameraState> = _state

    private lateinit var detector: YOLOv8Detector

    fun initializeDetector(detector: YOLOv8Detector) {
        this.detector = detector
        detector.initialize()
    }

    fun processImage(bitmap: Bitmap) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isProcessing = true)

            val startTime = System.currentTimeMillis()
            val detections = detector.detect(bitmap)
            val processingTime = System.currentTimeMillis() - startTime

            _state.value = _state.value.copy(
                bitmap = bitmap,
                detections = detections,
                isProcessing = false,
                processingTime = processingTime
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (::detector.isInitialized) {
            detector.close()
        }
    }
}