package com.aris.assistant.tts

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.aris.assistant.BuildConfig
import com.aris.assistant.brain.ArisBrain
import kotlinx.coroutines.*
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class ArisTTS(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    interface Listener {
        fun onPlaybackStarted() {}
        fun onPlaybackCompleted() {}
        fun onError(message: String) {}
    }

    private val apiKey = BuildConfig.ARIS_API_KEY
    private val voiceModelTTS = BuildConfig.ARIS_MODEL_TTS
    private val model = "s2.1-pro-free"
    private val TAG = "ARIS TTS"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val ttsScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var currentJob: Job? = null

    @Volatile
    private var activeCall: Call? = null

    private val playerLock = Any()
    private var mediaPlayer: MediaPlayer? = null
    private var activeTempFile: File? = null

    var listener: Listener? = null

    companion object {
        const val MAX_RETRIES = 2
        const val INITIAL_BACKOFF_MS = 500L
        const val TEMP_FILE_NAME = "aris_response.mp3"
    }

    fun speak(text: String?) {
        val rawText = text ?: ""
        val sanitizedText = if (rawText.isBlank()) " " else ArisBrain.filterResponse(rawText)

        if (sanitizedText.isBlank()) {
            Log.d(TAG, "ARIS TTS: Empty text after sanitization, skipping.")
            return
        }

        // Cancel previous pending network request and stop existing playback
        stop()

        Log.d(TAG, "ARIS TTS: Request started for: \"$sanitizedText\"")

        currentJob = ttsScope.launch {
            val audioBytes = fetchAudioWithRetry(sanitizedText)

            if (!isActive) {
                Log.d(TAG, "ARIS TTS: Job cancelled after fetch, aborting playback.")
                return@launch
            }

            if (audioBytes != null && audioBytes.isNotEmpty()) {
                saveAndPlay(audioBytes)
            } else {
                Log.w(TAG, "ARIS TTS: Failed to obtain audio after retries or fallback.")
                notifyError("TTS synthesis failed or network unavailable")
            }
        }
    }

    private suspend fun fetchAudioWithRetry(text: String): ByteArray? {
        var attempt = 0
        var currentBackoff = INITIAL_BACKOFF_MS

        while (attempt <= MAX_RETRIES && currentCoroutineContext().isActive) {
            attempt++
            try {
                Log.d(TAG, "ARIS TTS: Fetch attempt $attempt / ${MAX_RETRIES + 1}")
                val bytes = executeTtsRequest(text)
                if (bytes != null) {
                    return bytes
                }
            } catch (e: IOException) {
                Log.w(TAG, "ARIS TTS: Network exception on attempt $attempt: ${e.message}")
                if (attempt > MAX_RETRIES || !currentCoroutineContext().isActive) {
                    return null
                }
                delay(currentBackoff)
                currentBackoff *= 2
            } catch (e: Exception) {
                Log.e(TAG, "ARIS TTS: Non-retryable error on attempt $attempt: ${e.message}", e)
                return null
            }
        }
        return null
    }

    @Throws(IOException::class)
    private fun executeTtsRequest(text: String): ByteArray? {
        val json = JSONObject().apply {
            put("text", text)
            put("reference_id", voiceModelTTS)
            put("format", "mp3")
            put("prosody", JSONObject().apply {
                put("speed", 1.1)
                put("volume", 5)
            })
            put("temperature", 0.70)
            put("top_p", 0.70)
            put("normalize", true)
        }

        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://api.fish.audio/v1/tts")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("model", model)
            .post(requestBody)
            .build()

        val call = client.newCall(request)
        activeCall = call

        try {
            call.execute().use { response ->
                Log.d(TAG, "ARIS TTS: HTTP response = ${response.code}")
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes()
                    if (bytes != null && bytes.isNotEmpty()) {
                        Log.d(TAG, "ARIS TTS: Audio bytes received = ${bytes.size}")
                        return bytes
                    }
                } else {
                    val errorBody = response.body?.string()
                    Log.w(TAG, "ARIS TTS HTTP ERROR: ${response.code} $errorBody")
                    // If server error (5xx) or rate limit (429), throw IOException to trigger retry
                    if (response.code in 500..599 || response.code == 429) {
                        throw IOException("Server/RateLimit error: HTTP ${response.code}")
                    }
                }
            }
        } finally {
            if (activeCall == call) {
                activeCall = null
            }
        }
        return null
    }

    private fun saveAndPlay(audioBytes: ByteArray) {
        synchronized(playerLock) {
            try {
                val tempFile = File(context.cacheDir, TEMP_FILE_NAME)
                if (tempFile.exists()) {
                    tempFile.delete()
                }
                tempFile.writeBytes(audioBytes)
                activeTempFile = tempFile
                Log.d(TAG, "ARIS TTS: MP3 saved = ${tempFile.absolutePath} (${audioBytes.size} bytes)")
                playAudio(tempFile)
            } catch (e: Exception) {
                Log.e(TAG, "ARIS TTS: File write / save error: ${e.message}", e)
                notifyError("Failed to save audio cache: ${e.message}")
            }
        }
    }

    private fun playAudio(file: File) {
        mainHandler.post {
            synchronized(playerLock) {
                stopPlaybackInternal()

                try {
                    val player = MediaPlayer()
                    mediaPlayer = player

                    player.setDataSource(file.absolutePath)
                    player.setOnPreparedListener { mp ->
                        synchronized(playerLock) {
                            if (mediaPlayer == mp) {
                                Log.d(TAG, "ARIS TTS: Playback started")
                                mp.start()
                                notifyStarted()
                            } else {
                                mp.release()
                            }
                        }
                    }

                    player.setOnCompletionListener { mp ->
                        synchronized(playerLock) {
                            Log.d(TAG, "ARIS TTS: Playback completed")
                            if (mediaPlayer == mp) {
                                stopPlaybackInternal()
                            }
                            cleanupTempFile()
                            notifyCompleted()
                        }
                    }

                    player.setOnErrorListener { mp, what, extra ->
                        synchronized(playerLock) {
                            Log.w(TAG, "ARIS TTS: Playback error ($what, $extra)")
                            if (mediaPlayer == mp) {
                                stopPlaybackInternal()
                            }
                            cleanupTempFile()
                            notifyError("MediaPlayer error: $what, $extra")
                            true
                        }
                    }

                    player.prepareAsync()
                } catch (e: Exception) {
                    Log.e(TAG, "ARIS TTS EXCEPTION (Playback): ${e.message}", e)
                    stopPlaybackInternal()
                    cleanupTempFile()
                    notifyError("Playback initialization failed: ${e.message}")
                }
            }
        }
    }

    fun stop() {
        // Cancel active coroutine job
        currentJob?.cancel()
        currentJob = null

        // Cancel active OkHttp call if any
        try {
            activeCall?.cancel()
            activeCall = null
        } catch (_: Exception) {}

        // Stop media playback and cleanup
        mainHandler.post {
            synchronized(playerLock) {
                stopPlaybackInternal()
                cleanupTempFile()
            }
        }
    }

    private fun stopPlaybackInternal() {
        mediaPlayer?.let { player ->
            try {
                if (player.isPlaying) {
                    player.stop()
                }
            } catch (_: Exception) {}
            try {
                player.reset()
            } catch (_: Exception) {}
            try {
                player.release()
            } catch (_: Exception) {}
            Log.d(TAG, "ARIS TTS: MediaPlayer stopped and released")
        }
        mediaPlayer = null
    }

    private fun cleanupTempFile() {
        activeTempFile?.let { file ->
            try {
                if (file.exists()) {
                    file.delete()
                    Log.d(TAG, "ARIS TTS: Temp audio file deleted")
                }
            } catch (_: Exception) {}
        }
        activeTempFile = null
    }

    fun release() {
        stop()
        ttsScope.cancel()
    }

    fun close() {
        release()
    }

    private fun notifyStarted() {
        mainHandler.post { listener?.onPlaybackStarted() }
    }

    private fun notifyCompleted() {
        mainHandler.post { listener?.onPlaybackCompleted() }
    }

    private fun notifyError(message: String) {
        mainHandler.post { listener?.onError(message) }
    }
}
