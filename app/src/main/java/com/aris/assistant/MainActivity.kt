package com.aris.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.aris.assistant.brain.ArisBrain
import com.aris.assistant.speech.ArisSpeechRecognizer
import com.aris.assistant.tts.ArisTTS
import com.aris.assistant.ui.ArisScreen
import com.aris.assistant.ui.ArisUiMode
import com.aris.assistant.ui.theme.ARISTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity(), ArisSpeechRecognizer.Listener {
    private lateinit var arisTTS: ArisTTS
    private lateinit var speechRecognizer: ArisSpeechRecognizer
    private lateinit var arisBrain: ArisBrain

    private var uiMode by mutableStateOf(ArisUiMode.READY)
    private var recognizedText by mutableStateOf("")
    private var responseText by mutableStateOf("")
    private var speechStatus by mutableStateOf("Ready to assist")
    private var rmsLevel by mutableStateOf(0f)

    private val microphonePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startListening()
            } else {
                uiMode = ArisUiMode.READY
                speechStatus = "Microphone permission is required."
            }
        }

    override fun onCreate(savedInstance: Bundle?) {
        super.onCreate(savedInstance)

        arisTTS = ArisTTS(this)
        speechRecognizer = ArisSpeechRecognizer(this, this)
        arisBrain = ArisBrain(applicationContext)

        // Pre-warm AI brain in background for fast on-device inference
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                arisBrain.initialize()
            } catch (e: Exception) {
                // Warmup fallback
            }
        }

        setContent {
            ARISTheme {
                ArisScreen(
                    mode = uiMode,
                    recognizedText = recognizedText,
                    responseText = responseText,
                    speechStatus = speechStatus,
                    rmsLevel = rmsLevel,
                    onStartListening = {
                        requestMicrophonePermission()
                    },
                    onDoneSpeaking = {
                        speechRecognizer.stopListening()
                        if (recognizedText.isNotBlank()) {
                            uiMode = ArisUiMode.VERIFYING
                            speechStatus = "Please verify your speech"
                        }
                    },
                    onPauseListening = {
                        speechRecognizer.cancel()
                        if (recognizedText.isNotBlank()) {
                            uiMode = ArisUiMode.VERIFYING
                            speechStatus = "Speech paused. Verify or re-listen."
                        } else {
                            uiMode = ArisUiMode.READY
                            speechStatus = "Ready"
                        }
                    },
                    onTextChanged = { newText ->
                        recognizedText = newText
                    },
                    onSendVerifiedText = { textToSend ->
                        sendTextToBrain(textToSend)
                    },
                    onListenAgain = {
                        requestMicrophonePermission()
                    },
                    onReset = {
                        speechRecognizer.cancel()
                        uiMode = ArisUiMode.READY
                        recognizedText = ""
                        responseText = ""
                        speechStatus = "Ready"
                        rmsLevel = 0f
                    }
                )
            }
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
        recognizedText = ""
        speechStatus = "Listening... speak now"
        uiMode = ArisUiMode.LISTENING
        rmsLevel = 0f
        speechRecognizer.startListening()
    }

    private fun sendTextToBrain(input: String) {
        val prompt = input.trim()
        if (prompt.isBlank()) {
            speechStatus = "Cannot send empty request"
            return
        }

        recognizedText = prompt
        uiMode = ArisUiMode.PROCESSING
        speechStatus = "ARIS is thinking..."

        lifecycleScope.launch {
            try {
                // Pipeline: ArisBrain -> Gemma -> Response
                val result = withContext(Dispatchers.IO) {
                    arisBrain.process(prompt)
                }

                responseText = result
                uiMode = ArisUiMode.RESPONDED
                speechStatus = "Responding via voice..."

                // Pipeline: Response -> ArisTTS -> Fish Audio
                if (result.isNotBlank()) {
                    arisTTS.speak(result)
                }
            } catch (e: Exception) {
                val errorMsg = "Error processing request: ${e.message ?: "Unknown error"}"
                responseText = errorMsg
                uiMode = ArisUiMode.RESPONDED
                speechStatus = "Brain error"
            }
        }
    }

    // -------------------------------------------------------------
    // ArisSpeechRecognizer.Listener Callbacks
    // -------------------------------------------------------------

    override fun onReady() {
        uiMode = ArisUiMode.LISTENING
        speechStatus = "Listening..."
    }

    override fun onListening() {
        uiMode = ArisUiMode.LISTENING
        speechStatus = "Listening..."
    }

    override fun onRmsChanged(rmsdB: Float) {
        val normalized = (rmsdB.coerceAtLeast(0f) / 10f).coerceIn(0f, 1f)
        rmsLevel = normalized
    }

    override fun onPartialResult(text: String) {
        recognizedText = text
    }

    override fun onResult(text: String) {
        recognizedText = text
        rmsLevel = 0f

        if (text.isNotBlank()) {
            uiMode = ArisUiMode.VERIFYING
            speechStatus = "Speech recognized. Verify before sending."
        } else {
            uiMode = ArisUiMode.READY
            speechStatus = "I couldn't catch that. Please try speaking again."
        }
    }

    override fun onError(message: String) {
        rmsLevel = 0f
        if (recognizedText.isNotBlank()) {
            uiMode = ArisUiMode.VERIFYING
            speechStatus = "Verify captured speech"
        } else {
            uiMode = ArisUiMode.READY
            speechStatus = message
        }
    }

    override fun onEnd() {
        rmsLevel = 0f
        if (uiMode == ArisUiMode.LISTENING) {
            if (recognizedText.isNotBlank()) {
                uiMode = ArisUiMode.VERIFYING
                speechStatus = "Verify recognized speech"
            } else {
                uiMode = ArisUiMode.READY
                speechStatus = "Ready"
            }
        }
    }

    override fun onDestroy() {
        speechRecognizer.destroy()
        arisBrain.close()
        super.onDestroy()
    }
}
