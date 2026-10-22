package org.vaucam.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Durable record of a capture intent, original output, and its protected backup.
 */
@Entity(
    tableName = "capture_records",
    indices = [
        Index(value = ["state"]),
        Index(value = ["created_at"]),
        Index(value = ["original_uri"])
    ]
)
data class CaptureRecordEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "media_type")
    val mediaType: MediaType,

    @ColumnInfo(name = "state")
    val state: BackupState,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "captured_at")
    val capturedAt: Long? = null,

    @ColumnInfo(name = "original_uri")
    val originalUri: String? = null,

    @ColumnInfo(name = "original_path")
    val originalPath: String? = null,

    @ColumnInfo(name = "original_size_bytes")
    val originalSizeBytes: Long? = null,

    @ColumnInfo(name = "original_sha256")
    val originalSha256: String? = null,

    @ColumnInfo(name = "protected_path")
    val protectedPath: String? = null,

    @ColumnInfo(name = "protected_size_bytes")
    val protectedSizeBytes: Long? = null,

    @ColumnInfo(name = "protected_sha256")
    val protectedSha256: String? = null,

    @ColumnInfo(name = "verified_at")
    val verifiedAt: Long? = null,

    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,

    @ColumnInfo(name = "last_error")
    val lastError: String? = null,

    @ColumnInfo(name = "mime_type")
    val mimeType: String? = null,

    @ColumnInfo(name = "display_name")
    val displayName: String? = null
)
