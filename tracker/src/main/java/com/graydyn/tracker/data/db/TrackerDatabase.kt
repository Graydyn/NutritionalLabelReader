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
    entities = [
        Food::class,
        DiaryEntry::class,
        Goals::class,
        com.graydyn.tracker.data.model.SavedMeal::class,
        com.graydyn.tracker.data.model.SavedMealItem::class,
        com.graydyn.tracker.data.model.SavedMealSlotApplication::class
    ],
    version = 5,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class TrackerDatabase : RoomDatabase() {
    abstract fun foodDao(): FoodDao
    abstract fun diaryEntryDao(): DiaryEntryDao
    abstract fun goalsDao(): GoalsDao
    abstract fun savedMealDao(): SavedMealDao

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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `saved_meals` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `name` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `saved_meal_items` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `savedMealId` INTEGER NOT NULL,
                        `position` INTEGER NOT NULL,
                        `label` TEXT NOT NULL,
                        `foodId` INTEGER,
                        `unitType` TEXT NOT NULL,
                        `grams` REAL,
                        `count` REAL,
                        `calories` INTEGER,
                        `protein` REAL,
                        `fat` REAL,
                        `carbs` REAL,
                        FOREIGN KEY(`savedMealId`) REFERENCES `saved_meals`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_saved_meal_items_savedMealId`
                    ON `saved_meal_items` (`savedMealId`)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `saved_meal_slot_applications` (
                        `savedMealId` INTEGER NOT NULL,
                        `mealType` TEXT NOT NULL,
                        `lastAppliedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`savedMealId`, `mealType`),
                        FOREIGN KEY(`savedMealId`) REFERENCES `saved_meals`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Wipes everything that references foods.id; goals is preserved so users keep
                // their calorie/macro targets across the upgrade.
                // saved_meal_slot_applications cascades from saved_meals (FK ON DELETE CASCADE)
                // but cascade only fires on row delete, not bulk DELETE FROM, so clear it explicitly.
                db.execSQL("DELETE FROM saved_meal_slot_applications")
                db.execSQL("DELETE FROM saved_meal_items")
                db.execSQL("DELETE FROM saved_meals")
                db.execSQL("DELETE FROM diary_entries")
                db.execSQL("DROP TABLE foods")
                db.execSQL(
                    """
                    CREATE TABLE foods (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL,
                        unitType TEXT NOT NULL,
                        caloriesPer100g REAL,
                        proteinPer100g REAL,
                        fatPer100g REAL,
                        carbsPer100g REAL,
                        caloriesPerItem REAL,
                        proteinPerItem REAL,
                        fatPerItem REAL,
                        carbsPerItem REAL,
                        foundational INTEGER NOT NULL DEFAULT 0,
                        userAdded INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE foods ADD COLUMN caloriesPerServing REAL")
                db.execSQL("ALTER TABLE foods ADD COLUMN proteinPerServing REAL")
                db.execSQL("ALTER TABLE foods ADD COLUMN fatPerServing REAL")
                db.execSQL("ALTER TABLE foods ADD COLUMN carbsPerServing REAL")
                db.execSQL("ALTER TABLE foods ADD COLUMN gramsPerServing REAL")
                db.execSQL("ALTER TABLE foods ADD COLUMN itemsPerServing REAL")
                db.execSQL("ALTER TABLE diary_entries ADD COLUMN servings REAL")
                db.execSQL("ALTER TABLE saved_meal_items ADD COLUMN servings REAL")
            }
        }

        fun getInstance(context: Context): TrackerDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TrackerDatabase::class.java,
                    "tracker.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build().also { INSTANCE = it }
            }
    }
}