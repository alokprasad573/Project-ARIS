package com.aris.assistant.planner

data class TaskPlan(
    val id: String,
    val goal: String,
    val steps: List<PlanStep>,
    val status: PlanStatus = PlanStatus.CREATED
)

data class PlanStep(
    val id: String,
    val description: String,
    val order: Int,
    val dependencies: List<String> = emptyList()
)

enum class PlanStatus {
    CREATED,
    VALIDATED,
    EXECUTING,
    COMPLETED,
    FAILED,
    CANCELLED
}