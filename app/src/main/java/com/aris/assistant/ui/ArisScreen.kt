package com.aris.assistant.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ArisUiMode {
    READY,
    LISTENING,
    VERIFYING,
    PROCESSING,
    RESPONDED
}

// Matrix Cyber Theme Palette
private val MatrixBlack = Color(0xFF000000)
private val MatrixDarkSurface = Color(0xFF041004)
private val MatrixGreen = Color(0xFF00FF66)
private val MatrixGreenDim = Color(0xFF008F39)
private val MatrixCyan = Color(0xFF00E5FF)
private val MatrixYellow = Color(0xFFFFD700)

@Composable
fun ArisScreen(
    mode: ArisUiMode,
    recognizedText: String,
    responseText: String,
    speechStatus: String,
    rmsLevel: Float,
    onStartListening: () -> Unit,
    onDoneSpeaking: () -> Unit,
    onPauseListening: () -> Unit,
    onTextChanged: (String) -> Unit,
    onSendVerifiedText: (String) -> Unit,
    onListenAgain: () -> Unit,
    onReset: () -> Unit
) {
    val animatedScale by animateFloatAsState(
        targetValue = if (mode == ArisUiMode.LISTENING) 1f + (rmsLevel * 0.45f) else 1f,
        animationSpec = spring(),
        label = "rmsScale"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MatrixBlack
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Header (Matrix Style)
            Text(
                text = ">> PROJECT ARIS [MATRIX v4.0]",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    fontFamily = FontFamily.Monospace
                ),
                color = MatrixGreen
            )
            Text(
                text = "NEURAL KERNEL ACTIVE",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MatrixGreenDim
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Dynamic Voice Reactive Visualizer Orb (Matrix Edition)
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(animatedScale)
                    .background(
                        color = when (mode) {
                            ArisUiMode.LISTENING -> MatrixCyan.copy(alpha = 0.2f + (rmsLevel * 0.5f))
                            ArisUiMode.VERIFYING -> MatrixYellow.copy(alpha = 0.25f)
                            ArisUiMode.PROCESSING -> MatrixGreen.copy(alpha = pulseAlpha * 0.4f)
                            ArisUiMode.RESPONDED -> MatrixGreen.copy(alpha = pulseAlpha * 0.5f)
                            ArisUiMode.READY -> MatrixGreenDim.copy(alpha = 0.2f)
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = when (mode) {
                                    ArisUiMode.LISTENING -> listOf(MatrixCyan, Color(0xFF0091EA))
                                    ArisUiMode.VERIFYING -> listOf(MatrixYellow, Color(0xFFB78103))
                                    ArisUiMode.PROCESSING -> listOf(MatrixGreen, MatrixGreenDim)
                                    ArisUiMode.RESPONDED -> listOf(MatrixGreen, Color(0xFF006622))
                                    ArisUiMode.READY -> listOf(MatrixGreenDim, MatrixBlack)
                                }
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (mode) {
                            ArisUiMode.LISTENING -> "🎙️"
                            ArisUiMode.VERIFYING -> "✏️"
                            ArisUiMode.PROCESSING -> "🧠"
                            ArisUiMode.RESPONDED -> "🔊"
                            ArisUiMode.READY -> "⚡"
                        },
                        fontSize = 32.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Status indicator
            Text(
                text = speechStatus,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                ),
                color = when (mode) {
                    ArisUiMode.PROCESSING -> MatrixGreen
                    ArisUiMode.RESPONDED -> MatrixGreen
                    ArisUiMode.VERIFYING -> MatrixYellow
                    else -> MatrixGreenDim
                },
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Dynamic Content Area by Mode
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                contentAlignment = Alignment.Center
            ) {
                when (mode) {
                    ArisUiMode.READY -> {
                        ReadyControls(
                            manualInput = recognizedText,
                            onManualInputChanged = onTextChanged,
                            onSendManualInput = { onSendVerifiedText(recognizedText) },
                            onStartListening = onStartListening
                        )
                    }

                    ArisUiMode.LISTENING -> {
                        ListeningControls(
                            recognizedText = recognizedText,
                            rmsLevel = rmsLevel,
                            onDoneSpeaking = onDoneSpeaking,
                            onPauseListening = onPauseListening
                        )
                    }

                    ArisUiMode.VERIFYING -> {
                        VerifyingControls(
                            text = recognizedText,
                            onTextChanged = onTextChanged,
                            onSend = { verifiedText -> onSendVerifiedText(verifiedText) },
                            onListenAgain = onListenAgain,
                            onCancel = onReset
                        )
                    }

                    ArisUiMode.PROCESSING -> {
                        ProcessingView(prompt = recognizedText)
                    }

                    ArisUiMode.RESPONDED -> {
                        RespondedControls(
                            prompt = recognizedText,
                            response = responseText,
                            onAskAnother = onStartListening,
                            onReset = onReset
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadyControls(
    manualInput: String,
    onManualInputChanged: (String) -> Unit,
    onSendManualInput: () -> Unit,
    onStartListening: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Button(
            onClick = onStartListening,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MatrixGreenDim,
                contentColor = MatrixBlack
            )
        ) {
            Text(text = "🎙️ INITIALIZE VOICE COMMAND", fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MatrixDarkSurface
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "// MANUAL OVERRIDE INTERFACE",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MatrixGreenDim,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = manualInput,
                    onValueChange = onManualInputChanged,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Enter terminal prompt...", color = MatrixGreenDim.copy(alpha = 0.5f)) },
                    shape = RoundedCornerShape(8.dp),
                    singleLine = false,
                    maxLines = 3,
                    textStyle = androidx.compose.ui.text.TextStyle(color = MatrixGreen, fontFamily = FontFamily.Monospace),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MatrixGreen,
                        unfocusedBorderColor = MatrixGreenDim
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onSendManualInput,
                    enabled = manualInput.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MatrixGreen,
                        contentColor = MatrixBlack
                    )
                ) {
                    Text(text = "EXECUTE COMMAND", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun ListeningControls(
    recognizedText: String,
    rmsLevel: Float,
    onDoneSpeaking: () -> Unit,
    onPauseListening: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MatrixDarkSurface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "STATUS: LISTENING...",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MatrixCyan,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (recognizedText.isNotBlank()) "\"$recognizedText\"" else "Awaiting audio input stream...",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (recognizedText.isNotBlank()) FontWeight.Medium else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace
                    ),
                    textAlign = TextAlign.Center,
                    color = if (recognizedText.isNotBlank()) MatrixGreen else MatrixGreenDim
                )

                Spacer(modifier = Modifier.height(12.dp))

                LinearProgressIndicator(
                    progress = { rmsLevel.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MatrixCyan,
                    trackColor = MatrixDarkSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onDoneSpeaking,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MatrixGreen,
                contentColor = MatrixBlack
            )
        ) {
            Text(text = "✓ COMPLETE INPUT", fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = onPauseListening,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MatrixYellow)
        ) {
            Text(text = "⏸️ PAUSE STREAM", fontSize = 14.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun VerifyingControls(
    text: String,
    onTextChanged: (String) -> Unit,
    onSend: (String) -> Unit,
    onListenAgain: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MatrixDarkSurface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "VERIFY COMMAND BUFFER",
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = FontWeight.Bold,
                    color = MatrixYellow
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Edit transcript parameters before execution:",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MatrixGreenDim
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChanged,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Edit stream...", color = MatrixGreenDim) },
                    shape = RoundedCornerShape(8.dp),
                    minLines = 2,
                    maxLines = 5,
                    textStyle = androidx.compose.ui.text.TextStyle(color = MatrixGreen, fontFamily = FontFamily.Monospace),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MatrixYellow,
                        unfocusedBorderColor = MatrixGreenDim
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    if (text.isNotBlank()) {
                        onSend(text.trim())
                    }
                },
                enabled = text.trim().isNotBlank(),
                modifier = Modifier
                    .weight(1.2f)
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MatrixGreen,
                    contentColor = MatrixBlack
                )
            ) {
                Text(text = "🚀 TRANSMIT", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }

            OutlinedButton(
                onClick = onListenAgain,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MatrixCyan)
            ) {
                Text(text = "🎙️ RE-RECORD", fontFamily = FontFamily.Monospace)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "ABORT", color = Color.Red, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun ProcessingView(prompt: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MatrixDarkSurface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MatrixGreen,
                strokeWidth = 4.dp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "NEURAL PROCESSING...",
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                fontWeight = FontWeight.SemiBold,
                color = MatrixGreen
            )
            if (prompt.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "\"$prompt\"",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MatrixGreenDim,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun RespondedControls(
    prompt: String,
    response: String,
    onAskAnother: () -> Unit,
    onReset: () -> Unit
) {
    val isError = response.startsWith("Error", ignoreCase = true) || response.contains("Failed", ignoreCase = true)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MatrixDarkSurface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                if (prompt.isNotBlank()) {
                    Text(
                        text = "QUERY TRANSMITTED:",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MatrixGreenDim,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "\"$prompt\"",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MatrixGreen.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Text(
                    text = if (isError) "⚠️ SYSTEM DIAGNOSTIC ERROR:" else "ARIS SYSTEM RESPONSE:",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = if (isError) Color.Red else MatrixGreen,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = response.ifBlank { "Audio output stream active..." },
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = if (isError) Color.Red else MatrixGreen
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onAskAnother,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MatrixGreen,
                contentColor = MatrixBlack
            )
        ) {
            Text(text = "🎙️ TRANSMIT NEW COMMAND", fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = onReset,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "RESET TERMINAL", color = MatrixCyan, fontFamily = FontFamily.Monospace)
        }
    }
}