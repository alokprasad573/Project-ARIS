package com.jarvis.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.tooling.CompositionInstance
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    private lateinit var jarvisTTS: JarvisTTS

    override fun onCreate(
        savedInstance: Bundle?
    ) {
        super.onCreate(savedInstance)
        jarvisTTS = JarvisTTS(this)
        setContent {
            JarvisScreen {
                jarvisTTS.speak(
                    "नमस्ते। मैं जार्विस, एक वर्चुअल आर्टिफिशियल इंटेलिजेंस हूँ। सभी सिस्टम पूरी तरह से सक्रिय हैं, और मैंने अतिरिक्त सुरक्षा प्रोटोकॉल भी लागू कर दिए हैं। आज मैं आपकी रिक्वेस्ट में किस प्रकार सहायता कर सकता हूँ?\nGreetings. I am Jarvis, a virtual artificial intelligence. All systems are fully operational, and I've implemented additional security protocols. How may I assist you today with your requests? "
                )
            }
        }
    }
}

@Composable
fun JarvisScreen(
    onSpeak: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = onSpeak
        ) {
            Text("Jarvis")
        }
    }
}