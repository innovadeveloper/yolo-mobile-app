package com.innova.yolo_app.services

// YOLOv8Detector.kt
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

data class Detection(
    val bbox: RectF,
    val confidence: Float,
    val className: String = "person"
)

class YOLOv8Detector(private val context: Context) {
    private var interpreter: Interpreter? = null
    private val inputSize = 320
    private val outputSize = 84 * 2100 // [84, 2100] como en tu código Python

    private val paint = Paint().apply {
        color = android.graphics.Color.GREEN
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    private val textPaint = Paint().apply {
        color = android.graphics.Color.GREEN
        textSize = 40f
        style = Paint.Style.FILL
    }

    fun initialize() {
        try {
            val model = FileUtil.loadMappedFile(context, "yolov8n_float16.tflite")
            interpreter = Interpreter(model)
            println("✅ Modelo TFLite cargado correctamente")
        } catch (e: Exception) {
            println("❌ Error cargando modelo: ${e.message}")
        }
    }

    fun detect(bitmap: Bitmap, threshold: Float = 0.6f): List<Detection> {
        val interpreter = this.interpreter ?: return emptyList()

        // 1. Preprocesar imagen
        val inputBuffer = preprocessImage(bitmap)

        // 2. Preparar output buffer
        val outputBuffer = Array(1) { Array(84) { FloatArray(2100) } }

        // 3. Ejecutar inferencia
        interpreter.run(inputBuffer, outputBuffer)

        // 4. Procesar detecciones
        return processDetections(
            outputBuffer[0],
            bitmap.width,
            bitmap.height,
            threshold
        )
    }

    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        // Redimensionar a 320x320
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)

        val buffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        buffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(inputSize * inputSize)
        resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        // Normalizar [0,255] -> [0,1]
        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f

            buffer.putFloat(r)
            buffer.putFloat(g)
            buffer.putFloat(b)
        }

        return buffer
    }

    private fun processDetections(
        output: Array<FloatArray>,
        originalWidth: Int,
        originalHeight: Int,
        threshold: Float
    ): List<Detection> {
        val rawDetections = mutableListOf<Detection>()

        // Extraer detecciones brutas (igual que tu código Python)
        for (i in 0 until output[0].size) {
            val xCenter = output[0][i]
            val yCenter = output[1][i]
            val width = output[2][i]
            val height = output[3][i]
            val objectness = output[4][i]

            if (objectness > threshold) {
                // Convertir coordenadas (centro -> esquinas)
                val x1 = ((xCenter - width/2) * originalWidth / inputSize).toInt()
                val y1 = ((yCenter - height/2) * originalHeight / inputSize).toInt()
                val x2 = ((xCenter + width/2) * originalWidth / inputSize).toInt()
                val y2 = ((yCenter + height/2) * originalHeight / inputSize).toInt()

                // Aplicar límites
                val clampedX1 = max(0, min(x1, originalWidth))
                val clampedY1 = max(0, min(y1, originalHeight))
                val clampedX2 = max(0, min(x2, originalWidth))
                val clampedY2 = max(0, min(y2, originalHeight))

                if (clampedX2 > clampedX1 && clampedY2 > clampedY1) {
                    rawDetections.add(
                        Detection(
                            bbox = RectF(
                                clampedX1.toFloat(),
                                clampedY1.toFloat(),
                                clampedX2.toFloat(),
                                clampedY2.toFloat()
                            ),
                            confidence = objectness
                        )
                    )
                }
            }
        }

        // Aplicar NMS
        return applyNMS(rawDetections, 0.45f)
    }

    private fun applyNMS(detections: List<Detection>, iouThreshold: Float): List<Detection> {
        if (detections.isEmpty()) return emptyList()

        // Ordenar por confianza descendente
        val sorted = detections.sortedByDescending { it.confidence }
        val keep = mutableListOf<Detection>()
        val areas = sorted.map {
            (it.bbox.right - it.bbox.left) * (it.bbox.bottom - it.bbox.top)
        }

        val indices = sorted.indices.toMutableList()

        while (indices.isNotEmpty()) {
            val i = indices.removeAt(0)
            keep.add(sorted[i])

            val toRemove = mutableListOf<Int>()

            for (j in indices) {
                val iou = calculateIoU(sorted[i].bbox, sorted[j].bbox, areas[i], areas[j])
                if (iou > iouThreshold) {
                    toRemove.add(j)
                }
            }

            indices.removeAll(toRemove)
        }

        return keep
    }

    private fun calculateIoU(box1: RectF, box2: RectF, area1: Float, area2: Float): Float {
        val x1 = max(box1.left, box2.left)
        val y1 = max(box1.top, box2.top)
        val x2 = min(box1.right, box2.right)
        val y2 = min(box1.bottom, box2.bottom)

        if (x2 <= x1 || y2 <= y1) return 0f

        val intersection = (x2 - x1) * (y2 - y1)
        val union = area1 + area2 - intersection

        return intersection / union
    }

    fun drawDetections(bitmap: Bitmap, detections: List<Detection>): Bitmap {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)

        detections.forEach { detection ->
            // Dibujar rectángulo
            canvas.drawRect(detection.bbox, paint)

            // Dibujar texto de confianza
            val text = "Person: ${String.format("%.2f", detection.confidence)}"
            canvas.drawText(
                text,
                detection.bbox.left,
                detection.bbox.top - 10f,
                textPaint
            )
        }

        return mutableBitmap
    }

    fun close() {
        interpreter?.close()
    }
}