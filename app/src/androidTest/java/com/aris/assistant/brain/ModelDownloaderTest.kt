package com.aris.assistant.brain

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aris.assistant.brain.gemma.ModelDownloader
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ModelDownloaderTest {

    @Test
    fun modelDownloader_verifiesTargetFolderAccess() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val externalDir = context.getExternalFilesDir(null)

        assertNotNull("External files directory should be available", externalDir)
        assertTrue("External directory must exist or be creatable", externalDir!!.exists() || externalDir.mkdirs())

        val targetFile = File(externalDir, ModelDownloader.MODEL_FILE_NAME)
        assertTrue("Parent directory must be writable", targetFile.parentFile?.canWrite() == true)
    }

    @Test
    fun modelDownloader_checksConstantsAndPresenceMethod() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(ModelDownloader.MODEL_FILE_NAME.isNotBlank())
        assertTrue(ModelDownloader.MIN_VALID_MODEL_SIZE > 0)
        
        // Method should execute without throwing exception
        val isPresent = ModelDownloader.isModelPresent(context)
        val path = ModelDownloader.getPersistentModelPath(context)
        if (isPresent) {
            assertNotNull(path)
        }
    }
}
