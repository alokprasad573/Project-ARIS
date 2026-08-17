package com.aris.assistant.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
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
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Header
            Text(
                text = "PROJECT ARIS",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                ),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Autonomous Responsive Intelligent System",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Dynamic Voice Reactive Visualizer Orb
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(animatedScale)
                    .background(
                        color = when (mode) {
                            ArisUiMode.LISTENING -> Color(0xFF00E5FF).copy(alpha = 0.2f + (rmsLevel * 0.5f))
                            ArisUiMode.VERIFYING -> Color(0xFFFFB300).copy(alpha = 0.25f)
                            ArisUiMode.PROCESSING -> Color(0xFF7C4DFF).copy(alpha = pulseAlpha * 0.4f)
                            ArisUiMode.RESPONDED -> Color(0xFF00E676).copy(alpha = pulseAlpha * 0.4f)
                            ArisUiMode.READY -> Color(0xFF2979FF).copy(alpha = 0.15f)
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
                                    ArisUiMode.LISTENING -> listOf(Color(0xFF00E5FF), Color(0xFF0091EA))
                                    ArisUiMode.VERIFYING -> listOf(Color(0xFFFFD54F), Color(0xFFFF8F00))
                                    ArisUiMode.PROCESSING -> listOf(Color(0xFFB388FF), Color(0xFF651FFF))
                                    ArisUiMode.RESPONDED -> listOf(Color(0xFF69F0AE), Color(0xFF00C853))
                                    ArisUiMode.READY -> listOf(Color(0xFF82B1FF), Color(0xFF2979FF))
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
                            ArisUiMode.READY -> "🤖"
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
                    fontWeight = FontWeight.SemiBold
                ),
                color = when (mode) {
                    ArisUiMode.PROCESSING -> Color(0xFF7C4DFF)
                    ArisUiMode.RESPONDED -> Color(0xFF00C853)
                    ArisUiMode.VERIFYING -> Color(0xFFFFB300)
                    else -> MaterialTheme.colorScheme.onSurface
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
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2979FF)
            )
        ) {
            Text(text = "🎙️ Talk to ARIS", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "— OR TYPE COMMAND —",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = manualInput,
                    onValueChange = onManualInputChanged,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Type prompt or command here...") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = false,
                    maxLines = 3
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onSendManualInput,
                    enabled = manualInput.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = "🚀 Send Command", fontWeight = FontWeight.SemiBold)
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
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "LIVE SPEECH INPUT",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (recognizedText.isNotBlank()) "\"$recognizedText\"" else "Listening... (speak now)",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (recognizedText.isNotBlank()) FontWeight.Medium else FontWeight.Normal
                    ),
                    textAlign = TextAlign.Center,
                    color = if (recognizedText.isNotBlank()) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                LinearProgressIndicator(
                    progress = { rmsLevel.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = Color(0xFF00E5FF),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onDoneSpeaking,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF00A86B)
            )
        ) {
            Text(text = "✓ Done Speaking (Verify)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = onPauseListening,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(text = "⏸️ Pause / Stop", fontSize = 14.sp)
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
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Verify / Edit Speech Input",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "You can edit or add to the recognized words before sending:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChanged,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Edit your prompt...") },
                    shape = RoundedCornerShape(12.dp),
                    minLines = 2,
                    maxLines = 5
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
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2979FF)
                )
            ) {
                Text(text = "🚀 Send", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onListenAgain,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(text = "🎙️ Re-listen")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Cancel", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ProcessingView(prompt: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
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
                color = Color(0xFF7C4DFF),
                strokeWidth = 4.dp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "ARIS is thinking...",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (prompt.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "\"$prompt\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isError)
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                else
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                if (prompt.isNotBlank()) {
                    Text(
                        text = "You asked:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "\"$prompt\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Text(
                    text = if (isError) "⚠️ Error / Diagnostics:" else "ARIS Response (Audio):",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isError) MaterialTheme.colorScheme.error else Color(0xFF00C853),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = response.ifBlank { "Speaking response audio..." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onAskAnother,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF00A86B)
            )
        ) {
            Text(text = "🎙️ Talk to ARIS Again", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = onReset,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Home / Reset")
        }
    }
}