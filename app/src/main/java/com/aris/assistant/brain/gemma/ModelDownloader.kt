package com.aris.assistant.brain.gemma

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

class ModelDownloader(private val context: Context) {

    private val TAG = "ModelDownloader"

    suspend fun downloadGemmaModel(
        downloadUrl: String,
        onStatusChange: (status: String) -> Unit = {},
        onProgress: (progressPercent: Int, downloadedMB: String, totalMB: String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val destinationFile = File(getModelDirectory(context), MODEL_FILE_NAME)

        // Ensure directory exists
        destinationFile.parentFile?.mkdirs()

        // If already completely downloaded, return immediately
        if (isValidModelFile(destinationFile)) {
            val totalMBStr = String.format("%.1f MB", destinationFile.length() / (1024.0 * 1024.0))
            withContext(Dispatchers.Main) {
                onProgress(100, totalMBStr, totalMBStr)
            }
            Log.d(TAG, "Model already exists and is complete: ${destinationFile.absolutePath}")
            return@withContext true
        }

        Log.d(TAG, "Starting/resuming download from: $downloadUrl")
        Log.d(TAG, "Destination: ${destinationFile.absolutePath}")

        var retryCount = 0
        val maxRetries = 100 // Allow persistent auto-reconnection
        var backoffMs = 2000L

        while (coroutineContext.isActive && retryCount < maxRetries) {
            val existingBytes = if (destinationFile.exists()) destinationFile.length() else 0L

            // Check if download completed in a previous attempt
            if (isValidModelFile(destinationFile)) {
                val totalMBStr = String.format("%.1f MB", existingBytes / (1024.0 * 1024.0))
                withContext(Dispatchers.Main) {
                    onProgress(100, totalMBStr, totalMBStr)
                }
                Log.d(TAG, "Download completed successfully: ${destinationFile.absolutePath}")
                return@withContext true
            }

            var connection: HttpURLConnection? = null
            var outputStream: FileOutputStream? = null

            try {
                if (existingBytes > 0) {
                    val resumedMB = String.format("%.1f MB", existingBytes / (1024.0 * 1024.0))
                    Log.d(TAG, "Resuming download from byte: $existingBytes ($resumedMB)")
                    withContext(Dispatchers.Main) {
                        onStatusChange("RESUMING DOWNLOAD [$resumedMB]...")
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onStatusChange("CONNECTING TO SERVER...")
                    }
                }

                var currentUrl = downloadUrl
                var redirectCount = 0
                val maxRedirects = 6

                while (redirectCount < maxRedirects) {
                    val url = URL(currentUrl)
                    connection = (url.openConnection() as HttpURLConnection).apply {
                        instanceFollowRedirects = false
                        connectTimeout = 30000
                        readTimeout = 30000
                        setRequestProperty(
                            "User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                        )
                        if (existingBytes > 0) {
                            setRequestProperty("Range", "bytes=$existingBytes-")
                        }
                    }
                    connection.connect()

                    val responseCode = connection.responseCode
                    Log.d(TAG, "Response Code: $responseCode for URL: $currentUrl")

                    if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                        responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                        responseCode == 307 || responseCode == 308
                    ) {
                        currentUrl = connection.getHeaderField("Location")
                            ?: throw IOException("Redirect location header missing")
                        Log.d(TAG, "Redirecting to: $currentUrl")
                        connection.disconnect()
                        redirectCount++
                        continue
                    }
                    break
                }

                if (redirectCount >= maxRedirects) {
                    throw IOException("Too many redirects")
                }

                val responseCode = connection?.responseCode ?: -1
                val isPartial = responseCode == HttpURLConnection.HTTP_PARTIAL // 206
                val isOk = responseCode == HttpURLConnection.HTTP_OK // 200

                if (isPartial && existingBytes > 0) {
                    val contentRange = connection?.getHeaderField("Content-Range")
                    if (contentRange == null || !contentRange.startsWith("bytes $existingBytes-")) {
                        throw IOException("Content-Range mismatch or missing. Requested starts at $existingBytes, but got: $contentRange")
                    }
                }

                if (responseCode == 416) {
                    // Range Not Satisfiable: file might already be complete or corrupted
                    if (isValidModelFile(destinationFile)) {
                        return@withContext true
                    } else {
                        // Reset and retry from 0
                        Log.w(TAG, "Range 416 returned. Resetting partial file and retrying from beginning.")
                        destinationFile.delete()
                        continue
                    }
                }

                if (!isPartial && !isOk) {
                    throw IOException("Server returned HTTP $responseCode: ${connection?.responseMessage}")
                }

                val append = isPartial && existingBytes > 0
                val serverContentLength = connection?.contentLengthLong ?: -1L

                val totalExpectedBytes = when {
                    isPartial && serverContentLength > 0 -> existingBytes + serverContentLength
                    isOk && serverContentLength > 0 -> serverContentLength
                    else -> EXPECTED_TOTAL_BYTES
                }

                val totalMBStr = String.format("%.1f MB", totalExpectedBytes / (1024.0 * 1024.0))

                val inputStream = connection?.inputStream ?: throw IOException("Input stream is null")
                outputStream = FileOutputStream(destinationFile, append)

                val data = ByteArray(64 * 1024)
                var currentDownloaded = if (append) existingBytes else 0L
                var lastReportedBytes = currentDownloaded
                var lastReportedTime = System.currentTimeMillis()
                val updateIntervalBytes = 512 * 1024L
                val updateIntervalMs = 200L
                var count: Int

                withContext(Dispatchers.Main) {
                    onStatusChange("DOWNLOADING NEURAL WEIGHTS...")
                }

                while (inputStream.read(data).also { count = it } != -1) {
                    if (!coroutineContext.isActive) {
                        Log.d(TAG, "Download cancelled during data read loop.")
                        outputStream.flush()
                        outputStream.close()
                        inputStream.close()
                        connection?.disconnect()
                        return@withContext false
                    }

                    outputStream.write(data, 0, count)
                    currentDownloaded += count.toLong()

                    val currentTime = System.currentTimeMillis()
                    val progress = if (totalExpectedBytes > 0) {
                        ((currentDownloaded * 100) / totalExpectedBytes).toInt().coerceIn(0, 100)
                    } else {
                        0
                    }

                    if (currentDownloaded - lastReportedBytes >= updateIntervalBytes ||
                        currentTime - lastReportedTime >= updateIntervalMs ||
                        progress == 100
                    ) {
                        lastReportedBytes = currentDownloaded
                        lastReportedTime = currentTime

                        val downloadedMBStr = String.format("%.1f MB", currentDownloaded / (1024.0 * 1024.0))
                        withContext(Dispatchers.Main) {
                            onProgress(progress, downloadedMBStr, totalMBStr)
                        }
                    }
                }

                inputStream.close()
                connection?.disconnect()
                connection = null

                if (!isValidModelFile(destinationFile)) {
                    throw IOException("Download finished but file is incomplete or size mismatch. " +
                            "Expected: $EXPECTED_TOTAL_BYTES, Actual: ${destinationFile.length()}")
                }

                // Reset backoff ONLY on successful completion of a valid file
                retryCount = 0
                backoffMs = 2000L

                val finalMBStr = String.format("%.1f MB", destinationFile.length() / (1024.0 * 1024.0))
                withContext(Dispatchers.Main) {
                    onProgress(100, finalMBStr, finalMBStr)
                }
                Log.d(TAG, "Model download complete! Size: ${destinationFile.length()} bytes")
                return@withContext true

            } catch (e: Exception) {
                if (!coroutineContext.isActive) {
                    Log.d(TAG, "Download cancelled during exception handling: ${e.message}")
                    try {
                        outputStream?.flush()
                        outputStream?.close()
                    } catch (_: Exception) {}
                    try {
                        connection?.disconnect()
                    } catch (_: Exception) {}
                    return@withContext false
                }
                Log.w(TAG, "Download interrupted (${e.javaClass.simpleName}: ${e.message}). Will retry...", e)

                retryCount++
                val currentSizeMB = if (destinationFile.exists()) {
                    String.format("%.1f MB", destinationFile.length() / (1024.0 * 1024.0))
                } else {
                    "0 MB"
                }

                val statusMessage = when (e) {
                    is UnknownHostException -> "NO INTERNET CONNECTION. RETRYING [$currentSizeMB]..."
                    is SocketTimeoutException -> "CONNECTION TIMED OUT. RECONNECTING [$currentSizeMB]..."
                    else -> "CONNECTION BROKE. RETRYING [$currentSizeMB]..."
                }

                withContext(Dispatchers.Main) {
                    onStatusChange(statusMessage)
                }

                delay(backoffMs)
                backoffMs = (backoffMs * 1.5).toLong().coerceAtMost(15000L)
            }
        }

        Log.e(TAG, "Download failed after maximum retry attempts")
        return@withContext false
    }

    companion object {
        const val MODEL_FILE_NAME = "gemma-4-E2B-it.litertlm"
        const val EXPECTED_TOTAL_BYTES = 2588147712L // ~2.58 GB exact size

        fun getModelDirectory(context: Context): File {
            return context.getExternalFilesDir(null) ?: context.filesDir
        }

        private fun isValidModelFile(file: File): Boolean {
            if (!file.exists() || !file.isFile) return false
            if (file.length() != EXPECTED_TOTAL_BYTES) return false

            return try {
                file.inputStream().use { input ->
                    val header = ByteArray(16)
                    val bytesRead = input.read(header)

                    bytesRead == header.size &&
                            header.any { it.toInt() != 0 }
                }
            } catch (e: Exception) {
                Log.w("ModelDownloader", "Model integrity check failed: ${e.message}")
                false
            }
        }

        /**
         * Checks whether the model is already downloaded and present in persistent storage.
         * Returns the absolute path if available, or null otherwise.
         */
        fun getPersistentModelPath(context: Context): String? {
            val externalFile = File(getModelDirectory(context), MODEL_FILE_NAME)
            if (isValidModelFile(externalFile)) {
                return externalFile.absolutePath
            }
            val internalFile = File(context.filesDir, MODEL_FILE_NAME)
            if (isValidModelFile(internalFile)) {
                return internalFile.absolutePath
            }
            return null
        }

        /**
         * Returns true if a valid model file is found on device.
         */
        fun isModelPresent(context: Context): Boolean {
            return getPersistentModelPath(context) != null
        }
    }
}