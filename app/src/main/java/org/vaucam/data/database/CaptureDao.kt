package org.vaucam.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CaptureDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: CaptureRecordEntity)

    @Update
    suspend fun update(record: CaptureRecordEntity)

    @Query("SELECT * FROM capture_records WHERE id = :id")
    suspend fun getById(id: String): CaptureRecordEntity?

    @Query("SELECT * FROM capture_records WHERE original_uri = :uri LIMIT 1")
    suspend fun getByOriginalUri(uri: String): CaptureRecordEntity?

    @Query("SELECT * FROM capture_records WHERE state IN ('CAPTURED', 'BACKUP_PENDING') ORDER BY created_at ASC")
    suspend fun getPendingBackups(): List<CaptureRecordEntity>

    @Query("SELECT * FROM capture_records WHERE state IN ('BACKUP_IN_PROGRESS', 'VERIFYING', 'RECOVERY_REQUIRED')")
    suspend fun getInterruptedOrReconciliationRecords(): List<CaptureRecordEntity>

    @Query("SELECT * FROM capture_records WHERE state = :state ORDER BY created_at DESC")
    suspend fun getByState(state: BackupState): List<CaptureRecordEntity>

    @Query("SELECT * FROM capture_records ORDER BY created_at DESC LIMIT :limit")
    fun observeRecentRecords(limit: Int = 50): Flow<List<CaptureRecordEntity>>

    @Query("SELECT COUNT(*) FROM capture_records WHERE state = 'PROTECTED'")
    fun observeProtectedCount(): Flow<Int>

    @Query("UPDATE capture_records SET state = :newState, last_error = :error WHERE id = :id")
    suspend fun updateState(id: String, newState: BackupState, error: String? = null)

    @Query("""
        UPDATE capture_records 
        SET state = :newState,
            original_uri = :originalUri,
            original_path = :originalPath,
            original_size_bytes = :originalSizeBytes,
            mime_type = :mimeType,
            display_name = :displayName,
            captured_at = :capturedAt
        WHERE id = :id
    """)
    suspend fun updateCaptureSuccess(
        id: String,
        originalUri: String,
        originalPath: String?,
        originalSizeBytes: Long,
        mimeType: String?,
        displayName: String?,
        capturedAt: Long,
        newState: BackupState = BackupState.BACKUP_PENDING
    )

    @Query("""
        UPDATE capture_records 
        SET state = 'PROTECTED',
            protected_path = :protectedPath,
            protected_size_bytes = :protectedSizeBytes,
            protected_sha256 = :protectedSha256,
            original_sha256 = :originalSha256,
            verified_at = :verifiedAt,
            last_error = null
        WHERE id = :id
    """)
    suspend fun updateVerificationSuccess(
        id: String,
        protectedPath: String,
        protectedSizeBytes: Long,
        protectedSha256: String,
        originalSha256: String,
        verifiedAt: Long
    )

    @Query("""
        UPDATE capture_records 
        SET retry_count = retry_count + 1,
            state = :newState,
            last_error = :error
        WHERE id = :id
    """)
    suspend fun recordRetryFailure(
        id: String,
        error: String,
        newState: BackupState
    )

    @Query("DELETE FROM capture_records WHERE id = :id")
    suspend fun delete(id: String)
}
