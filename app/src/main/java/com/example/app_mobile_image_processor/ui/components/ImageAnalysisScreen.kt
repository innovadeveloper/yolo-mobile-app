package com.example.app_mobile_image_processor.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.app_mobile_image_processor.R
import com.example.app_mobile_image_processor.data.Detection
import com.example.app_mobile_image_processor.detector.PersonDetector
import com.example.app_mobile_image_processor.detector.ZoneDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ImageAnalysisScreen() {
    val context = LocalContext.current
    var isInitialized by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var currentImageIndex by remember { mutableStateOf(0) }
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var detections by remember { mutableStateOf<List<Detection>>(emptyList()) }
    var zoneResult by remember { mutableStateOf<ZoneDetector.ZoneResult?>(null) }
    var zoneDetector by remember { mutableStateOf<ZoneDetector?>(null) }
    var resultText by remember { mutableStateOf("Inicializando detector...") }

    val personDetector = remember { PersonDetector(context) }

    // Imágenes de prueba desde drawable
    val testImages = listOf(
        R.drawable.test_image_001,
        R.drawable.test_image_002,
        R.drawable.test_image_003,
        R.drawable.test_image_004
    )

    // Inicializar detector
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val initialized = personDetector.initialize()
            withContext(Dispatchers.Main) {
                isInitialized = initialized
                resultText = if (initialized) {
                    "Detector inicializado. Listo para procesar."
                } else {
                    "Error inicializando detector."
                }
                // Cargar primera imagen
                loadImage(testImages[currentImageIndex], context) { bitmap ->
                    currentBitmap = bitmap
                    bitmap?.let {
                        zoneDetector = ZoneDetector(it.width, it.height)
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Mostrar imagen con overlay de detecciones
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            currentBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Imagen de análisis",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )

                // Overlay con zonas y detecciones
                if (detections.isNotEmpty() || zoneDetector != null) {
                    zoneDetector?.let { detector ->
                        DetectionCanvas(
                            imageWidth = bitmap.width,
                            imageHeight = bitmap.height,
                            zones = detector.getAllZones(),
                            detections = detections,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Botones de control
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (isInitialized && !isProcessing) {
                        processCurrentImage(
                            personDetector = personDetector,
                            bitmap = currentBitmap,
                            zoneDetector = zoneDetector,
                            onProcessing = { isProcessing = it },
                            onResult = { newDetections, newZoneResult, newResultText ->
                                detections = newDetections
                                zoneResult = newZoneResult
                                resultText = newResultText
                            }
                        )
                    }
                },
                enabled = isInitialized && !isProcessing && currentBitmap != null,
                modifier = Modifier.weight(1f)
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Procesar")
                }
            }

            Button(
                onClick = {
                    currentImageIndex = (currentImageIndex + 1) % testImages.size
                    loadImage(testImages[currentImageIndex], context) { bitmap ->
                        currentBitmap = bitmap
                        bitmap?.let {
                            zoneDetector = ZoneDetector(it.width, it.height)
                        }
                        // Limpiar resultados anteriores
                        detections = emptyList()
                        zoneResult = null
                        resultText = "Imagen ${currentImageIndex + 1} cargada. Presiona 'Procesar' para analizar."
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Siguiente")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Resultados
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = resultText,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }

    // Limpiar recursos al destruir
    DisposableEffect(Unit) {
        onDispose {
            personDetector.close()
        }
    }
}

private fun loadImage(
    imageRes: Int,
    context: android.content.Context,
    onLoaded: (Bitmap?) -> Unit
) {
    try {
        val bitmap = BitmapFactory.decodeResource(context.resources, imageRes)
        onLoaded(bitmap)
    } catch (e: Exception) {
        onLoaded(null)
    }
}

private fun processCurrentImage(
    personDetector: PersonDetector,
    bitmap: Bitmap?,
    zoneDetector: ZoneDetector?,
    onProcessing: (Boolean) -> Unit,
    onResult: (List<Detection>, ZoneDetector.ZoneResult?, String) -> Unit
) {
    val currentBitmap = bitmap ?: return
    val currentZoneDetector = zoneDetector ?: return

    onProcessing(true)

    // Ejecutar en hilo de fondo
    kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
        try {
            // Detectar personas
            val newDetections = personDetector.detectPersons(currentBitmap)

            // Evaluar zonas
            val newZoneResult = currentZoneDetector.evaluateDetections(newDetections)

            // Crear texto de resultado
            val resultBuilder = StringBuilder().apply {
                append("Resultados:\n")
                append("Detecciones: ${newDetections.size}\n")
                append("${newZoneResult}\n\n")
                append("Detalle:\n")
                newDetections.forEach { detection ->
                    append("${detection}\n")
                }
            }

            withContext(Dispatchers.Main) {
                onResult(newDetections, newZoneResult, resultBuilder.toString())
                onProcessing(false)
            }

        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onResult(emptyList(), null, "Error procesando imagen: ${e.message}")
                onProcessing(false)
            }
        }
    }
}