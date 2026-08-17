package com.aris.assistant.brain.gemma

interface GemmaEngine {
    suspend fun initialize()
    suspend fun generate(prompt: String): String
    fun close()
    fun isReady(): Boolean
}