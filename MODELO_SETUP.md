# Configuración del Modelo YOLOv5

## Modelo Actual: yolov5s_f16.tflite

El proyecto está configurado para usar el modelo `yolov5s_f16.tflite`.

## Verificar la instalación
El archivo debe estar ubicado en:
```
app/src/main/assets/yolov5s_f16.tflite
```

## Información del modelo
- Archivo: yolov5s_f16.tflite
- Tipo: YOLOv5s con precisión float16
- Tamaño esperado: ~14 MB (aprox)
- Input size: 640x640

## Si necesitas cambiar el modelo
Para usar un modelo diferente, modifica la constante `MODEL_FILE` en:
```kotlin
// En PersonDetector.kt
private const val MODEL_FILE = "tu_modelo.tflite"
```

## Nota importante
El modelo debe estar en la carpeta `app/src/main/assets/` para que el código funcione correctamente.