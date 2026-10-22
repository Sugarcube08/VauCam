package org.vaucam.lut

import java.io.BufferedReader
import java.io.Reader

/**
 * Parsed 3D LUT from the common .cube interchange format.
 *
 * Values are stored in red-fastest order (R changes fastest, then G, then B),
 * matching the ordering of standard .cube 3D LUT data rows.
 */
data class CubeLut(
    val title: String?,
    val size: Int,
    val domainMin: FloatArray,
    val domainMax: FloatArray,
    val values: FloatArray
) {
    init {
        require(size >= MIN_SIZE && size <= MAX_SIZE)
        require(domainMin.size == 3 && domainMax.size == 3)
        require(values.size == size * size * size * 3)
    }

    fun sampleIndex(red: Int, green: Int, blue: Int): Int {
        require(red in 0 until size && green in 0 until size && blue in 0 until size)
        return ((blue * size + green) * size + red) * 3
    }

    fun sample(red: Int, green: Int, blue: Int): FloatArray {
        val offset = sampleIndex(red, green, blue)
        return floatArrayOf(values[offset], values[offset + 1], values[offset + 2])
    }

    companion object {
        const val MIN_SIZE = 2
        const val MAX_SIZE = 64
        private const val MAX_INPUT_LINES = MAX_SIZE * MAX_SIZE * MAX_SIZE + 256

        /**
         * Parse a .cube Reader. 1D LUT sections, malformed directives, non-finite
         * values, and excessive dimensions are rejected instead of being guessed.
         */
        fun parse(reader: Reader): CubeLut {
            var title: String? = null
            var size: Int? = null
            var domainMin = floatArrayOf(0f, 0f, 0f)
            var domainMax = floatArrayOf(1f, 1f, 1f)
            val data = ArrayList<Float>()
            var lines = 0

            BufferedReader(reader).use { buffered ->
                while (true) {
                    val raw = buffered.readLine() ?: break
                    lines++
                    require(lines <= MAX_INPUT_LINES) { "LUT file contains too many lines" }
                    val line = raw.substringBefore('#').trim()
                    if (line.isEmpty()) continue

                    val firstSpace = line.indexOfFirst { it.isWhitespace() }
                    val directive = if (firstSpace < 0) line else line.substring(0, firstSpace)
                    val rest = if (firstSpace < 0) "" else line.substring(firstSpace).trim()

                    when (directive.uppercase()) {
                        "TITLE" -> title = rest.trim().removeSurrounding("\"", "\"")
                        "LUT_1D_SIZE" -> throw IllegalArgumentException("1D LUTs are not supported; expected LUT_3D_SIZE")
                        "LUT_3D_SIZE" -> {
                            require(size == null) { "Duplicate LUT_3D_SIZE directive" }
                            val parsed = rest.toIntOrNull()
                                ?: throw IllegalArgumentException("Invalid LUT_3D_SIZE value")
                            require(parsed in MIN_SIZE..MAX_SIZE) {
                                "LUT size must be between $MIN_SIZE and $MAX_SIZE"
                            }
                            require(data.isEmpty()) { "LUT_3D_SIZE must appear before data rows" }
                            size = parsed
                        }
                        "DOMAIN_MIN" -> domainMin = parseTriple(rest, "DOMAIN_MIN")
                        "DOMAIN_MAX" -> domainMax = parseTriple(rest, "DOMAIN_MAX")
                        else -> {
                            // Numeric triples are data rows; unknown textual directives fail closed.
                            val triple = parseTripleOrNull(line)
                                ?: throw IllegalArgumentException("Unsupported .cube directive: $directive")
                            require(size != null) { "LUT_3D_SIZE must be declared before data rows" }
                            data.addAll(triple.toList())
                            require(data.size <= size!! * size!! * size!! * 3) {
                                "LUT contains more data rows than its declared dimensions"
                            }
                        }
                    }
                }
            }

            val lutSize = size ?: throw IllegalArgumentException("Missing LUT_3D_SIZE directive")
            require(domainMin.indices.all { domainMax[it] > domainMin[it] }) {
                "Each DOMAIN_MAX component must exceed DOMAIN_MIN"
            }
            val expectedValues = lutSize * lutSize * lutSize * 3
            require(data.size == expectedValues) {
                "Expected ${lutSize * lutSize * lutSize} RGB rows, found ${data.size / 3.0}"
            }
            val values = data.toFloatArray()
            require(values.all(Float::isFinite)) { "LUT values must be finite" }
            return CubeLut(title, lutSize, domainMin, domainMax, values)
        }

        private fun parseTriple(text: String, directive: String): FloatArray =
            parseTripleOrNull(text)
                ?: throw IllegalArgumentException("$directive must contain exactly three finite numbers")

        private fun parseTripleOrNull(text: String): FloatArray? {
            val tokens = text.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
            if (tokens.size != 3) return null
            val values = tokens.map { it.toFloatOrNull() ?: return null }
            if (values.any { !it.isFinite() }) return null
            return values.toFloatArray()
        }
    }
}
