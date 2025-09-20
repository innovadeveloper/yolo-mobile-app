package com.example.app_mobile_image_processor.data

import android.graphics.Rect

data class Detection(
    val boundingBox: Rect,
    val confidence: Float,
    val classId: Int,
    val className: String = getClassNameById(classId)
) {
    companion object {
        private fun getClassNameById(id: Int): String {
            val classes = arrayOf(
                "persona", "bicicleta", "auto", "motocicleta", "avion",
                "autobus", "tren", "camion", "barco", "semaforo"
            )
            return if (id < classes.size) classes[id] else "desconocido"
        }
    }

    override fun toString(): String {
        return "Detection(class=$className, conf=${String.format("%.2f", confidence)}, box=$boundingBox)"
    }
}