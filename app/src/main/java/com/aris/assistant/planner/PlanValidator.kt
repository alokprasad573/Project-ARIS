package com.aris.assistant.planner

class PlanValidator {

    fun validate(plan: TaskPlan): Result<TaskPlan> {
        if (plan.goal.isBlank()) {
            return Result.failure(
                IllegalArgumentException("Plan goal cannot be empty")
            )
        }

        if (plan.steps.isEmpty()) {
            return Result.failure(
                IllegalArgumentException("Plan must contain at least one step")
            )
        }

        if (!hasUniqueStepIds(plan.steps)) {
            return Result.failure(
                IllegalArgumentException("Plan contains duplicate step IDs")
            )
        }

        if (!hasValidStepOrder(plan.steps)) {
            return Result.failure(
                IllegalArgumentException("Plan contains invalid step order")
            )
        }

        if (!hasValidDependencies(plan.steps)) {
            return Result.failure(
                IllegalArgumentException("Plan contains invalid dependencies")
            )
        }

        if (hasCircularDependencies(plan.steps)) {
            return Result.failure(
                IllegalArgumentException("Plan contains circular dependencies")
            )
        }

        return Result.success(
            plan.copy(
                status = PlanStatus.VALIDATED
            )
        )
    }

    private fun hasUniqueStepIds(
        steps: List<PlanStep>
    ): Boolean {
        return steps.map { it.id }.toSet().size == steps.size
    }

    private fun hasValidStepOrder(
        steps: List<PlanStep>
    ): Boolean {
        val expectedOrders = (1..steps.size).toList()

        val actualOrders = steps
            .map { it.order }
            .sorted()

        return actualOrders == expectedOrders
    }

    private fun hasValidDependencies(
        steps: List<PlanStep>
    ): Boolean {
        val stepIds = steps.map { it.id }.toSet()

        return steps.all { step ->
            step.dependencies.all { dependencyId ->
                dependencyId in stepIds &&
                        dependencyId != step.id
            }
        }
    }

    private fun hasCircularDependencies(
        steps: List<PlanStep>
    ): Boolean {

        val dependencies = steps.associate { step ->
            step.id to step.dependencies
        }

        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()

        fun visit(stepId: String): Boolean {

            if (stepId in visiting) {
                return true
            }

            if (stepId in visited) {
                return false
            }

            visiting.add(stepId)

            for (dependencyId in dependencies[stepId].orEmpty()) {
                if (visit(dependencyId)) {
                    return true
                }
            }

            visiting.remove(stepId)
            visited.add(stepId)

            return false
        }

        return steps.any { step ->
            visit(step.id)
        }
    }
}