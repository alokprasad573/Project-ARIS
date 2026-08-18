package com.aris.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.aris.assistant.speech.ArisSpeechRecognizer
import com.aris.assistant.ui.ArisScreen
import com.aris.assistant.viewmodel.ArisViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: ArisViewModel by viewModels()
    private var speechRecognizer: ArisSpeechRecognizer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val downloadProgress by viewModel.downloadProgress.collectAsState()
            val downloadedSizeText by viewModel.downloadedSizeText.collectAsState()
            val totalSizeText by viewModel.totalSizeText.collectAsState()
            val isModelReady by viewModel.isModelReady.collectAsState()
            val isDownloading by viewModel.isDownloading.collectAsState()
            val statusText by viewModel.statusText.collectAsState()

            // ArisScreen UI States from ViewModel (survives rotation & config change)
            val uiMode by viewModel.uiMode.collectAsState()
            val recognizedText by viewModel.recognizedText.collectAsState()
            val responseText by viewModel.responseText.collectAsState()
            val speechStatus by viewModel.speechStatus.collectAsState()
            val rmsLevel by viewModel.rmsLevel.collectAsState()

            // Initialize Speech Recognizer with dynamic callbacks
            val recognizer = remember {
                ArisSpeechRecognizer(
                    context = this@MainActivity,
                    listener = object : ArisSpeechRecognizer.Listener {
                        override fun onReady() = viewModel.onSpeechReady()
                        override fun onListening() = viewModel.onSpeechListening()
                        override fun onRmsChanged(rmsdB: Float) = viewModel.onSpeechRmsChanged(rmsdB)
                        override fun onPartialResult(text: String) = viewModel.onSpeechPartialResult(text)
                        override fun onResult(text: String) = viewModel.onSpeechResult(text)
                        override fun onError(message: String) = viewModel.onSpeechError(message)
                        override fun onEnd() = viewModel.onSpeechEnd()
                    }
                )
            }

            speechRecognizer = recognizer

            // Audio Permission Launcher
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                if (isGranted) {
                    viewModel.onStartListening()
                    recognizer.startListening()
                } else {
                    viewModel.onPermissionDenied()
                }
            }

            val startListeningAction: () -> Unit = {
                val hasAudioPermission = ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED

                if (hasAudioPermission) {
                    viewModel.onStartListening()
                    recognizer.startListening()
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
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

                            Spacer(modifier = Modifier.height(24.dp))

                            if (isDownloading) {
                                Button(
                                    onClick = { viewModel.abortDownload() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.Red.copy(alpha = 0.7f),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text("ABORT")
                                }
                            } else {
                                Button(
                                    onClick = { viewModel.retryDownload() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF00FF66).copy(alpha = 0.7f),
                                        contentColor = Color.Black
                                    )
                                ) {
                                    Text("DOWNLOAD")
                                }
                            }
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
                        viewModel.onDoneSpeaking()
                    },
                    onPauseListening = {
                        recognizer.cancel()
                        viewModel.onPauseListening()
                    },
                    onTextChanged = { viewModel.onTextChanged(it) },
                    onSendVerifiedText = { verifiedText ->
                        recognizer.cancel()
                        viewModel.sendVerifiedText(verifiedText)
                    },
                    onListenAgain = startListeningAction,
                    onReset = {
                        recognizer.cancel()
                        viewModel.onReset()
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        super.onDestroy()
    }
}
