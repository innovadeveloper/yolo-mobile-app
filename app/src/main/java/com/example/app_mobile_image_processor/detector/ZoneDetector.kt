package com.example.app_mobile_image_processor.detector

import android.graphics.Rect
import androidx.compose.ui.graphics.Color
import com.example.app_mobile_image_processor.data.Detection

class ZoneDetector(private val imageWidth: Int, private val imageHeight: Int) {

    data class ZoneResult(
        val conductorDetected: Boolean,
        val passengerDetected: Boolean,
        val exchangeDetected: Boolean
    ) {
        override fun toString(): String {
            return "Conductor: ${if (conductorDetected) "SÍ" else "NO"}, " +
                    "Pasajero: ${if (passengerDetected) "SÍ" else "NO"}, " +
                    "Intercambio: ${if (exchangeDetected) "SÍ" else "NO"}"
        }
    }

    data class Zone(
        val rect: Rect,
        val color: Color,
        val name: String
    )

    val driverZone: Zone
    val passengerZone: Zone
    val exchangeZone: Zone

    init {
        // Zona conductor (lado izquierdo)
        driverZone = Zone(
            rect = Rect(
                (imageWidth * 0.1).toInt(),   // 10% desde izquierda
                (imageHeight * 0.2).toInt(),  // 20% desde arriba
                (imageWidth * 0.45).toInt(),  // hasta 45% del ancho
                (imageHeight * 0.8).toInt()   // hasta 80% de altura
            ),
            color = Color.Blue,
            name = "Conductor"
        )

        // Zona pasajero (lado derecho)
        passengerZone = Zone(
            rect = Rect(
                (imageWidth * 0.55).toInt(),  // 55% desde izquierda
                (imageHeight * 0.2).toInt(),  // 20% desde arriba
                (imageWidth * 0.9).toInt(),   // hasta 90% del ancho
                (imageHeight * 0.8).toInt()   // hasta 80% de altura
            ),
            color = Color.Green,
            name = "Pasajero"
        )

        // Zona de intercambio (centro)
        exchangeZone = Zone(
            rect = Rect(
                (imageWidth * 0.35).toInt(),  // 35% desde izquierda
                (imageHeight * 0.3).toInt(),  // 30% desde arriba
                (imageWidth * 0.65).toInt(),  // hasta 65% del ancho
                (imageHeight * 0.7).toInt()   // hasta 70% de altura
            ),
            color = Color.Red,
            name = "Intercambio"
        )
    }

    fun evaluateDetections(detections: List<Detection>): ZoneResult {
        var conductorDetected = false
        var passengerDetected = false
        var exchangeDetected = false

        for (detection in detections) {
            // Solo procesar personas con confianza > 50%
            if (detection.classId == 0 && detection.confidence > 0.5f) {
                val bbox = detection.boundingBox
                if (Rect.intersects(bbox, driverZone.rect)) {
                    conductorDetected = true
                }
                if (Rect.intersects(bbox, passengerZone.rect)) {
                    passengerDetected = true
                }
                if (Rect.intersects(bbox, exchangeZone.rect)) {
                    exchangeDetected = true
                }
            }
        }

        return ZoneResult(conductorDetected, passengerDetected, exchangeDetected)
    }

    fun getAllZones(): List<Zone> {
        return listOf(driverZone, passengerZone, exchangeZone)
    }

    fun getPersonDetections(detections: List<Detection>): List<Detection> {
        return detections.filter { it.classId == 0 && it.confidence > 0.5f }
    }
}