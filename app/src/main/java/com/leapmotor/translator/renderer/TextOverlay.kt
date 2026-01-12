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
    
    // Status visual indicator
    enum class Status {
        INITIALIZING, // Yellow
        ACTIVE,       // Green
        PAUSED,       // Gray
        ERROR         // Red
    }
    
    private var currentStatus: Status = Status.INITIALIZING
    
    // Status dot paint
    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    
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
        
        drawStatus(canvas)
        
        // Debug: draw screen border and text backgrounds
        if (debugMode) {
            // Draw RED border around screen to verify overlay presence
            val borderPaint = Paint().apply {
                color = Color.RED
                style = Paint.Style.STROKE
                strokeWidth = 10f
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), borderPaint)
        }
    }
    
    /**
     * Draw a single translated text item with shadow for contrast.
     * Dynamically positions and sizes text to match Chinese original.
     */
    private fun drawTranslatedText(canvas: Canvas, item: TranslatedText) {
        // Debug mode: draw semi-transparent background to show text bounds
        if (debugMode) {
            val bgPaint = Paint().apply {
                color = Color.argb(100, 255, 0, 0)
                style = Paint.Style.FILL
            }
            canvas.drawRect(item.bounds, bgPaint)
        }

        // Set font size from pre-calculated value
        textPaint.textSize = item.fontSize
        shadowPaint.textSize = item.fontSize
        
        // Calculate text metrics for vertical centering
        val fontMetrics = textPaint.fontMetrics
        val textHeight = fontMetrics.descent - fontMetrics.ascent
        
        // X position: small padding from left edge
        val x = item.bounds.left + 2f
        
        // Y position: vertically center text within bounds
        // Canvas drawText uses baseline, so we calculate baseline from center
        val centerY = item.bounds.centerY()
        val y = centerY - (fontMetrics.descent + fontMetrics.ascent) / 2f
        
        // Available width for text (with small padding)
        val availableWidth = item.bounds.width() - 4f
        
        // Measure actual text width at current font size
        val textWidth = textPaint.measureText(item.text)
        
        // Prepare display text - truncate if needed
        val displayText = if (textWidth > availableWidth && availableWidth > 20f) {
            truncateText(item.text, availableWidth)
        } else {
            item.text
        }
        
        // Draw shadow/stroke first for contrast on any background
        canvas.drawText(displayText, x, y, shadowPaint)
        
        // Draw main white text on top
        canvas.drawText(displayText, x, y, textPaint)
        
        // Debug mode: draw bounding box outline and text info
        if (debugMode) {
            canvas.drawRect(item.bounds, debugPaint)
            
            // Draw size info
            val infoText = "${item.fontSize.toInt()}px"
            val infoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.YELLOW
                textSize = 10f
            }
            canvas.drawText(infoText, item.bounds.left, item.bounds.top - 2f, infoPaint)
        }
    }

    private fun drawStatus(canvas: Canvas) {
        val color = when (currentStatus) {
            Status.INITIALIZING -> Color.YELLOW
            Status.ACTIVE -> Color.GREEN
            Status.PAUSED -> Color.GRAY
            Status.ERROR -> Color.RED
        }
        
        statusPaint.color = color
        
        // Draw small dot in top-left corner
        // 10px radius, 20px margin
        canvas.drawCircle(30f, 30f, 10f, statusPaint)
        
        // Draw blinking stroke if error or initializing
        if (currentStatus == Status.ERROR || currentStatus == Status.INITIALIZING) {
             val time = System.currentTimeMillis()
             if (time % 1000 < 500) { // Blink every 0.5s
                 canvas.drawCircle(30f, 30f, 12f, shadowPaint)
             }
        }
    }
    
    fun setStatus(status: Status) {
        currentStatus = status
        invalidate()
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
    /**
     * Estimate optimal font size that fits text within given bounds.
     * Uses actual Paint measurement for accuracy.
     * 
     * @param text The text to fit
     * @param bounds The bounding rectangle to fit within
     * @param maxSize Maximum font size allowed
     * @param minSize Minimum font size allowed
     * @return Optimal font size in pixels
     */
    fun estimateFontSize(text: String, bounds: RectF, maxSize: Float = 100f, minSize: Float = 10f): Float {
        if (text.isEmpty() || bounds.width() <= 0 || bounds.height() <= 0) {
            return minSize
        }
        
        val availableWidth = bounds.width() - 4f  // Small padding
        val availableHeight = bounds.height()
        
        // Start with height-based estimate (80% of box height)
        var fontSize = availableHeight * 0.8f
        
        // Create a temporary paint to measure
        val measurePaint = Paint(textPaint)
        
        // Binary search for optimal size that fits width (limit iterations)
        var low = minSize
        var high = minOf(fontSize, maxSize)
        
        for (i in 0 until 8) {  // Max 8 iterations for quick convergence
            val mid = (low + high) / 2f
            measurePaint.textSize = mid
            val measuredWidth = measurePaint.measureText(text)
            
            if (measuredWidth <= availableWidth) {
                low = mid
            } else {
                high = mid
            }
        }
        
        // Use the lower bound (which fits)
        return low.coerceIn(minSize, maxSize)
    }
}
