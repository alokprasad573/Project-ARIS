package com.aris.assistant.brain

import com.aris.assistant.brain.gemma.ModelDownloader
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ModelDownloaderTest {

    @Test
    fun modelDirectory_isAvailable() {
        val context = InstrumentationRegistry
            .getInstrumentation()
            .targetContext

        val directory = ModelDownloader.getModelDirectory(context)

        assertTrue(
            "Model directory should exist",
            directory.exists()
        )

        assertTrue(
            "Model directory should be a directory",
            directory.isDirectory
        )
    }

    @Test
    fun expectedModelSize_isCorrect() {
        assertEquals(
            2588147712L,
            ModelDownloader.EXPECTED_TOTAL_BYTES
        )
    }

    @Test
    fun modelFile_isValidWhenExactSizeMatches() {
        val context = InstrumentationRegistry
            .getInstrumentation()
            .targetContext

        val modelFile = File(
            ModelDownloader.getModelDirectory(context),
            ModelDownloader.MODEL_FILE_NAME
        )

        if (modelFile.exists()) {
            assertEquals(
                ModelDownloader.EXPECTED_TOTAL_BYTES,
                modelFile.length()
            )
        }
    }
}