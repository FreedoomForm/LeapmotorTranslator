package com.leapmotor.translator.renderer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.view.View
import com.leapmotor.translator.core.Logger
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.sin
import kotlin.random.Random

/**
 * Premium Text Overlay for rendering translated Russian text.
 * 
 * ENHANCED FEATURES:
 * - Multi-layer glow/shadow system for depth
 * - Gradient text fills for premium appearance
 * - Improved typography with custom font support
 * - Soft ambient shadows for readability
 * - Animated pulse effects on status indicator
 * - Glass-morphism style visual effects
 * 
 * Optimized for Snapdragon 8155 with hardware-accelerated Canvas.
 */
class TextOverlay(context: Context) : View(context) {
    
    companion object {
        private const val TAG = "TextOverlay"
        
        // Animation timing
        private const val GLOW_PULSE_SPEED = 0.003f
        private const val STATUS_BLINK_INTERVAL = 500L
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
    
    // Thread-safe list for eraser boxes (Canvas-based eraser)
    private val eraserBoxes = CopyOnWriteArrayList<RectF>()
    
    // Eraser background mode
    @Volatile
    var isLightBackground: Boolean = false
        set(value) {
            field = value
            invalidate()
        }
    
    // Current status
    private var currentStatus: Status = Status.INITIALIZING
    
    // Animation state
    private var animationTime = 0L
    private var lastDrawTime = System.currentTimeMillis()
    
    // Debug mode flag
    var debugMode: Boolean = false
        set(value) {
            field = value
            invalidate()
        }
    
    // ============================================================================
    // PREMIUM CONFIGURATION
    // ============================================================================
    
    /**
     * Premium text styling configuration.
     */
    object Style {
        // Main text colors - Rich gradient
        var textColorPrimary: Int = Color.rgb(25, 35, 120)     // Deep indigo
        var textColorSecondary: Int = Color.rgb(50, 70, 180)   // Bright blue
        
        // Glow/Shadow colors
        var glowColorOuter: Int = Color.argb(60, 100, 150, 255)  // Soft blue glow
        var glowColorInner: Int = Color.argb(120, 255, 255, 255)  // White inner glow
        var shadowColor: Int = Color.argb(180, 0, 0, 0)           // Strong shadow
        var ambientShadowColor: Int = Color.argb(40, 0, 0, 0)     // Soft ambient
        
        // Effect parameters
        var outerGlowRadius: Float = 12f
        var innerGlowRadius: Float = 4f
        var shadowOffsetX: Float = 2f
        var shadowOffsetY: Float = 3f
        var shadowRadius: Float = 6f
        var strokeWidth: Float = 4f
        
        // Status indicator
        var statusDotSize: Float = 12f
        var statusDotMargin: Float = 35f
        var statusGlowRadius: Float = 8f
        
        // Typography
        var fontScaleX: Float = 1.12f   // Slightly wider for Russian
        var letterSpacing: Float = 0.02f
        var lineHeightMultiplier: Float = 1.15f
        
        // Legacy compatibility
        var textColor: Int
            get() = textColorPrimary
            set(value) { textColorPrimary = value }
        var shadowWidth: Float = strokeWidth
    }
    
    // ============================================================================
    // PAINT OBJECTS (Premium Multi-Layer System)
    // ============================================================================
    
    // Layer 1: Outer glow (furthest back)
    private val outerGlowPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Style.glowColorOuter
        textSize = 24f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.LEFT
        maskFilter = BlurMaskFilter(Style.outerGlowRadius, BlurMaskFilter.Blur.NORMAL)
    }
    
    // Layer 2: Drop shadow
    private val shadowPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Style.shadowColor
        textSize = 24f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.LEFT
        maskFilter = BlurMaskFilter(Style.shadowRadius, BlurMaskFilter.Blur.NORMAL)
    }
    
    // Layer 3: Stroke outline
    private val strokePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 24f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.LEFT
        style = Paint.Style.STROKE
        strokeWidth = Style.strokeWidth
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        isSubpixelText = true
    }
    
    // Layer 4: Main text fill with gradient
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Style.textColorPrimary
        textSize = 24f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.LEFT
        isSubpixelText = true
        isLinearText = true
        hinting = Paint.HINTING_ON
        letterSpacing = Style.letterSpacing
    }
    
    // Layer 5: Inner highlight (top layer)
    private val innerGlowPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Style.glowColorInner
        textSize = 24f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.LEFT
        maskFilter = BlurMaskFilter(Style.innerGlowRadius, BlurMaskFilter.Blur.INNER)
    }
    
    // Debug paints
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
    
    // Status indicator paints
    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    
    private val statusGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    
    private val statusStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.WHITE
    }
    
    // ============================================================================
    // ERASER PAINTS (Canvas-based eraser - same approach as text rendering)
    // ============================================================================
    
    // Main eraser fill paint
    private val eraserFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    
    // Eraser outer glow/shadow
    private val eraserGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
    }
    
    // Eraser border paint
    private val eraserBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    
    // Noise paint for grain effect
    private val noisePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        alpha = 40 // ~15% opacity for noise effect
    }
    
    // ============================================================================
    // INITIALIZATION
    // ============================================================================
    
    init {
        // Enable hardware acceleration for smooth rendering
        setLayerType(LAYER_TYPE_HARDWARE, null)
        
        // Make view transparent and non-interactive
        setBackgroundColor(Color.TRANSPARENT)
        isFocusable = false
        isFocusableInTouchMode = false
        
        // Generate noise texture
        createNoiseTexture()
        
        // Try to load premium font
        loadPremiumFont()
        
        // Apply initial style
        applyStyle()
        
        Logger.d(TAG, "Premium TextOverlay initialized with multi-layer effects")
    }
    
    private fun createNoiseTexture() {
        try {
            val size = 64
            val noiseBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            
            for (x in 0 until size) {
                for (y in 0 until size) {
                    val alpha = Random.nextInt(255)
                    // Black noise
                    noiseBitmap.setPixel(x, y, Color.argb(alpha, 0, 0, 0))
                }
            }
            
            val shader = BitmapShader(noiseBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            noisePaint.shader = shader
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to create noise texture", e)
        }
    }

    private fun loadPremiumFont() {
        try {
            // Try to load Roboto Medium for better Russian typography
            val typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            textPaint.typeface = typeface
            strokePaint.typeface = typeface
            shadowPaint.typeface = typeface
            outerGlowPaint.typeface = typeface
            innerGlowPaint.typeface = typeface
        } catch (e: Exception) {
            Logger.d(TAG, "Using default font: ${e.message}")
        }
    }
    
    private fun applyStyle() {
        // Update all paints with current style
        textPaint.color = Style.textColorPrimary
        textPaint.textScaleX = Style.fontScaleX
        textPaint.letterSpacing = Style.letterSpacing
        
        strokePaint.strokeWidth = Style.strokeWidth
        strokePaint.textScaleX = Style.fontScaleX
        
        shadowPaint.color = Style.shadowColor
        shadowPaint.textScaleX = Style.fontScaleX
        
        outerGlowPaint.color = Style.glowColorOuter
        outerGlowPaint.textScaleX = Style.fontScaleX
        
        innerGlowPaint.color = Style.glowColorInner
        innerGlowPaint.textScaleX = Style.fontScaleX
    }
    
    // ============================================================================
    // PREMIUM DRAWING
    // ============================================================================
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Update animation time
        val currentTime = System.currentTimeMillis()
        animationTime += currentTime - lastDrawTime
        lastDrawTime = currentTime
        
        // Draw eraser boxes FIRST (bottom layer - covers Chinese text)
        for (box in eraserBoxes) {
            drawEraserBox(canvas, box)
        }
        
        // Draw all text items with premium effects (TOP layer - Russian text)
        for (item in textItems) {
            drawPremiumText(canvas, item)
        }
        
        // Draw premium status indicator
        drawPremiumStatusIndicator(canvas)
        
        // Draw debug overlay if enabled
        if (debugMode) {
            drawDebugOverlay(canvas)
        }
        
        // Request redraw for animations
        if (currentStatus == Status.ACTIVE || currentStatus == Status.INITIALIZING) {
            postInvalidateDelayed(16) // ~60fps for smooth animation
        }
    }
    
    /**
     * Draw an eraser box using Canvas (same approach as text rendering).
     * This covers the original Chinese text before Russian translation is drawn.
     */
    private fun drawEraserBox(canvas: Canvas, box: RectF) {
        // Apply Y offset: -110 pixels (moved up by 20px from -90)
        val yOffset = -110f
        val offsetBox = RectF(
            box.left,
            box.top + yOffset,
            box.right,
            box.bottom + yOffset
        )
        
        // --- APPLE GLASS STYLE ---
        // Pill shape rounded corners
        val startRadius = 12f
        val borderRadius = 12f
        
        if (isLightBackground) {
            // Light Glass: High opacity white with bluish tint
            eraserFillPaint.color = Color.argb(220, 245, 248, 255)
            // Border: Subtle white/blue outline
            eraserBorderPaint.color = Color.argb(100, 200, 220, 255)
            eraserBorderPaint.strokeWidth = 2f
            noisePaint.alpha = 20
        } else {
            // Dark Glass: Deep dark gray/blue
            eraserFillPaint.color = Color.argb(230, 30, 35, 45)
            // Border: Subtle white outline for glass edge effect
            eraserBorderPaint.color = Color.argb(60, 255, 255, 255)
            eraserBorderPaint.strokeWidth = 1.5f
            noisePaint.alpha = 30
        }
        
        // Expand slightly for glow effect/glass edge
        val glowExpand = 2f
        val glowBox = RectF(
            offsetBox.left - glowExpand,
            offsetBox.top - glowExpand,
            offsetBox.right + glowExpand,
            offsetBox.bottom + glowExpand
        )
        
        // Layer 1: Subtle outer shadow (Simulating glass depth)
        // Reusing glow paint as shadow/depth
        eraserGlowPaint.color = if (isLightBackground) Color.argb(20, 0, 0, 50) else Color.argb(60, 0, 0, 0)
        canvas.drawRoundRect(glowBox, startRadius + 2f, startRadius + 2f, eraserGlowPaint)
        
        // Layer 2: Main Glass Fill
        canvas.drawRoundRect(offsetBox, borderRadius, borderRadius, eraserFillPaint)
        
        // Layer 3: Noise effect (Texture)
        canvas.drawRoundRect(offsetBox, borderRadius, borderRadius, noisePaint)
        
        // Layer 4: Glass Border (Edge highlight)
        canvas.drawRoundRect(offsetBox, borderRadius, borderRadius, eraserBorderPaint)
        
        // Debug: show box info
        if (debugMode) {
            debugInfoPaint.color = Color.MAGENTA
            val info = "Eraser: ${offsetBox.width().toInt()}x${offsetBox.height().toInt()} (y=${offsetBox.top.toInt()})"
            canvas.drawText(info, offsetBox.left, offsetBox.top - 4f, debugInfoPaint)
            debugInfoPaint.color = Color.YELLOW
        }
    }
    
    /**
     * Draw a single translated text item with premium multi-layer effects.
     */
    private fun drawPremiumText(canvas: Canvas, item: TranslatedText) {
        // Debug: draw background
        if (debugMode) {
            canvas.drawRect(item.bounds, debugPaint)
        }

        // --- CONFIGURATION ---
        val targetFontSize = item.fontSize.coerceAtLeast(20f)
        
        // Update all paint sizes
        listOf(textPaint, strokePaint, shadowPaint, outerGlowPaint, innerGlowPaint).forEach { paint ->
            paint.textSize = targetFontSize
            paint.textScaleX = Style.fontScaleX
        }
        
        // Create gradient shader for main text
        val gradientShader = LinearGradient(
            0f, -targetFontSize * 0.8f,
            0f, targetFontSize * 0.3f,
            Style.textColorSecondary,
            Style.textColorPrimary,
            Shader.TileMode.CLAMP
        )
        
        val availableWidth = (item.bounds.width() - 4f).coerceAtLeast(1f)
        val textWidth = textPaint.measureText(item.text)
        
        // --- POSITIONING ---
        val x = item.bounds.left + 2f
        val anchorY = item.bounds.top
        val yOffset = -90f
        
        // Calculate animated glow intensity
        val glowPulse = (sin(animationTime * GLOW_PULSE_SPEED) * 0.15f + 0.85f).toFloat()
        
        // --- RENDERING ---
        if (textWidth <= availableWidth) {
            // Single line rendering with premium effects
            val fontMetrics = textPaint.fontMetrics
            val lineY = anchorY + yOffset - (fontMetrics.descent + fontMetrics.ascent) / 2f
            
            drawPremiumTextLayers(canvas, item.text, x, lineY, glowPulse, gradientShader)
            
        } else {
            // Multi-line rendering with premium effects
            drawPremiumMultilineText(canvas, item, x, anchorY + yOffset, availableWidth, glowPulse, gradientShader)
        }
        
        // Debug: draw bounds and info
        if (debugMode) {
            canvas.drawRect(item.bounds, debugBorderPaint)
            val info = "${targetFontSize.toInt()}px | ${item.text.length} chars"
            canvas.drawText(info, item.bounds.left, item.bounds.top - 2f, debugInfoPaint)
        }
    }
    
    /**
     * Draw text with all premium layers (glow, shadow, stroke, fill, highlight).
     */
    private fun drawPremiumTextLayers(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        glowPulse: Float,
        gradientShader: Shader
    ) {
        // SINGLE LAYER DRAWING (As requested: "risuy tolko odin sloy")
        // No outer glow, no shadow, no stroke, no inner glow.
        
        // Main text fill with gradient
        canvas.save()
        canvas.translate(x, y)
        textPaint.shader = gradientShader
        canvas.drawText(text, 0f, 0f, textPaint)
        textPaint.shader = null
        canvas.restore()
    }
    
    /**
     * Draw multi-line text with premium effects.
     */
    private fun drawPremiumMultilineText(
        canvas: Canvas,
        item: TranslatedText,
        x: Float,
        baseY: Float,
        availableWidth: Float,
        glowPulse: Float,
        gradientShader: Shader
    ) {
        val builder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            android.text.StaticLayout.Builder.obtain(item.text, 0, item.text.length, textPaint, availableWidth.toInt())
                .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, Style.lineHeightMultiplier)
                .setIncludePad(false)
                .build()
        } else {
            @Suppress("DEPRECATION")
            android.text.StaticLayout(
                item.text,
                textPaint,
                availableWidth.toInt(),
                android.text.Layout.Alignment.ALIGN_NORMAL,
                Style.lineHeightMultiplier,
                0f,
                false
            )
        }
        
        val layoutHeight = builder.height.toFloat()
        val layoutTop = baseY - (layoutHeight / 2f)
        
        canvas.save()
        canvas.translate(x, layoutTop)
        
        // Draw shadow layer
        canvas.save()
        canvas.translate(Style.shadowOffsetX, Style.shadowOffsetY)
        val originalShadowStyle = shadowPaint.style
        shadowPaint.style = Paint.Style.FILL
        drawLayoutWithPaint(builder, canvas, shadowPaint)
        shadowPaint.style = originalShadowStyle
        canvas.restore()
        
        // Draw stroke outline
        val originalTextStyle = textPaint.style
        val originalTextColor = textPaint.color
        textPaint.style = Paint.Style.STROKE
        textPaint.strokeWidth = Style.strokeWidth
        textPaint.color = Color.WHITE
        drawLayoutWithPaint(builder, canvas, textPaint)
        
        // Draw main fill with gradient
        textPaint.style = Paint.Style.FILL
        textPaint.strokeWidth = 0f
        textPaint.shader = gradientShader
        drawLayoutWithPaint(builder, canvas, textPaint)
        
        // Restore original state
        textPaint.shader = null
        textPaint.style = originalTextStyle
        textPaint.color = originalTextColor
        
        canvas.restore()
    }
    
    /**
     * Helper to draw StaticLayout with custom paint.
     */
    private fun drawLayoutWithPaint(layout: android.text.StaticLayout, canvas: Canvas, paint: TextPaint) {
        val originalPaint = layout.paint
        // Note: StaticLayout uses its own paint, but we can draw text line by line for custom effects
        for (i in 0 until layout.lineCount) {
            val lineStart = layout.getLineStart(i)
            val lineEnd = layout.getLineEnd(i)
            val lineText = layout.text.subSequence(lineStart, lineEnd).toString()
            val lineY = layout.getLineBaseline(i).toFloat()
            val lineX = layout.getLineLeft(i)
            canvas.drawText(lineText, lineX, lineY, paint)
        }
    }
    
    /**
     * Draw premium status indicator with glow effects.
     */
    private fun drawPremiumStatusIndicator(canvas: Canvas) {
        val (color, glowColor) = when (currentStatus) {
            Status.INITIALIZING -> Pair(Color.rgb(255, 200, 0), Color.argb(100, 255, 200, 0))
            Status.ACTIVE -> Pair(Color.rgb(0, 220, 100), Color.argb(100, 0, 220, 100))
            Status.PAUSED -> Pair(Color.rgb(150, 150, 150), Color.argb(50, 150, 150, 150))
            Status.ERROR -> Pair(Color.rgb(255, 60, 60), Color.argb(100, 255, 60, 60))
        }
        
        val cx = Style.statusDotMargin
        val cy = Style.statusDotMargin
        val radius = Style.statusDotSize
        
        // Animated glow pulse for active states
        val pulse = if (currentStatus == Status.ACTIVE || currentStatus == Status.ERROR) {
            (sin(animationTime * 0.005) * 0.4 + 0.6).toFloat()
        } else {
            0.5f
        }
        
        // Draw outer glow
        statusGlowPaint.color = glowColor
        statusGlowPaint.maskFilter = BlurMaskFilter(Style.statusGlowRadius * pulse, BlurMaskFilter.Blur.NORMAL)
        statusGlowPaint.alpha = (150 * pulse).toInt()
        canvas.drawCircle(cx, cy, radius + Style.statusGlowRadius, statusGlowPaint)
        
        // Draw filled dot with gradient-like effect
        statusPaint.color = color
        canvas.drawCircle(cx, cy, radius, statusPaint)
        
        // Draw inner highlight
        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.argb(100, 255, 255, 255)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx - radius * 0.25f, cy - radius * 0.25f, radius * 0.4f, highlightPaint)
        
        // Draw blinking stroke for warning states
        if (currentStatus == Status.ERROR || currentStatus == Status.INITIALIZING) {
            if (System.currentTimeMillis() % 1000 < STATUS_BLINK_INTERVAL) {
                canvas.drawCircle(cx, cy, radius + 3f, statusStrokePaint)
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
        
        // Stats background
        val statsBgPaint = Paint().apply {
            color = Color.argb(180, 0, 0, 0)
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, height - 30f, 300f, height.toFloat(), statsBgPaint)
        
        // Stats text
        val statsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.CYAN
            textSize = 14f
            typeface = Typeface.MONOSPACE
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
     * Update the list of eraser boxes (Canvas-based eraser).
     * These boxes are drawn BEFORE text to cover the original Chinese text.
     */
    fun updateEraserBoxes(boxes: List<RectF>) {
        eraserBoxes.clear()
        eraserBoxes.addAll(boxes)
        invalidate()
    }
    
    /**
     * Clear all eraser boxes.
     */
    fun clearEraserBoxes() {
        eraserBoxes.clear()
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
     * Clear all text items and eraser boxes.
     */
    fun clear() {
        textItems.clear()
        eraserBoxes.clear()
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
        strokePaint.textSize = size
        shadowPaint.textSize = size
        outerGlowPaint.textSize = size
        innerGlowPaint.textSize = size
    }
    
    /**
     * Set text color (updates gradient primary color).
     */
    fun setTextColor(color: Int) {
        Style.textColorPrimary = color
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
     * Set shadow/stroke width.
     */
    fun setShadowWidth(width: Float) {
        Style.strokeWidth = width
        strokePaint.strokeWidth = width
    }
    
    /**
     * Set glow colors for premium effects.
     */
    fun setGlowColors(outerGlow: Int, innerGlow: Int) {
        Style.glowColorOuter = outerGlow
        Style.glowColorInner = innerGlow
        outerGlowPaint.color = outerGlow
        innerGlowPaint.color = innerGlow
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
            measurePaint.textScaleX = Style.fontScaleX
            if (measurePaint.measureText(text) <= availableWidth) {
                low = mid
            } else {
                high = mid
            }
        }
        
        return low.coerceIn(minSize, maxSize)
    }
}

