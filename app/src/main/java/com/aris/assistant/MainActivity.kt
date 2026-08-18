package com.aris.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.aris.assistant.brain.gemma.LiteRtmEngine
import com.aris.assistant.speech.ArisSpeechRecognizer
import com.aris.assistant.tts.ArisTTS
import com.aris.assistant.ui.ArisScreen
import com.aris.assistant.ui.ArisUiMode

class MainActivity : ComponentActivity() {

    private lateinit var arisEngine: LiteRtmEngine
    private lateinit var arisTts: ArisTTS
    private var speechRecognizer: ArisSpeechRecognizer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arisEngine = LiteRtmEngine(this)
        arisTts = ArisTTS(this)

        setContent {
            var uiMode by remember { mutableStateOf(ArisUiMode.READY) }
            var recognizedText by remember { mutableStateOf("") }
            var responseText by remember { mutableStateOf("") }
            var speechStatus by remember { mutableStateOf("READY") }
            var rmsLevel by remember { mutableFloatStateOf(0f) }

            val recognizer = remember {
                ArisSpeechRecognizer(
                    context = this@MainActivity,
                    onPartialResult = { text ->
                        recognizedText = text
                    },
                    onFinalResult = { text ->
                        recognizedText = text
                    },
                    onRmsChanged = { rms ->
                        rmsLevel = rms
                    },
                    onStateChanged = { state ->
                        speechStatus = state
                    }
                )
            }

            DisposableEffect(Unit) {
                speechRecognizer = recognizer
                onDispose {
                    recognizer.destroy()
                    if (speechRecognizer === recognizer) {
                        speechRecognizer = null
                    }
                }
            }

            val startListeningAction = {
                uiMode = ArisUiMode.LISTENING
                speechStatus = "LISTENING"
                recognizer.startListening()
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

                    uiMode = ArisUiMode.PROCESSING
                    speechStatus = "PROCESSING"

                    // Existing response-generation flow remains unchanged here.
                    // Brain and Planner are intentionally not redesigned in Step 1.
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

    override fun onDestroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        arisEngine.close()
        arisTts.close()
        super.onDestroy()
    }
}
