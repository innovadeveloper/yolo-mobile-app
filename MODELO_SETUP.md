# Configuración del Modelo YOLOv5

## Descargar el modelo YOLOv5n.tflite

Necesitas descargar el modelo YOLOv5n en formato TensorFlow Lite y colocarlo en la carpeta assets.

### Opción 1: Descarga directa desde Ultralytics
```bash
# Navega al directorio assets
cd app/src/main/assets/

# Descarga el modelo YOLOv5n.tflite
curl -L -o yolov5n.tflite "https://github.com/ultralytics/yolov5/releases/download/v7.0/yolov5n.tflite"
```

### Opción 2: Generar desde Python
Si tienes Python y ultralytics instalado:

```python
from ultralytics import YOLO

# Cargar modelo YOLOv5n
model = YOLO('yolov5n.pt')

# Exportar a TensorFlow Lite
model.export(format='tflite')
```

### Opción 3: Descarga manual
1. Ve a: https://github.com/ultralytics/yolov5/releases/tag/v7.0
2. Descarga `yolov5n.tflite`
3. Colócalo en `app/src/main/assets/yolov5n.tflite`

## Verificar la instalación
El archivo debe estar ubicado en:
```
app/src/main/assets/yolov5n.tflite
```

## Tamaño esperado
- yolov5n.tflite: ~7.5 MB

## Nota importante
El modelo debe llamarse exactamente `yolov5n.tflite` para que el código funcione correctamente.