package com.aris.assistant.planner

class TaskPlanner(private val planGenerator: PlanGenerator) {

    suspend fun createPlan(request: String): TaskPlan {
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

    private fun generatePlanId(): String {
        return "plan_${System.currentTimeMillis()}"
    }
}