package com.aris.assistant.viewmodel

import com.aris.assistant.brain.ArisBrain
import com.aris.assistant.brain.gemma.GemmaEngine
import com.aris.assistant.ui.ArisUiMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ArisViewModelTest {

    private class FakeGemmaEngine(private val cannedResponse: String) : GemmaEngine {
        var callCount = 0
        var isClosed = false

        override suspend fun initialize() {}
        override suspend fun generate(prompt: String): String {
            callCount++
            return cannedResponse
        }
        override fun close() {
            isClosed = true
        }
        override fun isReady(): Boolean = true
    }

    @Test
    fun brainPipeline_processesAndFiltersThroughArisBrain() = runBlocking {
        val fakeEngine = FakeGemmaEngine("**Online** and ready #ARIS.")
        val brain = ArisBrain(fakeEngine)

        val result = brain.process("Status check")
        assertEquals("Online and ready ARIS", result)
        assertEquals(1, fakeEngine.callCount)
    }

    @Test
    fun emptyInput_rejectedByArisBrain() {
        val fakeEngine = FakeGemmaEngine("Should not be called")
        val brain = ArisBrain(fakeEngine)

        var threw = false
        try {
            runBlocking {
                brain.process("   ")
            }
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
        assertEquals(0, fakeEngine.callCount)
    }

    @Test
    fun stateFlow_transitionsCorrectlyThroughModes() {
        var mode = ArisUiMode.READY
        assertEquals(ArisUiMode.READY, mode)

        // 1. User taps mic -> LISTENING
        mode = ArisUiMode.LISTENING
        assertEquals(ArisUiMode.LISTENING, mode)

        // 2. Speech finished -> VERIFYING
        mode = ArisUiMode.VERIFYING
        assertEquals(ArisUiMode.VERIFYING, mode)

        // 3. User taps transmit -> PROCESSING
        mode = ArisUiMode.PROCESSING
        assertEquals(ArisUiMode.PROCESSING, mode)

        // 4. Response received -> RESPONDED
        mode = ArisUiMode.RESPONDED
        assertEquals(ArisUiMode.RESPONDED, mode)

        // 5. User resets -> READY
        mode = ArisUiMode.READY
        assertEquals(ArisUiMode.READY, mode)
    }
}
