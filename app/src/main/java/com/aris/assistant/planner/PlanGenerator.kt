package com.aris.assistant.planner

interface PlanGenerator {

   suspend fun generate(request: String): List<PlanStep>
}