package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [SwingEntity::class], version = 2, exportSchema = false)
abstract class SwingDatabase : RoomDatabase() {
    abstract fun swingDao(): SwingDao

    companion object {
        @Volatile
        private var INSTANCE: SwingDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE swing_sessions ADD COLUMN videoPath TEXT")
                db.execSQL("ALTER TABLE swing_sessions ADD COLUMN trimStartMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE swing_sessions ADD COLUMN trimEndMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE swing_sessions ADD COLUMN videoAspectRatio REAL NOT NULL DEFAULT 0.5625")
            }
        }

        fun getInstance(context: Context): SwingDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SwingDatabase::class.java,
                    "swing_arc.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
