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

        return gemmaEngine.generate(input)
    }

    fun close() {
        gemmaEngine.close()
    }

    fun isReady(): Boolean {
        return gemmaEngine.isReady()
    }
}
