package com.graydyn.tracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.graydyn.tracker.data.model.DiaryEntry
import com.graydyn.tracker.data.model.Food
import com.graydyn.tracker.data.model.Goals

@Database(
    entities = [Food::class, DiaryEntry::class, Goals::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class TrackerDatabase : RoomDatabase() {
    abstract fun foodDao(): FoodDao
    abstract fun diaryEntryDao(): DiaryEntryDao
    abstract fun goalsDao(): GoalsDao

    companion object {
        @Volatile private var INSTANCE: TrackerDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE foods ADD COLUMN unitType TEXT NOT NULL DEFAULT 'GRAM'")
                db.execSQL("ALTER TABLE foods ADD COLUMN caloriesPerItem REAL")
                db.execSQL("ALTER TABLE foods ADD COLUMN proteinPerItem REAL")
                db.execSQL("ALTER TABLE foods ADD COLUMN fatPerItem REAL")
                db.execSQL("ALTER TABLE foods ADD COLUMN carbsPerItem REAL")

                db.execSQL("ALTER TABLE diary_entries ADD COLUMN unitType TEXT NOT NULL DEFAULT 'GRAM'")
                db.execSQL("ALTER TABLE diary_entries ADD COLUMN count REAL")
            }
        }

        fun getInstance(context: Context): TrackerDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TrackerDatabase::class.java,
                    "tracker.db"
                ).addMigrations(MIGRATION_1_2)
                    .build().also { INSTANCE = it }
            }
    }
}
