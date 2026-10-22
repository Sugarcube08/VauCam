package org.vaucam.data.database

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class CaptureDaoTest {

    private lateinit var database: VauCamDatabase
    private lateinit var dao: CaptureDao

    @Before
    fun setUp() {
        val application = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(application, VauCamDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.captureDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `insert capture intent records state CAPTURE_REQUESTED`() = runBlocking {
        val record = CaptureRecordEntity(
            id = "intent-123",
            mediaType = MediaType.PHOTO,
            state = BackupState.CAPTURE_REQUESTED,
            createdAt = 1000L
        )
        dao.insert(record)

        val retrieved = dao.getById("intent-123")
        assertNotNull(retrieved)
        assertEquals(BackupState.CAPTURE_REQUESTED, retrieved?.state)
        assertEquals(MediaType.PHOTO, retrieved?.mediaType)
        assertEquals(1000L, retrieved?.createdAt)
    }

    @Test
    fun `updateCaptureSuccess transitions state to BACKUP_PENDING and stores metadata`() = runBlocking {
        val record = CaptureRecordEntity(
            id = "capture-456",
            mediaType = MediaType.PHOTO,
            state = BackupState.CAPTURE_REQUESTED,
            createdAt = 1000L
        )
        dao.insert(record)

        dao.updateCaptureSuccess(
            id = "capture-456",
            originalUri = "content://media/external/images/media/456",
            originalPath = "/storage/emulated/0/Pictures/VauCam/test.jpg",
            originalSizeBytes = 2048L,
            mimeType = "image/jpeg",
            displayName = "test.jpg",
            capturedAt = 1200L,
            newState = BackupState.BACKUP_PENDING
        )

        val updated = dao.getById("capture-456")
        assertNotNull(updated)
        assertEquals(BackupState.BACKUP_PENDING, updated?.state)
        assertEquals("content://media/external/images/media/456", updated?.originalUri)
        assertEquals(2048L, updated?.originalSizeBytes)
        assertEquals(1200L, updated?.capturedAt)
    }

    @Test
    fun `updateVerificationSuccess transitions state to PROTECTED and commits hashes`() = runBlocking {
        val record = CaptureRecordEntity(
            id = "verify-789",
            mediaType = MediaType.VIDEO,
            state = BackupState.VERIFYING,
            createdAt = 1000L,
            originalUri = "content://media/external/video/media/789"
        )
        dao.insert(record)

        val sha256 = "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9"
        dao.updateVerificationSuccess(
            id = "verify-789",
            protectedPath = "/data/user/0/org.vaucam/files/protected_media/verify-789.mp4",
            protectedSizeBytes = 50000L,
            protectedSha256 = sha256,
            originalSha256 = sha256,
            verifiedAt = 2000L
        )

        val protectedRecord = dao.getById("verify-789")
        assertNotNull(protectedRecord)
        assertEquals(BackupState.PROTECTED, protectedRecord?.state)
        assertEquals(sha256, protectedRecord?.protectedSha256)
        assertEquals(sha256, protectedRecord?.originalSha256)
        assertEquals(50000L, protectedRecord?.protectedSizeBytes)
        assertEquals(2000L, protectedRecord?.verifiedAt)
        assertNull(protectedRecord?.lastError)
    }

    @Test
    fun `recordRetryFailure increments retry count and sets error message`() = runBlocking {
        val record = CaptureRecordEntity(
            id = "retry-001",
            mediaType = MediaType.PHOTO,
            state = BackupState.BACKUP_IN_PROGRESS,
            createdAt = 1000L,
            retryCount = 0
        )
        dao.insert(record)

        dao.recordRetryFailure("retry-001", "Checksum mismatch", BackupState.BACKUP_FAILED)

        val retried = dao.getById("retry-001")
        assertNotNull(retried)
        assertEquals(1, retried?.retryCount)
        assertEquals(BackupState.BACKUP_FAILED, retried?.state)
        assertEquals("Checksum mismatch", retried?.lastError)
    }

    @Test
    fun `getPendingBackups returns only CAPTURED and BACKUP_PENDING records`() = runBlocking {
        dao.insert(CaptureRecordEntity("r1", MediaType.PHOTO, BackupState.CAPTURE_REQUESTED, 100L))
        dao.insert(CaptureRecordEntity("r2", MediaType.PHOTO, BackupState.CAPTURED, 200L, originalUri = "content://media/images/2"))
        dao.insert(CaptureRecordEntity("r3", MediaType.PHOTO, BackupState.BACKUP_PENDING, 300L, originalUri = "content://media/images/3"))
        dao.insert(CaptureRecordEntity("r4", MediaType.PHOTO, BackupState.PROTECTED, 400L))

        val pending = dao.getPendingBackups()
        assertEquals(2, pending.size)
        assertEquals("r2", pending[0].id)
        assertEquals("r3", pending[1].id)
    }

    @Test
    fun `getInterruptedOrReconciliationRecords queries in-flight states for recovery`() = runBlocking {
        dao.insert(CaptureRecordEntity("r1", MediaType.PHOTO, BackupState.BACKUP_IN_PROGRESS, 100L))
        dao.insert(CaptureRecordEntity("r2", MediaType.PHOTO, BackupState.VERIFYING, 200L))
        dao.insert(CaptureRecordEntity("r3", MediaType.PHOTO, BackupState.RECOVERY_REQUIRED, 300L))
        dao.insert(CaptureRecordEntity("r4", MediaType.PHOTO, BackupState.PROTECTED, 400L))

        val interrupted = dao.getInterruptedOrReconciliationRecords()
        assertEquals(3, interrupted.size)
    }

    @Test
    fun `findOrphanCaptureIntent matches stale intent by media type and narrow time window`() = runBlocking {
        val captureTime = 1_765_000_000_000L
        dao.insert(
            CaptureRecordEntity(
                id = "orphan-photo",
                mediaType = MediaType.PHOTO,
                state = BackupState.RECOVERY_REQUIRED,
                createdAt = captureTime + 250L
            )
        )
        dao.insert(
            CaptureRecordEntity(
                id = "wrong-media-type",
                mediaType = MediaType.VIDEO,
                state = BackupState.RECOVERY_REQUIRED,
                createdAt = captureTime
            )
        )

        val match = dao.findOrphanCaptureIntent(
            mediaType = MediaType.PHOTO,
            windowStart = captureTime - 10_000L,
            windowEnd = captureTime + 10_000L,
            capturedAt = captureTime
        )

        assertEquals("orphan-photo", match?.id)
    }

    @Test
    fun `findOrphanCaptureIntent does not match completed or already linked records`() = runBlocking {
        val captureTime = 1_765_000_000_000L
        dao.insert(
            CaptureRecordEntity(
                id = "already-protected",
                mediaType = MediaType.PHOTO,
                state = BackupState.PROTECTED,
                createdAt = captureTime
            )
        )
        dao.insert(
            CaptureRecordEntity(
                id = "already-linked",
                mediaType = MediaType.PHOTO,
                state = BackupState.RECOVERY_REQUIRED,
                createdAt = captureTime,
                originalUri = "content://media/123"
            )
        )

        val match = dao.findOrphanCaptureIntent(
            mediaType = MediaType.PHOTO,
            windowStart = captureTime - 10_000L,
            windowEnd = captureTime + 10_000L,
            capturedAt = captureTime
        )

        assertNull(match)
    }
}
