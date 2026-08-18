package com.aris.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.aris.assistant.brain.gemma.LiteRtmEngine
import com.aris.assistant.brain.gemma.ModelDownloader
import com.aris.assistant.speech.ArisSpeechRecognizer
import com.aris.assistant.tts.ArisTTS
import com.aris.assistant.ui.ArisScreen
import com.aris.assistant.ui.ArisUiMode
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var arisEngine: LiteRtmEngine
    private var speechRecognizer: ArisSpeechRecognizer? = null
    private lateinit var arisTts: ArisTTS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arisEngine = LiteRtmEngine(this)
        arisTts = ArisTTS(this)

        setContent {
            var downloadProgress by remember { mutableIntStateOf(0) }
            var downloadedSizeText by remember { mutableStateOf("0 MB") }
            var totalSizeText by remember { mutableStateOf("2.58 GB") }
            var isModelReady by remember { mutableStateOf(false) }
            var statusText by remember { mutableStateOf("INITIALIZING MATRIX...") }

            // ArisScreen UI States
            var uiMode by remember { mutableStateOf(ArisUiMode.READY) }
            var recognizedText by remember { mutableStateOf("") }
            var responseText by remember { mutableStateOf("") }
            var speechStatus by remember { mutableStateOf("READY") }
            var rmsLevel by remember { mutableFloatStateOf(0f) }

            val scope = rememberCoroutineScope()

            // Initialize Speech Recognizer with dynamic callbacks
            val recognizer = remember {
                ArisSpeechRecognizer(
                    context = this@MainActivity,
                    listener = object : ArisSpeechRecognizer.Listener {
                        override fun onReady() {
                            speechStatus = "LISTENING (SPEAK NOW)..."
                        }

                        override fun onListening() {
                            speechStatus = "LISTENING..."
                        }

                        override fun onRmsChanged(rmsdB: Float) {
                            // Normalize RMS dB (-2f to 10f) to 0f..1f for fluid animation
                            val normalized = ((rmsdB + 2f) / 10f).coerceIn(0f, 1f)
                            rmsLevel = normalized
                        }

                        override fun onPartialResult(text: String) {
                            recognizedText = text
                        }

                        override fun onResult(text: String) {
                            recognizedText = text
                            uiMode = ArisUiMode.VERIFYING
                            speechStatus = "VERIFY INPUT"
                            rmsLevel = 0f
                        }

                        override fun onError(message: String) {
                            speechStatus = message
                            rmsLevel = 0f
                            if (recognizedText.isNotBlank()) {
                                uiMode = ArisUiMode.VERIFYING
                            } else {
                                uiMode = ArisUiMode.READY
                            }
                        }

                        override fun onEnd() {
                            rmsLevel = 0f
                        }
                    }
                )
            }

            DisposableEffect(Unit) {
                speechRecognizer = recognizer
                onDispose {
                    recognizer.destroy()
                    speechRecognizer = null
                }
            }

            // Audio Permission Launcher
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                if (isGranted) {
                    uiMode = ArisUiMode.LISTENING
                    speechStatus = "LISTENING..."
                    rmsLevel = 0f
                    recognizer.startListening()
                } else {
                    speechStatus = "MICROPHONE PERMISSION REQUIRED"
                    uiMode = ArisUiMode.READY
                }
            }

            val startListeningAction: () -> Unit = {
                recognizedText = ""
                responseText = ""
                val hasAudioPermission = ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED

                if (hasAudioPermission) {
                    uiMode = ArisUiMode.LISTENING
                    speechStatus = "LISTENING..."
                    rmsLevel = 0f
                    recognizer.startListening()
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }

            LaunchedEffect(Unit) {
                // Check if model already downloaded
                if (ModelDownloader.isModelPresent(this@MainActivity)) {
                    statusText = "BOOTING ARIS ENGINE (LOADING NEURAL WEIGHTS)..."
                    try {
                        arisEngine.initialize()
                        isModelReady = true
                    } catch (e: Exception) {
                        statusText = "INITIALIZATION ERROR: ${e.message}"
                    }
                } else {
                    // Check if partial download exists to show initial progress
                    val partialFile = File(getExternalFilesDir(null), ModelDownloader.MODEL_FILE_NAME)
                    if (partialFile.exists() && partialFile.length() > 0) {
                        val downloadedMB = String.format("%.1f MB", partialFile.length() / (1024.0 * 1024.0))
                        val progress = ((partialFile.length() * 100) / ModelDownloader.EXPECTED_TOTAL_BYTES).toInt().coerceIn(0, 100)
                        downloadProgress = progress
                        downloadedSizeText = downloadedMB
                        statusText = "RESUMING DOWNLOAD [$downloadedMB]..."
                    } else {
                        statusText = "DOWNLOADING NEURAL WEIGHTS..."
                    }

                    val downloader = ModelDownloader(this@MainActivity)
                    val downloadUrl = "https://huggingface.co/alokprasad573/aris-gemma-model/resolve/main/gemma-4-E2B-it.litertlm?download=true"

                    val success = downloader.downloadGemmaModel(
                        downloadUrl = downloadUrl,
                        onStatusChange = { newStatus ->
                            statusText = newStatus
                        },
                        onProgress = { progress, downloadedMB, totalMB ->
                            downloadProgress = progress
                            downloadedSizeText = downloadedMB
                            totalSizeText = totalMB
                        }
                    )

                    if (success) {
                        statusText = "BOOTING ARIS ENGINE (LOADING NEURAL WEIGHTS)..."
                        try {
                            arisEngine.initialize()
                            isModelReady = true
                        } catch (e: Exception) {
                            statusText = "INITIALIZATION ERROR: ${e.message}"
                        }
                    } else {
                        statusText = "DOWNLOAD FAILED. CHECK INTERNET."
                    }
                }
            }

            if (!isModelReady) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = ">> PROJECT ARIS",
                            color = Color(0xFF00FF66),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.height(20.dp))

                        if (statusText.contains("BOOTING", ignoreCase = true)) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                color = Color(0xFF00FF66),
                                strokeWidth = 4.dp
                            )
                        } else {
                            LinearProgressIndicator(
                                progress = { downloadProgress / 100f },
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                                color = Color(0xFF00FF66),
                                trackColor = Color.DarkGray
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = statusText,
                            color = Color(0xFF00FF66),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        )

                        if (!statusText.contains("BOOTING", ignoreCase = true)) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "$downloadedSizeText / $totalSizeText [$downloadProgress%]",
                                color = Color(0xFF00FF66),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            } else {
                ArisScreen(
                    mode = uiMode,
                    recognizedText = recognizedText,
                    responseText = responseText,
                    speechStatus = speechStatus,
                    rmsLevel = rmsLevel,
                    onStartListening = startListeningAction,
                    onDoneSpeaking = {
                        recognizer.stopListening()
                        uiMode = ArisUiMode.VERIFYING
                        speechStatus = "VERIFY INPUT"
                    },
                    onPauseListening = {
                        recognizer.cancel()
                        uiMode = ArisUiMode.READY
                        speechStatus = "READY"
                        rmsLevel = 0f
                    },
                    onTextChanged = { recognizedText = it },
                    onSendVerifiedText = { verifiedText ->
                        recognizer.cancel()
                        uiMode = ArisUiMode.PROCESSING
                        speechStatus = "THINKING..."
                        scope.launch {
                            try {
                                val result = arisEngine.generate(verifiedText)
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

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        speechRecognizer = null
        arisEngine.close()
    }
}
