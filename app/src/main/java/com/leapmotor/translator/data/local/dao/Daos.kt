package com.leapmotor.translator.data.local.dao

import androidx.room.*
import com.leapmotor.translator.data.local.entity.DictionaryEntryEntity
import com.leapmotor.translator.data.local.entity.TranslationHistoryEntity
import com.leapmotor.translator.data.local.entity.TranslationStatsEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for dictionary operations.
 * 
 * Provides CRUD operations for translation dictionary with
 * reactive Flow support for real-time updates.
 */
@Dao
interface DictionaryDao {
    
    // ========================================================================
    // QUERIES
    // ========================================================================
    
    @Query("SELECT * FROM dictionary_entries ORDER BY usage_count DESC, updated_at DESC")
    fun getAllEntries(): Flow<List<DictionaryEntryEntity>>
    
    @Query("SELECT * FROM dictionary_entries WHERE is_user_defined = 1 ORDER BY updated_at DESC")
    fun getUserDefinedEntries(): Flow<List<DictionaryEntryEntity>>
    
    @Query("SELECT * FROM dictionary_entries WHERE is_user_defined = 0 ORDER BY usage_count DESC")
    fun getCachedEntries(): Flow<List<DictionaryEntryEntity>>
    
    @Query("SELECT * FROM dictionary_entries WHERE original_text = :text LIMIT 1")
    suspend fun getEntryByText(text: String): DictionaryEntryEntity?
    
    @Query("SELECT * FROM dictionary_entries WHERE category = :category ORDER BY usage_count DESC")
    fun getEntriesByCategory(category: String): Flow<List<DictionaryEntryEntity>>
    
    @Query("""
        SELECT * FROM dictionary_entries 
        WHERE original_text LIKE '%' || :query || '%' 
           OR translated_text LIKE '%' || :query || '%'
        ORDER BY usage_count DESC
        LIMIT :limit
    """)
    suspend fun searchEntries(query: String, limit: Int = 50): List<DictionaryEntryEntity>
    
    @Query("SELECT COUNT(*) FROM dictionary_entries")
    suspend fun getEntryCount(): Int
    
    @Query("SELECT COUNT(*) FROM dictionary_entries WHERE is_user_defined = 1")
    suspend fun getUserDefinedCount(): Int
    
    // ========================================================================
    // INSERTS
    // ========================================================================
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: DictionaryEntryEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<DictionaryEntryEntity>)
    
    @Transaction
    suspend fun insertOrUpdateEntry(originalText: String, translatedText: String, isUserDefined: Boolean) {
        val existing = getEntryByText(originalText)
        if (existing != null) {
            updateEntry(existing.copy(
                translatedText = translatedText,
                isUserDefined = isUserDefined || existing.isUserDefined,
                updatedAt = System.currentTimeMillis()
            ))
        } else {
            insertEntry(DictionaryEntryEntity(
                originalText = originalText,
                translatedText = translatedText,
                isUserDefined = isUserDefined
            ))
        }
    }
    
    // ========================================================================
    // UPDATES
    // ========================================================================
    
    @Update
    suspend fun updateEntry(entry: DictionaryEntryEntity)
    
    @Query("UPDATE dictionary_entries SET usage_count = usage_count + 1 WHERE original_text = :text")
    suspend fun incrementUsageCount(text: String)
    
    @Query("UPDATE dictionary_entries SET translated_text = :translation, updated_at = :timestamp WHERE original_text = :original")
    suspend fun updateTranslation(original: String, translation: String, timestamp: Long = System.currentTimeMillis())
    
    // ========================================================================
    // DELETES
    // ========================================================================
    
    @Delete
    suspend fun deleteEntry(entry: DictionaryEntryEntity)
    
    @Query("DELETE FROM dictionary_entries WHERE original_text = :text")
    suspend fun deleteEntryByText(text: String)
    
    @Query("DELETE FROM dictionary_entries WHERE is_user_defined = 0")
    suspend fun clearCache()
    
    @Query("DELETE FROM dictionary_entries")
    suspend fun deleteAll()
    
    // Keep only top N entries by usage
    @Query("""
        DELETE FROM dictionary_entries 
        WHERE is_user_defined = 0 
        AND id NOT IN (
            SELECT id FROM dictionary_entries 
            WHERE is_user_defined = 0 
            ORDER BY usage_count DESC 
            LIMIT :keepCount
        )
    """)
    suspend fun trimCache(keepCount: Int)
}

/**
 * DAO for translation history.
 */
@Dao
interface TranslationHistoryDao {
    
    @Query("SELECT * FROM translation_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentHistory(limit: Int = 100): Flow<List<TranslationHistoryEntity>>
    
    @Query("SELECT * FROM translation_history WHERE timestamp >= :since ORDER BY timestamp DESC")
    suspend fun getHistorySince(since: Long): List<TranslationHistoryEntity>
    
    @Insert
    suspend fun insertHistory(entry: TranslationHistoryEntity): Long
    
    @Insert
    suspend fun insertHistoryBatch(entries: List<TranslationHistoryEntity>)
    
    @Query("DELETE FROM translation_history WHERE timestamp < :before")
    suspend fun deleteHistoryBefore(before: Long)
    
    @Query("DELETE FROM translation_history")
    suspend fun clearHistory()
    
    @Query("SELECT COUNT(*) FROM translation_history")
    suspend fun getHistoryCount(): Int
}

/**
 * DAO for translation statistics.
 */
@Dao
interface TranslationStatsDao {
    
    @Query("SELECT * FROM translation_stats WHERE date = :date")
    suspend fun getStatsForDate(date: String): TranslationStatsEntity?
    
    @Query("SELECT * FROM translation_stats ORDER BY date DESC LIMIT :days")
    suspend fun getRecentStats(days: Int = 30): List<TranslationStatsEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStats(stats: TranslationStatsEntity)
    
    @Query("""
        UPDATE translation_stats 
        SET total_translations = total_translations + 1,
            cache_hits = cache_hits + :cacheHit,
            cache_misses = cache_misses + :cacheMiss,
            ml_translations = ml_translations + :mlTranslation,
            user_dictionary_hits = user_dictionary_hits + :userDictHit
        WHERE date = :date
    """)
    suspend fun incrementStats(
        date: String,
        cacheHit: Int = 0,
        cacheMiss: Int = 0,
        mlTranslation: Int = 0,
        userDictHit: Int = 0
    )
    
    @Transaction
    suspend fun recordTranslation(
        date: String,
        wasCacheHit: Boolean,
        wasUserDict: Boolean,
        wasMLTranslation: Boolean
    ) {
        val existing = getStatsForDate(date)
        if (existing == null) {
            insertStats(TranslationStatsEntity(
                date = date,
                totalTranslations = 1,
                cacheHits = if (wasCacheHit) 1 else 0,
                cacheMisses = if (!wasCacheHit) 1 else 0,
                mlTranslations = if (wasMLTranslation) 1 else 0,
                userDictionaryHits = if (wasUserDict) 1 else 0
            ))
        } else {
            incrementStats(
                date = date,
                cacheHit = if (wasCacheHit) 1 else 0,
                cacheMiss = if (!wasCacheHit) 1 else 0,
                mlTranslation = if (wasMLTranslation) 1 else 0,
                userDictHit = if (wasUserDict) 1 else 0
            )
        }
    }
}
