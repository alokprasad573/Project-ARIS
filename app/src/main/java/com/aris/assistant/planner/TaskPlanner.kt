package com.aris.assistant.planner

class TaskPlanner(private val planGenerator: PlanGenerator) {

    fun createPlan(request: String): TaskPlan {
        require(request.isNotBlank()) {
            "Planning request cannot be empty"
        }

        val cleanRequest = request.trim()
        val steps = planGenerator.generate(cleanRequest)

        return TaskPlan(
            id = generatePlanId(),
            goal = cleanRequest,
            steps = steps,
            status = PlanStatus.CREATED
        )
    }

    private fun generateSteps(request: String): List<PlanStep> {
        val parts = request
            .split(Regex("\\s+and\\s+|\\s+then\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        return parts.mapIndexed { index, description ->
            PlanStep(
                id = "step_${index + 1}",
                description = description,
                order = index + 1,
                dependencies = if (index == 0) {
                    emptyList()
                } else {
                    listOf("step_$index")
                }
            )
        }
    }

    private fun generatePlanId(): String {
        return "plan_${System.currentTimeMillis()}"
    }
}