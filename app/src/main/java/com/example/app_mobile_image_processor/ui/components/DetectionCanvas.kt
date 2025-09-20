package com.example.app_mobile_image_processor.ui.components

import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.example.app_mobile_image_processor.data.Detection
import com.example.app_mobile_image_processor.detector.ZoneDetector

@Composable
fun DetectionCanvas(
    imageWidth: Int,
    imageHeight: Int,
    zones: List<ZoneDetector.Zone>,
    detections: List<Detection>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        // Calcular factor de escala para ajustar al Canvas
        val scaleX = size.width / imageWidth
        val scaleY = size.height / imageHeight
        val scale = minOf(scaleX, scaleY)

        // Calcular offset para centrar la imagen
        val offsetX = (size.width - imageWidth * scale) / 2
        val offsetY = (size.height - imageHeight * scale) / 2

        // Dibujar zonas
        zones.forEach { zone ->
            drawZone(zone, scale, offsetX, offsetY)
        }

        // Dibujar detecciones
        detections.forEach { detection ->
            drawDetection(detection, scale, offsetX, offsetY)
        }
    }
}

private fun DrawScope.drawZone(
    zone: ZoneDetector.Zone,
    scale: Float,
    offsetX: Float,
    offsetY: Float
) {
    val rect = zone.rect
    val scaledRect = androidx.compose.ui.geometry.Rect(
        left = rect.left * scale + offsetX,
        top = rect.top * scale + offsetY,
        right = rect.right * scale + offsetX,
        bottom = rect.bottom * scale + offsetY
    )

    drawRect(
        color = zone.color.copy(alpha = 0.3f),
        topLeft = scaledRect.topLeft,
        size = scaledRect.size,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
    )

    // Dibujar etiqueta de zona
    drawIntoCanvas { canvas ->
        val paint = Paint().asFrameworkPaint().apply {
            color = zone.color.toArgb()
            textSize = 16.dp.toPx()
            isAntiAlias = true
        }

        canvas.nativeCanvas.drawText(
            zone.name,
            scaledRect.left,
            scaledRect.top - 10,
            paint
        )
    }
}

private fun DrawScope.drawDetection(
    detection: Detection,
    scale: Float,
    offsetX: Float,
    offsetY: Float
) {
    val rect = detection.boundingBox
    val scaledRect = androidx.compose.ui.geometry.Rect(
        left = rect.left * scale + offsetX,
        top = rect.top * scale + offsetY,
        right = rect.right * scale + offsetX,
        bottom = rect.bottom * scale + offsetY
    )

    // Dibujar bounding box
    drawRect(
        color = Color.Red,
        topLeft = scaledRect.topLeft,
        size = scaledRect.size,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
    )

    // Dibujar confianza
    drawIntoCanvas { canvas ->
        val paint = Paint().asFrameworkPaint().apply {
            color = Color.Red.toArgb()
            textSize = 14.dp.toPx()
            isAntiAlias = true
        }

        val confidenceText = "${(detection.confidence * 100).toInt()}%"
        canvas.nativeCanvas.drawText(
            confidenceText,
            scaledRect.left,
            scaledRect.top - 5,
            paint
        )
    }
}