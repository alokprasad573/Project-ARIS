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
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var initialized = false

    override suspend fun initialize() = withContext(Dispatchers.IO) {
        if (initialized) return@withContext

        val modelPath = getPersistentModelPath()
            ?: throw IllegalStateException("Model not found. Please download first.")

        Log.d(TAG, "Starting LiteRT-LM initialization for model: $modelPath")

        val newEngine = initializeEngineWithFallback(modelPath)

        try {
            val newConversation = newEngine.createConversation()

            engine = newEngine
            conversation = newConversation
            initialized = true

            Log.d(
                TAG,
                "ARIS Neural Engine is fully booted and conversation is ready."
            )
        } catch (e: Exception) {
            try {
                newEngine.close()
            } catch (_: Exception) {
            }

            throw e
        }
    }

    private fun initializeEngineWithFallback(modelPath: String): Engine {
        var gpuEngine: Engine? = null
        try {
            Log.d(TAG, "Attempting GPU initialization with shader cache: ${context.cacheDir.absolutePath}")
            val gpuDuration = measureTimeMillis {
                gpuEngine = Engine(
                    EngineConfig(
                        modelPath = modelPath,
                        backend = Backend.GPU(),
                        cacheDir = context.cacheDir.absolutePath
                    )
                ).apply {
                    initialize()
                }
            }
            Log.d(TAG, "GPU initialization succeeded in ${gpuDuration}ms")
            return gpuEngine!!
        } catch (e: Throwable) {
            Log.w(TAG, "GPU initialization failed (${e.message}). Cleaning up GPU context...", e)
            try {
                gpuEngine?.close()
            } catch (_: Exception) {}
        }

        // CPU Fallback logic
        Log.d(TAG, "Falling back to CPU backend...")
        val cpuEngine: Engine
        val cpuDuration = measureTimeMillis {
            cpuEngine = Engine(
                EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.CPU(),
                    cacheDir = context.cacheDir.absolutePath
                )
            ).apply {
                initialize()
            }
        }
        Log.d(TAG, "CPU initialization succeeded in ${cpuDuration}ms")
        return cpuEngine
    }

    private fun getPersistentModelPath(): String? {
        return ModelDownloader.getPersistentModelPath(context)
    }

    override suspend fun generate(prompt: String): String = withContext(Dispatchers.IO) {
        val safePrompt = if (prompt.isBlank()) "Hello" else prompt
        if (!initialized) { initialize() }
        check(initialized) { "LiteRtmEngine is not initialized." }

        val currentConversation = conversation ?: error("Conversation is not available")
        val arisPrompt = """
            $ARIS_SYSTEM_PROMPT
            User: $safePrompt
            ARIS:
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
        private const val TAG = "LiteRtmEngine"
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
- Respond only in Hindi + English.
- Keep answers clear, direct, and conversational so they are ready to be spoken aloud.
- Do not repeat yourself or include unnecessary disclaimers.

PERSONALITY:
- Be intelligent, calm, helpful and concise.
- Behave like a capable personal voice assistant.
- Give direct answers without unnecessary explanations.
"""
    }
}
