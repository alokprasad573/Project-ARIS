package com.aris.assistant.planner

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aris.assistant.brain.gemma.LiteRtmEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GemmaEngineInitializationTest {

    @Test
    fun initialize_realGemma_succeeds() = runBlocking {
        val context = InstrumentationRegistry
            .getInstrumentation()
            .targetContext

        val engine = LiteRtmEngine(context)

        try {
            engine.initialize()

            assertTrue(engine.isReady())
        } finally {
            engine.close()
        }
    }
}