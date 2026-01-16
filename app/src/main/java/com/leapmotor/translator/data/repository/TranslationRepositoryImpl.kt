package com.leapmotor.translator.data.repository

import android.content.Context
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.Translator
import com.leapmotor.translator.core.Logger
import com.leapmotor.translator.core.Result
import com.leapmotor.translator.data.local.dao.DictionaryDao
import com.leapmotor.translator.data.local.entity.DictionaryEntryEntity
import com.leapmotor.translator.domain.repository.TranslationRepository
import com.leapmotor.translator.domain.repository.TranslationRepository.CacheStats
import com.leapmotor.translator.domain.repository.TranslationRepository.ModelState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Implementation of TranslationRepository using Google ML Kit and Room.
 * 
 * Injected via Hilt with:
 * - ML Kit Translator
 * - Room DictionaryDao
 * 
 * Features:
 * - On-device translation
 * - In-memory caching with Room persistence
 * - User dictionary support
 * - Statistics tracking
 */
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions

@Singleton
class TranslationRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dictionaryDao: DictionaryDao
) : TranslationRepository {
    
    companion object {
        private const val TAG = "TranslationRepository"
        private const val MAX_MEMORY_CACHE_SIZE = 2000
    }
    
    // ========================================================================
    // STATE
    // ========================================================================
    
    private val _modelState = MutableStateFlow<ModelState>(ModelState.NotInitialized)
    override val modelState: StateFlow<ModelState> = _modelState.asStateFlow()
    
    override val isReady: Boolean
        get() = _modelState.value is ModelState.Ready
    
    // ========================================================================
    // CACHE
    // ========================================================================
    
    // In-memory cache for fast lookups
    private val memoryCache = ConcurrentHashMap<String, String>()
    
    // Statistics
    private val cacheHits = AtomicLong(0)
    private val cacheMisses = AtomicLong(0)
    private var userDictionarySize = 0
    
    // State
    private var translator: Translator? = null
    private var sourceLang = TranslateLanguage.CHINESE
    private var targetLang = TranslateLanguage.RUSSIAN
    
    // ========================================================================
    // INITIALIZATION
    // ========================================================================
    
    override fun configure(sourceLang: String, targetLang: String) {
        if (this.sourceLang != sourceLang || this.targetLang != targetLang) {
            this.sourceLang = sourceLang
            this.targetLang = targetLang
            // Reset model state to force re-initialization
            closeTranslator()
            _modelState.value = ModelState.NotInitialized
            Logger.i(TAG, "Configured languages: $sourceLang -> $targetLang")
        }
    }
    
    override suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        if (_modelState.value is ModelState.Ready && translator != null) {
            return@withContext Result.success(Unit)
        }
        
        if (_modelState.value is ModelState.Initializing ||
            _modelState.value is ModelState.Downloading) {
            return@withContext Result.error("Initialization in progress")
        }
        
        _modelState.value = ModelState.Initializing
        Logger.i(TAG, "Initializing translation model for $sourceLang -> $targetLang")
        
        try {
            // Load user dictionary into memory cache
            loadUserDictionaryToMemory()
            
            // Create translator options
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(sourceLang)
                .setTargetLanguage(targetLang)
                .build()
                
            // Create client
            translator = Translation.getClient(options)
            
            // Download ML model
            _modelState.value = ModelState.Downloading
            
            var success = downloadModel(
                DownloadConditions.Builder().requireWifi().build()
            )
            
            if (!success) {
                Logger.w(TAG, "WiFi download failed, trying any network")
                success = downloadModel(DownloadConditions.Builder().build())
            }
            
            if (success) {
                _modelState.value = ModelState.Ready
                Logger.i(TAG, "Translation model ready")
                return@withContext Result.success(Unit)
            } else {
                val error = Exception("Failed to download translation model")
                _modelState.value = ModelState.Error(error, error.message ?: "Download failed")
                return@withContext Result.error(error)
            }
            
        } catch (e: Exception) {
            Logger.e(TAG, "Initialization failed", e)
            _modelState.value = ModelState.Error(e, e.message ?: "Initialization failed")
            return@withContext Result.error(e)
        }
    }
    
    private suspend fun loadUserDictionaryToMemory() {
        val entries = dictionaryDao.searchEntries("", 10000)
        entries.forEach { entry ->
            memoryCache[entry.originalText] = entry.translatedText
        }
        userDictionarySize = entries.count { it.isUserDefined }
        Logger.d(TAG, "Loaded ${entries.size} entries to memory cache")
    }
    
    private suspend fun downloadModel(conditions: DownloadConditions): Boolean =
        suspendCancellableCoroutine { continuation ->
            translator?.downloadModelIfNeeded(conditions)
                ?.addOnSuccessListener {
                    if (continuation.isActive) continuation.resume(true)
                }
                ?.addOnFailureListener {
                    if (continuation.isActive) continuation.resume(false)
                } ?: continuation.resume(false)
        }
    
    // ========================================================================
    // TRANSLATION
    // ========================================================================
    
    override suspend fun translate(text: String): Result<String> = withContext(Dispatchers.IO) {
        if (text.isBlank()) {
            return@withContext Result.success(text)
        }
        
        val normalizedText = text.trim()
        
        // Check memory cache first
        memoryCache[normalizedText]?.let {
            cacheHits.incrementAndGet()
            dictionaryDao.incrementUsageCount(normalizedText)
            return@withContext Result.success(it)
        }
        
        // Cache miss
        cacheMisses.incrementAndGet()
        
        if (!isReady) {
            Logger.w(TAG, "Translation requested but model not ready")
            return@withContext Result.success(text)
        }
        
        try {
            val translation = performTranslation(normalizedText)
            
            // Add to memory cache
            if (memoryCache.size < MAX_MEMORY_CACHE_SIZE) {
                memoryCache[normalizedText] = translation
            }
            
            // Persist to Room database (cached entry)
            dictionaryDao.insertEntry(
                DictionaryEntryEntity(
                    originalText = normalizedText,
                    translatedText = translation,
                    isUserDefined = false
                )
            )
            
            return@withContext Result.success(translation)
            
        } catch (e: Exception) {
            Logger.e(TAG, "Translation failed", e)
            return@withContext Result.error(e)
        }
    }
    
    private suspend fun performTranslation(text: String): String =
        suspendCancellableCoroutine { continuation ->
            translator?.translate(text)
                ?.addOnSuccessListener { result ->
                    if (continuation.isActive) continuation.resume(result)
                }
                ?.addOnFailureListener {
                    if (continuation.isActive) continuation.resume(text) // Fallback
                } ?: continuation.resume(text)
        }
    
    override suspend fun translateBatch(texts: List<String>): Result<Map<String, String>> =
        withContext(Dispatchers.IO) {
            val results = mutableMapOf<String, String>()
            
            for (text in texts) {
                val result = translate(text)
                results[text] = result.getOrDefault(text)
            }
            
            Result.success(results)
        }
    
    // ========================================================================
    // CACHE MANAGEMENT
    // ========================================================================
    
    override fun isCached(text: String): Boolean {
        return memoryCache.containsKey(text.trim())
    }
    
    override fun getCached(text: String): String? {
        return memoryCache[text.trim()]
    }
    
    override fun preloadTranslations(translations: Map<String, String>) {
        memoryCache.putAll(translations)
        Logger.i(TAG, "Preloaded ${translations.size} translations")
    }
    
    override fun clearCache() {
        memoryCache.clear()
        cacheHits.set(0)
        cacheMisses.set(0)
        Logger.i(TAG, "Memory cache cleared")
    }
    
    override fun getCacheStats(): CacheStats {
        val hits = cacheHits.get()
        val misses = cacheMisses.get()
        val total = hits + misses
        
        return CacheStats(
            size = memoryCache.size,
            hits = hits,
            misses = misses,
            hitRate = if (total > 0) hits.toFloat() / total else 0f,
            userDictionarySize = userDictionarySize
        )
    }
    
    // ========================================================================
    // USER DICTIONARY
    // ========================================================================
    
    override suspend fun updateTranslation(original: String, translation: String) {
        val normalizedOriginal = original.trim()
        val normalizedTranslation = translation.trim()
        
        // Update memory cache
        memoryCache[normalizedOriginal] = normalizedTranslation
        
        // Update/insert in Room
        dictionaryDao.insertOrUpdateEntry(
            originalText = normalizedOriginal,
            translatedText = normalizedTranslation,
            isUserDefined = true
        )
        
        userDictionarySize++
        Logger.d(TAG, "Updated user dictionary: $normalizedOriginal -> $normalizedTranslation")
    }
    
    override fun getAllTranslations(): Map<String, String> {
        return HashMap(memoryCache)
    }
    
    // ========================================================================
    // LIFECYCLE
    // ========================================================================
    
    override fun release() {
        closeTranslator()
        _modelState.value = ModelState.NotInitialized
        Logger.i(TAG, "Repository released")
    }
    
    private fun closeTranslator() {
        translator?.close()
        translator = null
    }
}
