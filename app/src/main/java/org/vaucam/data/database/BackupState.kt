package org.vaucam.data.database

/**
 * Explicit durable state machine for the VauCam capture and backup protocol.
 */
enum class BackupState {
    /** Durable intent registered in database; capture has not yet been confirmed by camera hardware. */
    CAPTURE_REQUESTED,

    /** Original output is confirmed written to disk or MediaStore. */
    CAPTURED,

    /** Original exists and backup copy operation is queued. */
    BACKUP_PENDING,

    /** Backup copy to staging storage is actively underway. */
    BACKUP_IN_PROGRESS,

    /** Byte counts and SHA-256 integrity digests are being computed and verified. */
    VERIFYING,

    /** Backup copy was atomically committed and SHA-256 verification passed. */
    PROTECTED,

    /** Backup operation failed; retry or manual intervention required. */
    BACKUP_FAILED,

    /** The expected original media file could not be located on disk or MediaStore. */
    SOURCE_MISSING,

    /** Discrepancy detected between database and filesystem; requires reconciliation. */
    RECOVERY_REQUIRED
}

enum class MediaType {
    PHOTO,
    VIDEO
}
