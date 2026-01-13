package com.leapmotor.translator.domain.model

import android.graphics.RectF

/**
 * Domain model for a UI text element detected by accessibility service.
 * 
 * Immutable data class following DDD principles.
 * Contains only domain-relevant data, no Android-specific types.
 */
data class TextElement(
    /**
     * Unique identifier for this element.
     * Generated from viewId + text + dimensions for stable tracking.
     */
    val id: String,
    
    /**
     * Original Chinese text content.
     */
    val originalText: String,
    
    /**
     * Bounding box in screen coordinates.
     */
    val bounds: BoundingBox,
    
    /**
     * Depth in the view hierarchy (for z-ordering).
     */
    val depth: Int = 0
) {
    /**
     * Check if this element contains valid data.
     */
    val isValid: Boolean
        get() = originalText.isNotBlank() && bounds.isValid
    
    /**
     * Check if this element contains Chinese characters.
     */
    val hasChinese: Boolean
        get() = originalText.any { it.isChinese() }
        
    companion object {
        /**
         * Create a unique ID for tracking this element across frames.
         */
        fun createId(viewId: String?, text: String, width: Int, height: Int): String {
            return "${viewId ?: "no_id"}|$text|${width}x$height"
        }
    }
}

/**
 * Domain model for a translated text element ready for display.
 */
data class TranslatedElement(
    /**
     * Unique element identifier.
     */
    val id: String,
    
    /**
     * Original Chinese text.
     */
    val originalText: String,
    
    /**
     * Russian translated text.
     */
    val translatedText: String,
    
    /**
     * Display bounding box (may include Kalman prediction offset).
     */
    val displayBounds: BoundingBox,
    
    /**
     * Original bounds without prediction.
     */
    val originalBounds: BoundingBox,
    
    /**
     * Predicted Y position from Kalman filter.
     */
    val predictedY: Float,
    
    /**
     * Calculated font size for display.
     */
    val fontSize: Float,
    
    /**
     * Timestamp when this element was last seen.
     */
    val lastSeenTime: Long
) {
    /**
     * Check if this element has expired (not seen recently).
     */
    fun isExpired(currentTime: Long, thresholdMs: Long = 500L): Boolean {
        return currentTime - lastSeenTime > thresholdMs
    }
}

/**
 * Represents a rectangle with Float coordinates.
 * Platform-agnostic version of RectF.
 */
data class BoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = left + width / 2f
    val centerY: Float get() = top + height / 2f
    
    val isValid: Boolean
        get() = width > 0 && height > 0 && left >= 0 && top >= 0
    
    /**
     * Create a copy with offset Y position.
     */
    fun withY(newY: Float): BoundingBox = copy(
        top = newY,
        bottom = newY + height
    )
    
    /**
     * Add padding to all sides.
     */
    fun withPadding(padding: Float): BoundingBox = copy(
        left = left - padding,
        top = top - padding,
        right = right + padding,
        bottom = bottom + padding
    )
    
    /**
     * Convert to Android RectF.
     */
    fun toRectF(): RectF = RectF(left, top, right, bottom)
    
    companion object {
        val EMPTY = BoundingBox(0f, 0f, 0f, 0f)
        
        fun fromRectF(rect: RectF): BoundingBox = BoundingBox(
            left = rect.left,
            top = rect.top,
            right = rect.right,
            bottom = rect.bottom
        )
        
        fun fromRect(left: Int, top: Int, right: Int, bottom: Int): BoundingBox = BoundingBox(
            left = left.toFloat(),
            top = top.toFloat(),
            right = right.toFloat(),
            bottom = bottom.toFloat()
        )
    }
}

/**
 * Check if a character is Chinese (CJK Unified Ideographs).
 */
fun Char.isChinese(): Boolean {
    val code = this.code
    return code in 0x4E00..0x9FFF ||    // CJK Unified Ideographs
           code in 0x3400..0x4DBF ||    // CJK Extension A
           code in 0x3000..0x303F       // CJK Punctuation
}

/**
 * Check if a string contains any Chinese characters.
 */
fun String.containsChinese(): Boolean = any { it.isChinese() }
