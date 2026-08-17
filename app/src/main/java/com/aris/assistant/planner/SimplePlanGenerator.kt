package com.aris.assistant.planner

class SimplePlanGenerator : PlanGenerator {

    override fun generate(request: String): List<PlanStep> {
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
}