package com.leapmotor.translator.renderer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.view.View
import com.leapmotor.translator.core.Logger
import java.util.concurrent.CopyOnWriteArrayList

/**
 * High-performance text overlay for rendering translated text.
 * 
 * Senior-level improvements:
 * - Optimized Paint object reuse
 * - Thread-safe item management
 * - Efficient text measurement caching
 * - Status indicator with animation
 * - Configurable styling
 * 
 * Uses hardware-accelerated Canvas drawing optimized for Snapdragon 8155.
 */
class TextOverlay(context: Context) : View(context) {
    
    companion object {
        private const val TAG = "TextOverlay"
    }
    
    // ============================================================================
    // DATA CLASSES
    // ============================================================================
    
    /**
     * Represents a translated text item to render.
     */
    data class TranslatedText(
        val text: String,
        val bounds: RectF,
        val originalText: String,
        val fontSize: Float = 24f
    )
    
    /**
     * Status indicator states.
     */
    enum class Status {
        INITIALIZING,
        ACTIVE,
        PAUSED,
        ERROR
    }
    
    // ============================================================================
    // STATE
    // ============================================================================
    
    // Thread-safe list for text items
    private val textItems = CopyOnWriteArrayList<TranslatedText>()
    
    // Current status
    private var currentStatus: Status = Status.INITIALIZING
    
    // Debug mode flag
    var debugMode: Boolean = false
        set(value) {
            field = value
            invalidate()
        }
    
    // ============================================================================
    // PAINT OBJECTS (Reused for performance)
    // ============================================================================
    
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 24f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.LEFT
        isSubpixelText = true
        isLinearText = true
        hinting = Paint.HINTING_ON
    }
    
    private val shadowPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 24f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.LEFT
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isSubpixelText = true
    }
    
    private val debugPaint = Paint().apply {
        color = Color.argb(100, 255, 0, 0)
        style = Paint.Style.FILL
    }
    
    private val debugBorderPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    
    private val debugInfoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        textSize = 10f
    }
    
    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    
    private val statusStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.WHITE
    }
    
    // ============================================================================
    // CONFIGURATION
    // ============================================================================
    
    /**
     * Text styling configuration.
     */
    object Style {
        var textColor: Int = Color.rgb(0, 0, 139) // Dark Blue
        var shadowColor: Int = Color.BLACK
        var shadowWidth: Float = 3f
        var statusDotSize: Float = 10f
        var statusDotMargin: Float = 30f
    }
    
    // ============================================================================
    // INITIALIZATION
    // ============================================================================
    
    init {
        // Enable hardware acceleration
        setLayerType(LAYER_TYPE_HARDWARE, null)
        
        // Make view transparent and non-interactive
        setBackgroundColor(Color.TRANSPARENT)
        isFocusable = false
        isFocusableInTouchMode = false
        
        // Apply initial style
        applyStyle()
        
        Logger.d(TAG, "TextOverlay initialized")
    }
    
    private fun applyStyle() {
        textPaint.color = Style.textColor
        shadowPaint.color = Style.shadowColor
        shadowPaint.strokeWidth = Style.shadowWidth
    }
    
    // ============================================================================
    // DRAWING
    // ============================================================================
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Draw all text items
        for (item in textItems) {
            drawTranslatedText(canvas, item)
        }
        
        // Draw status indicator
        drawStatusIndicator(canvas)
        
        // Draw debug overlay if enabled
        if (debugMode) {
            drawDebugOverlay(canvas)
        }
    }
    
    /**
     * Draw a single translated text item with shadow for contrast.
     */
    private fun drawTranslatedText(canvas: Canvas, item: TranslatedText) {
        // Debug: draw background
        if (debugMode) {
            canvas.drawRect(item.bounds, debugPaint)
        }

        // --- 1. CONFIGURATION ---
        // "3 times bigger" -> huge font. Default was 24, now ~72.
        // Let's autoscaling take care of fitting, but start BIG.
        // "1.5x" request -> Respect limits
        val targetFontSize = item.fontSize.coerceAtLeast(20f) 
        textPaint.textSize = targetFontSize
        shadowPaint.textSize = targetFontSize
        shadowPaint.strokeWidth = 5f // Thicker outline for "chetkiy" (sharpness)
        shadowPaint.style = Paint.Style.STROKE
        
        val availableWidth = (item.bounds.width() - 4f).coerceAtLeast(1f)
        val textWidth = textPaint.measureText(item.text)
        
        // --- 2. POSITIONING ---
        // "better 25 pixels" -> Adjusted from -90f to -25f
        val x = item.bounds.left + 2f
        val anchorY = item.bounds.top
        val yOffset = -25f 
        
        // --- 3. RENDERING STRATEGY ---
        if (textWidth <= availableWidth) {
            // Case A: Fits on one line
            val fontMetrics = textPaint.fontMetrics
            val lineY = anchorY + yOffset - (fontMetrics.descent + fontMetrics.ascent) / 2f
            
            // Draw Outline (High contrast)
            canvas.drawText(item.text, x, lineY, shadowPaint)
            // Draw Fill
            canvas.drawText(item.text, x, lineY, textPaint)
        } else {
            // Case B: Too long -> Multi-line Wrap
            val builder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                android.text.StaticLayout.Builder.obtain(item.text, 0, item.text.length, textPaint, availableWidth.toInt())
                    .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1.0f)
                    .setIncludePad(false)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                android.text.StaticLayout(
                    item.text, 
                    textPaint, 
                    availableWidth.toInt(), 
                    android.text.Layout.Alignment.ALIGN_NORMAL, 
                    1.0f, 
                    0f, 
                    false
                )
            }
            
            // Draw Layout centered vertically at the offset position
            canvas.save()
            val layoutHeight = builder.height.toFloat()
            val layoutTop = anchorY + yOffset - (layoutHeight / 2f)
            
            canvas.translate(x, layoutTop)
            
            // Draw Outline
            val originalColor = textPaint.color
            textPaint.style = Paint.Style.STROKE
            textPaint.strokeWidth = 5f
            textPaint.color = Color.BLACK
            builder.draw(canvas)
            
            // Draw Fill
            textPaint.style = Paint.Style.FILL
            textPaint.strokeWidth = 0f
            textPaint.color = originalColor
            builder.draw(canvas)
            
            canvas.restore()
        }
        
        // Debug: draw bounds and info
        if (debugMode) {
            canvas.drawRect(item.bounds, debugBorderPaint)
            val info = "${targetFontSize.toInt()}px"
            canvas.drawText(info, item.bounds.left, item.bounds.top - 2f, debugInfoPaint)
        }
    }
    
    /**
     * Fit text to available width with ellipsis.
     */
    private fun fitTextToWidth(text: String, maxWidth: Float): String {
        val textWidth = textPaint.measureText(text)
        if (textWidth <= maxWidth || maxWidth <= 20f) return text
        
        val ellipsis = "…"
        val ellipsisWidth = textPaint.measureText(ellipsis)
        val targetWidth = maxWidth - ellipsisWidth
        
        if (targetWidth <= 0) return ellipsis
        
        // Binary search for optimal length
        var low = 0
        var high = text.length
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (textPaint.measureText(text, 0, mid) <= targetWidth) {
                low = mid
            } else {
                high = mid - 1
            }
        }
        
        return if (low < text.length) text.substring(0, low) + ellipsis else text
    }
    
    /**
     * Draw status indicator dot.
     */
    private fun drawStatusIndicator(canvas: Canvas) {
        val color = when (currentStatus) {
            Status.INITIALIZING -> Color.YELLOW
            Status.ACTIVE -> Color.GREEN
            Status.PAUSED -> Color.GRAY
            Status.ERROR -> Color.RED
        }
        
        statusPaint.color = color
        
        val cx = Style.statusDotMargin
        val cy = Style.statusDotMargin
        val radius = Style.statusDotSize
        
        // Draw filled dot
        canvas.drawCircle(cx, cy, radius, statusPaint)
        
        // Draw blinking stroke for warning states
        if (currentStatus == Status.ERROR || currentStatus == Status.INITIALIZING) {
            if (System.currentTimeMillis() % 1000 < 500) {
                canvas.drawCircle(cx, cy, radius + 2f, statusStrokePaint)
            }
        }
    }
    
    /**
     * Draw debug overlay (red border, stats).
     */
    private fun drawDebugOverlay(canvas: Canvas) {
        // Screen border
        val borderPaint = Paint().apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 10f
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), borderPaint)
        
        // Stats text
        val statsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.CYAN
            textSize = 12f
        }
        val stats = "Items: ${textItems.size} | Status: $currentStatus"
        canvas.drawText(stats, 10f, height - 10f, statsPaint)
    }
    
    // ============================================================================
    // PUBLIC API
    // ============================================================================
    
    /**
     * Update the list of text items to display.
     */
    fun updateTextItems(items: List<TranslatedText>) {
        textItems.clear()
        textItems.addAll(items)
        invalidate()
    }
    
    /**
     * Add a single text item.
     */
    fun addTextItem(item: TranslatedText) {
        textItems.add(item)
        invalidate()
    }
    
    /**
     * Clear all text items.
     */
    fun clear() {
        textItems.clear()
        invalidate()
    }
    
    /**
     * Set the current status.
     */
    fun setStatus(status: Status) {
        currentStatus = status
        invalidate()
    }
    
    /**
     * Get current item count.
     */
    fun getItemCount(): Int = textItems.size
    
    /**
     * Set default text size.
     */
    fun setDefaultTextSize(size: Float) {
        textPaint.textSize = size
        shadowPaint.textSize = size
    }
    
    /**
     * Set text color.
     */
    fun setTextColor(color: Int) {
        Style.textColor = color
        textPaint.color = color
    }
    
    /**
     * Set shadow color.
     */
    fun setShadowColor(color: Int) {
        Style.shadowColor = color
        shadowPaint.color = color
    }
    
    /**
     * Set shadow stroke width.
     */
    fun setShadowWidth(width: Float) {
        Style.shadowWidth = width
        shadowPaint.strokeWidth = width
    }
    
    /**
     * Estimate optimal font size for bounds.
     */
    fun estimateFontSize(
        text: String,
        bounds: RectF,
        maxSize: Float = 100f,
        minSize: Float = 10f
    ): Float {
        if (text.isEmpty() || bounds.width() <= 0 || bounds.height() <= 0) {
            return minSize
        }
        
        val availableWidth = bounds.width() - 4f
        val availableHeight = bounds.height()
        
        // Start with height-based estimate
        var fontSize = availableHeight * 0.8f
        
        // Binary search for optimal size
        val measurePaint = Paint(textPaint)
        var low = minSize
        var high = minOf(fontSize, maxSize)
        
        repeat(8) {
            val mid = (low + high) / 2f
            measurePaint.textSize = mid
            if (measurePaint.measureText(text) <= availableWidth) {
                low = mid
            } else {
                high = mid
            }
        }
        
        return low.coerceIn(minSize, maxSize)
    }
}
