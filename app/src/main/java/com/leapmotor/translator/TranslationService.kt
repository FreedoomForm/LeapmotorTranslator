package com.leapmotor.translator

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.leapmotor.translator.core.Logger
import com.leapmotor.translator.core.containsChinese
import com.leapmotor.translator.domain.repository.TranslationRepository
import com.leapmotor.translator.filter.KalmanFilter2D
import com.leapmotor.translator.filter.KalmanFilter2DPool
import com.leapmotor.translator.renderer.EraserSurfaceView
import com.leapmotor.translator.renderer.OverlayRenderer
import com.leapmotor.translator.renderer.TextOverlay
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * AccessibilityService that translates Chinese UI elements to Russian.
 * 
 * Uses Hilt for dependency injection and coroutines for async operations.
 * 
 * Responsibilities:
 * - Capture accessibility events
 * - Extract text nodes from UI hierarchy
 * - Translate text using ML Kit
 * - Render overlay with translations
 * - Predict scroll position with Kalman filter
 */
@AndroidEntryPoint
class TranslationService : AccessibilityService() {
    
    companion object {
        private const val TAG = "TranslationService"
        
        // Service instance for external access
        @Volatile
        var instance: TranslationService? = null
            private set
        
        // Configuration
        private const val UPDATE_DEBOUNCE_MS = 50L
        private const val MAX_NODES_PER_FRAME = 128
        private const val ELEMENT_TIMEOUT_MS = 500L
        private const val MAX_HISTORY_SIZE = 100
        
        // History log for debug activity
        val historyLog = java.util.concurrent.ConcurrentLinkedDeque<HistoryEntry>()
    }
    
    /**
     * History entry for debug logging.
     */
    data class HistoryEntry(
        val original: String,
        val translated: String,
        val bounds: android.graphics.Rect,
        val time: Long = System.currentTimeMillis()
    )
    
    // ========================================================================
    // INJECTED DEPENDENCIES
    // ========================================================================
    
    @Inject
    lateinit var translationRepository: TranslationRepository
    
    // ========================================================================
    // SERVICE STATE
    // ========================================================================
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var eraserView: EraserSurfaceView? = null
    private var textOverlay: TextOverlay? = null
    
    private var debugMode = false
    private var isOverlayShowing = false
    
    // ========================================================================
    // TRACKING STATE
    // ========================================================================
    
    private val activeElements = ConcurrentHashMap<String, TrackedElement>()
    private val kalmanFilters = ConcurrentHashMap<String, KalmanFilter2D>()
    
    private var lastUpdateTime = 0L
    private var updateJob: Job? = null
    
    // ========================================================================
    // DATA CLASSES
    // ========================================================================
    
    data class TrackedElement(
        val id: String,
        val originalText: String,
        var translatedText: String,
        var bounds: RectF,
        var predictedY: Float,
        var lastSeenTime: Long,
        var fontSize: Float = 24f
    )
    
    // ========================================================================
    // LIFECYCLE
    // ========================================================================
    
    override fun onCreate() {
        super.onCreate()
        Logger.i(TAG, "TranslationService onCreate")
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        
        Logger.i(TAG, "TranslationService connected")
        
        // Configure accessibility service
        configureAccessibilityService()
        
        // Initialize overlay
        initializeOverlay()
        
        // Initialize translation if needed
        serviceScope.launch {
            if (!translationRepository.isReady) {
                translationRepository.initialize()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        
        // Cleanup
        serviceScope.cancel()
        removeOverlay()
        releaseFilters()
        
        Logger.i(TAG, "TranslationService destroyed")
    }
    
    override fun onInterrupt() {
        Logger.w(TAG, "TranslationService interrupted")
    }
    
    // ========================================================================
    // CONFIGURATION
    // ========================================================================
    
    private fun configureAccessibilityService() {
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                        AccessibilityEvent.TYPE_VIEW_SCROLLED
            
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                   AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                   AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            
            notificationTimeout = 50
        }
        
        serviceInfo = info
    }
    
    // ========================================================================
    // ACCESSIBILITY EVENTS
    // ========================================================================
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        // Debounce updates
        val now = System.currentTimeMillis()
        if (now - lastUpdateTime < UPDATE_DEBOUNCE_MS) return
        lastUpdateTime = now
        
        // Cancel previous update and start new one
        updateJob?.cancel()
        updateJob = serviceScope.launch {
            try {
                processEvent(event, now)
            } catch (e: Exception) {
                Logger.e(TAG, "Error processing event", e)
            }
        }
    }
    
    private suspend fun processEvent(event: AccessibilityEvent, currentTime: Long) {
        val rootNode = rootInActiveWindow ?: return
        
        try {
            // Extract text nodes
            val textNodes = mutableListOf<TextNodeInfo>()
            extractTextNodes(rootNode, textNodes, 0)
            
            // Limit nodes per frame
            val limitedNodes = textNodes.take(MAX_NODES_PER_FRAME)
            
            // Mark current elements as seen
            val seenIds = mutableSetOf<String>()
            
            // Process each text node
            for (nodeInfo in limitedNodes) {
                val elementId = createElementId(nodeInfo)
                seenIds.add(elementId)
                
                // Check if element exists
                val existing = activeElements[elementId]
                
                if (existing != null) {
                    // Update existing element
                    updateExistingElement(existing, nodeInfo, currentTime)
                } else {
                    // Create new element
                    createNewElement(elementId, nodeInfo, currentTime)
                }
            }
            
            // Remove expired elements
            removeExpiredElements(currentTime, seenIds)
            
            // Update overlay
            withContext(Dispatchers.Main) {
                updateOverlay()
            }
            
        } finally {
            rootNode.recycle()
        }
    }
    
    // ========================================================================
    // TEXT NODE EXTRACTION
    // ========================================================================
    
    private data class TextNodeInfo(
        val text: String,
        val bounds: RectF,
        val viewId: String?,
        val depth: Int
    )
    
    private fun extractTextNodes(
        node: AccessibilityNodeInfo,
        result: MutableList<TextNodeInfo>,
        depth: Int
    ) {
        // Get text content
        val text = node.text?.toString()?.trim()
        
        if (!text.isNullOrEmpty() && text.containsChinese()) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            val bounds = RectF(rect)
            
            if (isValidBounds(bounds)) {
                result.add(TextNodeInfo(
                    text = text,
                    bounds = bounds,
                    viewId = node.viewIdResourceName,
                    depth = depth
                ))
            }
        }
        
        // Process children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                extractTextNodes(child, result, depth + 1)
            } finally {
                child.recycle()
            }
        }
    }
    
    private fun isValidBounds(bounds: RectF): Boolean {
        return bounds.width() > 10 &&
               bounds.height() > 10 &&
               bounds.left >= 0 &&
               bounds.top >= 0 &&
               bounds.right <= 4096 &&
               bounds.bottom <= 4096
    }
    
    // ========================================================================
    // ELEMENT MANAGEMENT
    // ========================================================================
    
    private fun createElementId(nodeInfo: TextNodeInfo): String {
        return "${nodeInfo.viewId ?: "no_id"}|${nodeInfo.text}|${nodeInfo.bounds.width().toInt()}x${nodeInfo.bounds.height().toInt()}"
    }
    
    private suspend fun createNewElement(
        elementId: String,
        nodeInfo: TextNodeInfo,
        currentTime: Long
    ) {
        // Translate text
        val translation = translationRepository.translate(nodeInfo.text)
            .getOrDefault(nodeInfo.text)
        
        // Calculate font size
        val fontSize = calculateFontSize(translation, nodeInfo.bounds)
        
        // Create element
        val element = TrackedElement(
            id = elementId,
            originalText = nodeInfo.text,
            translatedText = translation,
            bounds = nodeInfo.bounds,
            predictedY = nodeInfo.bounds.top,
            lastSeenTime = currentTime,
            fontSize = fontSize
        )
        
        activeElements[elementId] = element
    }
    
    private fun updateExistingElement(
        element: TrackedElement,
        nodeInfo: TextNodeInfo,
        currentTime: Long
    ) {
        // Get or create Kalman filter
        val filter = kalmanFilters.getOrPut(element.id) {
            KalmanFilter2DPool.acquire()
        }
        
        // Update Kalman filter and get prediction
        val (_, predictedY) = filter.update(
            nodeInfo.bounds.left,
            nodeInfo.bounds.top,
            currentTime
        )
        
        // Update element
        element.bounds = nodeInfo.bounds
        element.predictedY = predictedY
        element.lastSeenTime = currentTime
    }
    
    private fun removeExpiredElements(currentTime: Long, seenIds: Set<String>) {
        val iterator = activeElements.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!seenIds.contains(entry.key) && 
                currentTime - entry.value.lastSeenTime > ELEMENT_TIMEOUT_MS) {
                
                // Release Kalman filter
                kalmanFilters.remove(entry.key)?.let { filter ->
                    KalmanFilter2DPool.release(filter)
                }
                
                iterator.remove()
            }
        }
    }
    
    private fun releaseFilters() {
        kalmanFilters.values.forEach { filter ->
            KalmanFilter2DPool.release(filter)
        }
        kalmanFilters.clear()
        activeElements.clear()
    }
    
    // ========================================================================
    // FONT SIZE CALCULATION
    // ========================================================================
    
    private fun calculateFontSize(text: String, bounds: RectF): Float {
        // "3 times bigger" strategy
        val heightBasedSize = bounds.height() * 2.0f // Start double height
        val avgCharWidth = 0.6f
        val requiredWidth = text.length * heightBasedSize * avgCharWidth
        
        val widthBasedSize = if (requiredWidth > bounds.width() && bounds.width() > 0) {
            heightBasedSize * (bounds.width() / requiredWidth)
        } else {
            heightBasedSize
        }
        
        // Massive range, default big
        return widthBasedSize.coerceIn(30f, 150f)
    }
    
    // ========================================================================
    // OVERLAY MANAGEMENT
    // ========================================================================
    
    private fun initializeOverlay() {
        if (isOverlayShowing) return
        
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        // Create eraser view (OpenGL)
        eraserView = EraserSurfaceView(this)
        
        // Create text overlay
        textOverlay = TextOverlay(this)
        
        // Overlay layout params
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        
        try {
            windowManager?.addView(eraserView, layoutParams)
            windowManager?.addView(textOverlay, layoutParams)
            isOverlayShowing = true
            
            textOverlay?.setStatus(TextOverlay.Status.ACTIVE)
            
            Logger.i(TAG, "Overlay initialized")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to add overlay", e)
        }
    }
    
    private fun removeOverlay() {
        try {
            eraserView?.let { windowManager?.removeView(it) }
            textOverlay?.let { windowManager?.removeView(it) }
        } catch (e: Exception) {
            Logger.e(TAG, "Error removing overlay", e)
        }
        
        eraserView = null
        textOverlay = null
        isOverlayShowing = false
    }
    
    private fun updateOverlay() {
        if (!isOverlayShowing) return
        
        val elements = activeElements.values.toList()
        
        // Update eraser
        val boundingBoxes = elements.map { element ->
            RectF(
                element.bounds.left,
                element.predictedY,
                element.bounds.right,
                element.predictedY + element.bounds.height()
            )
        }
        
        // Smart Theme Detection: If text is dark, background is likely light
        val textColor = TextOverlay.Style.textColor
        val r = android.graphics.Color.red(textColor)
        val g = android.graphics.Color.green(textColor)
        val b = android.graphics.Color.blue(textColor)
        val brightness = (0.299*r + 0.587*g + 0.114*b)
        val isLightBg = brightness < 128
        
        eraserView?.renderer?.isLightBackground = isLightBg
        eraserView?.renderer?.updateBoundingBoxes(boundingBoxes)
        eraserView?.requestRender()
        
        // Update text overlay
        val textItems = elements.map { element ->
            TextOverlay.TranslatedText(
                text = element.translatedText,
                bounds = RectF(
                    element.bounds.left,
                    element.predictedY,
                    element.bounds.right,
                    element.predictedY + element.bounds.height()
                ),
                originalText = element.originalText,
                fontSize = element.fontSize
            )
        }
        textOverlay?.updateTextItems(textItems)
    }
    
    // ========================================================================
    // PUBLIC API
    // ========================================================================
    
    fun setDebugMode(enabled: Boolean) {
        debugMode = enabled
        textOverlay?.debugMode = enabled
        Logger.i(TAG, "Debug mode: $enabled")
    }
    
    fun getActiveElementCount(): Int = activeElements.size
    
    fun clearTranslations() {
        activeElements.clear()
        releaseFilters()
        serviceScope.launch(Dispatchers.Main) {
            textOverlay?.clear()
            eraserView?.renderer?.updateBoundingBoxes(emptyList())
            eraserView?.requestRender()
        }
    }
}
