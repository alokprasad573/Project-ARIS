package com.aris.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

import com.aris.assistant.tts.ArisTTS
import com.aris.assistant.speech.ArisSpeechRecognizer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.scale

class MainActivity : ComponentActivity(), ArisSpeechRecognizer.Listener {
    private lateinit var arisTTS: ArisTTS

    private lateinit var speechRecognizer: ArisSpeechRecognizer

    private var recognizedText by mutableStateOf("")
    private var speechStatus by mutableStateOf("Ready")
    private var isListening by mutableStateOf(false)
    private var rmsLevel by mutableStateOf(0f)

    private val microphonePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            startListening()
        } else {
            isListening = false
            speechStatus = "Microphone permission denied"
        }
    }

    override fun onCreate(savedInstance: Bundle?) {
        super.onCreate(savedInstance)

        // Existing M0 Fish Audio TTS
        arisTTS = ArisTTS(this)

        speechRecognizer = ArisSpeechRecognizer(this, this)

        setContent {
            ArisScreen(
                recognizedText = recognizedText,
                speechStatus = speechStatus,
                isListening = isListening,
                rmsLevel = rmsLevel,
                onListen = {
                    requestMicrophonePermission()
                },
                onStop = {
                    stopListening()
                },
                onSpeak = {
                    arisTTS.speak("Hi, I am Aris, a virtual artificial intelligence. How may i assist you today?")
                }
            )
        }
    }

    private fun requestMicrophonePermission() {
        val permission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)

        if (permission == PackageManager.PERMISSION_GRANTED) {
            startListening()
        } else {
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startListening() {
        if (isListening) {
            return
        }

        recognizedText = ""
        speechStatus = "Starting microphone..."
        isListening = true
        rmsLevel = 0f
        speechRecognizer.startListening()
    }

    private fun stopListening() {
        if (!isListening) {
            return
        }

        isListening = false
        speechStatus = "Stopping..."
        rmsLevel = 0f
        speechRecognizer.stopListening()
    }

    // ---------------------
    // SpeechRecognizer callbacks
    // ---------------------

    override fun onReady() {
        isListening = true
        speechStatus = "Listening..."
    }

    override fun onListening() {
        isListening = true
        speechStatus = "Listening..."
    }

    override fun onRmsChanged(rmsdB: Float) {
        // rmsdB typically ranges from -2 dB (silence) to ~10+ dB (loud speech)
        // Normalize to 0.0 .. 1.0 range
        val normalized = (rmsdB.coerceAtLeast(0f) / 10f).coerceIn(0f, 1f)
        rmsLevel = normalized
    }

    override fun onPartialResult(text: String) {
        recognizedText = text
    }

    override fun onResult(text: String) {
        recognizedText = text
        speechStatus = if (text.isNotBlank()) "Recognized" else "Ready"
        isListening = false
        rmsLevel = 0f
    }

    override fun onError(message: String) {
        speechStatus = message
        isListening = false
        rmsLevel = 0f
    }

    override fun onEnd() {
        if (isListening) {
            speechStatus = "Processing..."
            isListening = false
        } else if (speechStatus == "Stopping...") {
            speechStatus = if (recognizedText.isNotBlank()) "Recognized" else "Ready"
        }
        rmsLevel = 0f
    }

    override fun onDestroy() {
        speechRecognizer.destroy()
        super.onDestroy()
    }
}

@androidx.compose.runtime.Composable
fun ArisScreen(
    recognizedText: String,
    speechStatus: String,
    isListening: Boolean,
    rmsLevel: Float,
    onListen: () -> Unit,
    onStop: () -> Unit,
    onSpeak: () -> Unit
) {
    val animatedScale by animateFloatAsState(
        targetValue = if (isListening) 1f + (rmsLevel * 0.45f) else 1f,
        animationSpec = spring(),
        label = "rmsScale"
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "ARIS", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        // Dynamic Voice Reactive Indicator Ring
        Box(
            modifier = Modifier
                .size(100.dp)
                .scale(animatedScale)
                .background(
                    color = if (isListening) {
                        Color(0xFF00E5FF).copy(alpha = 0.2f + (rmsLevel * 0.6f))
                    } else {
                        Color.Transparent
                    },
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(
                        color = if (isListening) Color(0xFF00B0FF) else Color(0xFFE0E0E0),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isListening) "🎙️" else "💤",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = speechStatus, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Text(text = if (recognizedText.isBlank()) {
            "Say something..."
        } else {
            "\"$recognizedText\""
        })
        Spacer(modifier = Modifier.height(24.dp))

        if (isListening) {
            Button(onClick = onStop) {
                Text(text = "🔴 Stop")
            }
        } else {
            Button(onClick = onListen) {
                Text(text = "🎧 Listen")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onSpeak) {
            Text(text = "🤖 Aris")
        }
    }
}
