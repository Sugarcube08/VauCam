package org.vaucam.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CaptureRecordEntity::class],
    version = 1,
    exportSchema = false
)
abstract class VauCamDatabase : RoomDatabase() {

    abstract fun captureDao(): CaptureDao

    companion object {
        private const val DATABASE_NAME = "vaucam_backup.db"

        @Volatile
        private var instance: VauCamDatabase? = null

        fun getInstance(context: Context): VauCamDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    VauCamDatabase::class.java,
                    DATABASE_NAME
                )
                    // Never silently erase the capture ledger. Future schema changes must
                    // declare explicit migrations; a failed migration should fail visibly.
                    .build()
                    .also { instance = it }
            }
        }
    }
}
