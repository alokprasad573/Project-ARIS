package com.aris.assistant.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

class ArisSpeechRecognizer(
    private val context: Context,
    private val listener: Listener
) {
    interface Listener {
        fun onReady()
        fun onListening()
        fun onRmsChanged(rmsdB: Float)
        fun onPartialResult(text: String)
        fun onResult(text: String)
        fun onError(message: String)
        fun onEnd()
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null

    private fun initRecognizer(): SpeechRecognizer? {
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            // Ignore
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            return null
        }

        return try {
            SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(recognitionListener)
            }
        } catch (e: Exception) {
            null
        }
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            mainHandler.post { listener.onReady() }
        }

        override fun onBeginningOfSpeech() {
            mainHandler.post { listener.onListening() }
        }

        override fun onRmsChanged(rmsdB: Float) {
            mainHandler.post { listener.onRmsChanged(rmsdB) }
        }

        override fun onBufferReceived(buffer: ByteArray?) {
            // Not Required
        }

        override fun onEndOfSpeech() {
            mainHandler.post { listener.onEnd() }
        }

        override fun onError(error: Int) {
            val errorMsg = getErrorMessage(error)
            mainHandler.post {
                listener.onError(errorMsg)
                listener.onEnd()
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()?.trim() ?: ""

            mainHandler.post {
                if (text.isNotBlank()) {
                    listener.onResult(text)
                } else {
                    listener.onError("I couldn't catch that. Please speak again.")
                }
                listener.onEnd()
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()?.trim() ?: ""

            if (text.isNotBlank()) {
                mainHandler.post { listener.onPartialResult(text) }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {
            // Not required
        }
    }

    fun startListening() {
        mainHandler.post {
            try {
                speechRecognizer = initRecognizer()

                val recognizer = speechRecognizer
                if (recognizer == null) {
                    listener.onError("Speech recognition is not available on this device.")
                    return@post
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    val langTag = Locale.getDefault().toLanguageTag()
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                }

                recognizer.startListening(intent)
            } catch (exception: Exception) {
                listener.onError("Unable to start speech recognition: ${exception.message}")
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                listener.onError("Error stopping microphone: ${e.message}")
            }
        }
    }

    fun cancel() {
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                // Ignore
            }
            listener.onEnd()
        }
    }

    fun destroy() {
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun getErrorMessage(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Microphone audio error."
            SpeechRecognizer.ERROR_CLIENT -> "Speech recognizer client error."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
            SpeechRecognizer.ERROR_NETWORK -> "Network error. Please check your internet connection."
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout."
            SpeechRecognizer.ERROR_NO_MATCH -> "I couldn't understand that. Please try speaking again."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy."
            SpeechRecognizer.ERROR_SERVER -> "Speech recognition server error."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Please speak louder."
            else -> "Speech recognition error ($error)."
        }
    }
}
