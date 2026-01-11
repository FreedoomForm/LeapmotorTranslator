package com.leapmotor.translator.renderer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View

/**
 * Custom View for rendering translated Russian text on top of the eraser overlay.
 * 
 * Uses hardware-accelerated Canvas drawing for optimal performance
 * on Snapdragon 8155.
 */
class TextOverlay(context: Context) : View(context) {
    
    /**
     * Data class representing a single translated text item.
     */
    data class TranslatedText(
        val text: String,           // Russian translated text
        val bounds: RectF,          // Position on screen
        val originalText: String,   // Original Chinese text
        val fontSize: Float = 24f   // Font size in pixels
    )
    
    // Text items to render
    private val textItems = mutableListOf<TranslatedText>()
    private val itemsLock = Any()
    
    // Primary text paint (white text)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 24f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.LEFT
        isSubpixelText = true
        isLinearText = true
        hinting = Paint.HINTING_ON
    }
    
    // Shadow/stroke paint for contrast
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 24f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.LEFT
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isSubpixelText = true
    }
    
    // Debug paint for bounding box visualization (optional)
    private val debugPaint = Paint().apply {
        color = Color.argb(50, 255, 0, 0)
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    
    // Debug mode flag
    var debugMode: Boolean = false
    
    init {
        // Enable hardware acceleration
        setLayerType(LAYER_TYPE_HARDWARE, null)
        
        // Make view transparent and non-focusable
        setBackgroundColor(Color.TRANSPARENT)
        isFocusable = false
        isFocusableInTouchMode = false
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        synchronized(itemsLock) {
            for (item in textItems) {
                drawTranslatedText(canvas, item)
            }
        }
    }
    
    /**
     * Draw a single translated text item with shadow for contrast.
     */
    private fun drawTranslatedText(canvas: Canvas, item: TranslatedText) {
        // Update paint size
        textPaint.textSize = item.fontSize
        shadowPaint.textSize = item.fontSize
        
        // Calculate text position
        // Center vertically within bounds, align left
        val textHeight = textPaint.descent() - textPaint.ascent()
        val x = item.bounds.left + 2f  // Small padding
        val y = item.bounds.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
        
        // Measure text and handle overflow
        val availableWidth = item.bounds.width() - 4f
        val textWidth = textPaint.measureText(item.text)
        
        val displayText = if (textWidth > availableWidth && availableWidth > 30f) {
            // Truncate with ellipsis if too long
            truncateText(item.text, availableWidth)
        } else {
            item.text
        }
        
        // Draw shadow/stroke first (for contrast against any background)
        canvas.drawText(displayText, x, y, shadowPaint)
        
        // Draw main text
        canvas.drawText(displayText, x, y, textPaint)
        
        // Debug: draw bounding box
        if (debugMode) {
            canvas.drawRect(item.bounds, debugPaint)
        }
    }
    
    /**
     * Truncate text to fit within available width, adding ellipsis.
     */
    private fun truncateText(text: String, maxWidth: Float): String {
        val ellipsis = "…"
        val ellipsisWidth = textPaint.measureText(ellipsis)
        val targetWidth = maxWidth - ellipsisWidth
        
        if (targetWidth <= 0) return ellipsis
        
        var endIndex = text.length
        while (endIndex > 0 && textPaint.measureText(text, 0, endIndex) > targetWidth) {
            endIndex--
        }
        
        return if (endIndex < text.length) {
            text.substring(0, endIndex) + ellipsis
        } else {
            text
        }
    }
    
    /**
     * Update the list of translated text items to display.
     * 
     * @param items List of TranslatedText items
     */
    fun updateTextItems(items: List<TranslatedText>) {
        synchronized(itemsLock) {
            textItems.clear()
            textItems.addAll(items)
        }
        invalidate()
    }
    
    /**
     * Add a single translated text item.
     */
    fun addTextItem(item: TranslatedText) {
        synchronized(itemsLock) {
            textItems.add(item)
        }
        invalidate()
    }
    
    /**
     * Clear all text items.
     */
    fun clear() {
        synchronized(itemsLock) {
            textItems.clear()
        }
        invalidate()
    }
    
    /**
     * Set the default text size.
     */
    fun setDefaultTextSize(size: Float) {
        textPaint.textSize = size
        shadowPaint.textSize = size
    }
    
    /**
     * Set text color.
     */
    fun setTextColor(color: Int) {
        textPaint.color = color
    }
    
    /**
     * Set shadow/stroke color.
     */
    fun setShadowColor(color: Int) {
        shadowPaint.color = color
    }
    
    /**
     * Set shadow stroke width.
     */
    fun setShadowWidth(width: Float) {
        shadowPaint.strokeWidth = width
    }
    
    /**
     * Get current number of text items.
     */
    fun getItemCount(): Int {
        synchronized(itemsLock) {
            return textItems.size
        }
    }
    
    /**
     * Estimate optimal font size for a given bounds.
     */
    fun estimateFontSize(text: String, bounds: RectF, maxSize: Float = 36f, minSize: Float = 12f): Float {
        val testPaint = Paint(textPaint)
        var size = maxSize
        
        while (size >= minSize) {
            testPaint.textSize = size
            val textWidth = testPaint.measureText(text)
            val textHeight = testPaint.descent() - testPaint.ascent()
            
            if (textWidth <= bounds.width() - 4f && textHeight <= bounds.height()) {
                return size
            }
            size -= 2f
        }
        
        return minSize
    }
}
