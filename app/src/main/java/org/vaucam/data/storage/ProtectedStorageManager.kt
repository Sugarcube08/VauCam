package org.vaucam.data.storage

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

class ProtectedStorageManager(context: Context) {

    val protectedDir: File = context.filesDir.resolve("protected_media").also {
        check(it.exists() || it.mkdirs()) { "Could not create protected media directory" }
    }
    val stagingDir: File = context.cacheDir.resolve("backup_staging").also {
        check(it.exists() || it.mkdirs()) { "Could not create backup staging directory" }
    }

    fun createStagingFile(captureId: String): File {
        val safeId = captureId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File(stagingDir, "${safeId}_${System.currentTimeMillis()}.tmp")
    }

    fun streamToStagingWithDigest(
        sourceStream: InputStream,
        targetTempFile: File
    ): Pair<String, Long> {
        require(targetTempFile.canonicalFile.parentFile == stagingDir.canonicalFile) {
            "Staging target must be a direct child of the staging directory"
        }
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var totalBytes = 0L

        sourceStream.use { input ->
            FileOutputStream(targetTempFile).use { output ->
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    digest.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                    totalBytes += count
                }
                output.flush()
                output.fd.sync()
            }
        }
        return toHex(digest.digest()) to totalBytes
    }

    /** Only promote a staging child into the private protected directory. */
    fun promoteToProtected(stagingFile: File, finalFileName: String): File {
        require(stagingFile.canonicalFile.parentFile == stagingDir.canonicalFile) {
            "Source must be a direct child of the staging directory"
        }
        val safeName = finalFileName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(180)
        require(safeName.isNotBlank() && safeName != "." && safeName != "..") {
            "Invalid protected file name"
        }
        val finalFile = File(protectedDir, safeName)
        check(finalFile.canonicalFile.parentFile == protectedDir.canonicalFile) {
            "Destination must be a direct child of the protected directory"
        }
        try {
            Files.move(
                stagingFile.toPath(),
                finalFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: Exception) {
            if (!stagingFile.renameTo(finalFile)) {
                Files.copy(stagingFile.toPath(), finalFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                FileOutputStream(finalFile, true).use { it.fd.sync() }
                check(stagingFile.delete() || !stagingFile.exists()) {
                    "Could not remove staging file after protected copy"
                }
            }
        }
        check(finalFile.isFile) { "Protected file was not created" }
        return finalFile
    }

    fun cleanupStaging(maxAgeMs: Long = 3_600_000L) {
        val now = System.currentTimeMillis()
        stagingDir.listFiles()?.forEach { file ->
            if (file.isFile && now - file.lastModified() > maxAgeMs) file.delete()
        }
    }

    fun getProtectedFile(fileName: String): File? {
        val safeName = fileName.substringAfterLast('/').substringAfterLast('\\')
        val file = File(protectedDir, safeName)
        return if (file.canonicalFile.parentFile == protectedDir.canonicalFile && file.isFile) file else null
    }

    private fun toHex(bytes: ByteArray): String {
        val chars = CharArray(bytes.size * 2)
        val lookup = "0123456789abcdef"
        for (i in bytes.indices) {
            val value = bytes[i].toInt() and 0xFF
            chars[i * 2] = lookup[value ushr 4]
            chars[i * 2 + 1] = lookup[value and 0x0F]
        }
        return String(chars)
    }
}
