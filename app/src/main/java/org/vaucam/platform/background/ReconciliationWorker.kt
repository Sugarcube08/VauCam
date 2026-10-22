package org.vaucam.platform.background

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.vaucam.data.database.BackupState
import org.vaucam.data.database.CaptureDao
import org.vaucam.data.database.CaptureRecordEntity
import org.vaucam.data.database.MediaType
import org.vaucam.data.database.VauCamDatabase
import org.vaucam.data.repository.BackupRepository
import org.vaucam.data.storage.ProtectedStorageManager
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Periodic repair pass for work interrupted by process death and media files written before
 * their capture callback could persist the MediaStore URI.
 */
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

        repository.getInterruptedOrReconciliationRecords().forEach { record ->
            if (record.originalUri != null) {
                dao.updateState(record.id, BackupState.BACKUP_PENDING, "Recovered after process restart")
                enqueueBackup(workManager, record.id)
            } else {
                // MediaStore scan below may correlate this record by the timestamped output name.
                dao.updateState(
                    record.id,
                    BackupState.RECOVERY_REQUIRED,
                    "No output URI persisted; waiting for MediaStore reconciliation"
                )
            }
        }

        repository.getPendingBackups().forEach { record ->
            enqueueBackup(workManager, record.id)
        }

        val now = System.currentTimeMillis()
        dao.getByState(BackupState.CAPTURE_REQUESTED).forEach { record ->
            if (now - record.createdAt > CAPTURE_INTENT_GRACE_MS) {
                dao.updateState(
                    record.id,
                    BackupState.RECOVERY_REQUIRED,
                    "Capture intent timed out; checking MediaStore for an orphaned output"
                )
            }
        }

        reconcileMediaStore(dao, workManager)
        storageManager.cleanupStaging()
        return Result.success()
    }

    private fun enqueueBackup(workManager: WorkManager, captureId: String) {
        workManager.enqueueUniqueWork(
            "backup_$captureId",
            ExistingWorkPolicy.KEEP,
            MediaBackupWorker.buildWorkRequest(captureId)
        )
    }

    private suspend fun reconcileMediaStore(dao: CaptureDao, workManager: WorkManager) {
        // RELATIVE_PATH is a scoped-storage column introduced in Android 10.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return

        scanCollection(
            dao = dao,
            workManager = workManager,
            collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_ADDED
            ),
            selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?",
            selectionArgs = arrayOf("%Pictures/VauCam%"),
            mediaType = MediaType.PHOTO,
            mimeType = "image/jpeg"
        )

        scanCollection(
            dao = dao,
            workManager = workManager,
            collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATE_ADDED
            ),
            selection = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?",
            selectionArgs = arrayOf("%Movies/VauCam%"),
            mediaType = MediaType.VIDEO,
            mimeType = "video/mp4"
        )
    }

    private suspend fun scanCollection(
        dao: CaptureDao,
        workManager: WorkManager,
        collection: Uri,
        projection: Array<String>,
        selection: String,
        selectionArgs: Array<String>,
        mediaType: MediaType,
        mimeType: String
    ) {
        try {
            applicationContext.contentResolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val mediaUri = ContentUris.withAppendedId(collection, cursor.getLong(idColumn))
                    if (dao.getByOriginalUri(mediaUri.toString()) != null) continue

                    val displayName = cursor.getString(nameColumn)
                    val dateAddedMs = cursor.getLong(dateColumn) * 1_000L
                    val namedCaptureTime = parseCaptureTimestamp(displayName)
                    val capturedAt = namedCaptureTime ?: dateAddedMs

                    // CameraViewModel names the output immediately after registering its intent.
                    // Matching is deliberately narrow so old intents are not attached to unrelated media.
                    val orphanIntent = namedCaptureTime?.let { timestamp ->
                        dao.findOrphanCaptureIntent(
                            mediaType = mediaType,
                            windowStart = timestamp - INTENT_MATCH_TOLERANCE_MS,
                            windowEnd = timestamp + INTENT_MATCH_TOLERANCE_MS,
                            capturedAt = timestamp
                        )
                    }

                    val captureId = orphanIntent?.id ?: UUID.randomUUID().toString()
                    if (orphanIntent != null) {
                        dao.updateCaptureSuccess(
                            id = captureId,
                            originalUri = mediaUri.toString(),
                            originalPath = null,
                            originalSizeBytes = cursor.getLong(sizeColumn),
                            mimeType = mimeType,
                            displayName = displayName,
                            capturedAt = capturedAt,
                            newState = BackupState.BACKUP_PENDING
                        )
                        Log.i(TAG, "Recovered orphaned media into capture intent $captureId")
                    } else {
                        dao.insert(
                            CaptureRecordEntity(
                                id = captureId,
                                mediaType = mediaType,
                                state = BackupState.BACKUP_PENDING,
                                createdAt = capturedAt,
                                capturedAt = capturedAt,
                                originalUri = mediaUri.toString(),
                                originalSizeBytes = cursor.getLong(sizeColumn),
                                displayName = displayName,
                                mimeType = mimeType
                            )
                        )
                    }
                    enqueueBackup(workManager, captureId)
                }
            }
        } catch (e: Exception) {
            // Permission revocation, a temporarily unavailable provider, or schema variation
            // must not crash the worker; a later scheduled pass can retry reconciliation.
            Log.w(TAG, "MediaStore reconciliation failed for $collection", e)
        }
    }

    private fun parseCaptureTimestamp(displayName: String?): Long? {
        if (displayName.isNullOrBlank()) return null
        val baseName = displayName.substringAfterLast('/').substringBeforeLast('.', displayName)
        return runCatching {
            SimpleDateFormat(FILENAME_FORMAT, Locale.US).apply {
                isLenient = false
            }.parse(baseName)?.time
        }.getOrNull()
    }

    companion object {
        private const val TAG = "VauCamReconcile"
        private const val UNIQUE_WORK_NAME = "vaucam_reconciliation_worker"
        private const val CAPTURE_INTENT_GRACE_MS = 120_000L
        private const val INTENT_MATCH_TOLERANCE_MS = 10_000L
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"

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
