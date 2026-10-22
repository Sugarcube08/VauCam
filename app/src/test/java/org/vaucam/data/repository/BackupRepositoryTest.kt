package org.vaucam.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.vaucam.data.database.BackupState
import org.vaucam.data.database.CaptureDao
import org.vaucam.data.database.MediaType
import org.vaucam.data.database.VauCamDatabase
import org.vaucam.data.storage.HashVerifier
import org.vaucam.data.storage.ProtectedStorageManager
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

class BackupRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var filesDir: File
    private lateinit var cacheDir: File
    private lateinit var externalMediaDir: File

    private lateinit var database: VauCamDatabase
    private lateinit var dao: CaptureDao
    private lateinit var storageManager: ProtectedStorageManager
    private lateinit var repository: BackupRepository

    @Before
    fun setUp() {
        filesDir = tempFolder.newFolder("files")
        cacheDir = tempFolder.newFolder("cache")
        externalMediaDir = tempFolder.newFolder("external_media")

        val stubContext = object : android.content.ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = filesDir
            override fun getCacheDir(): File = cacheDir
        }

        database = Room.inMemoryDatabaseBuilder(stubContext, VauCamDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.captureDao()
        storageManager = ProtectedStorageManager(stubContext)
        repository = BackupRepository(stubContext, dao, storageManager)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `registerCaptureIntent creates durable intent with CAPTURE_REQUESTED state`() = runBlocking {
        val record = repository.registerCaptureIntent(MediaType.PHOTO)

        assertNotNull(record.id)
        assertEquals(BackupState.CAPTURE_REQUESTED, record.state)
        assertEquals(MediaType.PHOTO, record.mediaType)

        val retrieved = dao.getById(record.id)
        assertNotNull(retrieved)
        assertEquals(BackupState.CAPTURE_REQUESTED, retrieved?.state)
    }

    @Test
    fun `successful capture and backup execution promotes file and sets PROTECTED state`() = runBlocking {
        // 1. Register Intent
        val record = repository.registerCaptureIntent(MediaType.PHOTO)
        val captureId = record.id

        // 2. Simulate camera hardware creating original image
        val originalFile = File(externalMediaDir, "VauCam_20261019_001.jpg")
        val samplePayload = "test_image_raw_bytes_vaucam_payload".toByteArray(Charsets.UTF_8)
        originalFile.writeBytes(samplePayload)
        val originalUri = Uri.fromFile(originalFile)

        val (expectedHash, expectedSize) = HashVerifier.computeSha256(FileInputStream(originalFile))

        // 3. Confirm capture
        repository.confirmCapture(
            captureId = captureId,
            originalUri = originalUri,
            originalPath = originalFile.absolutePath,
            sizeBytes = expectedSize,
            mimeType = "image/jpeg",
            displayName = originalFile.name
        )

        val pendingRecord = dao.getById(captureId)
        assertEquals(BackupState.BACKUP_PENDING, pendingRecord?.state)

        // 4. Execute Backup
        val result = repository.executeBackup(captureId)
        assertTrue("Backup execution should succeed", result is BackupExecutionResult.Success)

        val success = result as BackupExecutionResult.Success
        assertTrue("Protected file must exist on disk", success.protectedFile.exists())
        assertEquals(expectedHash, success.sha256)
        assertEquals(expectedSize, success.sizeBytes)

        // 5. Verify database commit
        val finalRecord = dao.getById(captureId)
        assertNotNull(finalRecord)
        assertEquals(BackupState.PROTECTED, finalRecord?.state)
        assertEquals(expectedHash, finalRecord?.protectedSha256)
        assertEquals(expectedHash, finalRecord?.originalSha256)
        assertEquals(expectedSize, finalRecord?.protectedSizeBytes)
        assertNotNull(finalRecord?.verifiedAt)
    }

    @Test
    fun `executeBackup detects missing source and transitions to SOURCE_MISSING`() = runBlocking {
        val record = repository.registerCaptureIntent(MediaType.PHOTO)
        val nonExistentUri = Uri.fromFile(File(externalMediaDir, "non_existent_image.jpg"))

        repository.confirmCapture(
            captureId = record.id,
            originalUri = nonExistentUri,
            originalPath = "/path/to/missing.jpg",
            sizeBytes = 1024L,
            mimeType = "image/jpeg",
            displayName = "missing.jpg"
        )

        val result = repository.executeBackup(record.id)
        assertTrue(result is BackupExecutionResult.Failure)

        val updatedRecord = dao.getById(record.id)
        assertEquals(BackupState.SOURCE_MISSING, updatedRecord?.state)
    }

    @Test
    fun `idempotent execution returns Success if already PROTECTED`() = runBlocking {
        val record = repository.registerCaptureIntent(MediaType.PHOTO)
        val originalFile = File(externalMediaDir, "idempotent_test.jpg")
        originalFile.writeBytes("idempotent_data".toByteArray())

        repository.confirmCapture(
            captureId = record.id,
            originalUri = Uri.fromFile(originalFile),
            originalPath = originalFile.absolutePath,
            sizeBytes = originalFile.length(),
            mimeType = "image/jpeg",
            displayName = originalFile.name
        )

        val firstResult = repository.executeBackup(record.id)
        assertTrue(firstResult is BackupExecutionResult.Success)

        // Second call should return success idempotently
        val secondResult = repository.executeBackup(record.id)
        assertTrue(secondResult is BackupExecutionResult.Success)
    }
    @Test
    fun `revalidates and repairs an existing protected copy when it is corrupted`() = runBlocking {
        val record = repository.registerCaptureIntent(MediaType.PHOTO)
        val originalFile = File(externalMediaDir, "repair_test.jpg")
        originalFile.writeBytes("original_image_bytes".toByteArray())

        repository.confirmCapture(
            captureId = record.id,
            originalUri = Uri.fromFile(originalFile),
            originalPath = originalFile.absolutePath,
            sizeBytes = originalFile.length(),
            mimeType = "image/jpeg",
            displayName = originalFile.name
        )

        val first = repository.executeBackup(record.id) as BackupExecutionResult.Success
        first.protectedFile.writeBytes("corrupted".toByteArray())

        val repaired = repository.executeBackup(record.id)
        assertTrue("A corrupted private backup should be repaired from the original", repaired is BackupExecutionResult.Success)
        val success = repaired as BackupExecutionResult.Success
        assertEquals(originalFile.readBytes().toList(), success.protectedFile.readBytes().toList())

        val finalRecord = dao.getById(record.id)
        assertEquals(BackupState.PROTECTED, finalRecord?.state)
        assertEquals(success.sha256, finalRecord?.protectedSha256)
    }

}
