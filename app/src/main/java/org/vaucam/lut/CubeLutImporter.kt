package org.vaucam.lut

import android.content.ContentResolver
import android.net.Uri
import java.io.IOException

/**
 * Reads a selected .cube document using the URI permission granted by the system picker.
 * Callers should persist the URI permission if they intend to reopen the source later.
 */
object CubeLutImporter {

    private const val MAX_LUT_BYTES = 16 * 1024 * 1024

    @Throws(IOException::class, IllegalArgumentException::class)
    fun import(resolver: ContentResolver, uri: Uri): CubeLut {
        val name = queryDisplayName(resolver, uri)
        require(name == null || name.endsWith(".cube", ignoreCase = true)) {
            "Select a .cube LUT file"
        }

        val stream = resolver.openInputStream(uri)
            ?: throw IOException("Could not open selected LUT document")
        val raw = stream.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                total += count
                require(total <= MAX_LUT_BYTES) { "LUT file exceeds 16 MiB limit" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        val text = raw.toString(Charsets.UTF_8.name())
        require(!text.contains('\u0000')) { "LUT file is not valid UTF-8 text" }
        return CubeLut.parse(text.reader())
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? {
        return runCatching {
            resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) cursor.getString(index) else null
                    } else null
                }
        }.getOrNull()
    }
}
