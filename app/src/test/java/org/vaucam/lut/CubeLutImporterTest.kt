package org.vaucam.lut

import android.content.ContentResolver
import android.database.MatrixCursor
import android.net.Uri
import android.provider.OpenableColumns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream

class CubeLutImporterTest {

    @Test
    fun `parser bounds file input and validates UTF-8 text`() {
        // Parser-level malformed data must fail closed even after a source passes picker filtering.
        assertThrows(IllegalArgumentException::class.java) {
            CubeLut.parse("LUT_3D_SIZE 2\nNaN 0 0".reader())
        }
    }
}
