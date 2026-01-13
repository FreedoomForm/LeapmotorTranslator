package com.leapmotor.translator.domain.usecase

import com.leapmotor.translator.core.Result
import com.leapmotor.translator.domain.model.BoundingBox
import com.leapmotor.translator.domain.model.TextElement
import com.leapmotor.translator.domain.model.TranslatedElement
import com.leapmotor.translator.domain.repository.TranslationRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Use case for translating text elements.
 * 
 * Encapsulates the business logic for:
 * - Processing text elements from accessibility tree
 * - Translating Chinese text to Russian
 * - Tracking elements with Kalman filtering
 * - Calculating display properties
 * 
 * Following Clean Architecture's use case pattern.
 */
class TranslateElementsUseCase(
    private val translationRepository: TranslationRepository
) {
    
    /**
     * Process and translate a list of text elements.
     * 
     * @param elements Raw text elements from accessibility tree
     * @param existingElements Existing tracked elements (for Kalman updates)
     * @param currentTime Current timestamp
     * @param getKalmanPrediction Function to get Kalman-filtered Y position
     * @return Result containing list of translated elements
     */
    suspend operator fun invoke(
        elements: List<TextElement>,
        existingElements: Map<String, TranslatedElement>,
        currentTime: Long,
        getKalmanPrediction: (String, Float, Long) -> Float = { _, y, _ -> y }
    ): Result<List<TranslatedElement>> = coroutineScope {
        if (elements.isEmpty()) {
            return@coroutineScope Result.success(emptyList())
        }
        
        // Process elements in parallel for better performance
        val translatedElements = elements.map { element ->
            async {
                processElement(
                    element = element,
                    existing = existingElements[element.id],
                    currentTime = currentTime,
                    getKalmanPrediction = getKalmanPrediction
                )
            }
        }.awaitAll()
        
        // Filter out null results and return
        Result.success(translatedElements.filterNotNull())
    }
    
    /**
     * Process a single text element.
     */
    private suspend fun processElement(
        element: TextElement,
        existing: TranslatedElement?,
        currentTime: Long,
        getKalmanPrediction: (String, Float, Long) -> Float
    ): TranslatedElement? {
        // Skip elements without Chinese text
        if (!element.hasChinese) return null
        
        val translation: String
        val predictedY: Float
        
        if (existing != null && existing.originalText == element.originalText) {
            // Existing element - reuse translation, update prediction
            translation = existing.translatedText
            predictedY = getKalmanPrediction(element.id, element.bounds.top, currentTime)
        } else {
            // New element - translate
            val result = translationRepository.translate(element.originalText)
            translation = result.getOrDefault(element.originalText)
            predictedY = element.bounds.top
        }
        
        // Calculate font size
        val fontSize = calculateFontSize(
            text = translation,
            boxWidth = element.bounds.width,
            boxHeight = element.bounds.height
        )
        
        // Create display bounds with predicted Y
        val displayBounds = element.bounds.withY(predictedY)
        
        return TranslatedElement(
            id = element.id,
            originalText = element.originalText,
            translatedText = translation,
            displayBounds = displayBounds,
            originalBounds = element.bounds,
            predictedY = predictedY,
            fontSize = fontSize,
            lastSeenTime = currentTime
        )
    }
    
    /**
     * Calculate optimal font size to fit text in bounds.
     * 
     * Algorithm:
     * 1. Start with height-based estimate (70% of box height)
     * 2. Calculate required width for text
     * 3. If overflow, scale down proportionally
     * 4. Clamp to reasonable range
     */
    private fun calculateFontSize(
        text: String,
        boxWidth: Float,
        boxHeight: Float
    ): Float {
        // Height-based initial estimate
        val heightBasedSize = boxHeight * 0.7f
        
        // Russian characters are wider than Chinese on average
        // Estimate: ~0.55 * fontSize per character
        val avgCharWidth = 0.55f
        val requiredWidth = text.length * heightBasedSize * avgCharWidth
        
        // Scale down if needed to fit width
        val widthBasedSize = if (requiredWidth > boxWidth && boxWidth > 0) {
            heightBasedSize * (boxWidth / requiredWidth)
        } else {
            heightBasedSize
        }
        
        // Clamp to reasonable range
        return widthBasedSize.coerceIn(10f, 48f)
    }
}

/**
 * Use case for initializing the translation engine.
 */
class InitializeTranslationUseCase(
    private val translationRepository: TranslationRepository,
    private val commonTranslations: Map<String, String>
) {
    
    suspend operator fun invoke(): Result<Unit> {
        // Preload common translations first
        translationRepository.preloadTranslations(commonTranslations)
        
        // Initialize the ML model
        return translationRepository.initialize()
    }
}

/**
 * Use case for managing user dictionary.
 */
class ManageUserDictionaryUseCase(
    private val translationRepository: TranslationRepository
) {
    
    /**
     * Add or update a translation.
     */
    suspend fun updateTranslation(original: String, translation: String) {
        translationRepository.updateTranslation(original, translation)
    }
    
    /**
     * Get all translations.
     */
    fun getAllTranslations(): Map<String, String> {
        return translationRepository.getAllTranslations()
    }
    
    /**
     * Search translations by query.
     */
    fun searchTranslations(query: String): Map<String, String> {
        val allTranslations = translationRepository.getAllTranslations()
        if (query.isBlank()) return allTranslations
        
        val lowerQuery = query.lowercase()
        return allTranslations.filter { (original, translated) ->
            original.lowercase().contains(lowerQuery) ||
            translated.lowercase().contains(lowerQuery)
        }
    }
}
