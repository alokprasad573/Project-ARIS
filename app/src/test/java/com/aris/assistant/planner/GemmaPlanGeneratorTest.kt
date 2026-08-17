package com.aris.assistant.planner

import com.aris.assistant.brain.gemma.GemmaEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GemmaPlanGeneratorTest {

    @Test
    fun generate_parsesPlanReturnedByGemma() = runBlocking {
        val engine = FakeGemmaEngine(
            response = """
                {
                  "steps": [
                    {
                      "id": "step_1",
                      "description": "Open Chrome",
                      "order": 1,
                      "dependencies": []
                    },
                    {
                      "id": "step_2",
                      "description": "Search for ARIS",
                      "order": 2,
                      "dependencies": ["step_1"]
                    }
                  ]
                }
            """.trimIndent()
        )
        val generator = GemmaPlanGenerator(engine)

        val steps = generator.generate("Open Chrome and search for ARIS")

        assertEquals(2, steps.size)
        assertEquals("Open Chrome", steps[0].description)
        assertEquals(listOf("step_1"), steps[1].dependencies)
        assertTrue(engine.lastPrompt.contains("Open Chrome and search for ARIS"))
        assertFalse(engine.lastPrompt.isBlank())
    }

    private class FakeGemmaEngine(
        private val response: String
    ) : GemmaEngine {
        var lastPrompt: String = ""
            private set

        override suspend fun initialize() = Unit

        override suspend fun generate(prompt: String): String {
            lastPrompt = prompt
            return response
        }

        override fun close() = Unit

        override fun isReady(): Boolean = true
    }
}
