package org.vaucam.data.repository

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.vaucam.data.database.BackupState
import org.vaucam.data.database.CaptureDao
import org.vaucam.data.database.CaptureRecordEntity
import org.vaucam.data.database.MediaType
import org.vaucam.data.storage.HashVerifier
import org.vaucam.data.storage.ProtectedStorageManager
import java.io.File
import java.util.UUID

sealed class BackupExecutionResult {
    data class Success(val protectedFile: File, val sha256: String, val sizeBytes: Long) : BackupExecutionResult()
    data class Failure(val reason: String, val isRetryable: Boolean) : BackupExecutionResult()
}

class BackupRepository(
    private val context: Context,
    private val captureDao: CaptureDao,
    private val storageManager: ProtectedStorageManager
) {

    val recentRecords: Flow<List<CaptureRecordEntity>> = captureDao.observeRecentRecords()
    val protectedCount: Flow<Int> = captureDao.observeProtectedCount()

    /**
     * Step 1: Pre-register durable capture intent BEFORE initiating camera hardware operation.
     */
    suspend fun registerCaptureIntent(mediaType: MediaType): CaptureRecordEntity = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val record = CaptureRecordEntity(
            id = id,
            mediaType = mediaType,
            state = BackupState.CAPTURE_REQUESTED,
            createdAt = System.currentTimeMillis()
        )
        captureDao.insert(record)
        record
    }

    /**
     * Step 2: Confirm successful original capture output and transition to BACKUP_PENDING.
     */
    suspend fun confirmCapture(
        captureId: String,
        originalUri: Uri,
        originalPath: String?,
        sizeBytes: Long,
        mimeType: String?,
        displayName: String?
    ) = withContext(Dispatchers.IO) {
        captureDao.updateCaptureSuccess(
            id = captureId,
            originalUri = originalUri.toString(),
            originalPath = originalPath,
            originalSizeBytes = sizeBytes,
            mimeType = mimeType,
            displayName = displayName,
            capturedAt = System.currentTimeMillis(),
            newState = BackupState.BACKUP_PENDING
        )
    }

    /**
     * Record capture hardware failure before media was produced.
     */
    suspend fun recordCaptureFailure(captureId: String, error: String) = withContext(Dispatchers.IO) {
        captureDao.updateState(captureId, BackupState.BACKUP_FAILED, error)
    }

    fun openSourceStream(uri: Uri): InputStream? {
        return if (uri.scheme == "file" || uri.scheme == null) {
            val path = uri.path ?: return null
            val file = File(path)
            if (file.exists()) java.io.FileInputStream(file) else null
        } else {
            runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
        }
    }

    /**
     * Execute full atomic copy, hash verification, and commit to PROTECTED.
     */
    suspend fun executeBackup(captureId: String): BackupExecutionResult = withContext(Dispatchers.IO) {
        val record = captureDao.getById(captureId)
            ?: return@withContext BackupExecutionResult.Failure("Capture record $captureId not found", false)

        if (record.state == BackupState.PROTECTED) {
            val file = record.protectedPath?.let { File(it) }
            if (file != null && file.exists()) {
                return@withContext BackupExecutionResult.Success(
                    protectedFile = file,
                    sha256 = record.protectedSha256.orEmpty(),
                    sizeBytes = record.protectedSizeBytes ?: file.length()
                )
            }
        }

        val originalUriStr = record.originalUri
            ?: return@withContext BackupExecutionResult.Failure("Original URI is missing for $captureId", false)

        val uri = Uri.parse(originalUriStr)

        // Verify source existence
        val sourceCheckStream = openSourceStream(uri)
        if (sourceCheckStream == null) {
            captureDao.updateState(captureId, BackupState.SOURCE_MISSING, "Unable to open source URI: $uri")
            return@withContext BackupExecutionResult.Failure("Source original media missing at $uri", false)
        } else {
            sourceCheckStream.close()
        }

        // Transition to BACKUP_IN_PROGRESS
        captureDao.updateState(captureId, BackupState.BACKUP_IN_PROGRESS)

        val stagingFile = storageManager.createStagingFile(captureId)

        try {
            // Stream original directly into staging while computing staging hash & size
            val (stagingSha256, stagingBytes) = openSourceStream(uri)?.use { stream ->
                storageManager.streamToStagingWithDigest(stream, stagingFile)
            } ?: throw IllegalStateException("Could not open input stream from $uri")

            // Transition to VERIFYING
            captureDao.updateState(captureId, BackupState.VERIFYING)

            // Re-read original to compute authoritative original SHA-256
            val (originalSha256, originalBytes) = openSourceStream(uri)?.use { stream ->
                HashVerifier.computeSha256(stream)
            } ?: throw IllegalStateException("Could not re-open original stream for verification")

            // Verify integrity match
            if (stagingSha256 != originalSha256 || stagingBytes != originalBytes) {
                stagingFile.delete()
                val mismatchMsg = "Integrity mismatch: original($originalSha256, $originalBytes B) vs staging($stagingSha256, $stagingBytes B)"
                captureDao.recordRetryFailure(captureId, mismatchMsg, BackupState.BACKUP_FAILED)
                return@withContext BackupExecutionResult.Failure(mismatchMsg, isRetryable = true)
            }

            // Promote staging file atomically
            val extension = if (record.mediaType == MediaType.VIDEO) "mp4" else "jpg"
            val sanitizedName = record.displayName ?: "${captureId}.$extension"
            val finalProtectedFile = storageManager.promoteToProtected(stagingFile, "${captureId}_$sanitizedName")

            // Commit to Room as PROTECTED
            val verifiedAt = System.currentTimeMillis()
            captureDao.updateVerificationSuccess(
                id = captureId,
                protectedPath = finalProtectedFile.absolutePath,
                protectedSizeBytes = stagingBytes,
                protectedSha256 = stagingSha256,
                originalSha256 = originalSha256,
                verifiedAt = verifiedAt
            )

            BackupExecutionResult.Success(
                protectedFile = finalProtectedFile,
                sha256 = stagingSha256,
                sizeBytes = stagingBytes
            )
        } catch (e: Exception) {
            stagingFile.delete()
            val errorMsg = e.message ?: "Backup execution failed"
            val willRetry = record.retryCount < MAX_RETRIES
            val nextState = if (willRetry) BackupState.BACKUP_PENDING else BackupState.BACKUP_FAILED
            captureDao.recordRetryFailure(captureId, errorMsg, nextState)
            BackupExecutionResult.Failure(errorMsg, isRetryable = willRetry)
        }
    }

    suspend fun getRecordById(id: String): CaptureRecordEntity? = withContext(Dispatchers.IO) {
        captureDao.getById(id)
    }

    suspend fun getPendingBackups(): List<CaptureRecordEntity> = withContext(Dispatchers.IO) {
        captureDao.getPendingBackups()
    }

    suspend fun getInterruptedOrReconciliationRecords(): List<CaptureRecordEntity> = withContext(Dispatchers.IO) {
        captureDao.getInterruptedOrReconciliationRecords()
    }

    companion object {
        const val MAX_RETRIES = 3
    }
}
