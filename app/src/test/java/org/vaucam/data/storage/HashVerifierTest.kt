package org.vaucam.data.storage

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

class HashVerifierTest {

    @Test
    fun `empty input produces known SHA-256 digest and zero bytes`() {
        val stream = ByteArrayInputStream(ByteArray(0))
        val (hash, bytes) = HashVerifier.computeSha256(stream)

        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hash)
        assertEquals(0L, bytes)
    }

    @Test
    fun `standard test vector produces correct SHA-256 digest`() {
        val testData = "hello world".toByteArray(Charsets.UTF_8)
        val stream = ByteArrayInputStream(testData)
        val (hash, bytes) = HashVerifier.computeSha256(stream)

        assertEquals("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9", hash)
        assertEquals(testData.size.toLong(), bytes)
    }

    @Test
    fun `large stream chunking produces consistent digest`() {
        val largeData = ByteArray(256 * 1024) { (it % 127).toByte() }
        val stream1 = ByteArrayInputStream(largeData)
        val stream2 = ByteArrayInputStream(largeData)

        val (hash1, size1) = HashVerifier.computeSha256(stream1)
        val (hash2, size2) = HashVerifier.computeSha256(stream2)

        assertEquals(hash1, hash2)
        assertEquals(256 * 1024L, size1)
        assertEquals(size1, size2)
    }
}
