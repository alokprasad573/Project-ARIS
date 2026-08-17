package com.aris.assistant.planner

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanGeneratorTest {

    private val generator = SimplePlanGenerator()

    @Test
    fun generate_createsSingleStep() {
        runBlocking {
            val steps = generator.generate("Open Chrome")

            assertEquals(1, steps.size)
            assertEquals("step_1", steps[0].id)
            assertEquals("Open Chrome", steps[0].description)
            assertEquals(1, steps[0].order)
            assertTrue(steps[0].dependencies.isEmpty())
        }
    }

    @Test
    fun generate_createsMultipleSteps() {
        runBlocking {
            val steps = generator.generate(
                "Open Chrome and search for ARIS"
            )

            assertEquals(2, steps.size)

            assertEquals(
                "Open Chrome",
                steps[0].description
            )

            assertEquals(
                "search for ARIS",
                steps[1].description
            )

            assertEquals(
                listOf("step_1"),
                steps[1].dependencies
            )
        }
    }

    @Test
    fun generate_supportsThen() {
        runBlocking {
            val steps = generator.generate(
                "Open Chrome then search for ARIS"
            )

            assertEquals(2, steps.size)
            assertEquals("Open Chrome", steps[0].description)
            assertEquals("search for ARIS", steps[1].description)
        }
    }
}