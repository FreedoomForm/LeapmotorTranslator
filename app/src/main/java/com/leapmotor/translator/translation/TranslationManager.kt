package com.leapmotor.translator.translation

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Manager for Chinese to Russian translation using Google ML Kit.
 * 
 * Features:
 * - On-device translation (no network required after model download)
 * - Thread-safe caching to prevent re-translation
 * - Batch translation support
 * - Automatic model download management
 */
class TranslationManager {
    
    // ML Kit Translator instance
    private var translator: Translator? = null
    
    // Translation cache: Chinese text -> Russian translation
    private val translationCache = ConcurrentHashMap<String, String>()
    
    // Model download state
    private var isModelReady = false
    private var isDownloading = false
    
    // Statistics
    private var cacheHits = 0L
    private var cacheMisses = 0L
    
    // Listeners
    private var onModelReadyListener: (() -> Unit)? = null
    private var onErrorListener: ((Exception) -> Unit)? = null


    
    companion object {
        private const val TAG = "TranslationManager"
        private const val MAX_CACHE_SIZE = 5000
        
        // Singleton instance
        @Volatile
        private var instance: TranslationManager? = null
        
        fun getInstance(): TranslationManager {
            return instance ?: synchronized(this) {
                instance ?: TranslationManager().also { instance = it }
            }
        }
    }
    
    /**
     * Initialize the translator and download the model if needed.
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (isModelReady) {
            downloadState = DownloadState.DOWNLOADED
            return@withContext true
        }
        if (isDownloading) {
            downloadState = DownloadState.DOWNLOADING
            return@withContext false
        }
        
        isDownloading = true
        downloadState = DownloadState.DOWNLOADING
        
        try {
            // Configure translator: Chinese -> Russian
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.CHINESE)
                .setTargetLanguage(TranslateLanguage.RUSSIAN)
                .build()
            
            translator = Translation.getClient(options)
            
            // Download model conditions (WiFi only to save mobile data)
            val conditions = DownloadConditions.Builder()
                .requireWifi()
                .build()
            
            // Download model
            var success = downloadModel(conditions)
            
            if (!success) {
                // Try without WiFi requirement
                val anyConditions = DownloadConditions.Builder().build()
                success = downloadModel(anyConditions)
            }
            
            if (success) {
                isModelReady = true
                isDownloading = false
                downloadState = DownloadState.DOWNLOADED
                onModelReadyListener?.invoke()
                return@withContext true
            } else {
                isDownloading = false
                downloadState = DownloadState.ERROR
                return@withContext false
            }
            
        } catch (e: Exception) {
            isDownloading = false
            downloadState = DownloadState.ERROR
            onErrorListener?.invoke(e)
            return@withContext false
        }
    }
    
    /**
     * Download the translation model.
     */
    private suspend fun downloadModel(conditions: DownloadConditions): Boolean {
        return suspendCancellableCoroutine { continuation ->
            translator?.downloadModelIfNeeded(conditions)
                ?.addOnSuccessListener {
                    if (continuation.isActive) {
                        continuation.resume(true)
                    }
                }
                ?.addOnFailureListener { e ->
                    if (continuation.isActive) {
                        continuation.resume(false)
                    }
                }
                ?: run {
                    if (continuation.isActive) {
                        continuation.resume(false)
                    }
                }
        }
    }
    
    /**
     * Translate a single text from Chinese to Russian.
     * Uses cache for previously translated strings.
     * 
     * @param text Chinese text to translate
     * @return Russian translation, or original text if translation fails
     */
    suspend fun translate(text: String): String = withContext(Dispatchers.IO) {
        // Skip empty or very short text
        if (text.isBlank() || text.length < 1) {
            return@withContext text
        }
        
        // Normalize text (remove extra whitespace)
        val normalizedText = text.trim()
        
        // Check cache first
        translationCache[normalizedText]?.let {
            cacheHits++
            return@withContext it
        }
        
        // Not in cache - translate
        cacheMisses++
        
        if (!isModelReady || translator == null) {
            // Model not ready, return original
            return@withContext text
        }
        
        try {
            val translation = performTranslation(normalizedText)
            
            // Store in cache (with size limit)
            if (translationCache.size < MAX_CACHE_SIZE) {
                translationCache[normalizedText] = translation
            } else {
                // Simple eviction: clear half the cache
                val keysToRemove = translationCache.keys.take(MAX_CACHE_SIZE / 2)
                keysToRemove.forEach { translationCache.remove(it) }
                translationCache[normalizedText] = translation
            }
            
            return@withContext translation
            
        } catch (e: Exception) {
            onErrorListener?.invoke(e)
            return@withContext text
        }
    }
    
    /**
     * Perform the actual translation using ML Kit.
     */
    private suspend fun performTranslation(text: String): String {
        return suspendCancellableCoroutine { continuation ->
            translator?.translate(text)
                ?.addOnSuccessListener { translatedText ->
                    if (continuation.isActive) {
                        continuation.resume(translatedText)
                    }
                }
                ?.addOnFailureListener { e ->
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                }
                ?: run {
                    if (continuation.isActive) {
                        continuation.resumeWithException(IllegalStateException("Translator not initialized"))
                    }
                }
        }
    }
    
    /**
     * Translate multiple texts in batch.
     * 
     * @param texts List of Chinese texts to translate
     * @return Map of original text to translation
     */
    suspend fun translateBatch(texts: List<String>): Map<String, String> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, String>()
        
        for (text in texts) {
            val translation = translate(text)
            results[text] = translation
        }
        
        return@withContext results
    }
    
    /**
     * Check if a translation is already cached.
     */
    fun isCached(text: String): Boolean {
        return translationCache.containsKey(text.trim())
    }
    
    /**
     * Get cached translation if available.
     */
    fun getCached(text: String): String? {
        return translationCache[text.trim()]
    }
    
    /**
     * Pre-populate cache with known translations.
     * Useful for common UI strings.
     */
    fun preloadTranslations(translations: Map<String, String>) {
        translationCache.putAll(translations)
    }
    
    /**
     * Get cache statistics.
     */
    fun getCacheStats(): CacheStats {
        return CacheStats(
            size = translationCache.size,
            hits = cacheHits,
            misses = cacheMisses,
            hitRate = if (cacheHits + cacheMisses > 0) {
                cacheHits.toFloat() / (cacheHits + cacheMisses)
            } else 0f
        )
    }
    
    /**
     * Clear the translation cache.
     */
    fun clearCache() {
        translationCache.clear()
        cacheHits = 0L
        cacheMisses = 0L
    }
    
    /**
     * Set callback for when model is ready.
     */
    fun setOnModelReadyListener(listener: () -> Unit) {
        onModelReadyListener = listener
        if (isModelReady) {
            listener.invoke()
        }
    }
    
    fun setOnDownloadStateChangeListener(listener: (DownloadState) -> Unit) {
        onDownloadStateChangeListener = listener
        // Immediately invoke with current state
        listener.invoke(downloadState)
    }

    fun setOnDownloadStateChangeListener(listener: (DownloadState) -> Unit) {
        onDownloadStateChangeListener = listener
        // Immediately invoke with current state
        listener.invoke(downloadState)
    }

    /**
     * Set callback for errors.
     */
    fun setOnErrorListener(listener: (Exception) -> Unit) {
        onErrorListener = listener
    }
    
    /**
     * Check if model is downloaded and ready.
     */
    fun isReady(): Boolean = isModelReady
    
    /**
     * Release resources.
     */
    fun release() {
        translator?.close()
        translator = null
        isModelReady = false
        isDownloading = false
    }
    
    /**
     * Cache statistics data class.
     */
    data class CacheStats(
        val size: Int,
        val hits: Long,
        val misses: Long,
        val hitRate: Float
    )
}

/**
 * Common UI translations for Leapmotor C11.
 * Pre-loaded to cache for instant display.
 */
object CommonTranslations {
    
    val LEAPMOTOR_UI = mapOf(
        // Navigation
        "导航" to "Навигация",
        "地图" to "Карта",
        "目的地" to "Пункт назначения",
        "路线" to "Маршрут",
        "到达时间" to "Время прибытия",
        "剩余距离" to "Оставшееся расстояние",
        
        // Climate control
        "空调" to "Климат",
        "温度" to "Температура",
        "风量" to "Скорость вентилятора",
        "自动" to "Авто",
        "关闭" to "Выкл",
        "打开" to "Вкл",
        "制冷" to "Охлаждение",
        "制热" to "Обогрев",
        "座椅加热" to "Подогрев сидений",
        "座椅通风" to "Вентиляция сидений",
        
        // Media
        "音乐" to "Музыка",
        "收音机" to "Радио",
        "蓝牙" to "Bluetooth",
        "音量" to "Громкость",
        "播放" to "Воспроизвести",
        "暂停" to "Пауза",
        "下一首" to "Следующий",
        "上一首" to "Предыдущий",
        
        // Vehicle
        "车辆" to "Автомобиль",
        "设置" to "Настройки",
        "电池" to "Батарея",
        "充电" to "Зарядка",
        "续航" to "Запас хода",
        "公里" to "км",
        "驾驶模式" to "Режим вождения",
        "运动" to "Спорт",
        "经济" to "Эко",
        "舒适" to "Комфорт",
        
        // Parking / Camera
        "倒车" to "Задний ход",
        "雷达" to "Радар",
        "摄像头" to "Камера",
        "全景" to "Панорама",
        "前方" to "Спереди",
        "后方" to "Сзади",
        "左侧" to "Слева",
        "右侧" to "Справа",
        
        // Alerts
        "警告" to "Предупреждение",
        "错误" to "Ошибка",
        "确认" to "Подтвердить",
        "取消" to "Отмена",
        "返回" to "Назад",
        "主页" to "Главная",
        
        // Time
        "小时" to "ч",
        "分钟" to "мин",
        "秒" to "сек"
    )
}
