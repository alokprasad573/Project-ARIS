package com.aris.assistant.tts

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.aris.assistant.BuildConfig
import com.aris.assistant.brain.ArisBrain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ArisTTS(context: Context) {

    private val appContext = context.applicationContext
    private val client = OkHttpClient()
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val apiKey = BuildConfig.ARIS_API_KEY
    private val voiceModelTTS = BuildConfig.ARIS_MODEL_TTS
    private val model = "s2.1-pro-free"

    @Volatile
    private var closed = false

    private var mediaPlayer: MediaPlayer? = null

    fun speak(text: String) {
        if (closed) return

        val sanitizedText = ArisBrain.filterResponse(text)
        if (sanitizedText.isBlank()) return

        executor.execute {
            if (closed) return@execute

            var audioFile: File? = null

            try {
                val json = JSONObject().apply {
                    put("text", sanitizedText)
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

                val requestBody = json
                    .toString()
                    .toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("https://api.fish.audio/v1/tts")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("model", model)
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val error = response.body?.string()
                        Log.e(TAG, "Fish Audio request failed: ${response.code} $error")
                        return@use
                    }

                    val audioBytes = response.body?.bytes()
                    if (audioBytes.isNullOrEmpty()) {
                        Log.e(TAG, "Fish Audio returned an empty audio response")
                        return@use
                    }

                    audioFile = File.createTempFile(
                        "aris_tts_",
                        ".mp3",
                        appContext.cacheDir
                    )
                    audioFile!!.writeBytes(audioBytes)

                    if (closed) {
                        audioFile?.delete()
                        audioFile = null
                        return@use
                    }

                    playAudio(audioFile!!)
                    audioFile = null
                }
            } catch (e: Exception) {
                audioFile?.delete()

                if (!closed) {
                    Log.e(TAG, "TTS generation failed", e)
                }
            }
        }
    }

    private fun playAudio(file: File) {
        mainHandler.post {
            if (closed) {
                file.delete()
                return@post
            }

            stopCurrentPlayback()

            val player = MediaPlayer()
            mediaPlayer = player

            try {
                player.setDataSource(file.absolutePath)

                player.setOnPreparedListener { preparedPlayer ->
                    if (closed) {
                        if (mediaPlayer === preparedPlayer) {
                            mediaPlayer = null
                        }
                        preparedPlayer.release()
                        file.delete()
                        return@setOnPreparedListener
                    }

                    preparedPlayer.start()
                }

                player.setOnCompletionListener { completedPlayer ->
                    if (mediaPlayer === completedPlayer) {
                        mediaPlayer = null
                    }
                    completedPlayer.release()
                    file.delete()
                }

                player.setOnErrorListener { errorPlayer, _, _ ->
                    if (mediaPlayer === errorPlayer) {
                        mediaPlayer = null
                    }
                    errorPlayer.release()
                    file.delete()
                    true
                }

                player.prepareAsync()
            } catch (e: Exception) {
                if (mediaPlayer === player) {
                    mediaPlayer = null
                }
                player.release()
                file.delete()
                Log.e(TAG, "Unable to prepare TTS audio", e)
            }
        }
    }

    private fun stopCurrentPlayback() {
        val player = mediaPlayer ?: return
        mediaPlayer = null

        try {
            if (player.isPlaying) {
                player.stop()
            }
        } catch (_: Exception) {
        }

        try {
            player.reset()
        } catch (_: Exception) {
        }

        try {
            player.release()
        } catch (_: Exception) {
        }
    }

    fun close() {
        if (closed) return
        closed = true

        executor.shutdownNow()
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()

        mainHandler.post {
            stopCurrentPlayback()
        }
    }

    companion object {
        private const val TAG = "ArisTTS"
    }
}
