package org.vaucam.lut

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.StringReader

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CubeLutBitmapProcessorTest {

    @Test
    fun `identity LUT preserves RGB and alpha within rounding tolerance`() {
        val lut = CubeLut.parse(StringReader("""
            TITLE "Identity"
            LUT_3D_SIZE 2
            0 0 0
            1 0 0
            0 1 0
            1 1 0
            0 0 1
            1 0 1
            0 1 1
            1 1 1
        """.trimIndent()))
        val source = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        source.setPixel(0, 0, Color.argb(123, 128, 64, 192))

        val result = CubeLutBitmapProcessor.apply(source, lut)
        val actual = result.getPixel(0, 0)

        assertEquals(123, Color.alpha(actual))
        assertEquals(128, Color.red(actual))
        assertEquals(64, Color.green(actual))
        assertEquals(192, Color.blue(actual))
        assertEquals(Color.argb(123, 128, 64, 192), source.getPixel(0, 0))

        result.recycle()
        source.recycle()
    }

    @Test
    fun `LUT output clamps out of range values to valid display channels`() {
        val lut = CubeLut(
            title = "Clamp",
            size = 2,
            domainMin = floatArrayOf(0f, 0f, 0f),
            domainMax = floatArrayOf(1f, 1f, 1f),
            values = FloatArray(2 * 2 * 2 * 3) { if (it % 3 == 0) 2f else -1f }
        )
        val source = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        source.setPixel(0, 0, Color.WHITE)

        val result = CubeLutBitmapProcessor.apply(source, lut)
        val actual = result.getPixel(0, 0)

        assertEquals(255, Color.red(actual))
        assertEquals(0, Color.green(actual))
        assertEquals(0, Color.blue(actual))

        result.recycle()
        source.recycle()
    }
}
