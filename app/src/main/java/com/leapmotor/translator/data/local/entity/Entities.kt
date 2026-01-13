package com.leapmotor.translator.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a dictionary entry.
 * 
 * Stores user-defined and cached translations.
 */
@Entity(
    tableName = "dictionary_entries",
    indices = [
        Index(value = ["original_text"], unique = true),
        Index(value = ["is_user_defined"]),
        Index(value = ["category"])
    ]
)
data class DictionaryEntryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    
    @ColumnInfo(name = "original_text")
    val originalText: String,
    
    @ColumnInfo(name = "translated_text")
    val translatedText: String,
    
    @ColumnInfo(name = "is_user_defined")
    val isUserDefined: Boolean = false,
    
    @ColumnInfo(name = "category")
    val category: String? = null,
    
    @ColumnInfo(name = "usage_count")
    val usageCount: Int = 0,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Room entity for translation history/logs.
 */
@Entity(
    tableName = "translation_history",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["original_text"])
    ]
)
data class TranslationHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    
    @ColumnInfo(name = "original_text")
    val originalText: String,
    
    @ColumnInfo(name = "translated_text")
    val translatedText: String,
    
    @ColumnInfo(name = "bounds_left")
    val boundsLeft: Float,
    
    @ColumnInfo(name = "bounds_top")
    val boundsTop: Float,
    
    @ColumnInfo(name = "bounds_right")
    val boundsRight: Float,
    
    @ColumnInfo(name = "bounds_bottom")
    val boundsBottom: Float,
    
    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "source_app")
    val sourceApp: String? = null
)

/**
 * Room entity for app settings/preferences.
 */
@Entity(
    tableName = "app_settings",
    indices = [Index(value = ["key"], unique = true)]
)
data class AppSettingEntity(
    @PrimaryKey
    @ColumnInfo(name = "key")
    val key: String,
    
    @ColumnInfo(name = "value")
    val value: String,
    
    @ColumnInfo(name = "type")
    val type: String = "string", // string, int, float, boolean
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Room entity for caching translation statistics.
 */
@Entity(tableName = "translation_stats")
data class TranslationStatsEntity(
    @PrimaryKey
    @ColumnInfo(name = "date")
    val date: String, // Format: YYYY-MM-DD
    
    @ColumnInfo(name = "total_translations")
    val totalTranslations: Int = 0,
    
    @ColumnInfo(name = "cache_hits")
    val cacheHits: Int = 0,
    
    @ColumnInfo(name = "cache_misses")
    val cacheMisses: Int = 0,
    
    @ColumnInfo(name = "ml_translations")
    val mlTranslations: Int = 0,
    
    @ColumnInfo(name = "user_dictionary_hits")
    val userDictionaryHits: Int = 0,
    
    @ColumnInfo(name = "average_latency_ms")
    val averageLatencyMs: Float = 0f
)
