package com.aris.assistant.planner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanValidatorTest {

    private val validator = PlanValidator()

    @Test
    fun validate_acceptsValidPlan() {
        val plan = TaskPlan(
            id = "plan_1",
            goal = "Open Chrome and search for ARIS",
            steps = listOf(
                PlanStep(
                    id = "step_1",
                    description = "Open Chrome",
                    order = 1
                ),
                PlanStep(
                    id = "step_2",
                    description = "Search for ARIS",
                    order = 2,
                    dependencies = listOf("step_1")
                )
            )
        )

        val result = validator.validate(plan)

        assertTrue(result.isSuccess)
        assertEquals(
            PlanStatus.VALIDATED,
            result.getOrThrow().status
        )
    }

    @Test
    fun validate_rejectsEmptyGoal() {
        val plan = TaskPlan(
            id = "plan_1",
            goal = "",
            steps = listOf(
                PlanStep(
                    id = "step_1",
                    description = "Open Chrome",
                    order = 1
                )
            )
        )

        val result = validator.validate(plan)

        assertTrue(result.isFailure)
    }

    @Test
    fun validate_rejectsEmptySteps() {
        val plan = TaskPlan(
            id = "plan_1",
            goal = "Open Chrome",
            steps = emptyList()
        )

        val result = validator.validate(plan)

        assertTrue(result.isFailure)
    }

    @Test
    fun validate_rejectsDuplicateStepIds() {
        val plan = TaskPlan(
            id = "plan_1",
            goal = "Open Chrome",
            steps = listOf(
                PlanStep(
                    id = "step_1",
                    description = "Open Chrome",
                    order = 1
                ),
                PlanStep(
                    id = "step_1",
                    description = "Search for ARIS",
                    order = 2
                )
            )
        )

        val result = validator.validate(plan)

        assertTrue(result.isFailure)
    }

    @Test
    fun validate_rejectsInvalidDependency() {
        val plan = TaskPlan(
            id = "plan_1",
            goal = "Search for ARIS",
            steps = listOf(
                PlanStep(
                    id = "step_1",
                    description = "Search for ARIS",
                    order = 1,
                    dependencies = listOf("step_99")
                )
            )
        )

        val result = validator.validate(plan)

        assertTrue(result.isFailure)
    }

    @Test
    fun validate_rejectsSelfDependency() {
        val plan = TaskPlan(
            id = "plan_1",
            goal = "Open Chrome",
            steps = listOf(
                PlanStep(
                    id = "step_1",
                    description = "Open Chrome",
                    order = 1,
                    dependencies = listOf("step_1")
                )
            )
        )

        val result = validator.validate(plan)

        assertTrue(result.isFailure)
    }

    @Test
    fun validate_rejectsCircularDependency() {
        val plan = TaskPlan(
            id = "plan_1",
            goal = "Perform task",
            steps = listOf(
                PlanStep(
                    id = "step_1",
                    description = "First step",
                    order = 1,
                    dependencies = listOf("step_3")
                ),
                PlanStep(
                    id = "step_2",
                    description = "Second step",
                    order = 2,
                    dependencies = listOf("step_1")
                ),
                PlanStep(
                    id = "step_3",
                    description = "Third step",
                    order = 3,
                    dependencies = listOf("step_2")
                )
            )
        )

        val result = validator.validate(plan)

        assertTrue(result.isFailure)
    }
}