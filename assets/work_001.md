## Configuración del proyecto (build.gradle)

```gradle
android {
    compileSdk 34
    
    defaultConfig {
        targetSdkVersion 34
        minSdkVersion 24
    }
    
    aaptOptions {
        noCompress "tflite"
    }
}

dependencies {
    // TensorFlow Lite
    implementation 'org.tensorflow:tensorflow-lite:2.13.0'
    implementation 'org.tensorflow:tensorflow-lite-gpu:2.13.0'
    implementation 'org.tensorflow:tensorflow-lite-support:0.4.4'
    
    // Para manipulación de imágenes
    implementation 'androidx.exifinterface:exifinterface:1.3.6'
    
    // UI
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.9.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
}
```

## Estructura del proyecto

```
app/src/main/
├── assets/
│   └── yolov5n.tflite          // Modelo descargado
├── res/
│   └── drawable/
│       ├── test_image1.jpg     // Tus fotos de prueba
│       ├── test_image2.jpg
│       └── test_image3.jpg
└── java/com/tuapp/
    ├── MainActivity.java
    ├── DetectorPersonas.java
    ├── ZonasDeteccion.java
    └── models/
        └── Detection.java
```

## 1. Clase Detection (modelo de datos)

```java
// models/Detection.java
package com.tuapp.models;

import android.graphics.Rect;

public class Detection {
    private Rect boundingBox;
    private float confidence;
    private int classId;
    private String className;
    
    public Detection(Rect boundingBox, float confidence, int classId) {
        this.boundingBox = boundingBox;
        this.confidence = confidence;
        this.classId = classId;
        this.className = getClassNameById(classId);
    }
    
    private String getClassNameById(int id) {
        // COCO classes - 0 = persona
        String[] classes = {"persona", "bicicleta", "auto", "motocicleta", "avion"};
        return id < classes.length ? classes[id] : "desconocido";
    }
    
    // Getters
    public Rect getBoundingBox() { return boundingBox; }
    public float getConfidence() { return confidence; }
    public int getClassId() { return classId; }
    public String getClassName() { return className; }
    
    @Override
    public String toString() {
        return String.format("Detection{class=%s, conf=%.2f, box=%s}", 
                           className, confidence, boundingBox.toString());
    }
}
```

## 2. Zonas de detección

```java
// ZonasDeteccion.java
package com.tuapp;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import com.tuapp.models.Detection;
import java.util.List;

public class ZonasDeteccion {
    private Rect zonaConductor;
    private Rect zonaPasajero;
    private Rect zonaIntercambio;
    private Paint paintZona;
    private Paint paintDeteccion;
    
    public ZonasDeteccion(int imageWidth, int imageHeight) {
        inicializarZonas(imageWidth, imageHeight);
        configurarPaints();
    }
    
    private void inicializarZonas(int width, int height) {
        // Ajusta estos valores según tu disposición de cámara
        // Zona conductor (lado izquierdo)
        zonaConductor = new Rect(
            (int)(width * 0.1),   // 10% desde izquierda
            (int)(height * 0.2),  // 20% desde arriba
            (int)(width * 0.45),  // hasta 45% del ancho
            (int)(height * 0.8)   // hasta 80% de altura
        );
        
        // Zona pasajero (lado derecho)
        zonaPasajero = new Rect(
            (int)(width * 0.55),  // 55% desde izquierda
            (int)(height * 0.2),  // 20% desde arriba
            (int)(width * 0.9),   // hasta 90% del ancho
            (int)(height * 0.8)   // hasta 80% de altura
        );
        
        // Zona de intercambio (centro)
        zonaIntercambio = new Rect(
            (int)(width * 0.35),  // 35% desde izquierda
            (int)(height * 0.3),  // 30% desde arriba
            (int)(width * 0.65),  // hasta 65% del ancho
            (int)(height * 0.7)   // hasta 70% de altura
        );
    }
    
    private void configurarPaints() {
        paintZona = new Paint();
        paintZona.setStyle(Paint.Style.STROKE);
        paintZona.setStrokeWidth(3);
        paintZona.setAlpha(180);
        
        paintDeteccion = new Paint();
        paintDeteccion.setStyle(Paint.Style.STROKE);
        paintDeteccion.setStrokeWidth(4);
    }
    
    public ResultadoZonas evaluarDetecciones(List<Detection> detecciones) {
        boolean conductorDetectado = false;
        boolean pasajeroDetectado = false;
        boolean intercambioDetectado = false;
        
        for (Detection det : detecciones) {
            if (det.getClassId() == 0 && det.getConfidence() > 0.5f) { // Persona con confianza > 50%
                Rect bbox = det.getBoundingBox();
                
                if (Rect.intersects(bbox, zonaConductor)) {
                    conductorDetectado = true;
                }
                if (Rect.intersects(bbox, zonaPasajero)) {
                    pasajeroDetectado = true;
                }
                if (Rect.intersects(bbox, zonaIntercambio)) {
                    intercambioDetectado = true;
                }
            }
        }
        
        return new ResultadoZonas(conductorDetectado, pasajeroDetectado, intercambioDetectado);
    }
    
    public void dibujarZonas(Canvas canvas) {
        // Zona conductor (azul)
        paintZona.setColor(Color.BLUE);
        canvas.drawRect(zonaConductor, paintZona);
        
        // Zona pasajero (verde)
        paintZona.setColor(Color.GREEN);
        canvas.drawRect(zonaPasajero, paintZona);
        
        // Zona intercambio (amarillo)
        paintZona.setColor(Color.YELLOW);
        canvas.drawRect(zonaIntercambio, paintZona);
    }
    
    public void dibujarDetecciones(Canvas canvas, List<Detection> detecciones) {
        for (Detection det : detecciones) {
            if (det.getClassId() == 0 && det.getConfidence() > 0.5f) {
                paintDeteccion.setColor(Color.RED);
                canvas.drawRect(det.getBoundingBox(), paintDeteccion);
                
                // Texto con confianza
                canvas.drawText(
                    String.format("%.1f%%", det.getConfidence() * 100),
                    det.getBoundingBox().left,
                    det.getBoundingBox().top - 10,
                    paintDeteccion
                );
            }
        }
    }
    
    // Clase resultado
    public static class ResultadoZonas {
        public final boolean conductorDetectado;
        public final boolean pasajeroDetectado;
        public final boolean intercambioDetectado;
        
        public ResultadoZonas(boolean conductor, boolean pasajero, boolean intercambio) {
            this.conductorDetectado = conductor;
            this.pasajeroDetectado = pasajero;
            this.intercambioDetectado = intercambio;
        }
        
        @Override
        public String toString() {
            return String.format("Conductor: %s, Pasajero: %s, Intercambio: %s",
                               conductorDetectado ? "SÍ" : "NO",
                               pasajeroDetectado ? "SÍ" : "NO",
                               intercambioDetectado ? "SÍ" : "NO");
        }
    }
}
```

## 3. Detector de personas

```java
// DetectorPersonas.java
package com.tuapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import org.tensorflow.lite.Interpreter;
import com.tuapp.models.Detection;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import android.graphics.Rect;

public class DetectorPersonas {
    private static final String TAG = "DetectorPersonas";
    private static final int INPUT_SIZE = 640; // YOLOv5 input size
    private static final float CONFIDENCE_THRESHOLD = 0.5f;
    private static final float NMS_THRESHOLD = 0.45f;
    
    private Interpreter tflite;
    private Context context;
    
    public DetectorPersonas(Context context) {
        this.context = context;
    }
    
    public boolean inicializar() {
        try {
            MappedByteBuffer tfliteModel = loadModelFile();
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(4); // Usar 4 threads
            tflite = new Interpreter(tfliteModel, options);
            
            Log.d(TAG, "Modelo TensorFlow Lite cargado exitosamente");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error cargando modelo TensorFlow Lite", e);
            return false;
        }
    }
    
    private MappedByteBuffer loadModelFile() throws Exception {
        FileInputStream inputStream = new FileInputStream(
            context.getAssets().openFd("yolov5n.tflite").getFileDescriptor()
        );
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = context.getAssets().openFd("yolov5n.tflite").getStartOffset();
        long declaredLength = context.getAssets().openFd("yolov5n.tflite").getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }
    
    public List<Detection> detectarPersonas(Bitmap bitmap) {
        if (tflite == null) {
            Log.e(TAG, "Modelo no inicializado");
            return new ArrayList<>();
        }
        
        // Redimensionar imagen
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true);
        
        // Convertir a ByteBuffer
        ByteBuffer inputBuffer = convertBitmapToByteBuffer(resizedBitmap);
        
        // Preparar output
        float[][][] output = new float[1][25200][85]; // YOLOv5 output format
        
        // Ejecutar inferencia
        long startTime = System.currentTimeMillis();
        tflite.run(inputBuffer, output);
        long inferenceTime = System.currentTimeMillis() - startTime;
        
        Log.d(TAG, "Tiempo de inferencia: " + inferenceTime + "ms");
        
        // Post-procesar resultados
        return postProcessDetections(output[0], bitmap.getWidth(), bitmap.getHeight());
    }
    
    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3);
        byteBuffer.order(ByteOrder.nativeOrder());
        
        int[] intValues = new int[INPUT_SIZE * INPUT_SIZE];
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        
        int pixel = 0;
        for (int i = 0; i < INPUT_SIZE; ++i) {
            for (int j = 0; j < INPUT_SIZE; ++j) {
                final int val = intValues[pixel++];
                
                // Normalizar a [0,1] y reordenar a RGB
                byteBuffer.putFloat(((val >> 16) & 0xFF) / 255.0f); // R
                byteBuffer.putFloat(((val >> 8) & 0xFF) / 255.0f);  // G
                byteBuffer.putFloat((val & 0xFF) / 255.0f);         // B
            }
        }
        
        return byteBuffer;
    }
    
    private List<Detection> postProcessDetections(float[][] predictions, int originalWidth, int originalHeight) {
        List<Detection> detections = new ArrayList<>();
        
        // Factores de escala para convertir coordenadas
        float scaleX = (float) originalWidth / INPUT_SIZE;
        float scaleY = (float) originalHeight / INPUT_SIZE;
        
        for (float[] prediction : predictions) {
            float confidence = prediction[4]; // Confidence score
            
            if (confidence > CONFIDENCE_THRESHOLD) {
                // Encontrar clase con mayor probabilidad
                int bestClassId = 0;
                float bestClassScore = prediction[5];
                
                for (int i = 6; i < prediction.length; i++) {
                    if (prediction[i] > bestClassScore) {
                        bestClassScore = prediction[i];
                        bestClassId = i - 5;
                    }
                }
                
                // Solo procesar si es persona (clase 0) y tiene buena confianza
                if (bestClassId == 0 && (confidence * bestClassScore) > CONFIDENCE_THRESHOLD) {
                    // Convertir coordenadas YOLO a coordenadas de imagen
                    float centerX = prediction[0] * scaleX;
                    float centerY = prediction[1] * scaleY;
                    float width = prediction[2] * scaleX;
                    float height = prediction[3] * scaleY;
                    
                    // Crear bounding box
                    int left = (int) (centerX - width / 2);
                    int top = (int) (centerY - height / 2);
                    int right = (int) (centerX + width / 2);
                    int bottom = (int) (centerY + height / 2);
                    
                    // Asegurar que esté dentro de los límites de la imagen
                    left = Math.max(0, left);
                    top = Math.max(0, top);
                    right = Math.min(originalWidth, right);
                    bottom = Math.min(originalHeight, bottom);
                    
                    Rect boundingBox = new Rect(left, top, right, bottom);
                    detections.add(new Detection(boundingBox, confidence * bestClassScore, bestClassId));
                }
            }
        }
        
        Log.d(TAG, "Detecciones encontradas: " + detections.size());
        return detections;
    }
    
    public void cerrar() {
        if (tflite != null) {
            tflite.close();
            tflite = null;
        }
    }
}
```

## 4. MainActivity principal

```java
// MainActivity.java
package com.tuapp;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.tuapp.models.Detection;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    
    private DetectorPersonas detector;
    private ZonasDeteccion zonasDeteccion;
    private ImageView imageView;
    private TextView textViewResultado;
    private Button buttonProcesar;
    
    // Imágenes de prueba (colócalas en res/drawable)
    private int[] imagenesTest = {
        R.drawable.test_image1,
        R.drawable.test_image2,
        R.drawable.test_image3
    };
    private int imagenActual = 0;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        inicializarVistas();
        inicializarDetector();
    }
    
    private void inicializarVistas() {
        imageView = findViewById(R.id.imageView);
        textViewResultado = findViewById(R.id.textViewResultado);
        buttonProcesar = findViewById(R.id.buttonProcesar);
        
        buttonProcesar.setOnClickListener(v -> procesarImagenActual());
        
        Button buttonSiguiente = findViewById(R.id.buttonSiguiente);
        buttonSiguiente.setOnClickListener(v -> {
            imagenActual = (imagenActual + 1) % imagenesTest.length;
            cargarImagen();
        });
        
        // Cargar primera imagen
        cargarImagen();
    }
    
    private void inicializarDetector() {
        detector = new DetectorPersonas(this);
        
        new Thread(() -> {
            boolean inicializado = detector.inicializar();
            runOnUiThread(() -> {
                if (inicializado) {
                    textViewResultado.setText("Detector inicializado. Listo para procesar.");
                    buttonProcesar.setEnabled(true);
                } else {
                    textViewResultado.setText("Error inicializando detector.");
                }
            });
        }).start();
    }
    
    private void cargarImagen() {
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), imagenesTest[imagenActual]);
        imageView.setImageBitmap(bitmap);
        
        // Inicializar zonas basadas en dimensiones de imagen
        if (zonasDeteccion == null || 
            zonasDeteccion.getImageWidth() != bitmap.getWidth() ||
            zonasDeteccion.getImageHeight() != bitmap.getHeight()) {
            
            zonasDeteccion = new ZonasDeteccion(bitmap.getWidth(), bitmap.getHeight());
        }
        
        textViewResultado.setText("Imagen " + (imagenActual + 1) + " cargada. Presiona 'Procesar' para analizar.");
    }
    
    private void procesarImagenActual() {
        if (detector == null) {
            textViewResultado.setText("Detector no inicializado");
            return;
        }
        
        buttonProcesar.setEnabled(false);
        textViewResultado.setText("Procesando...");
        
        new Thread(() -> {
            try {
                // Obtener bitmap actual
                Bitmap bitmap = BitmapFactory.decodeResource(getResources(), imagenesTest[imagenActual]);
                
                // Detectar personas
                List<Detection> detecciones = detector.detectarPersonas(bitmap);
                
                // Evaluar zonas
                ZonasDeteccion.ResultadoZonas resultado = zonasDeteccion.evaluarDetecciones(detecciones);
                
                // Crear imagen con visualizaciones
                Bitmap bitmapConZonas = dibujarVisualizaciones(bitmap, detecciones);
                
                runOnUiThread(() -> {
                    imageView.setImageBitmap(bitmapConZonas);
                    
                    String textoResultado = "Resultados:\n" +
                                          "Detecciones: " + detecciones.size() + "\n" +
                                          resultado.toString() + "\n\n" +
                                          "Detalle:\n";
                    
                    for (Detection det : detecciones) {
                        textoResultado += det.toString() + "\n";
                    }
                    
                    textViewResultado.setText(textoResultado);
                    buttonProcesar.setEnabled(true);
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error procesando imagen", e);
                runOnUiThread(() -> {
                    textViewResultado.setText("Error procesando imagen: " + e.getMessage());
                    buttonProcesar.setEnabled(true);
                });
            }
        }).start();
    }
    
    private Bitmap dibujarVisualizaciones(Bitmap originalBitmap, List<Detection> detecciones) {
        Bitmap mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(mutableBitmap);
        
        // Dibujar zonas
        zonasDeteccion.dibujarZonas(canvas);
        
        // Dibujar detecciones
        zonasDeteccion.dibujarDetecciones(canvas, detecciones);
        
        return mutableBitmap;
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (detector != null) {
            detector.cerrar();
        }
    }
}
```

## 5. Layout XML

```xml
<!-- res/layout/activity_main.xml -->
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp">

    <ImageView
        android:id="@+id/imageView"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:scaleType="fitCenter" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:layout_marginTop="16dp">

        <Button
            android:id="@+id/buttonProcesar"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="Procesar"
            android:enabled="false" />

        <Button
            android:id="@+id/buttonSiguiente"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:layout_marginStart="8dp"
            android:text="Siguiente Imagen" />

    </LinearLayout>

    <ScrollView
        android:layout_width="match_parent"
        android:layout_height="200dp"
        android:layout_marginTop="16dp">

        <TextView
            android:id="@+id/textViewResultado"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="Inicializando..."
            android:textSize="12sp"
            android:fontFamily="monospace" />

    </ScrollView>

</LinearLayout>
```
