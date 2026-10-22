package org.vaucam.camera

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.vaucam.data.database.CaptureRecordEntity
import org.vaucam.data.database.MediaType
import org.vaucam.data.database.VauCamDatabase
import org.vaucam.data.repository.BackupRepository
import org.vaucam.data.storage.ProtectedStorageManager
import org.vaucam.platform.background.MediaBackupWorker
import org.vaucam.platform.background.ReconciliationWorker

/** Bridges CameraX callbacks to durable backup state and background retry. */
class CaptureCoordinator(
    private val context: Context,
    val repository: BackupRepository = BackupRepository(
        context.applicationContext,
        VauCamDatabase.getInstance(context).captureDao(),
        ProtectedStorageManager(context.applicationContext)
    )
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val workManager = WorkManager.getInstance(context.applicationContext)

    val recentCaptures: Flow<List<CaptureRecordEntity>> = repository.recentRecords
    val protectedCount: Flow<Int> = repository.protectedCount

    init {
        ReconciliationWorker.schedulePeriodic(context.applicationContext)
    }

    suspend fun registerCaptureIntent(mediaType: MediaType): String {
        val record = repository.registerCaptureIntent(mediaType)
        Log.d(TAG, "Registered capture intent: ${record.id} for $mediaType")
        return record.id
    }

    fun onCaptureSucceeded(
        captureId: String,
        outputUri: Uri,
        originalPath: String? = null,
        sizeBytes: Long = 0L,
        mimeType: String? = null,
        displayName: String? = null
    ) {
        scope.launch {
            try {
                repository.confirmCapture(
                    captureId = captureId,
                    originalUri = outputUri,
                    originalPath = originalPath,
                    sizeBytes = sizeBytes,
                    mimeType = mimeType,
                    displayName = displayName
                )
                // KEEP prevents a callback/reconciliation race from replacing an active worker.
                workManager.enqueueUniqueWork(
                    "backup_$captureId",
                    ExistingWorkPolicy.KEEP,
                    MediaBackupWorker.buildWorkRequest(captureId)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to coordinate capture success for $captureId", e)
            }
        }
    }

    fun onCaptureFailed(captureId: String, error: String) {
        scope.launch {
            runCatching { repository.recordCaptureFailure(captureId, error) }
                .onFailure { Log.e(TAG, "Failed to record capture failure for $captureId", it) }
        }
    }

    fun close() = scope.cancel()

    companion object {
        private const val TAG = "CaptureCoordinator"
    }
}
