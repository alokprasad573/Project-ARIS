package com.aris.assistant.planner

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class GemmaModelAssetTest {

    @Test
    fun modelFile_isAvailableAndValidSize() {
        val context = InstrumentationRegistry
            .getInstrumentation()
            .targetContext

        val expectedSize = 2588147712L
        val fileName = "gemma-4-E2B-it.litertlm"

        val externalFile = File(context.getExternalFilesDir(null), fileName)
        val internalFile = File(context.filesDir, fileName)

        val targetFile = when {
            externalFile.exists() -> externalFile
            internalFile.exists() -> internalFile
            else -> externalFile
        }

        assertTrue(
            "Model file not found in ${externalFile.absolutePath} or ${internalFile.absolutePath}",
            targetFile.exists()
        )

        assertTrue(
            "Model file size must be > 2 GB",
            targetFile.length() > 2000000000L
        )

        assertEquals(
            "Model file size does not match expected downloaded size",
            expectedSize,
            targetFile.length()
        )
    }
}