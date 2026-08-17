package com.aris.assistant.brain

import android.content.Context
import com.aris.assistant.brain.gemma.GemmaEngine
import com.aris.assistant.brain.gemma.LiteRtmEngine

class ArisBrain(context: Context) {
    private val gemmaEngine: GemmaEngine = LiteRtmEngine(context.applicationContext)

    suspend fun initialize() {
        gemmaEngine.initialize()
    }

    suspend fun process(input: String): String {
        require(input.isNotBlank()) {
            "Input cannot be blank."
        }

        if (!gemmaEngine.isReady()) {
            gemmaEngine.initialize()
        }

        val rawResponse = gemmaEngine.generate(input)
        return filterResponse(rawResponse)
    }

    fun close() {
        gemmaEngine.close()
    }

    fun isReady(): Boolean {
        return gemmaEngine.isReady()
    }

    companion object {
        /**
         * Filters the response so that only alphanumeric words (a-z, A-Z, 0-9) and spaces are retained.
         * Strips markdown symbols (*, #, _, `, ~, etc.), emojis, and special characters.
         */
        fun filterResponse(text: String): String {
            if (text.isBlank()) return ""

            // 1. Remove markdown links: [text](url) -> text
            var cleaned = text.replace(Regex("\\[(.*?)\\]\\(.*?\\)"), "$1")

            // 2. Keep only letters (a-z, A-Z), numbers (0-9), and whitespace
            cleaned = cleaned.replace(Regex("[^a-zA-Z0-9\\s]"), " ")

            // 3. Normalize multiple spaces and trim
            return cleaned.replace(Regex("\\s+"), " ").trim()
        }
    }
}
