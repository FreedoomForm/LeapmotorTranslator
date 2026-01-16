package com.leapmotor.translator.domain.repository

import com.leapmotor.translator.core.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository interface for translation operations.
 * 
 * Following Clean Architecture, this interface is part of the domain layer.
 * The actual implementation is in the data layer.
 * 
 * Benefits:
 * - Abstracts away data source details
 * - Easy to mock for testing
 * - Can swap implementations without changing domain logic
 */
interface TranslationRepository {
    
    // ============================================================================
    // STATE
    // ============================================================================
    
    /**
     * Current state of the translation model.
     */
    val modelState: StateFlow<ModelState>
    
    /**
     * Whether the translation model is ready for use.
     */
    val isReady: Boolean
    
    // ============================================================================
    // OPERATIONS
    // ============================================================================
    
    /**
     * Configure translation languages.
     * Must be called before initialize().
     * 
     * @param sourceLang Source language code (e.g. "zh")
     * @param targetLang Target language code (e.g. "ru")
     */
    fun configure(sourceLang: String, targetLang: String)

    /**
     * Initialize the translation engine and download model if needed.
     * 
     * @return Result indicating success or failure
     */
    suspend fun initialize(): Result<Unit>
    
    /**
     * Translate text from Chinese to Russian.
     * 
     * @param text Chinese text to translate
     * @return Result containing Russian translation or error
     */
    suspend fun translate(text: String): Result<String>
    
    /**
     * Translate multiple texts in batch.
     * 
     * @param texts List of Chinese texts
     * @return Result containing map of original -> translated
     */
    suspend fun translateBatch(texts: List<String>): Result<Map<String, String>>
    
    /**
     * Check if a translation is cached.
     * 
     * @param text Original Chinese text
     * @return true if cached
     */
    fun isCached(text: String): Boolean
    
    /**
     * Get cached translation if available.
     * 
     * @param text Original Chinese text
     * @return Cached translation or null
     */
    fun getCached(text: String): String?
    
    /**
     * Add or update a translation manually (user dictionary).
     * 
     * @param original Chinese text
     * @param translation Russian translation
     */
    suspend fun updateTranslation(original: String, translation: String)
    
    /**
     * Preload common translations into cache.
     * 
     * @param translations Map of Chinese -> Russian translations
     */
    fun preloadTranslations(translations: Map<String, String>)
    
    /**
     * Get all translations (cache + user dictionary).
     * 
     * @return Map of all known translations
     */
    fun getAllTranslations(): Map<String, String>
    
    /**
     * Get cache statistics.
     * 
     * @return Current cache stats
     */
    fun getCacheStats(): CacheStats
    
    /**
     * Clear the translation cache (but not user dictionary).
     */
    fun clearCache()
    
    /**
     * Release resources when no longer needed.
     */
    fun release()
    
    // ============================================================================
    // DATA CLASSES
    // ============================================================================
    
    /**
     * Represents the state of the translation model.
     */
    sealed class ModelState {
        object NotInitialized : ModelState()
        object Initializing : ModelState()
        object Downloading : ModelState()
        object Ready : ModelState()
        data class Error(val exception: Throwable, val message: String) : ModelState()
    }
    
    /**
     * Cache statistics.
     */
    data class CacheStats(
        val size: Int,
        val hits: Long,
        val misses: Long,
        val hitRate: Float,
        val userDictionarySize: Int = 0
    )
}
