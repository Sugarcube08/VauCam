package org.vaucam.data.storage

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

class ProtectedStorageManager(context: Context) {

    val protectedDir: File = context.filesDir.resolve("protected_media").also { it.mkdirs() }
    val stagingDir: File = context.cacheDir.resolve("backup_staging").also { it.mkdirs() }

    /**
     * Creates a unique temporary file in the staging directory.
     */
    fun createStagingFile(captureId: String): File {
        return File(stagingDir, "${captureId}_${System.currentTimeMillis()}.tmp")
    }

    /**
     * Streams data from [sourceStream] into [targetTempFile], simultaneously
     * computing the SHA-256 hash and byte count. Flushes and syncs to disk.
     */
    fun streamToStagingWithDigest(
        sourceStream: InputStream,
        targetTempFile: File
    ): Pair<String, Long> {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var totalBytes = 0L

        sourceStream.use { input ->
            FileOutputStream(targetTempFile).use { output ->
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                    output.write(buffer, 0, bytesRead)
                    totalBytes += bytesRead
                }
                output.flush()
                output.fd.sync() // Ensure physical disk persistence
            }
        }

        val hashHex = toHex(digest.digest())
        return Pair(hashHex, totalBytes)
    }

    /**
     * Atomically promotes a verified staging file to the permanent protected directory.
     */
    fun promoteToProtected(stagingFile: File, finalFileName: String): File {
        val finalFile = File(protectedDir, finalFileName)
        try {
            Files.move(
                stagingFile.toPath(),
                finalFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: Exception) {
            // Fallback for filesystems that do not support ATOMIC_MOVE across mount points
            if (!stagingFile.renameTo(finalFile)) {
                Files.copy(stagingFile.toPath(), finalFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                stagingFile.delete()
            }
        }
        return finalFile
    }

    /**
     * Cleans up orphaned staging temporary files older than [maxAgeMs].
     */
    fun cleanupStaging(maxAgeMs: Long = 3600_000L) {
        val now = System.currentTimeMillis()
        stagingDir.listFiles()?.forEach { file ->
            if (file.isFile && (now - file.lastModified()) > maxAgeMs) {
                file.delete()
            }
        }
    }

    fun getProtectedFile(fileName: String): File? {
        val file = File(protectedDir, fileName)
        return if (file.exists()) file else null
    }

    private fun toHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        val lookup = "0123456789abcdef"
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            hexChars[i * 2] = lookup[v ushr 4]
            hexChars[i * 2 + 1] = lookup[v and 0x0F]
        }
        return String(hexChars)
    }
}
