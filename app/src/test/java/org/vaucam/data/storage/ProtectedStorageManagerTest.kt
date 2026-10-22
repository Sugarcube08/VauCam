package org.vaucam.data.storage

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

class ProtectedStorageManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var filesDir: File
    private lateinit var cacheDir: File
    private lateinit var storageManager: ProtectedStorageManager

    @Before
    fun setUp() {
        filesDir = tempFolder.newFolder("files")
        cacheDir = tempFolder.newFolder("cache")
        val stubContext = object : android.content.ContextWrapper(null) {
            override fun getFilesDir(): File = filesDir
            override fun getCacheDir(): File = cacheDir
        }
        storageManager = ProtectedStorageManager(stubContext)
    }

    @Test
    fun `streamToStagingWithDigest writes bytes and calculates SHA-256 accurately`() {
        val testData = "vaucam_protected_payload_test".toByteArray(Charsets.UTF_8)
        val stagingFile = storageManager.createStagingFile("test-capture-1")
        val (hash, bytes) = storageManager.streamToStagingWithDigest(
            ByteArrayInputStream(testData), stagingFile
        )

        assertTrue(stagingFile.exists())
        assertEquals(testData.size.toLong(), bytes)
        assertEquals(testData.size.toLong(), stagingFile.length())
        val (expectedHash, expectedBytes) =
            HashVerifier.computeSha256(ByteArrayInputStream(testData))
        assertEquals(expectedHash, hash)
        assertEquals(expectedBytes, bytes)
    }

    @Test
    fun `promoteToProtected moves staging file under a safe name`() {
        val testData = "atomic_promotion_data".toByteArray()
        val stagingFile = storageManager.createStagingFile("test-capture-2")
        stagingFile.writeBytes(testData)

        val protectedFile = storageManager.promoteToProtected(
            stagingFile, "test-capture-2_final.jpg"
        )

        assertFalse(stagingFile.exists())
        assertTrue(protectedFile.exists())
        assertEquals(
            File(storageManager.protectedDir, "test-capture-2_final.jpg").absolutePath,
            protectedFile.absolutePath
        )
        assertEquals(testData.size.toLong(), protectedFile.length())
    }

    @Test
    fun `promoteToProtected strips path traversal from destination name`() {
        val stagingFile = storageManager.createStagingFile("capture")
        stagingFile.writeText("payload")

        val protectedFile = storageManager.promoteToProtected(stagingFile, "../../escape.jpg")

        assertEquals(storageManager.protectedDir.canonicalFile, protectedFile.canonicalFile.parentFile)
        assertTrue(protectedFile.isFile)
    }

    @Test
    fun `promoteToProtected rejects a source outside staging directory`() {
        val outside = File(filesDir, "outside.tmp")
        outside.writeText("payload")

        assertThrows(IllegalArgumentException::class.java) {
            storageManager.promoteToProtected(outside, "safe.jpg")
        }
    }

    @Test
    fun `streamToStagingWithDigest rejects a target outside staging directory`() {
        val outside = File(filesDir, "outside.tmp")

        assertThrows(IllegalArgumentException::class.java) {
            storageManager.streamToStagingWithDigest(ByteArrayInputStream(byteArrayOf(1)), outside)
        }
    }

    @Test
    fun `cleanupStaging removes old temporary files and retains recent ones`() {
        val oldFile = storageManager.createStagingFile("old-capture")
        oldFile.writeBytes(byteArrayOf(1, 2, 3))
        oldFile.setLastModified(System.currentTimeMillis() - 7_200_000L)

        val freshFile = storageManager.createStagingFile("fresh-capture")
        freshFile.writeBytes(byteArrayOf(4, 5, 6))

        storageManager.cleanupStaging(maxAgeMs = 3_600_000L)

        assertFalse(oldFile.exists())
        assertTrue(freshFile.exists())
    }
}
