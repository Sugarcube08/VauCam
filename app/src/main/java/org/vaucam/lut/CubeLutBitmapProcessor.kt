package org.vaucam.lut

import android.graphics.Bitmap
import kotlin.math.floor

/**
 * Applies a 3D LUT to an ARGB bitmap using trilinear interpolation.
 *
 * Processing is deliberately separate from the camera preview pipeline: it creates an
 * edited still while leaving the original capture (including any future RAW/DNG output)
 * untouched. Call from a background dispatcher.
 */
object CubeLutBitmapProcessor {

    fun apply(source: Bitmap, lut: CubeLut): Bitmap {
        require(source.width > 0 && source.height > 0) { "Source bitmap is empty" }
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
            ?: throw IllegalStateException("Could not allocate bitmap for LUT processing")

        val row = IntArray(source.width)
        val size = lut.size
        val maxIndex = size - 1
        val domainMin = lut.domainMin
        val domainMax = lut.domainMax
        val values = lut.values

        fun channelAt(r: Int, g: Int, b: Int, channel: Int): Float =
            values[((b * size + g) * size + r) * 3 + channel]

        fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t

        for (y in 0 until source.height) {
            output.getPixels(row, 0, source.width, 0, y, source.width, 1)
            for (x in row.indices) {
                val pixel = row[x]
                val red = ((pixel ushr 16) and 0xff) / 255f
                val green = ((pixel ushr 8) and 0xff) / 255f
                val blue = (pixel and 0xff) / 255f

                val nr = ((red - domainMin[0]) / (domainMax[0] - domainMin[0])).coerceIn(0f, 1f) * maxIndex
                val ng = ((green - domainMin[1]) / (domainMax[1] - domainMin[1])).coerceIn(0f, 1f) * maxIndex
                val nb = ((blue - domainMin[2]) / (domainMax[2] - domainMin[2])).coerceIn(0f, 1f) * maxIndex

                val r0 = floor(nr).toInt()
                val g0 = floor(ng).toInt()
                val b0 = floor(nb).toInt()
                val r1 = (r0 + 1).coerceAtMost(maxIndex)
                val g1 = (g0 + 1).coerceAtMost(maxIndex)
                val b1 = (b0 + 1).coerceAtMost(maxIndex)
                val tr = nr - r0
                val tg = ng - g0
                val tb = nb - b0

                fun interpolate(channel: Int): Float {
                    val c00 = mix(channelAt(r0, g0, b0, channel), channelAt(r1, g0, b0, channel), tr)
                    val c10 = mix(channelAt(r0, g1, b0, channel), channelAt(r1, g1, b0, channel), tr)
                    val c01 = mix(channelAt(r0, g0, b1, channel), channelAt(r1, g0, b1, channel), tr)
                    val c11 = mix(channelAt(r0, g1, b1, channel), channelAt(r1, g1, b1, channel), tr)
                    return mix(mix(c00, c10, tg), mix(c01, c11, tg), tb).coerceIn(0f, 1f)
                }

                val outR = (interpolate(0) * 255f + 0.5f).toInt().coerceIn(0, 255)
                val outG = (interpolate(1) * 255f + 0.5f).toInt().coerceIn(0, 255)
                val outB = (interpolate(2) * 255f + 0.5f).toInt().coerceIn(0, 255)
                row[x] = (pixel and -0x1000000) or (outR shl 16) or (outG shl 8) or outB
            }
            output.setPixels(row, 0, source.width, 0, y, source.width, 1)
        }
        return output
    }
}
