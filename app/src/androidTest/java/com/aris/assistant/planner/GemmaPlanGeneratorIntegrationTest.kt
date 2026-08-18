package com.aris.assistant.planner

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aris.assistant.brain.gemma.LiteRtmEngine
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GemmaPlanGeneratorIntegrationTest {

    companion object {
        private lateinit var engine: LiteRtmEngine

        @BeforeClass
        @JvmStatic
        fun setUpClass() = runBlocking {
            val context = InstrumentationRegistry
                .getInstrumentation()
                .targetContext
            engine = LiteRtmEngine(context)
            engine.initialize()
        }

        @AfterClass
        @JvmStatic
        fun tearDownClass() {
            if (::engine.isInitialized) {
                engine.close()
            }
        }
    }

    @Test
    fun generate_withRealGemma_returnsValidPlan() = runBlocking {
        assertTrue("Gemma engine should be ready", engine.isReady())

        val generator = GemmaPlanGenerator(engine)

        val steps = generator.generate(
            "Open Chrome and search for ARIS"
        )

        assertFalse("Plan steps should not be empty", steps.isEmpty())

        steps.forEach { step ->
            assertTrue("Step ID should not be blank", step.id.isNotBlank())
            assertTrue("Step description should not be blank", step.description.isNotBlank())
            assertTrue("Step order must be positive", step.order > 0)
        }
    }
}