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

        println("ARIS GEMMA: Initializing CPU backend...")
        println("ARIS GEMMA: Model = $modelPath")

        val cpuConfig = EngineConfig(
            modelPath = modelPath,
            backend = Backend.CPU(),
            cacheDir = context.cacheDir.absolutePath
        )
        val newEngine = Engine(cpuConfig)

        newEngine.initialize()

        engine = newEngine
        conversation = newEngine.createConversation()
        initialized = true

        println("ARIS GEMMA: CPU backend initialized successfully")
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

        val currentConversation = conversation?: error("Conversation is not avaliable")
        val arisPrompt = """
            $ARIS_SYSTEM_PROMPT
            
            User: $prompt
        """.trimIndent()

        println("ARIS GEMMA: Sending prompt...")

        val response = currentConversation.sendMessage(arisPrompt)
        val result = extractText(response.contents.contents)
        println("ARIS GEMMA: Response generated")

        result
    }

    override fun close() {
        conversation?.close()
        conversation = null

        engine?.close()
        engine = null

        initialized = false
        println("ARIS GEMMA: Engine closed")
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

            println("ARIS GEMMA: Copying model...")
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
            println("ARIS GEMMA: Model copied successfully")
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