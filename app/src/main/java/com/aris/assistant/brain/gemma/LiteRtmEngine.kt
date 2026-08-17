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

        var modelPath = prepareModel(forceCopy = false)

        val newEngine = try {
            // 1. Try GPU first (standard for Gemma .litertlm)
            val gpuConfig = EngineConfig(
                modelPath = modelPath,
                backend = Backend.GPU(),
                cacheDir = context.cacheDir.absolutePath
            )
            val eng = Engine(gpuConfig)
            eng.initialize()
            eng
        } catch (gpuError: Exception) {
            gpuError.printStackTrace()
            try {
                // 2. Try CPU fallback
                val cpuConfig = EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.CPU(),
                    cacheDir = context.cacheDir.absolutePath
                )
                val eng = Engine(cpuConfig)
                eng.initialize()
                eng
            } catch (cpuError: Exception) {
                cpuError.printStackTrace()
                // 3. Re-copy model if the local file was corrupted or incomplete
                modelPath = prepareModel(forceCopy = true)
                val retryConfig = EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.GPU(),
                    cacheDir = context.cacheDir.absolutePath
                )
                val eng = Engine(retryConfig)
                eng.initialize()
                eng
            }
        }

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

        val arisPrompt = """
            $ARIS_SYSTEM_PROMPT
            
            User: $prompt
        """.trimIndent()

        try {
            val currentConversation = conversation ?: error("Conversation is not available.")
            val response = currentConversation.sendMessage(arisPrompt)
            extractText(response.contents.contents)
        } catch (runtimeError: Exception) {
            runtimeError.printStackTrace()
            // If GPU/OpenCL failed during inference, fallback to CPU and retry
            val modelPath = prepareModel(forceCopy = false)
            val cpuConfig = EngineConfig(
                modelPath = modelPath,
                backend = Backend.CPU(),
                cacheDir = context.cacheDir.absolutePath
            )
            val cpuEngine = Engine(cpuConfig)
            cpuEngine.initialize()
            engine?.close()
            engine = cpuEngine
            val newConversation = cpuEngine.createConversation()
            conversation = newConversation
            val retryResponse = newConversation.sendMessage(arisPrompt)
            extractText(retryResponse.contents.contents)
        }
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

    private fun prepareModel(forceCopy: Boolean = false): String {
        val modelFile = File(context.filesDir, MODEL_FILE_NAME)

        val needsCopy = forceCopy || !modelFile.exists() || modelFile.length() != MODEL_EXPECTED_SIZE

        if (needsCopy) {
            if (modelFile.exists()) {
                modelFile.delete()
            }
            context.assets.open(MODEL_FILE_NAME).use { input ->
                modelFile.outputStream().use { output ->
                    val buffer = ByteArray(128 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                    output.flush()
                }
            }
        }
        return modelFile.absolutePath
    }

    companion object {
        private const val MODEL_FILE_NAME = "gemma-4-E2B-it.litertlm"
        private const val MODEL_EXPECTED_SIZE = 2588147712L
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