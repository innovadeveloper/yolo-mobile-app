package com.example.app_mobile_image_processor

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.app_mobile_image_processor.ui.components.ImageAnalysisScreen
import com.example.app_mobile_image_processor.ui.theme.AppmobileimageprocessorTheme

class MainActivity : ComponentActivity() {
    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppmobileimageprocessorTheme {
                Scaffold(modifier = Modifier.fillMaxSize()){
                    ImageAnalysisScreen()
                }
            }
        }
    }
}