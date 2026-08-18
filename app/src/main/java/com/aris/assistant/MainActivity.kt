package com.aris.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.aris.assistant.brain.gemma.LiteRtmEngine
import com.aris.assistant.speech.ArisSpeechRecognizer
import com.aris.assistant.tts.ArisTTS
import com.aris.assistant.ui.ArisScreen
import com.aris.assistant.ui.ArisUiMode
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var arisEngine: LiteRtmEngine
    private lateinit var arisTts: ArisTTS
    private var speechRecognizer: ArisSpeechRecognizer? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            speechRecognizer?.startListening()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arisEngine = LiteRtmEngine(this)
        arisTts = ArisTTS(this)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var uiMode by remember { mutableStateOf(ArisUiMode.READY) }
                    var recognizedText by remember { mutableStateOf("") }
                    var responseText by remember { mutableStateOf("") }
                    var speechStatus by remember { mutableStateOf("READY") }
                    var rmsLevel by remember { mutableFloatStateOf(0f) }
                    val scope = rememberCoroutineScope()

                    val recognizer = remember {
                        ArisSpeechRecognizer(
                            context = this@MainActivity,
                            onPartialResult = { text ->
                                recognizedText = text
                                uiMode = ArisUiMode.LISTENING
                                speechStatus = "LISTENING"
                            },
                            onFinalResult = { text ->
                                recognizedText = text
                                uiMode = ArisUiMode.VERIFYING
                                speechStatus = "VERIFYING"
                            },
                            onRmsChanged = { rms ->
                                rmsLevel = rms
                            },
                            onError = { error ->
                                speechStatus = error
                                uiMode = ArisUiMode.READY
                            },
                            onListeningStarted = {
                                uiMode = ArisUiMode.LISTENING
                                speechStatus = "LISTENING"
                            },
                            onListeningStopped = {
                                speechStatus = "STOPPED"
                            }
                        )
                    }

                    speechRecognizer = recognizer

                    val startListeningAction: () -> Unit = {
                        if (ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            recognizedText = ""
                            responseText = ""
                            uiMode = ArisUiMode.LISTENING
                            speechStatus = "LISTENING"
                            recognizer.startListening()
                        } else {
                            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }

                    ArisScreen(
                        uiMode = uiMode,
                        recognizedText = recognizedText,
                        responseText = responseText,
                        speechStatus = speechStatus,
                        rmsLevel = rmsLevel,
                        onListen = startListeningAction,
                        onProcess = {
                            val prompt = recognizedText.trim()
                            if (prompt.isBlank()) return@ArisScreen

                            scope.launch {
                                try {
                                    uiMode = ArisUiMode.PROCESSING
                                    speechStatus = "PROCESSING"

                                    val result = arisEngine.generate(prompt)

                                    responseText = result
                                    uiMode = ArisUiMode.RESPONDED
                                    speechStatus = "ARIS RESPONDED"
                                    arisTts.speak(result)
                                } catch (e: Exception) {
                                    responseText = "Error: ${e.message}"
                                    uiMode = ArisUiMode.RESPONDED
                                    speechStatus = "ERROR"
                                }
                            }
                        },
                        onListenAgain = startListeningAction,
                        onReset = {
                            recognizer.cancel()
                            uiMode = ArisUiMode.READY
                            recognizedText = ""
                            responseText = ""
                            speechStatus = "READY"
                            rmsLevel = 0f
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        arisEngine.close()
        arisTts.close()
        super.onDestroy()
    }
}
