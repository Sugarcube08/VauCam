package org.vaucam.data.repository

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.vaucam.data.database.BackupState
import org.vaucam.data.database.CaptureDao
import org.vaucam.data.database.CaptureRecordEntity
import org.vaucam.data.database.MediaType
import org.vaucam.data.storage.HashVerifier
import org.vaucam.data.storage.ProtectedStorageManager
import java.io.File
import java.io.InputStream
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

    private val backupLocks = mutableMapOf<String, Mutex>()
    private val locksGuard = Any()

    private fun lockFor(captureId: String): Mutex = synchronized(locksGuard) {
        backupLocks.getOrPut(captureId) { Mutex() }
    }

    suspend fun registerCaptureIntent(mediaType: MediaType): CaptureRecordEntity = withContext(Dispatchers.IO) {
        val record = CaptureRecordEntity(
            id = UUID.randomUUID().toString(),
            mediaType = mediaType,
            state = BackupState.CAPTURE_REQUESTED,
            createdAt = System.currentTimeMillis()
        )
        captureDao.insert(record)
        record
    }

    suspend fun confirmCapture(
        captureId: String,
        originalUri: Uri,
        originalPath: String?,
        sizeBytes: Long,
        mimeType: String?,
        displayName: String?
    ) = withContext(Dispatchers.IO) {
        val existing = captureDao.getById(captureId)
            ?: throw IllegalStateException("Capture intent $captureId was not found")
        if (existing.state == BackupState.PROTECTED) return@withContext
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

    suspend fun recordCaptureFailure(captureId: String, error: String) = withContext(Dispatchers.IO) {
        captureDao.updateState(captureId, BackupState.BACKUP_FAILED, error)
    }

    fun openSourceStream(uri: Uri): InputStream? {
        return if (uri.scheme == "file" || uri.scheme == null) {
            val file = uri.path?.let(::File) ?: return null
            if (file.isFile) file.inputStream() else null
        } else {
            runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
        }
    }

    suspend fun executeBackup(captureId: String): BackupExecutionResult =
        lockFor(captureId).withLock {
            withContext(Dispatchers.IO) {
                val record = captureDao.getById(captureId)
                    ?: return@withContext BackupExecutionResult.Failure("Capture record $captureId not found", false)

                if (record.state == BackupState.PROTECTED) {
                    val file = record.protectedPath?.let(::File)
                    if (file != null && file.isFile) {
                        val digest = runCatching { HashVerifier.computeSha256(file) }.getOrNull()
                        if (digest != null && digest.first == record.protectedSha256 &&
                            digest.second == record.protectedSizeBytes
                        ) {
                            return@withContext BackupExecutionResult.Success(file, digest.first, digest.second)
                        }
                    }
                    captureDao.updateState(
                        captureId, BackupState.RECOVERY_REQUIRED,
                        "Previously protected backup is missing or does not match its recorded digest"
                    )
                }

                val latest = captureDao.getById(captureId)
                    ?: return@withContext BackupExecutionResult.Failure("Capture record $captureId disappeared", false)
                val originalUriString = latest.originalUri
                    ?: return@withContext BackupExecutionResult.Failure("Original URI is missing for $captureId", false)
                val uri = Uri.parse(originalUriString)

                val check = openSourceStream(uri)
                if (check == null) {
                    captureDao.updateState(captureId, BackupState.SOURCE_MISSING, "Unable to open source URI: $uri")
                    return@withContext BackupExecutionResult.Failure("Source original media missing at $uri", false)
                }
                check.use { }

                captureDao.updateState(captureId, BackupState.BACKUP_IN_PROGRESS)
                val stagingFile = storageManager.createStagingFile(captureId)
                try {
                    val (stagingHash, stagingSize) = openSourceStream(uri)?.use { input ->
                        storageManager.streamToStagingWithDigest(input, stagingFile)
                    } ?: throw IllegalStateException("Could not open input stream from $uri")

                    captureDao.updateState(captureId, BackupState.VERIFYING)
                    val (sourceHash, sourceSize) = openSourceStream(uri)?.use(HashVerifier::computeSha256)
                        ?: throw IllegalStateException("Could not reopen original stream for verification")

                    if (stagingHash != sourceHash || stagingSize != sourceSize) {
                        stagingFile.delete()
                        val message = "Integrity mismatch: source($sourceHash, $sourceSize B) vs backup($stagingHash, $stagingSize B)"
                        captureDao.recordRetryFailure(captureId, message, BackupState.BACKUP_FAILED)
                        return@withContext BackupExecutionResult.Failure(message, true)
                    }

                    val extension = if (latest.mediaType == MediaType.VIDEO) "mp4" else "jpg"
                    val safeBaseName = latest.displayName.orEmpty()
                        .substringAfterLast('/')
                        .substringAfterLast('\\')
                        .replace(Regex("[^A-Za-z0-9._-]"), "_")
                        .take(120)
                        .takeIf(String::isNotBlank) ?: captureId
                    // MediaStore display names may omit their extension even when MIME_TYPE is set.
                    // Keep protected copies usable by file managers and media players.
                    val safeName = if (safeBaseName.substringAfterLast('.', "").isBlank()) {
                        "$safeBaseName.$extension"
                    } else {
                        safeBaseName
                    }
                    val protectedFile = storageManager.promoteToProtected(stagingFile, "${captureId}_$safeName")

                    // Verify the promoted final artifact, not just the staging file.
                    val finalDigest = HashVerifier.computeSha256(protectedFile)
                    if (finalDigest.first != sourceHash || finalDigest.second != sourceSize) {
                        protectedFile.delete()
                        throw IllegalStateException("Promoted backup failed final integrity verification")
                    }
                    captureDao.updateVerificationSuccess(
                        id = captureId,
                        protectedPath = protectedFile.absolutePath,
                        protectedSizeBytes = finalDigest.second,
                        protectedSha256 = finalDigest.first,
                        originalSha256 = sourceHash,
                        verifiedAt = System.currentTimeMillis()
                    )
                    BackupExecutionResult.Success(protectedFile, finalDigest.first, finalDigest.second)
                } catch (e: Exception) {
                    stagingFile.delete()
                    val error = e.message ?: "Backup execution failed"
                    val retryable = latest.retryCount < MAX_RETRIES
                    captureDao.recordRetryFailure(
                        captureId, error,
                        if (retryable) BackupState.BACKUP_PENDING else BackupState.BACKUP_FAILED
                    )
                    BackupExecutionResult.Failure(error, retryable)
                }
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
