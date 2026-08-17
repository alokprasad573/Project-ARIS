package com.aris.assistant.planner

interface PlanGenerator {

    fun generate(request: String): List<PlanStep>
}