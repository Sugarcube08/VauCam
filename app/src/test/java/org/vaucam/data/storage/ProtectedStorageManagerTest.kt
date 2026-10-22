package org.vaucam.data.storage

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

        // Create a stub context delegating filesDir and cacheDir
        val stubContext = object : android.content.ContextWrapper(null) {
            override fun getFilesDir(): File = filesDir
            override fun getCacheDir(): File = cacheDir
        }
        storageManager = ProtectedStorageManager(stubContext)
    }

    @Test
    fun `streamToStagingWithDigest writes data and calculates SHA-256 and size accurately`() {
        val testData = "vaucam_protected_payload_test".toByteArray(Charsets.UTF_8)
        val stagingFile = storageManager.createStagingFile("test-capture-1")

        val (hash, bytes) = storageManager.streamToStagingWithDigest(
            ByteArrayInputStream(testData),
            stagingFile
        )

        assertTrue(stagingFile.exists())
        assertEquals(testData.size.toLong(), bytes)
        assertEquals(testData.size.toLong(), stagingFile.length())

        val (expectedHash, expectedBytes) = HashVerifier.computeSha256(ByteArrayInputStream(testData))
        assertEquals(expectedHash, hash)
        assertEquals(expectedBytes, bytes)
    }

    @Test
    fun `promoteToProtected atomically moves file from staging to protected directory`() {
        val testData = "atomic_promotion_data".toByteArray()
        val stagingFile = storageManager.createStagingFile("test-capture-2")
        stagingFile.writeBytes(testData)

        assertTrue(stagingFile.exists())

        val finalFileName = "test-capture-2_final.jpg"
        val protectedFile = storageManager.promoteToProtected(stagingFile, finalFileName)

        assertFalse(stagingFile.exists())
        assertTrue(protectedFile.exists())
        assertEquals(File(storageManager.protectedDir, finalFileName).absolutePath, protectedFile.absolutePath)
        assertEquals(testData.size.toLong(), protectedFile.length())
    }

    @Test
    fun `cleanupStaging removes old temporary staging files`() {
        val oldFile = storageManager.createStagingFile("old-capture")
        oldFile.writeBytes(byteArrayOf(1, 2, 3))
        // Set timestamp back by 2 hours
        oldFile.setLastModified(System.currentTimeMillis() - 7200_000L)

        val freshFile = storageManager.createStagingFile("fresh-capture")
        freshFile.writeBytes(byteArrayOf(4, 5, 6))

        storageManager.cleanupStaging(maxAgeMs = 3600_000L)

        assertFalse("Old staging file should be deleted", oldFile.exists())
        assertTrue("Fresh staging file should be retained", freshFile.exists())
    }
}
