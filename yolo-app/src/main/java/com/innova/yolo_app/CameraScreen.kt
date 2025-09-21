package com.innova.yolo_app

// MainActivity.kt
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.YuvImage
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        // Vista previa de la cámara o imagen capturada
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (state.isShowingCapturedImage && state.bitmap != null) {
                // Mostrar imagen capturada con detecciones
                state?.bitmap?.asImageBitmap()?.let {
                    Image(
                        bitmap = it,
                        contentDescription = "Imagen capturada",
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Overlay de detecciones sobre la imagen capturada
                DetectionOverlay(
                    detections = state.detections,
                    bitmap = state.bitmap,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Mostrar cámara en vivo
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).also { preview ->
                            previewView = preview
                            startCamera(ctx, lifecycleOwner, preview, viewModel)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

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

        // Botón de captura/activar cámara
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = {
                    if (state.isShowingCapturedImage) {
                        viewModel.returnToLiveCamera()
                    } else {
                        previewView?.let { preview ->
                            captureImage(preview, viewModel)
                        }
                    }
                },
                modifier = Modifier.padding(16.dp),
                enabled = !state.isProcessing
            ) {
                Text(
                    if (state.isShowingCapturedImage) "Activar Cámara"
                    else "Capturar y Detectar"
                )
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
                    items(state.detections.take(3).withIndex().toList()) { (index, detection) ->
                        DetectionItem(detection = detection, personNumber = index + 1)
                    }
                }
            }
        }
    }
}

@Composable
fun DetectionItem(detection: Detection, personNumber: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            Text(
                text = "Persona $personNumber - Confianza: ${String.format("%.2f", detection.confidence)}",
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

@Composable
fun DetectionOverlay(
    detections: List<Detection>,
    bitmap: Bitmap?,
    modifier: Modifier = Modifier
) {
    if (detections.isEmpty() || bitmap == null) return

    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // Calcular factores de escala entre el bitmap original y el canvas
        val scaleX = canvasWidth / bitmap.width
        val scaleY = canvasHeight / bitmap.height

        detections.forEachIndexed { index, detection ->
            // Escalar las coordenadas del bounding box al tamaño del canvas
            val scaledLeft = detection.bbox.left * scaleX
            val scaledTop = detection.bbox.top * scaleY
            val scaledRight = detection.bbox.right * scaleX
            val scaledBottom = detection.bbox.bottom * scaleY

            // Dibujar rectángulo
            drawRect(
                color = Color.Green,
                topLeft = Offset(scaledLeft, scaledTop),
                size = Size(
                    scaledRight - scaledLeft,
                    scaledBottom - scaledTop
                ),
                style = Stroke(width = 3.dp.toPx())
            )

            // Dibujar etiqueta con numeración y confianza
            val personNumber = index + 1
            val label = "Persona $personNumber: ${String.format("%.2f", detection.confidence)}"
            val textSizeSp = 16.sp.toPx() // Aumentado de 14sp a 18sp

            // Fondo para el texto con más altura y ancho
            val textWidth = label.length * textSizeSp * 0.7f
            val textHeight = textSizeSp * 1.5f

            // Fondo con borde negro para mayor contraste
            drawRect(
                color = Color.Black,
                topLeft = Offset(scaledLeft - 2f, scaledTop - textHeight - 2f),
                size = Size(textWidth + 4f, textHeight + 4f)
            )

            drawRect(
                color = Color.Green,
                topLeft = Offset(scaledLeft, scaledTop - textHeight),
                size = Size(textWidth, textHeight)
            )

            // Texto más grande y con mejor contraste
            drawIntoCanvas { canvas ->
                val paint = Paint().asFrameworkPaint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = textSizeSp
                    isAntiAlias = true
                    isFakeBoldText = true // Hacer el texto en negrita
                    setShadowLayer(2f, 1f, 1f, android.graphics.Color.BLACK) // Sombra para mejor legibilidad
                }

                canvas.nativeCanvas.drawText(
                    label,
                    scaledLeft + 4f, // Pequeño padding
                    scaledTop - textHeight * 0.3f,
                    paint
                )
            }
        }
    }
}

private fun captureImage(previewView: PreviewView, viewModel: CameraViewModel) {
    // Capturar bitmap del preview
    val bitmap = previewView.getBitmap()
    bitmap?.let {
        viewModel.processImage(it)
    }
}