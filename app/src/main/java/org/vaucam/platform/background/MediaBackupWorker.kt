package org.vaucam.platform.background

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import org.vaucam.data.database.VauCamDatabase
import org.vaucam.data.repository.BackupExecutionResult
import org.vaucam.data.repository.BackupRepository
import org.vaucam.data.storage.ProtectedStorageManager

class MediaBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val captureId = inputData.getString(KEY_CAPTURE_ID) ?: return Result.failure()

        val database = VauCamDatabase.getInstance(applicationContext)
        val storageManager = ProtectedStorageManager(applicationContext)
        val repository = BackupRepository(applicationContext, database.captureDao(), storageManager)

        return when (val result = repository.executeBackup(captureId)) {
            is BackupExecutionResult.Success -> Result.success()
            is BackupExecutionResult.Failure -> {
                if (result.isRetryable && runAttemptCount < BackupRepository.MAX_RETRIES) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            }
        }
    }

    companion object {
        const val KEY_CAPTURE_ID = "key_capture_id"

        fun buildWorkRequest(captureId: String): androidx.work.OneTimeWorkRequest {
            val data = Data.Builder()
                .putString(KEY_CAPTURE_ID, captureId)
                .build()

            val constraints = Constraints.Builder()
                .setRequiresStorageNotLow(true)
                .build()

            return OneTimeWorkRequestBuilder<MediaBackupWorker>()
                .setInputData(data)
                .setConstraints(constraints)
                .build()
        }
    }
}
