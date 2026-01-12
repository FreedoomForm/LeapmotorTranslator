package com.leapmotor.translator

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import com.leapmotor.translator.filter.KalmanFilter
import com.leapmotor.translator.filter.KalmanFilterPool
import com.leapmotor.translator.renderer.EraserSurfaceView
import com.leapmotor.translator.renderer.TextOverlay
import com.leapmotor.translator.translation.CommonTranslations
import com.leapmotor.translator.translation.TranslationManager
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Main Accessibility Service for Leapmotor C11 Translation Overlay.
 * 
 * Captures Chinese UI elements, translates to Russian, and renders
 * an overlay that masks original text and displays translations.
 * 
 * Architecture:
 * 1. Input: AccessibilityService captures UI tree
 * 2. Processing: ML Kit translation + Kalman filter for scroll prediction
 * 3. Visual: OpenGL shader eraser + Canvas text overlay
 * 
 * Optimized for Snapdragon 8155 / Android 9 Automotive.
 */
class TranslationService : AccessibilityService() {

    companion object {
        private const val TAG = "TranslationService"
        
        // Processing settings
        private const val UPDATE_DEBOUNCE_MS = 50L
        // Processing settings
        private const val UPDATE_DEBOUNCE_MS = 50L
        private const val MAX_NODES_PER_FRAME = 128
        
        // Singleton reference for external access
        @Volatile
        var instance: TranslationService? = null
            private set
            
        // Debug History Log
        data class LogItem(
            val time: Long,
            val original: String,
            val translated: String?,
            val bounds: RectF
        )
        
        val historyLog = java.util.concurrent.CopyOnWriteArrayList<LogItem>()
        
        fun addToLog(original: String, translated: String?, bounds: RectF) {
            if (historyLog.size > 50) {
                historyLog.removeAt(0)
            }
            historyLog.add(LogItem(System.currentTimeMillis(), original, translated, bounds))
        }
    }
    
    // Window manager for overlays
    private lateinit var windowManager: WindowManager

    // Dynamic screen metrics
    private var screenWidth = 1080
    private var screenHeight = 2340 // Default to a reasonable mobile size, updated in onCreate

    // Overlay views
    private var overlayContainer: FrameLayout? = null
    private var eraserView: EraserSurfaceView? = null
    private var textOverlay: TextOverlay? = null
    
    // Translation engine
    private val translationManager = TranslationManager.getInstance()
    
    // Kalman filters for motion prediction (indexed by node ID hashcode)
    private val kalmanFilters = ConcurrentHashMap<Int, KalmanFilter>()
    private val filterPool = KalmanFilterPool(64)
    
    // Coroutine scope for async processing
    private val serviceScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineName("TranslationService")
    )
    
    // Main thread handler for UI updates
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Debouncing
    private var lastUpdateTime = 0L
    private var pendingUpdate: Job? = null
    
    // Tracked text elements
    private val trackedElements = ConcurrentHashMap<String, TrackedElement>()
    
    // State
    private var isOverlayActive = false
    private var isInitialized = false
    
    /**
     * Data class for tracking UI text elements.
     */
    data class TrackedElement(
        val id: String,
        val originalText: String,
        var translatedText: String?,
        val bounds: RectF,
        var predictedY: Float,
        var lastSeenTime: Long,
        var kalmanFilter: KalmanFilter?
    )
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "TranslationService created")
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "TranslationService connected")
        
        // Configure service info
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
        
        // Initialize components
        initializeService()
    }
    
    private fun initializeService() {
        if (isInitialized) return
        
        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            
            // Get dynamic screen metrics
            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(metrics)
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
            Log.d(TAG, "Screen metrics initialized: ${screenWidth}x${screenHeight}")

            // Initialize dictionary persistence
            translationManager.init(this)

            // initialize translation engine with safe try-catch wrapper
            serviceScope.launch {
                try {
                    withContext(Dispatchers.Main) {
                        textOverlay?.setStatus(TextOverlay.Status.INITIALIZING)
                    }
                    
                    translationManager.preloadTranslations(CommonTranslations.LEAPMOTOR_UI)
                    val ready = translationManager.initialize()
                    
                    withContext(Dispatchers.Main) {
                        if (ready) {
                            Log.d(TAG, "Translation model ready")
                            createOverlay()
                            textOverlay?.setStatus(TextOverlay.Status.ACTIVE)
                        } else {
                            Log.e(TAG, "Failed to initialize translation model")
                            textOverlay?.setStatus(TextOverlay.Status.ERROR)
                            android.widget.Toast.makeText(this@TranslationService, "Ошибка инициализации модели перевода", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                     Log.e(TAG, "Error in translation initialization: ${e.message}")
                     e.printStackTrace()
                     withContext(Dispatchers.Main) {
                         android.widget.Toast.makeText(this@TranslationService, "Ошибка перевода: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                     }
                }
            }
            
            isInitialized = true
            Log.d(TAG, "TranslationService initialized")
            
            mainHandler.post {
                android.widget.Toast.makeText(this, "Сервис перевода запущен!", android.widget.Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Critical error initializing service: ${e.message}")
            e.printStackTrace()
            mainHandler.post {
                android.widget.Toast.makeText(this, "Критическая ошибка сервиса: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
    
    /**
     * Set debug mode to show/hide visualization helpers.
     */
    fun setDebugMode(enabled: Boolean) {
        textOverlay?.debugMode = enabled
        textOverlay?.invalidate()
        if (enabled) {
             mainHandler.post {
                android.widget.Toast.makeText(this, "Debug Mode Enabled (Red Border)", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Create the overlay window with OpenGL eraser and text layers.
     */
    private fun createOverlay() {
        if (overlayContainer != null) return
        
        // Container layout
        overlayContainer = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        
        // OpenGL eraser surface
        eraserView = EraserSurfaceView(this)
        overlayContainer?.addView(eraserView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        
        // Canvas text overlay
        textOverlay = TextOverlay(this)
        overlayContainer?.addView(textOverlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        
        // Window parameters
        val params = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
            }
            
            format = PixelFormat.TRANSLUCENT
            
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
        }
        
        try {
            windowManager.addView(overlayContainer, params)
            isOverlayActive = true
            Log.d(TAG, "Overlay created successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create overlay: ${e.message}")
            e.printStackTrace()
            mainHandler.post {
                android.widget.Toast.makeText(this, "Ошибка создания наложения: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
    
    /**
     * Remove the overlay window.
     */
    private fun removeOverlay() {
        if (!isOverlayActive) return
        
        try {
            eraserView?.renderer?.release()
            overlayContainer?.let { windowManager.removeView(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing overlay: ${e.message}")
        }
        
        overlayContainer = null
        eraserView = null
        textOverlay = null
        isOverlayActive = false
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !isOverlayActive) return
        
        val now = SystemClock.elapsedRealtime()
        
        // Debounce rapid updates
        if (now - lastUpdateTime < UPDATE_DEBOUNCE_MS) {
            // Schedule delayed update instead
            pendingUpdate?.cancel()
            pendingUpdate = serviceScope.launch {
                delay(UPDATE_DEBOUNCE_MS)
                withContext(Dispatchers.Main) {
                    processEvent(event)
                }
            }
            return
        }
        
        lastUpdateTime = now
        processEvent(event)
    }
    
    /**
     * Process accessibility event and update overlay.
     */
    private fun processEvent(event: AccessibilityEvent) {
        val rootNode = rootInActiveWindow ?: return
        
        serviceScope.launch {
            try {
                // Extract text nodes from UI tree
                val textNodes = mutableListOf<TextNode>()
                extractTextNodes(rootNode, textNodes, 0)
                
                // Process and translate
                val elements = processTextNodes(textNodes)
                
                // Update overlay on main thread
                withContext(Dispatchers.Main) {
                    updateOverlay(elements)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error processing event: ${e.message}")
            } finally {
                rootNode.recycle()
            }
        }
    }
    
    /**
     * Extracted text node data class.
     */
    data class TextNode(
        val id: String,
        val text: String,
        val bounds: Rect,
        val depth: Int
    )
    
    /**
     * Recursively extract text nodes from accessibility tree.
     */
    private fun extractTextNodes(
        node: AccessibilityNodeInfo,
        results: MutableList<TextNode>,
        depth: Int
    ) {
        if (results.size >= MAX_NODES_PER_FRAME) return
        
        // Extract text if present
        val text = node.text?.toString()?.trim()
        if (!text.isNullOrEmpty() && containsChinese(text)) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            
            // Skip off-screen or too small elements
            if (bounds.width() > 10 && bounds.height() > 10 &&
                bounds.left >= 0 && bounds.top >= 0 &&
                bounds.right <= screenWidth &&
                bounds.bottom <= screenHeight
            ) {
                val id = "${node.viewIdResourceName ?: ""}:${bounds.hashCode()}"
                results.add(TextNode(id, text, bounds, depth))
            }
        }
        
        // Recursively process children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                extractTextNodes(child, results, depth + 1)
            } finally {
                child.recycle()
            }
        }
    }
    
    /**
     * Check if text contains Chinese characters.
     */
    private fun containsChinese(text: String): Boolean {
        return text.any { char ->
            val code = char.code
            // CJK Unified Ideographs range
            code in 0x4E00..0x9FFF ||
            // CJK Extension A
            code in 0x3400..0x4DBF ||
            // Common CJK punctuation
            code in 0x3000..0x303F
        }
    }
    
    /**
     * Process text nodes: translate and apply Kalman prediction.
     */
    private suspend fun processTextNodes(nodes: List<TextNode>): List<TrackedElement> {
        val currentTime = SystemClock.elapsedRealtime()
        val elements = mutableListOf<TrackedElement>()
        
        for (node in nodes) {
            // Get or create tracked element
            var element = trackedElements[node.id]
            
            if (element == null) {
                // New element - translate and create tracker
                val translation = translationManager.translate(node.text)
                
                // Log it
                addToLog(node.text, translation, RectF(node.bounds))
                
                val filter = filterPool.acquire()
                
                element = TrackedElement(
                    id = node.id,
                    originalText = node.text,
                    translatedText = translation,
                    bounds = RectF(node.bounds),
                    predictedY = node.bounds.top.toFloat(),
                    lastSeenTime = currentTime,
                    kalmanFilter = filter
                )
                
                trackedElements[node.id] = element
                
            } else {
                // Existing element - update with Kalman prediction
                element.lastSeenTime = currentTime
                element.bounds.set(node.bounds.left.toFloat(), node.bounds.top.toFloat(),
                    node.bounds.right.toFloat(), node.bounds.bottom.toFloat())
                
                // Apply Kalman filter for Y prediction
                element.kalmanFilter?.let { filter ->
                    element.predictedY = filter.update(
                        node.bounds.top.toFloat(),
                        currentTime
                    )
                }
                
                // Re-translate if text changed
                if (element.originalText != node.text) {
                    element.translatedText = translationManager.translate(node.text)
                }
                
                // Recovery: If filter was lost (e.g. pool exhausted previously), try to acquire one again
                if (element.kalmanFilter == null) {
                    element.kalmanFilter = filterPool.acquire()
                    // If we got one, sync it
                    element.kalmanFilter?.update(node.bounds.top.toFloat(), currentTime)
                }
            }
            
            elements.add(element)
        }
        
        // Clean up stale elements (not seen for 500ms)
        val staleThreshold = currentTime - 500
        val staleKeys = trackedElements.entries
            .filter { it.value.lastSeenTime < staleThreshold }
            .map { it.key }
        
        for (key in staleKeys) {
            trackedElements[key]?.kalmanFilter?.let { filterPool.release(it) }
            trackedElements.remove(key)
        }
        
        return elements
    }
    
    /**
     * Update overlay with translated elements.
     */
    private fun updateOverlay(elements: List<TrackedElement>) {
        if (!isOverlayActive) return
        
        // Update eraser shader bounding boxes
        val eraserBoxes = elements.map { element ->
            // Use Kalman-predicted Y for smoother scroll tracking
            val predictedBounds = RectF(element.bounds)
            predictedBounds.offsetTo(predictedBounds.left, element.predictedY)
            predictedBounds
        }
        eraserView?.updateBoxes(eraserBoxes)
        
        // Update text overlay
        val textItems = elements.mapNotNull { element ->
            val translation = element.translatedText ?: return@mapNotNull null
            
            // Use predicted bounds
            val predictedBounds = RectF(element.bounds)
            predictedBounds.offsetTo(predictedBounds.left, element.predictedY)
            
            // Estimate font size based on bounds
            val fontSize = textOverlay?.estimateFontSize(
                translation,
                predictedBounds,
                maxSize = 32f,
                minSize = 14f
            ) ?: 20f
            
            TextOverlay.TranslatedText(
                text = translation,
                bounds = predictedBounds,
                originalText = element.originalText,
                fontSize = fontSize
            )
        }
        textOverlay?.updateTextItems(textItems)
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "TranslationService interrupted")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Cancel all coroutines
        serviceScope.cancel()
        pendingUpdate?.cancel()
        
        // Release resources
        removeOverlay()
        translationManager.release()
        filterPool.releaseAll()
        trackedElements.clear()
        
        instance = null
        Log.d(TAG, "TranslationService destroyed")
    }
    
    /**
     * Toggle overlay visibility.
     */
    fun toggleOverlay() {
        if (isOverlayActive) {
            overlayContainer?.visibility = View.GONE
        } else {
            overlayContainer?.visibility = View.VISIBLE
        }
    }
    

    
    /**
     * Get translation cache statistics.
     */
    fun getCacheStats(): TranslationManager.CacheStats {
        return translationManager.getCacheStats()
    }
}
