package com.aris.assistant.brain.gemma

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LiteRtmEngine(private val context: Context): GemmaEngine {

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var initialized = false

    override suspend fun initialize() = withContext(Dispatchers.IO) {
        if (initialized) return@withContext

        val modelPath = prepareModel()

        val engineConfig = EngineConfig(
            modelPath = modelPath,
            backend = Backend.CPU(),
            cacheDir = context.cacheDir.absolutePath
        )

        val newEngine = Engine(engineConfig)
        newEngine.initialize()
        engine = newEngine
        conversation = newEngine.createConversation()
        initialized = true
    }

    override suspend fun generate(prompt: String): String = withContext(Dispatchers.IO) {
        require(prompt.isNotBlank()) {
            "Prompt cannot be blank."
        }

        if (!initialized) {
            initialize()
        }

        check(initialized) {
            "LiteRtmEngine is not initialized."
        }

        val currentConversation = conversation ?: error("Conversation is not available.")

        val arisPrompt = """
            $ARIS_SYSTEM_PROMPT
            
            User: $prompt
        """.trimIndent()

        val response = currentConversation.sendMessage(arisPrompt)
        extractText(response.contents.contents)
    }

    override fun close() {
        conversation?.close()
        conversation = null

        engine?.close()
        engine = null

        initialized = false
    }

    override fun isReady(): Boolean {
        return initialized && engine?.isInitialized() == true && conversation?.isAlive == true
    }

    private fun extractText(contents: List<Content>): String {
        return contents
            .filterIsInstance<Content.Text>()
            .joinToString(separator = "") { it.text }
            .trim()
    }

    private fun prepareModel(): String {
        val modelFile = File(context.filesDir, MODEL_FILE_NAME)

        if (!modelFile.exists() || modelFile.length() == 0L) {
            context.assets.open(MODEL_FILE_NAME).use { input ->
                modelFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        return modelFile.absolutePath
    }

    companion object {
        private const val MODEL_FILE_NAME = "gemma-4-E2B-it.litertlm"
        private const val ARIS_SYSTEM_PROMPT = """
         You are ARIS, a personal voice assistant.
         
         IDENTITY:
         - Your name is ARIS.
         - ARIS is spelled A-R-I-S.
         - Never call yourself Aries or ARIES.
         - If the user asks your name, say: "I'm ARIS."
         - If the user asks for introduction, say: "I'm ARIS. Your personal voice assistant."
         
         LANGUAGE:
         - Respond in natural Hinglish by default.
         - Use a natural mixture of Hindi and English.
         - Prefer Hindi + English because your responses may be spoken aloud.
         - Use English technical terms when they are clearer.
         - If the user explicitly asks for Hindi, respond in Hindi.
         - If the user explicitly asks for English, respond in English.
         - Naturally match the user's language style.
         
         PERSONALITY:
         - Be intelligent, calm, helpful and concise.
         - Behave like a capable personal voice assistant.
         - Keep responses natural and conversational.
         - Do not unnecessarily repeat information.
         - Do not repeatedly introduce yourself unless the user asks.
         - Never claim to have access to a capability or tool that you do not actually have.
         """
    }
}