package com.leapmotor.translator.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.leapmotor.translator.data.local.dao.DictionaryDao
import com.leapmotor.translator.data.local.dao.TranslationHistoryDao
import com.leapmotor.translator.data.local.dao.TranslationStatsDao
import com.leapmotor.translator.data.local.entity.AppSettingEntity
import com.leapmotor.translator.data.local.entity.DictionaryEntryEntity
import com.leapmotor.translator.data.local.entity.TranslationHistoryEntity
import com.leapmotor.translator.data.local.entity.TranslationStatsEntity

/**
 * Room database for the translator app.
 * 
 * Contains:
 * - Dictionary entries (user-defined + cached translations)
 * - Translation history
 * - App settings
 * - Translation statistics
 */
@Database(
    entities = [
        DictionaryEntryEntity::class,
        TranslationHistoryEntity::class,
        AppSettingEntity::class,
        TranslationStatsEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class TranslatorDatabase : RoomDatabase() {
    
    // ========================================================================
    // DAOs
    // ========================================================================
    
    abstract fun dictionaryDao(): DictionaryDao
    abstract fun translationHistoryDao(): TranslationHistoryDao
    abstract fun translationStatsDao(): TranslationStatsDao
    
    companion object {
        const val DATABASE_NAME = "translator_database"
        
        /**
         * Create database instance.
         * Note: For production, use Hilt to provide as singleton.
         */
        fun create(context: Context): TranslatorDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                TranslatorDatabase::class.java,
                DATABASE_NAME
            )
                .addCallback(DatabaseCallback())
                .addMigrations(*MIGRATIONS)
                .build()
        }
        
        /**
         * Migration strategies for database upgrades.
         */
        private val MIGRATIONS = arrayOf<Migration>(
            // Example migration from version 1 to 2
            // MIGRATION_1_2
        )
        
        // Example migration
        // private val MIGRATION_1_2 = object : Migration(1, 2) {
        //     override fun migrate(database: SupportSQLiteDatabase) {
        //         database.execSQL("ALTER TABLE dictionary_entries ADD COLUMN notes TEXT")
        //     }
        // }
    }
    
    /**
     * Callback for database operations.
     */
    private class DatabaseCallback : Callback() {
        
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            // Database created for the first time
            // Could pre-populate with common translations here
        }
        
        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            // Database opened
        }
    }
}

/**
 * Type converters for Room.
 * Add this class if you need to store complex types.
 */
// @TypeConverters(Converters::class)
// class Converters {
//     @TypeConverter
//     fun fromStringList(value: List<String>?): String? {
//         return value?.joinToString(",")
//     }
//     
//     @TypeConverter
//     fun toStringList(value: String?): List<String>? {
//         return value?.split(",")?.map { it.trim() }
//     }
// }
