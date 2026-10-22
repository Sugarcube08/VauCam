package org.vaucam.lut

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.StringReader

class CubeLutTest {
    @Test
    fun `parses valid 2x2x2 LUT in red fastest order`() {
        val lut = CubeLut.parse(StringReader("""
            TITLE "Identity 2"
            LUT_3D_SIZE 2
            DOMAIN_MIN 0.0 0.0 0.0
            DOMAIN_MAX 1.0 1.0 1.0
            0 0 0
            1 0 0
            0 1 0
            1 1 0
            0 0 1
            1 0 1
            0 1 1
            1 1 1
        """.trimIndent()))

        assertEquals("Identity 2", lut.title)
        assertEquals(2, lut.size)
        assertArrayEquals(floatArrayOf(1f, 0f, 0f), lut.sample(1, 0, 0), 0f)
        assertArrayEquals(floatArrayOf(0f, 0f, 1f), lut.sample(0, 0, 1), 0f)
        assertArrayEquals(floatArrayOf(1f, 1f, 1f), lut.sample(1, 1, 1), 0f)
    }

    @Test
    fun `rejects missing size directive`() {
        assertThrows(IllegalArgumentException::class.java) {
            CubeLut.parse(StringReader("0 0 0\n"))
        }
    }

    @Test
    fun `rejects 1D LUTs explicitly`() {
        assertThrows(IllegalArgumentException::class.java) {
            CubeLut.parse(StringReader("LUT_1D_SIZE 2\n"))
        }
    }

    @Test
    fun `rejects incomplete LUT data`() {
        assertThrows(IllegalArgumentException::class.java) {
            CubeLut.parse(StringReader("""
                LUT_3D_SIZE 2
                0 0 0
            """.trimIndent()))
        }
    }

    @Test
    fun `rejects unsupported dimensions`() {
        assertThrows(IllegalArgumentException::class.java) {
            CubeLut.parse(StringReader("LUT_3D_SIZE 65\n"))
        }
    }

    @Test
    fun `rejects non finite values`() {
        assertThrows(IllegalArgumentException::class.java) {
            CubeLut.parse(StringReader("""
                LUT_3D_SIZE 2
                NaN 0 0
            """.trimIndent()))
        }
    }
}
