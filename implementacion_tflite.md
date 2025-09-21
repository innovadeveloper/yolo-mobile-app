# Importación e Inferencia de Modelos TensorFlow Lite en Android

## 1. Estructura Básica del Detector

```kotlin
class TensorFlowLiteDetector(private val context: Context) {
    private var interpreter: Interpreter? = null

    // Parámetros específicos del modelo
    private val inputSize = 320 // Debe coincidir con el tamaño de entrada del modelo
    private val outputSize = 84 * 2100 // [features, anchors] según arquitectura

    fun initialize() {
        try {
            val model = FileUtil.loadMappedFile(context, "modelo.tflite")
            interpreter = Interpreter(model)
        } catch (e: Exception) {
            println("Error cargando modelo: ${e.message}")
        }
    }
}
```

## 2. Configuración de Parámetros del Modelo

### 2.1 Tamaño de Entrada (Input Shape)
```kotlin
// Para modelos YOLO comunes:
val inputSize = 320  // YOLOv8n: 320x320
val inputSize = 416  // YOLOv5s: 416x416
val inputSize = 640  // YOLOv8s: 640x640

// El modelo espera: [batch_size, height, width, channels]
// Ejemplo: [1, 320, 320, 3] para RGB
```

### 2.2 Formato de Salida (Output Shape)
```kotlin
// YOLO v8 formato: [1, 84, 2100]
// 84 = 4 (bbox coords) + 80 (clases COCO)
// 2100 = número de anchors/detecciones posibles

// YOLO v5 formato: [1, 25200, 85]
// 85 = 4 (bbox) + 1 (objectness) + 80 (clases)
// 25200 = anchors para diferentes escalas

val outputSize = when(modelType) {
    "yolov8" -> 84 * 2100
    "yolov5" -> 25200 * 85
    "custom" -> features * anchors
}
```

### 2.3 Tipos de Datos
```kotlin
// Float32 (más preciso, mayor tamaño)
val buffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
buffer.order(ByteOrder.nativeOrder())

// Float16 (menor tamaño, ligeramente menos preciso)
val buffer = ByteBuffer.allocateDirect(2 * inputSize * inputSize * 3)

// Cuantizado INT8 (más rápido, menor tamaño)
val buffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3)
```

## 3. Preprocesamiento de Imagen

```kotlin
private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
    // 1. Redimensionar a la entrada requerida del modelo
    val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)

    val buffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
    buffer.order(ByteOrder.nativeOrder())

    val pixels = IntArray(inputSize * inputSize)
    resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

    for (pixel in pixels) {
        // Extraer RGB
        val r = ((pixel shr 16) and 0xFF)
        val g = ((pixel shr 8) and 0xFF)
        val b = (pixel and 0xFF)

        // Normalización según el modelo:
        when(normalizationType) {
            "0_1" -> {
                // [0,255] -> [0,1]
                buffer.putFloat(r / 255.0f)
                buffer.putFloat(g / 255.0f)
                buffer.putFloat(b / 255.0f)
            }
            "neg1_1" -> {
                // [0,255] -> [-1,1]
                buffer.putFloat((r / 127.5f) - 1.0f)
                buffer.putFloat((g / 127.5f) - 1.0f)
                buffer.putFloat((b / 127.5f) - 1.0f)
            }
            "imagenet" -> {
                // Normalización ImageNet
                buffer.putFloat((r - 123.68f) / 58.393f)
                buffer.putFloat((g - 116.78f) / 57.12f)
                buffer.putFloat((b - 103.94f) / 57.375f)
            }
        }
    }

    return buffer
}
```

## 4. Configuración de Salida según Arquitectura

### 4.1 YOLO v8
```kotlin
// Output shape: [1, 84, 2100]
val outputBuffer = Array(1) { Array(84) { FloatArray(2100) } }

private fun processYOLOv8Output(output: Array<FloatArray>): List<Detection> {
    val detections = mutableListOf<Detection>()

    for (i in 0 until output[0].size) {
        val xCenter = output[0][i]      // Centro X
        val yCenter = output[1][i]      // Centro Y
        val width = output[2][i]        // Ancho
        val height = output[3][i]       // Alto

        // Confianza de clase (máxima de las 80 clases)
        val classConfidences = (4 until 84).map { output[it][i] }
        val maxConfidence = classConfidences.maxOrNull() ?: 0f
        val classIndex = classConfidences.indexOf(maxConfidence)

        if (maxConfidence > threshold) {
            // Convertir de centro a esquinas
            val x1 = xCenter - width/2
            val y1 = yCenter - height/2
            val x2 = xCenter + width/2
            val y2 = yCenter + height/2

            detections.add(Detection(x1, y1, x2, y2, maxConfidence, classIndex))
        }
    }

    return detections
}
```

### 4.2 YOLO v5
```kotlin
// Output shape: [1, 25200, 85]
val outputBuffer = Array(1) { Array(25200) { FloatArray(85) } }

private fun processYOLOv5Output(output: Array<Array<FloatArray>>): List<Detection> {
    val detections = mutableListOf<Detection>()

    for (i in output[0].indices) {
        val detection = output[0][i]

        val xCenter = detection[0]
        val yCenter = detection[1]
        val width = detection[2]
        val height = detection[3]
        val objectness = detection[4]  // Confianza de objeto

        if (objectness > threshold) {
            // Encontrar clase con mayor confianza
            val classConfidences = detection.sliceArray(5 until 85)
            val maxConfidence = classConfidences.maxOrNull() ?: 0f
            val classIndex = classConfidences.indexOf(maxConfidence)

            // Confianza final = objectness * class_confidence
            val finalConfidence = objectness * maxConfidence

            if (finalConfidence > threshold) {
                detections.add(Detection(xCenter, yCenter, width, height, finalConfidence, classIndex))
            }
        }
    }

    return detections
}
```

## 5. Consideraciones por Tipo de Modelo

### 5.1 Modelos de Clasificación
```kotlin
// Input: [1, 224, 224, 3] (típico)
// Output: [1, num_classes]

val outputBuffer = Array(1) { FloatArray(numClasses) }
interpreter.run(inputBuffer, outputBuffer)

val probabilities = outputBuffer[0]
val maxIndex = probabilities.indexOf(probabilities.maxOrNull() ?: 0f)
```

### 5.2 Modelos de Segmentación
```kotlin
// Input: [1, height, width, 3]
// Output: [1, height, width, num_classes]

val outputBuffer = Array(1) { Array(height) { Array(width) { FloatArray(numClasses) } } }
```

### 5.3 Modelos Cuantizados
```kotlin
// Para modelos INT8 cuantizados
val inputBuffer = ByteBuffer.allocateDirect(inputSize * inputSize * 3)

for (pixel in pixels) {
    // Sin normalización, directamente bytes
    inputBuffer.put(((pixel shr 16) and 0xFF).toByte())
    inputBuffer.put(((pixel shr 8) and 0xFF).toByte())
    inputBuffer.put((pixel and 0xFF).toByte())
}
```

## 6. Configuración de Intérprete

```kotlin
fun initialize() {
    val options = Interpreter.Options().apply {
        // Usar GPU si está disponible
        useGpu = true

        // Número de hilos
        numThreads = 4

        // Usar NNAPI (Neural Networks API de Android)
        useNNAPI = true
    }

    val model = FileUtil.loadMappedFile(context, "modelo.tflite")
    interpreter = Interpreter(model, options)

    // Verificar dimensiones del modelo
    val inputShape = interpreter.getInputTensor(0).shape()
    val outputShape = interpreter.getOutputTensor(0).shape()

    println("Input shape: ${inputShape.contentToString()}")
    println("Output shape: ${outputShape.contentToString()}")
}
```

## 7. Lista de Verificación para Nuevos Modelos

- [ ] **Tamaño de entrada**: ¿320x320? ¿416x416? ¿640x640?
- [ ] **Formato de entrada**: RGB o BGR
- [ ] **Normalización**: [0,1], [-1,1], o ImageNet
- [ ] **Tipo de datos**: Float32, Float16, o INT8
- [ ] **Formato de salida**: ¿Centro+tamaño o esquinas?
- [ ] **Número de clases**: ¿80 (COCO)? ¿Custom?
- [ ] **Umbral de confianza**: Típicamente 0.5-0.7
- [ ] **NMS IoU threshold**: Típicamente 0.45
- [ ] **Arquitectura específica**: YOLO v5/v8, EfficientDet, etc.

## 8. Ejemplo de Inferencia Completa

```kotlin
fun detect(bitmap: Bitmap): List<Detection> {
    val interpreter = this.interpreter ?: return emptyList()

    // 1. Preprocesar
    val inputBuffer = preprocessImage(bitmap)

    // 2. Preparar salida según arquitectura del modelo
    val outputBuffer = Array(1) { Array(84) { FloatArray(2100) } }

    // 3. Ejecutar inferencia
    interpreter.run(inputBuffer, outputBuffer)

    // 4. Postprocesar según formato del modelo
    val rawDetections = processOutput(outputBuffer[0], bitmap.width, bitmap.height)

    // 5. Aplicar NMS
    return applyNMS(rawDetections, iouThreshold = 0.45f)
}
```

Esta documentación cubre los aspectos esenciales para importar cualquier modelo TensorFlow Lite, adaptando los parámetros según la arquitectura específica del modelo que se desee utilizar.