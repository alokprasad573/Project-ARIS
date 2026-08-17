package com.aris.assistant.tts

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import com.aris.assistant.BuildConfig
import com.aris.assistant.brain.ArisBrain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File

class ArisTTS(private val context: Context) {
    private val client = OkHttpClient()
    private val apiKey = BuildConfig.ARIS_API_KEY
    private val voiceModelTTS = BuildConfig.ARIS_MODEL_TTS
    private val model = "s2.1-pro-free"

    fun speak(text: String) {
        val sanitizedText = ArisBrain.filterResponse(text)
        if (sanitizedText.isBlank()) return

        Thread {
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

                val requestBody = json.toString().toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("https://api.fish.audio/v1/tts")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("model", model)
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val audioBytes = response.body?.bytes() ?: return@use
                        val audioFile = File(
                            context.cacheDir, "aris_response.mp3"
                        )
                        audioFile.writeBytes(audioBytes)
                        playAudio(audioFile)
                    } else {
                        val error = response.body?.string()
                        println("ARIS FISH ERROR: ${response.code} $error")
                    }
                }

            } catch (e: Exception) {
                println("ARIS TTS EXCEPTION : ${e.message}")
                e.printStackTrace()
            }
        }.start()
    }

    private fun playAudio(file: File) {
        Handler(Looper.getMainLooper()).post {
            try {
                val player = MediaPlayer()
                player.setDataSource(file.absolutePath)
                player.setOnPreparedListener { it.start() }
                player.setOnCompletionListener {
                    it.release()
                    file.delete()
                }
                player.setOnErrorListener { mp, _, _ ->
                    mp.release()
                    file.delete()
                    true
                }
                player.prepareAsync()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
