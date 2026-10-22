package org.vaucam.data.storage

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest

object HashVerifier {

    private const val BUFFER_SIZE = 64 * 1024 // 64 KB streaming buffer

    /**
     * Computes the SHA-256 digest and total byte count from an input stream.
     * Consumes and closes the stream.
     */
    fun computeSha256(inputStream: InputStream): Pair<String, Long> {
        return inputStream.use { stream ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            var totalBytes = 0L

            while (stream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
                totalBytes += bytesRead
            }

            val hashBytes = digest.digest()
            val hexString = toHex(hashBytes)
            Pair(hexString, totalBytes)
        }
    }

    /**
     * Computes SHA-256 digest and total byte count for a local file.
     */
    fun computeSha256(file: File): Pair<String, Long> {
        return computeSha256(FileInputStream(file))
    }

    /**
     * Computes SHA-256 digest and byte count for a content URI.
     */
    fun computeSha256(context: Context, uri: Uri): Pair<String, Long>? {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.let { stream ->
                computeSha256(stream)
            }
        }.getOrNull()
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
