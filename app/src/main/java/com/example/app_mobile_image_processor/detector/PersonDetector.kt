package com.example.app_mobile_image_processor.detector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.example.app_mobile_image_processor.data.Detection
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min

class PersonDetector(private val context: Context) {

    companion object {
        private const val TAG = "PersonDetector"
        private var INPUT_SIZE = 640 // YOLOv5 input size (se ajustará automáticamente)
        private const val CONFIDENCE_THRESHOLD = 0.25f
        private const val NMS_THRESHOLD = 0.45f
        private const val MODEL_FILE = "yolov5s_f16.tflite"
    }

    private var tflite: Interpreter? = null

    suspend fun initialize(): Boolean {
        return try {
            val tfliteModel = loadModelFile()
            if (tfliteModel == null) {
                Log.w(TAG, "Modelo no encontrado, funcionando en modo demo")
                return true // Permitir modo demo sin modelo
            }
            val options = Interpreter.Options().apply {
                numThreads = 4
            }
            tflite = Interpreter(tfliteModel, options)

            // Detectar dimensiones del modelo automáticamente
            val inputTensor = tflite!!.getInputTensor(0)
            val inputShape = inputTensor.shape()
            Log.d(TAG, "Forma del tensor de entrada: ${inputShape.contentToString()}")

            if (inputShape.size >= 3) {
                INPUT_SIZE = inputShape[1] // Asume formato [batch, height, width, channels]
                Log.d(TAG, "INPUT_SIZE detectado automáticamente: $INPUT_SIZE")
            }

            // También revisar dimensiones de salida
            val outputTensor = tflite!!.getOutputTensor(0)
            val outputShape = outputTensor.shape()
            Log.d(TAG, "Forma del tensor de salida: ${outputShape.contentToString()}")

            Log.d(TAG, "Modelo TensorFlow Lite cargado exitosamente")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error cargando modelo TensorFlow Lite", e)
            false
        }
    }

    private fun loadModelFile(): MappedByteBuffer? {
        return try {
            val fileDescriptor = context.assets.openFd(MODEL_FILE)
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        } catch (e: Exception) {
            Log.w(TAG, "Modelo TensorFlow Lite no encontrado: $MODEL_FILE")
            null
        }
    }

    fun detectPersons(bitmap: Bitmap): List<Detection> {
        Log.d(TAG, "=== INICIANDO DETECCIÓN ===")
        Log.d(TAG, "Imagen: ${bitmap.width}x${bitmap.height}")

        val interpreter = tflite
        if (interpreter == null) {
            Log.w(TAG, "Modelo no disponible, generando detecciones de demo")
            return generateDemoDetections(bitmap)
        }

        Log.d(TAG, "Modelo TensorFlow Lite disponible")

        // Redimensionar imagen
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        Log.d(TAG, "Imagen redimensionada a: ${resizedBitmap.width}x${resizedBitmap.height}")

        // Convertir a ByteBuffer
        val inputBuffer = convertBitmapToByteBuffer(resizedBitmap)
        Log.d(TAG, "ByteBuffer creado, tamaño: ${inputBuffer.capacity()}")

        // Preparar output dinámicamente basado en las dimensiones del modelo
        val outputTensor = interpreter.getOutputTensor(0)
        val outputShape = outputTensor.shape()
        Log.d(TAG, "Dimensiones de salida del modelo: ${outputShape.contentToString()}")

        val output = if (outputShape.size == 3) {
            Array(outputShape[0]) { Array(outputShape[1]) { FloatArray(outputShape[2]) } }
        } else {
            // Fallback para formato estándar
            Array(1) { Array(25200) { FloatArray(85) } }
        }
        Log.d(TAG, "Array de output preparado con dimensiones detectadas")

        // Ejecutar inferencia
        val startTime = System.currentTimeMillis()
        try {
            interpreter.run(inputBuffer, output)
            val inferenceTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "Inferencia completada en: ${inferenceTime}ms")

            // Debug: revisar primeras predicciones
            Log.d(TAG, "Primeras 3 predicciones:")
            for (i in 0..2) {
                val pred = output[0][i]
                Log.d(TAG, "  Pred $i: conf=${pred[4]}, x=${pred[0]}, y=${pred[1]}, w=${pred[2]}, h=${pred[3]}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error durante inferencia", e)
            return emptyList()
        }

        // Post-procesar resultados
        val detections = postProcessDetections(output[0], bitmap.width, bitmap.height)
        Log.d(TAG, "Post-procesamiento completado: ${detections.size} detecciones")

        return detections
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3)
        byteBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        var pixel = 0
        for (i in 0 until INPUT_SIZE) {
            for (j in 0 until INPUT_SIZE) {
                val value = intValues[pixel++]

                // Normalizar a [0,1] y reordenar a RGB
                byteBuffer.putFloat(((value shr 16) and 0xFF) / 255.0f) // R
                byteBuffer.putFloat(((value shr 8) and 0xFF) / 255.0f)  // G
                byteBuffer.putFloat((value and 0xFF) / 255.0f)          // B
            }
        }

        return byteBuffer
    }

    private fun postProcessDetections(predictions: Array<FloatArray>, originalWidth: Int, originalHeight: Int): List<Detection> {
        val detections = mutableListOf<Detection>()

        // Factores de escala para convertir coordenadas
        val scaleX = originalWidth.toFloat() / INPUT_SIZE
        val scaleY = originalHeight.toFloat() / INPUT_SIZE

        Log.d(TAG, "=== POST-PROCESAMIENTO ===")
        Log.d(TAG, "Escalas: scaleX=$scaleX, scaleY=$scaleY")
        Log.d(TAG, "Umbral de confianza: $CONFIDENCE_THRESHOLD")

        var validPredictions = 0
        var personPredictions = 0

        for (i in predictions.indices) {
            val prediction = predictions[i]
            val confidence = prediction[4] // Confidence score

            if (confidence > 0.1f) { // Umbral muy bajo para debug
                validPredictions++

                // Encontrar clase con mayor probabilidad
                var bestClassId = 0
                var bestClassScore = prediction[5]

                for (j in 6 until prediction.size) {
                    if (prediction[j] > bestClassScore) {
                        bestClassScore = prediction[j]
                        bestClassId = j - 5
                    }
                }

                val finalConfidence = confidence * bestClassScore

                if (bestClassId == 0) {
                    personPredictions++
                    if (finalConfidence > 0.1f) {
                        Log.d(TAG, "Persona #$personPredictions: conf=$confidence, classScore=$bestClassScore, final=$finalConfidence")
                        Log.d(TAG, "  Coordenadas: x=${prediction[0]}, y=${prediction[1]}, w=${prediction[2]}, h=${prediction[3]}")
                    }
                }

                // Solo procesar si es persona (clase 0) y tiene buena confianza
                if (bestClassId == 0 && finalConfidence > CONFIDENCE_THRESHOLD) {
                    // Log coordenadas originales del modelo
                    Log.d(TAG, "=== CONVERSIÓN DE COORDENADAS ===")
                    Log.d(TAG, "Coordenadas modelo: x=${prediction[0]}, y=${prediction[1]}, w=${prediction[2]}, h=${prediction[3]}")
                    Log.d(TAG, "Imagen original: ${originalWidth}x${originalHeight}")
                    Log.d(TAG, "Escalas: scaleX=$scaleX, scaleY=$scaleY")

                    // Las coordenadas ya están normalizadas (0-1), escalar directamente por dimensiones originales
                    val centerX = prediction[0] * originalWidth
                    val centerY = prediction[1] * originalHeight
                    val width = prediction[2] * originalWidth
                    val height = prediction[3] * originalHeight

                    Log.d(TAG, "Coordenadas escaladas: centerX=$centerX, centerY=$centerY, w=$width, h=$height")

                    // Crear bounding box
                    val left = max(0, (centerX - width / 2).toInt())
                    val top = max(0, (centerY - height / 2).toInt())
                    val right = min(originalWidth, (centerX + width / 2).toInt())
                    val bottom = min(originalHeight, (centerY + height / 2).toInt())

                    Log.d(TAG, "Bounding box final: left=$left, top=$top, right=$right, bottom=$bottom")

                    val boundingBox = Rect(left, top, right, bottom)
                    detections.add(Detection(boundingBox, finalConfidence, bestClassId))

                    Log.d(TAG, "Detección válida añadida: bbox=$boundingBox, conf=$finalConfidence")
                }
            }
        }

        Log.d(TAG, "Predicciones con confianza > 0.1: $validPredictions")
        Log.d(TAG, "Predicciones de personas: $personPredictions")
        Log.d(TAG, "Detecciones antes de NMS: ${detections.size}")

        // Aplicar Non-Maximum Suppression
        val finalDetections = applyNMS(detections)
        Log.d(TAG, "Detecciones después de NMS: ${finalDetections.size}")

        return finalDetections
    }

    private fun generateDemoDetections(bitmap: Bitmap): List<Detection> {
        val demoDetections = mutableListOf<Detection>()

        Log.d(TAG, "Generando detecciones de demo para imagen ${bitmap.width}x${bitmap.height}")

        // Generar algunas detecciones de demostración en diferentes zonas
        // Zona conductor (izquierda)
        demoDetections.add(
            Detection(
                boundingBox = Rect(
                    (bitmap.width * 0.15).toInt(),
                    (bitmap.height * 0.3).toInt(),
                    (bitmap.width * 0.35).toInt(),
                    (bitmap.height * 0.7).toInt()
                ),
                confidence = 0.85f,
                classId = 0
            )
        )

        // Zona pasajero (derecha)
        demoDetections.add(
            Detection(
                boundingBox = Rect(
                    (bitmap.width * 0.6).toInt(),
                    (bitmap.height * 0.25).toInt(),
                    (bitmap.width * 0.8).toInt(),
                    (bitmap.height * 0.65).toInt()
                ),
                confidence = 0.75f,
                classId = 0
            )
        )

        Log.d(TAG, "Detecciones de demo generadas: ${demoDetections.size}")
        return demoDetections
    }

    private fun applyNMS(detections: List<Detection>): List<Detection> {
        if (detections.isEmpty()) return detections

        // Ordenar por confianza (mayor primero)
        val sortedDetections = detections.sortedByDescending { it.confidence }
        val finalDetections = mutableListOf<Detection>()

        for (detection in sortedDetections) {
            var shouldKeep = true

            // Verificar solapamiento con detecciones ya seleccionadas
            for (selectedDetection in finalDetections) {
                val iou = calculateIoU(detection.boundingBox, selectedDetection.boundingBox)
                if (iou > NMS_THRESHOLD) {
                    shouldKeep = false
                    Log.d(TAG, "NMS: Eliminando detección con IoU=$iou")
                    break
                }
            }

            if (shouldKeep) {
                finalDetections.add(detection)
                Log.d(TAG, "NMS: Manteniendo detección con confianza=${detection.confidence}")
            }
        }

        return finalDetections
    }

    private fun calculateIoU(rect1: Rect, rect2: Rect): Float {
        val intersectionArea = maxOf(0, minOf(rect1.right, rect2.right) - maxOf(rect1.left, rect2.left)) *
                maxOf(0, minOf(rect1.bottom, rect2.bottom) - maxOf(rect1.top, rect2.top))

        val rect1Area = (rect1.right - rect1.left) * (rect1.bottom - rect1.top)
        val rect2Area = (rect2.right - rect2.left) * (rect2.bottom - rect2.top)
        val unionArea = rect1Area + rect2Area - intersectionArea

        return if (unionArea > 0) intersectionArea.toFloat() / unionArea.toFloat() else 0f
    }

    fun close() {
        tflite?.close()
        tflite = null
    }
}