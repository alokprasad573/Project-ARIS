package com.aris.assistant.planner

import com.aris.assistant.brain.gemma.GemmaEngine
import org.json.JSONArray
import org.json.JSONObject

class GemmaPlanGenerator(
    private val gemmaEngine: GemmaEngine
) : PlanGenerator {

    override suspend fun generate(request: String): List<PlanStep> {
        require(request.isNotBlank()) {
            "Request cannot be blank."
        }

        val prompt = """
            You are ARIS task planner.

            Convert the user's request into a structured execution plan.

            Return ONLY valid JSON.
            Do not use markdown.
            Do not wrap the JSON in code fences.

            Required format:
            {
              "steps": [
                {
                  "id": "step_1",
                  "description": "Open Chrome",
                  "order": 1,
                  "dependencies": []
                }
              ]
            }

            Rules:
            - steps must be ordered
            - id must be unique
            - order starts at 1
            - dependencies must contain existing step IDs
            - do not invent unnecessary steps

            User request:
            $request
        """.trimIndent()

        val response = gemmaEngine.generate(prompt)

        return parsePlan(response)
    }

    private fun parsePlan(response: String): List<PlanStep> {
        val root = JSONObject(response)
        val steps = root.getJSONArray("steps")

        return buildList {
            for (index in 0 until steps.length()) {
                val item = steps.getJSONObject(index)

                val dependenciesJson =
                    item.optJSONArray("dependencies") ?: JSONArray()

                val dependencies = buildList {
                    for (i in 0 until dependenciesJson.length()) {
                        add(dependenciesJson.getString(i))
                    }
                }

                add(
                    PlanStep(
                        id = item.getString("id"),
                        description = item.getString("description"),
                        order = item.getInt("order"),
                        dependencies = dependencies
                    )
                )
            }
        }
    }
}