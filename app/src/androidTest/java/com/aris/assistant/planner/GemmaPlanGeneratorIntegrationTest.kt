package com.aris.assistant.planner

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aris.assistant.brain.gemma.LiteRtmEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GemmaPlanGeneratorIntegrationTest {

    @Test
    fun generate_withRealGemma_returnsValidPlan() = runBlocking {
        val context =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext

        val engine = LiteRtmEngine(context)

        try {
            engine.initialize()

            assertTrue(engine.isReady())

            val generator = GemmaPlanGenerator(engine)

            val steps = generator.generate(
                "Open Chrome and search for ARIS"
            )

            assertFalse(steps.isEmpty())

            steps.forEach { step ->
                assertTrue(step.id.isNotBlank())
                assertTrue(step.description.isNotBlank())
                assertTrue(step.order > 0)
            }
        } finally {
            engine.close()
        }
    }
}