package com.innova.yolo_app

// MainActivity.kt
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.YuvImage
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.innova.yolo_app.services.CameraViewModel
import com.innova.yolo_app.services.YOLOv8Detector
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import com.innova.yolo_app.services.CameraState
import com.innova.yolo_app.services.Detection

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    viewModel: CameraViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)

    // Inicializar detector
    LaunchedEffect(Unit) {
        val detector = YOLOv8Detector(context)
        viewModel.initializeDetector(detector)
    }

    if (cameraPermissionState.status.isGranted) {
        CameraContent(viewModel = viewModel)
    } else {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Se necesita permiso de cámara para detectar personas")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                Text("Otorgar permiso")
            }
        }
    }
}

@Composable
fun CameraContent(viewModel: CameraViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.state.collectAsState()

    var previewView: PreviewView? by remember { mutableStateOf(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Vista previa de la cámara
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).also { preview ->
                        previewView = preview
                        startCamera(ctx, lifecycleOwner, preview, viewModel)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (state.isProcessing) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        // Panel de información
        DetectionInfoPanel(state = state)

        // Botón de captura
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = {
                    previewView?.let { preview ->
                        captureImage(preview, viewModel)
                    }
                },
                modifier = Modifier.padding(16.dp),
                enabled = !state.isProcessing
            ) {
                Text("Capturar y Detectar")
            }
        }
    }
}

@Composable
fun DetectionInfoPanel(state: CameraState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Detecciones: ${state.detections.size}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Tiempo: ${state.processingTime}ms",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (state.detections.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.height(120.dp)
                ) {
                    items(state.detections.take(3)) { detection ->
                        DetectionItem(detection = detection)
                    }
                }
            }
        }
    }
}

@Composable
fun DetectionItem(detection: Detection) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            Text(
                text = "Persona - Confianza: ${String.format("%.2f", detection.confidence)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Coordenadas: (${detection.bbox.left.toInt()}, ${detection.bbox.top.toInt()}) - (${detection.bbox.right.toInt()}, ${detection.bbox.bottom.toInt()})",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun startCamera(
    context: android.content.Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    previewView: PreviewView,
    viewModel: CameraViewModel
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

    cameraProviderFuture.addListener({
        val cameraProvider = cameraProviderFuture.get()

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview
            )
        } catch (exc: Exception) {
            println("Error iniciando cámara: ${exc.message}")
        }
    }, ContextCompat.getMainExecutor(context))
}

private fun captureImage(previewView: PreviewView, viewModel: CameraViewModel) {
    // Capturar bitmap del preview
    val bitmap = previewView.getBitmap()
    bitmap?.let {
        viewModel.processImage(it)
    }
}