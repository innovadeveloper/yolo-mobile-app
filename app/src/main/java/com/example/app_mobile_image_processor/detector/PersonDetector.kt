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
        private const val INPUT_SIZE = 640 // YOLOv5 input size
        private const val CONFIDENCE_THRESHOLD = 0.5f
        private const val NMS_THRESHOLD = 0.45f
        private const val MODEL_FILE = "yolov5n.tflite"
    }

    private var tflite: Interpreter? = null

    suspend fun initialize(): Boolean {
        return try {
            val tfliteModel = loadModelFile()
            val options = Interpreter.Options().apply {
                numThreads = 4
            }
            tflite = Interpreter(tfliteModel, options)
            Log.d(TAG, "Modelo TensorFlow Lite cargado exitosamente")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error cargando modelo TensorFlow Lite", e)
            false
        }
    }

    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(MODEL_FILE)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun detectPersons(bitmap: Bitmap): List<Detection> {
        val interpreter = tflite ?: run {
            Log.e(TAG, "Modelo no inicializado")
            return emptyList()
        }

        // Redimensionar imagen
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)

        // Convertir a ByteBuffer
        val inputBuffer = convertBitmapToByteBuffer(resizedBitmap)

        // Preparar output
        val output = Array(1) { Array(25200) { FloatArray(85) } }

        // Ejecutar inferencia
        val startTime = System.currentTimeMillis()
        interpreter.run(inputBuffer, output)
        val inferenceTime = System.currentTimeMillis() - startTime

        Log.d(TAG, "Tiempo de inferencia: ${inferenceTime}ms")

        // Post-procesar resultados
        return postProcessDetections(output[0], bitmap.width, bitmap.height)
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

        for (prediction in predictions) {
            val confidence = prediction[4] // Confidence score

            if (confidence > CONFIDENCE_THRESHOLD) {
                // Encontrar clase con mayor probabilidad
                var bestClassId = 0
                var bestClassScore = prediction[5]

                for (i in 6 until prediction.size) {
                    if (prediction[i] > bestClassScore) {
                        bestClassScore = prediction[i]
                        bestClassId = i - 5
                    }
                }

                // Solo procesar si es persona (clase 0) y tiene buena confianza
                if (bestClassId == 0 && (confidence * bestClassScore) > CONFIDENCE_THRESHOLD) {
                    // Convertir coordenadas YOLO a coordenadas de imagen
                    val centerX = prediction[0] * scaleX
                    val centerY = prediction[1] * scaleY
                    val width = prediction[2] * scaleX
                    val height = prediction[3] * scaleY

                    // Crear bounding box
                    val left = max(0, (centerX - width / 2).toInt())
                    val top = max(0, (centerY - height / 2).toInt())
                    val right = min(originalWidth, (centerX + width / 2).toInt())
                    val bottom = min(originalHeight, (centerY + height / 2).toInt())

                    val boundingBox = Rect(left, top, right, bottom)
                    detections.add(Detection(boundingBox, confidence * bestClassScore, bestClassId))
                }
            }
        }

        Log.d(TAG, "Detecciones encontradas: ${detections.size}")
        return detections
    }

    fun close() {
        tflite?.close()
        tflite = null
    }
}