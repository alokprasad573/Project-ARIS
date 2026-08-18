package com.aris.assistant.brain.gemma

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.system.measureTimeMillis

class LiteRtmEngine(private val context: Context) : GemmaEngine {

    private val TAG = "LiteRtmEngine"
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var initialized = false

    override suspend fun initialize() = withContext(Dispatchers.IO) {
        if (initialized) return@withContext

        val modelPath = getPersistentModelPath()
            ?: throw IllegalStateException("Model not found. Please download first.")

        Log.d(TAG, "Starting LiteRT-LM initialization for model: $modelPath")

        val newEngine = try {
            Log.d(TAG, "Attempting GPU initialization with shader cache: ${context.cacheDir.absolutePath}")
            var engineInstance: Engine
            val gpuDuration = measureTimeMillis {
                val gpuConfig = EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.GPU(),
                    cacheDir = context.cacheDir.absolutePath
                )
                engineInstance = Engine(gpuConfig)
                engineInstance.initialize()
            }
            Log.d(TAG, "GPU initialization succeeded in ${gpuDuration}ms")
            engineInstance
        } catch (e: Throwable) {
            Log.w(TAG, "GPU initialization failed (${e.message}). Falling back to CPU backend...", e)
            var engineInstance: Engine
            val cpuDuration = measureTimeMillis {
                val cpuConfig = EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.CPU(),
                    cacheDir = context.cacheDir.absolutePath
                )
                engineInstance = Engine(cpuConfig)
                engineInstance.initialize()
            }
            Log.d(TAG, "CPU initialization succeeded in ${cpuDuration}ms")
            engineInstance
        }

        engine = newEngine
        conversation = newEngine.createConversation()
        initialized = true
        Log.d(TAG, "ARIS Neural Engine is fully booted and conversation is ready.")
    }

    private fun getPersistentModelPath(): String? {
        return ModelDownloader.getPersistentModelPath(context)
    }

    override suspend fun generate(prompt: String): String = withContext(Dispatchers.IO) {
        require(prompt.isNotBlank()) { "Prompt cannot be blank." }
        if (!initialized) { initialize() }
        check(initialized) { "LiteRtmEngine is not initialized." }

        val currentConversation = conversation ?: error("Conversation is not available")
        val arisPrompt = """
            $ARIS_SYSTEM_PROMPT
            User: $prompt
        """.trimIndent()

        val response = currentConversation.sendMessage(arisPrompt)
        extractText(response.contents.contents)
    }

    override fun close() {
        try {
            conversation?.close()
        } catch (_: Exception) {}
        conversation = null

        try {
            engine?.close()
        } catch (_: Exception) {}
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

    companion object {
        private const val ARIS_SYSTEM_PROMPT = """
You are ARIS, a personal voice assistant.

IDENTITY:
- Your name is ARIS.
- ARIS is spelled A-R-I-S.
- Never call yourself Aries or ARIES.
- If the user asks your name, say: "I'm ARIS."
- If the user asks for introduction, say: "I'm ARIS. Your personal voice assistant."
- If the user says "your", they mean your name / ARIS. Do not interpret "your" as a general question.

LANGUAGE & OUTPUT RULES:
- Only use letters (a-z, A-Z) and numbers (0-9).
- Do NOT use markdown symbols (such as *, #, _, `, ~), bullet points, emojis, or special characters.
- Use natural Hinglish (using only English/Latin letters) or English.
- Keep answers clear, direct, and conversational so they are ready to be spoken aloud.
- Do not repeat yourself or include unnecessary disclaimers.

PERSONALITY:
- Be intelligent, calm, helpful and concise.
- Behave like a capable personal voice assistant.
- Give direct answers without unnecessary explanations.
         """
    }
}