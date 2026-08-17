package com.aris.assistant.planner

import com.aris.assistant.brain.gemma.GemmaEngine
import org.json.JSONObject

class GemmaPlanGenerator(
    private val gemmaEngine: GemmaEngine
) : PlanGenerator {

    override suspend fun generate(request: String): List<PlanStep> {
        require(request.isNotBlank()) {
            "Planning request cannot be empty"
        }

        val prompt = buildPrompt(request.trim())

        val response = gemmaEngine.generate(prompt)

        return parsePlan(response)
    }

    private fun buildPrompt(request: String): String {
        return """
            You are the ARIS task planning system.

            Convert the user's request into an ordered list of executable steps.

            User request:
            $request

            Return ONLY valid JSON using this exact structure:

            {
              "steps": [
                {
                  "id": "step_1",
                  "description": "first step",
                  "order": 1,
                  "dependencies": []
                }
              ]
            }

            Rules:
            - Every step must have a unique ID.
            - IDs must be step_1, step_2, step_3, etc.
            - order starts at 1.
            - dependencies must contain IDs of previous steps.
            - Do not include markdown.
            - Do not include explanations.
            - Return valid JSON only.
        """.trimIndent()
    }

    private fun parsePlan(response: String): List<PlanStep> {
        val json = JSONObject(response)
        val stepsJson = json.getJSONArray("steps")

        val steps = mutableListOf<PlanStep>()

        for (index in 0 until stepsJson.length()) {
            val stepJson = stepsJson.getJSONObject(index)

            val dependencies = mutableListOf<String>()
            val dependenciesJson = stepJson.optJSONArray("dependencies")

            if (dependenciesJson != null) {
                for (dependencyIndex in 0 until dependenciesJson.length()) {
                    dependencies.add(
                        dependenciesJson.getString(dependencyIndex)
                    )
                }
            }

            steps.add(
                PlanStep(
                    id = stepJson.getString("id"),
                    description = stepJson.getString("description"),
                    order = stepJson.getInt("order"),
                    dependencies = dependencies
                )
            )
        }

        return steps
    }
}