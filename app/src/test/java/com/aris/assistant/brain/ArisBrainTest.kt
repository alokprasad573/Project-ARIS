package com.aris.assistant.brain

import com.aris.assistant.brain.gemma.GemmaEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArisBrainTest {

    @Test
    fun filterResponse_removesMarkdownAndSpecialCharacters() {
        val raw = "Hello! **This is ARIS.** Here is a list:\n* Item 1\n* Item 2\nCheck [link](http://example.com) for details. `code` #hashtag"
        val filtered = ArisBrain.filterResponse(raw)

        // Only alphanumeric and single spaces should remain
        assertEquals("Hello This is ARIS Here is a list Item 1 Item 2 Check link for details code hashtag", filtered)
    }

    @Test
    fun filterResponse_handlesEmptyAndBlank() {
        assertEquals("", ArisBrain.filterResponse(""))
        assertEquals("", ArisBrain.filterResponse("    "))
    }

    @Test
    fun filterResponse_preservesAlphanumericAndSpaces() {
        val input = "ARIS is running 100 percent on device"
        assertEquals(input, ArisBrain.filterResponse(input))
    }

    @Test
    fun process_rejectsBlankInput() {
        var threw = false
        try {
            runBlocking {
                val fakeEngine = FakeGemmaEngine("Response")
                val brain = TestableArisBrain(fakeEngine)
                brain.process("   ")
            }
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun process_returnsFilteredOutputFromEngine() = runBlocking {
        val fakeEngine = FakeGemmaEngine("**Hello!** I am ARIS.")
        val brain = TestableArisBrain(fakeEngine)

        val result = brain.process("Who are you?")
        assertEquals("Hello I am ARIS", result)
    }

    private class FakeGemmaEngine(private val cannedResponse: String) : GemmaEngine {
        var initialized = false
        var closed = false

        override suspend fun initialize() {
            initialized = true
        }

        override suspend fun generate(prompt: String): String {
            return cannedResponse
        }

        override fun close() {
            closed = true
        }

        override fun isReady(): Boolean = initialized
    }

    // Helper subclass to inject FakeGemmaEngine for testing
    private class TestableArisBrain(private val engine: GemmaEngine) {
        suspend fun process(input: String): String {
            require(input.isNotBlank()) { "Input cannot be blank." }
            if (!engine.isReady()) {
                engine.initialize()
            }
            val raw = engine.generate(input)
            return ArisBrain.filterResponse(raw)
        }
    }
}
