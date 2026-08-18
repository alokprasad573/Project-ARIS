package com.aris.assistant.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aris.assistant.brain.ArisBrain
import com.aris.assistant.brain.gemma.ModelDownloader
import com.aris.assistant.tts.ArisTTS
import com.aris.assistant.ui.ArisUiMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class ArisViewModel(
    application: Application,
    private val arisBrain: ArisBrain = ArisBrain(application),
    private val arisTts: ArisTTS = ArisTTS(application)
) : AndroidViewModel(application) {

    // UI Flow States
    private val _uiMode = MutableStateFlow(ArisUiMode.READY)
    val uiMode: StateFlow<ArisUiMode> = _uiMode.asStateFlow()

    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText.asStateFlow()

    private val _responseText = MutableStateFlow("")
    val responseText: StateFlow<String> = _responseText.asStateFlow()

    private val _speechStatus = MutableStateFlow("READY")
    val speechStatus: StateFlow<String> = _speechStatus.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    // Model Downloader / Engine States
    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress: StateFlow<Int> = _downloadProgress.asStateFlow()

    private val _downloadedSizeText = MutableStateFlow("0 MB")
    val downloadedSizeText: StateFlow<String> = _downloadedSizeText.asStateFlow()

    private val _totalSizeText = MutableStateFlow("2.58 GB")
    val totalSizeText: StateFlow<String> = _totalSizeText.asStateFlow()

    private val _isModelReady = MutableStateFlow(false)
    val isModelReady: StateFlow<Boolean> = _isModelReady.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _shouldDownload = MutableStateFlow(true)
    val shouldDownload: StateFlow<Boolean> = _shouldDownload.asStateFlow()

    private val _statusText = MutableStateFlow("INITIALIZING MATRIX...")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private var processJob: Job? = null
    private var downloadJob: Job? = null

    init {
        // Setup TTS listener
        arisTts.listener = object : ArisTTS.Listener {
            override fun onPlaybackStarted() {
                _speechStatus.value = "SPEAKING..."
            }

            override fun onPlaybackCompleted() {
                _speechStatus.value = "PLAYBACK COMPLETED"
            }

            override fun onError(message: String) {
                _speechStatus.value = "TTS NOT AVAILABLE"
            }
        }

        checkAndInitModel()
    }

    fun checkAndInitModel() {
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            val context = getApplication<Application>()

            if (!_shouldDownload.value) {
                _isDownloading.value = false
                if (!_isModelReady.value) _statusText.value = "DOWNLOAD CANCELLED."
                return@launch
            }

            if (ModelDownloader.isModelPresent(context)) {
                _isDownloading.value = false
                _statusText.value = "BOOTING ARIS ENGINE (LOADING NEURAL WEIGHTS)..."
                try {
                    arisBrain.initialize()
                    _isModelReady.value = true
                } catch (e: Exception) {
                    _statusText.value = "INITIALIZATION ERROR: ${e.message}"
                }
            } else {
                _isDownloading.value = true
                val partialFile = File(context.getExternalFilesDir(null), ModelDownloader.MODEL_FILE_NAME)
                if (partialFile.exists() && partialFile.length() > 0) {
                    val downloadedMB = String.format("%.1f MB", partialFile.length() / (1024.0 * 1024.0))
                    val progress = ((partialFile.length() * 100) / ModelDownloader.EXPECTED_TOTAL_BYTES).toInt().coerceIn(0, 100)
                    _downloadProgress.value = progress
                    _downloadedSizeText.value = downloadedMB
                    _statusText.value = "RESUMING DOWNLOAD [$downloadedMB]..."
                } else {
                    _statusText.value = "DOWNLOADING NEURAL WEIGHTS..."
                }

                val downloader = ModelDownloader(context)
                val downloadUrl = "https://huggingface.co/alokprasad573/aris-gemma-model/resolve/main/gemma-4-E2B-it.litertlm?download=true"

                val success = downloader.downloadGemmaModel(
                    downloadUrl = downloadUrl,
                    onStatusChange = { newStatus ->
                        _statusText.value = newStatus
                    },
                    onProgress = { progress, downloadedMB, totalMB ->
                        _downloadProgress.value = progress
                        _downloadedSizeText.value = downloadedMB
                        _totalSizeText.value = totalMB
                    }
                )

                _isDownloading.value = false
                if (success) {
                    _statusText.value = "BOOTING ARIS ENGINE (LOADING NEURAL WEIGHTS)..."
                    try {
                        arisBrain.initialize()
                        _isModelReady.value = true
                    } catch (e: Exception) {
                        _statusText.value = "INITIALIZATION ERROR: ${e.message}"
                    }
                } else {
                    if (!_shouldDownload.value) {
                        _statusText.value = "DOWNLOAD CANCELLED."
                    } else {
                        _statusText.value = "DOWNLOAD FAILED. CHECK INTERNET."
                    }
                }
            }
        }
    }

    fun abortDownload() {
        _shouldDownload.value = false
        _isDownloading.value = false
        _statusText.value = "DOWNLOAD CANCELLED."
        downloadJob?.cancel()
    }

    fun retryDownload() {
        _shouldDownload.value = true
        checkAndInitModel()
    }

    // Speech Recognizer Callback Handlers
    fun onSpeechReady() {
        _speechStatus.value = "LISTENING (SPEAK NOW)..."
    }

    fun onSpeechListening() {
        _speechStatus.value = "LISTENING..."
    }

    fun onSpeechRmsChanged(rmsdB: Float) {
        val normalized = ((rmsdB + 2f) / 10f).coerceIn(0f, 1f)
        _rmsLevel.value = normalized
    }

    fun onSpeechPartialResult(text: String) {
        if (_uiMode.value != ArisUiMode.PROCESSING) {
            _recognizedText.value = text
        }
    }

    fun onSpeechResult(text: String) {
        if (_uiMode.value != ArisUiMode.PROCESSING) {
            _recognizedText.value = text
            _uiMode.value = ArisUiMode.VERIFYING
            _speechStatus.value = "VERIFY INPUT"
            _rmsLevel.value = 0f
        }
    }

    fun onSpeechError(message: String) {
        if (_uiMode.value != ArisUiMode.PROCESSING) {
            _speechStatus.value = message
            _rmsLevel.value = 0f
            if (_recognizedText.value.isNotBlank()) {
                _uiMode.value = ArisUiMode.VERIFYING
            } else {
                _uiMode.value = ArisUiMode.READY
            }
        }
    }

    fun onSpeechEnd() {
        _rmsLevel.value = 0f
    }

    // User Interaction Actions
    fun onTextChanged(text: String) {
        _recognizedText.value = text
    }

    fun onStartListening() {
        arisTts.stop()
        _recognizedText.value = ""
        _responseText.value = ""
        _uiMode.value = ArisUiMode.LISTENING
        _speechStatus.value = "LISTENING..."
        _rmsLevel.value = 0f
    }

    fun onDoneSpeaking() {
        _uiMode.value = ArisUiMode.VERIFYING
        _speechStatus.value = "VERIFY INPUT"
    }

    fun onPauseListening() {
        arisTts.stop()
        _uiMode.value = ArisUiMode.READY
        _speechStatus.value = "READY"
        _rmsLevel.value = 0f
    }

    fun onReset() {
        arisTts.stop()
        _uiMode.value = ArisUiMode.READY
        _recognizedText.value = ""
        _responseText.value = ""
        _speechStatus.value = "READY"
        _rmsLevel.value = 0f
    }

    fun onPermissionDenied() {
        _speechStatus.value = "MICROPHONE PERMISSION REQUIRED"
        _uiMode.value = ArisUiMode.READY
    }

    // Double-submit and Empty-Input Protected Processing Pipeline
    fun sendVerifiedText(verifiedText: String) {
        val cleanText = verifiedText.trim()
        if (cleanText.isBlank()) {
            return
        }

        // Double-submit protection
        if (_uiMode.value == ArisUiMode.PROCESSING) {
            return
        }

        _uiMode.value = ArisUiMode.PROCESSING
        _speechStatus.value = "THINKING..."

        processJob?.cancel()
        processJob = viewModelScope.launch {
            try {
                // Route through ArisBrain for proper filtering and processing
                val result = arisBrain.process(cleanText)
                _responseText.value = result
                _uiMode.value = ArisUiMode.RESPONDED
                _speechStatus.value = "ARIS RESPONDED"
                arisTts.speak(result)
            } catch (e: Exception) {
                _responseText.value = "Error: ${e.message}"
                _uiMode.value = ArisUiMode.RESPONDED
                _speechStatus.value = "ERROR"
            }
        }
    }

    fun stopTts() {
        arisTts.stop()
    }

    override fun onCleared() {
        super.onCleared()
        downloadJob?.cancel()
        processJob?.cancel()
        arisTts.release()
        arisBrain.close()
    }
}
