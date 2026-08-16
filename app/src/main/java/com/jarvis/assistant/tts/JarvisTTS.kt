package com.jarvis.assistant.tts

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import com.jarvis.assistant.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File

class JarvisTTS(private val context: Context) {
    private val client = OkHttpClient()
    private val apiKey = BuildConfig.JARVIS_API_KEY
    private val voiceModelTTS = BuildConfig.JARVIS_MODEL_TTS
    private val model = "s2.1-pro-free"

    fun speak(text: String) {
        Thread {
            try {
                val json = JSONObject().apply {
                    put("text", text)
                    put("reference_id", voiceModelTTS)
                    put("format", "mp3")
                    put("prosody", JSONObject().apply {
                        put("speed", 1.15)
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
                            context.cacheDir, "jarvis_response.mp3"
                        )
                        audioFile.writeBytes(audioBytes)
                        playAudio(audioFile)
                    } else {
                        val error = response.body?.string()
                        println("JARVIS FISH ERROR: ${response.code} $error")
                    }
                }

            } catch (e: Exception) {
                println("JARVIS TTS EXCEPTION : ${e.message}")
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
