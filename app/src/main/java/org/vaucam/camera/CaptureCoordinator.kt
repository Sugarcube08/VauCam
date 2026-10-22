package org.vaucam.camera

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.vaucam.data.database.CaptureRecordEntity
import org.vaucam.data.database.MediaType
import org.vaucam.data.database.VauCamDatabase
import org.vaucam.data.repository.BackupRepository
import org.vaucam.data.storage.ProtectedStorageManager
import org.vaucam.platform.background.MediaBackupWorker
import org.vaucam.platform.background.ReconciliationWorker

class CaptureCoordinator(
    private val context: Context,
    val repository: BackupRepository = BackupRepository(
        context,
        VauCamDatabase.getInstance(context).captureDao(),
        ProtectedStorageManager(context)
    )
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val workManager = WorkManager.getInstance(context)

    val recentCaptures: Flow<List<CaptureRecordEntity>> = repository.recentRecords
    val protectedCount: Flow<Int> = repository.protectedCount

    init {
        // Schedule periodic reconciliation in the background
        ReconciliationWorker.schedulePeriodic(context)
    }

    /**
     * Adjustment A: Register intent BEFORE hardware capture is initiated.
     * Returns the unique capture ID to track the in-flight operation.
     */
    suspend fun registerCaptureIntent(mediaType: MediaType): String {
        val record = repository.registerCaptureIntent(mediaType)
        Log.d(TAG, "Registered capture intent: ${record.id} for $mediaType")
        return record.id
    }

    /**
     * Called when original media is saved successfully to disk/MediaStore.
     */
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
                // Confirm capture in Room
                repository.confirmCapture(
                    captureId = captureId,
                    originalUri = outputUri,
                    originalPath = originalPath,
                    sizeBytes = sizeBytes,
                    mimeType = mimeType,
                    displayName = displayName
                )

                // Enqueue durable WorkManager job to survive process termination
                val workRequest = MediaBackupWorker.buildWorkRequest(captureId)
                workManager.enqueueUniqueWork(
                    "backup_$captureId",
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )

                // Also immediately execute in foreground coroutine for instant protection
                repository.executeBackup(captureId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to coordinate capture success for $captureId", e)
            }
        }
    }

    /**
     * Called when capture hardware or saving fails.
     */
    fun onCaptureFailed(captureId: String, error: String) {
        scope.launch {
            try {
                repository.recordCaptureFailure(captureId, error)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to record capture failure for $captureId", e)
            }
        }
    }

    companion object {
        private const val TAG = "CaptureCoordinator"
    }
}
