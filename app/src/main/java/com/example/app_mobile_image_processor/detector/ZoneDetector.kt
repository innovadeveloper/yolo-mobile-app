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

        android.util.Log.d("ZoneDetector", "=== EVALUANDO ${detections.size} DETECCIONES ===")
        android.util.Log.d("ZoneDetector", "Imagen: ${imageWidth}x${imageHeight}")
        android.util.Log.d("ZoneDetector", "Zona Conductor: ${driverZone.rect}")
        android.util.Log.d("ZoneDetector", "Zona Pasajero: ${passengerZone.rect}")
        android.util.Log.d("ZoneDetector", "Zona Intercambio: ${exchangeZone.rect}")

        for (detection in detections) {
            android.util.Log.d("ZoneDetector", "Detección: ${detection.className}, confianza: ${detection.confidence}, bbox: ${detection.boundingBox}")

            // Solo procesar personas con confianza > 30% (reducido para debug)
            if (detection.classId == 0 && detection.confidence > 0.3f) {
                val bbox = detection.boundingBox

                val intersectsConductor = Rect.intersects(bbox, driverZone.rect)
                val intersectsPasajero = Rect.intersects(bbox, passengerZone.rect)
                val intersectsIntercambio = Rect.intersects(bbox, exchangeZone.rect)

                android.util.Log.d("ZoneDetector", "  - Intersecciones: Conductor=$intersectsConductor, Pasajero=$intersectsPasajero, Intercambio=$intersectsIntercambio")

                if (intersectsConductor) {
                    conductorDetected = true
                    android.util.Log.d("ZoneDetector", "  ✓ CONDUCTOR DETECTADO!")
                }
                if (intersectsPasajero) {
                    passengerDetected = true
                    android.util.Log.d("ZoneDetector", "  ✓ PASAJERO DETECTADO!")
                }
                if (intersectsIntercambio) {
                    exchangeDetected = true
                    android.util.Log.d("ZoneDetector", "  ✓ INTERCAMBIO DETECTADO!")
                }
            } else {
                android.util.Log.d("ZoneDetector", "  - Ignorada: no es persona o confianza muy baja")
            }
        }

        android.util.Log.d("ZoneDetector", "=== RESULTADO FINAL ===")
        android.util.Log.d("ZoneDetector", "Conductor: $conductorDetected, Pasajero: $passengerDetected, Intercambio: $exchangeDetected")

        return ZoneResult(conductorDetected, passengerDetected, exchangeDetected)
    }

    fun getAllZones(): List<Zone> {
        return listOf(driverZone, passengerZone, exchangeZone)
    }

    fun getPersonDetections(detections: List<Detection>): List<Detection> {
        return detections.filter { it.classId == 0 && it.confidence > 0.5f }
    }
}