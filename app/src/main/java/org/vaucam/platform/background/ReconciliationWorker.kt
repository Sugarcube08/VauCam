package org.vaucam.platform.background

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.vaucam.data.database.BackupState
import org.vaucam.data.database.CaptureRecordEntity
import org.vaucam.data.database.MediaType
import org.vaucam.data.database.VauCamDatabase
import org.vaucam.data.repository.BackupRepository
import org.vaucam.data.storage.ProtectedStorageManager
import java.util.UUID
import java.util.concurrent.TimeUnit

class ReconciliationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val database = VauCamDatabase.getInstance(applicationContext)
        val dao = database.captureDao()
        val storageManager = ProtectedStorageManager(applicationContext)
        val repository = BackupRepository(applicationContext, dao, storageManager)
        val workManager = WorkManager.getInstance(applicationContext)

        // Recover interrupted backup states idempotently. Never create parallel work chains
        // for a capture: the capture callback and reconciliation worker can race.
        repository.getInterruptedOrReconciliationRecords().forEach { record ->
            if (record.originalUri != null) {
                dao.updateState(record.id, BackupState.BACKUP_PENDING, "Recovered after process restart")
                workManager.enqueueUniqueWork(
                    "backup_${record.id}",
                    androidx.work.ExistingWorkPolicy.KEEP,
                    MediaBackupWorker.buildWorkRequest(record.id)
                )
            } else {
                // An intent without an output URI cannot be copied yet. Let MediaStore scanning
                // correlate any orphan output before treating the record as unrecoverable.
                dao.updateState(
                    record.id,
                    BackupState.RECOVERY_REQUIRED,
                    "No original URI recorded; waiting for MediaStore reconciliation"
                )
            }
        }

        // Requeue rows that have a confirmed source and are awaiting/retrying a backup.
        repository.getPendingBackups().forEach { record ->
            workManager.enqueueUniqueWork(
                "backup_${record.id}",
                androidx.work.ExistingWorkPolicy.KEEP,
                MediaBackupWorker.buildWorkRequest(record.id)
            )
        }

        // A stale request may have produced a file before the process died. Mark it for
        // reconciliation rather than assuming capture failed.
        val now = System.currentTimeMillis()
        dao.getByState(BackupState.CAPTURE_REQUESTED).forEach { record ->
            if (now - record.createdAt > 120_000L) {
                dao.updateState(
                    record.id,
                    BackupState.RECOVERY_REQUIRED,
                    "Capture intent timed out; checking MediaStore for an orphan output"
                )
            }
        }

        // 3. Scan MediaStore for unindexed VauCam captures (orphaned files)
        reconcileMediaStore(dao, workManager)

        // 4. Clean up lingering staging tmp files
        storageManager.cleanupStaging()

        return Result.success()
    }

    private suspend fun reconcileMediaStore(dao: org.vaucam.data.database.CaptureDao, workManager: WorkManager) {
        val resolver = applicationContext.contentResolver

        // Check Images
        val imageProjection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED
        )
        val imageSelection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        val imageArgs = arrayOf("%Pictures/VauCam%")

        runCatching {
            resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                imageProjection,
                imageSelection,
                imageArgs,
                null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol)
                    val size = cursor.getLong(sizeCol)
                    val dateAddedSec = cursor.getLong(dateCol)
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                    val existing = dao.getByOriginalUri(uri.toString())
                    if (existing == null) {
                        val captureId = UUID.randomUUID().toString()
                        val newRecord = CaptureRecordEntity(
                            id = captureId,
                            mediaType = MediaType.PHOTO,
                            state = BackupState.BACKUP_PENDING,
                            createdAt = dateAddedSec * 1000L,
                            capturedAt = dateAddedSec * 1000L,
                            originalUri = uri.toString(),
                            originalSizeBytes = size,
                            displayName = name,
                            mimeType = "image/jpeg"
                        )
                        dao.insert(newRecord)
                        workManager.enqueue(MediaBackupWorker.buildWorkRequest(captureId))
                    }
                }
            }
        }

        // Check Videos
        val videoProjection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED
        )
        val videoSelection = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
        val videoArgs = arrayOf("%Movies/VauCam%")

        runCatching {
            resolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoProjection,
                videoSelection,
                videoArgs,
                null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol)
                    val size = cursor.getLong(sizeCol)
                    val dateAddedSec = cursor.getLong(dateCol)
                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                    val existing = dao.getByOriginalUri(uri.toString())
                    if (existing == null) {
                        val captureId = UUID.randomUUID().toString()
                        val newRecord = CaptureRecordEntity(
                            id = captureId,
                            mediaType = MediaType.VIDEO,
                            state = BackupState.BACKUP_PENDING,
                            createdAt = dateAddedSec * 1000L,
                            capturedAt = dateAddedSec * 1000L,
                            originalUri = uri.toString(),
                            originalSizeBytes = size,
                            displayName = name,
                            mimeType = "video/mp4"
                        )
                        dao.insert(newRecord)
                        workManager.enqueue(MediaBackupWorker.buildWorkRequest(captureId))
                    }
                }
            }
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "vaucam_reconciliation_worker"

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReconciliationWorker>(1, TimeUnit.HOURS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
