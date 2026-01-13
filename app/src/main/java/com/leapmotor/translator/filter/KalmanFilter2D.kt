package com.leapmotor.translator.filter

import com.leapmotor.translator.core.ObjectPool

/**
 * Enhanced 2D Kalman Filter for position prediction.
 * 
 * Improvements over original:
 * - Full 2D support (X and Y)
 * - Configurable process/measurement noise
 * - Velocity smoothing for better prediction
 * - State inspection methods
 * 
 * State vector: [x, y, vx, vy]
 * Measurement: [x, y]
 * 
 * Optimized for real-time UI element tracking with ~20ms lag compensation.
 */
class KalmanFilter2D private constructor() {
    
    // ============================================================================
    // CONFIGURATION
    // ============================================================================
    
    /**
     * Process noise - higher values make filter more responsive but noisier.
     * Recommended: 0.001 - 0.1
     */
    var processNoise: Float = 1.0f
    
    /**
     * Measurement noise - higher values make filter smoother but laggier.
     * Recommended: 1.0 - 10.0
     */
    var measurementNoise: Float = 0.1f
    
    /**
     * Prediction lookahead time in milliseconds.
     */
    var predictionTimeMs: Long = 10L
    
    // ============================================================================
    // STATE
    // ============================================================================
    
    // Position estimates
    private var x: Float = 0f
    private var y: Float = 0f
    
    // Velocity estimates
    private var vx: Float = 0f
    private var vy: Float = 0f
    
    // Error covariances (diagonal approximation for performance)
    private var px: Float = 1f   // Position X error
    private var py: Float = 1f   // Position Y error
    private var pvx: Float = 100f  // Velocity X error (high initial uncertainty)
    private var pvy: Float = 100f  // Velocity Y error (high initial uncertainty)
    
    // Timing
    private var lastUpdateTime: Long = 0L
    private var isInitialized: Boolean = false
    
    companion object {
        private const val MIN_DT = 0.001f  // 1ms minimum
        private const val MAX_DT = 0.1f    // 100ms maximum
        
        /**
         * Create a new filter with default configuration.
         */
        fun create(
            processNoise: Float = 0.01f,
            measurementNoise: Float = 1.0f,
            predictionTimeMs: Long = 10L
        ): KalmanFilter2D = KalmanFilter2D().apply {
            this.processNoise = processNoise
            this.measurementNoise = measurementNoise
            this.predictionTimeMs = predictionTimeMs
        }
    }
    
    // ============================================================================
    // PUBLIC API
    // ============================================================================
    
    /**
     * Reset the filter to uninitialized state.
     */
    fun reset() {
        x = 0f; y = 0f
        vx = 0f; vy = 0f
        px = 1f; py = 1f
        pvx = 100f; pvy = 100f
        lastUpdateTime = 0L
        isInitialized = false
    }
    
    /**
     * Update filter with new measurement and get predicted position.
     * 
     * @param measuredX Measured X coordinate
     * @param measuredY Measured Y coordinate
     * @param currentTimeMs Current timestamp in milliseconds
     * @return Pair of (predictedX, predictedY)
     */
    fun update(measuredX: Float, measuredY: Float, currentTimeMs: Long): Pair<Float, Float> {
        if (!isInitialized) {
            // Initialize with first measurement
            x = measuredX
            y = measuredY
            vx = 0f
            vy = 0f
            lastUpdateTime = currentTimeMs
            isInitialized = true
            return Pair(measuredX, measuredY)
        }
        
        // Calculate delta time
        val dtMs = (currentTimeMs - lastUpdateTime).coerceIn(1L, 100L)
        val dt = (dtMs / 1000f).coerceIn(MIN_DT, MAX_DT)
        lastUpdateTime = currentTimeMs
        
        // === PREDICTION STEP ===
        val xPred = x + vx * dt
        val yPred = y + vy * dt
        
        // Error prediction (simplified diagonal covariance)
        val pxPred = px + processNoise * dt
        val pyPred = py + processNoise * dt
        val pvxPred = pvx + processNoise * dt
        val pvyPred = pvy + processNoise * dt
        
        // === UPDATE STEP ===
        // Innovation (measurement residual)
        val innovX = measuredX - xPred
        val innovY = measuredY - yPred
        
        // Kalman gains
        val kx = pxPred / (pxPred + measurementNoise)
        val ky = pyPred / (pyPred + measurementNoise)
        val kvx = pvxPred / (pvxPred + measurementNoise)
        val kvy = pvyPred / (pvyPred + measurementNoise)
        
        // State update
        x = xPred + kx * innovX
        y = yPred + ky * innovY
        
        // Velocity update using innovation
        val measuredVx = innovX / dt
        val measuredVy = innovY / dt
        vx += kvx * (measuredVx - vx)
        vy += kvy * (measuredVy - vy)
        
        // Error update
        px = (1f - kx) * pxPred
        py = (1f - ky) * pyPred
        pvx = (1f - kvx) * pvxPred
        pvy = (1f - kvy) * pvyPred
        
        // === PREDICT AHEAD ===
        val predDt = predictionTimeMs / 1000f
        return Pair(x + vx * predDt, y + vy * predDt)
    }
    
    /**
     * Update Y coordinate only (common use case for vertical scrolling).
     * 
     * @param measuredY Measured Y coordinate
     * @param currentTimeMs Current timestamp
     * @return Predicted Y coordinate
     */
    fun updateY(measuredY: Float, currentTimeMs: Long): Float {
        val result = update(x, measuredY, currentTimeMs)
        return result.second
    }
    
    /**
     * Get current predicted position without updating.
     */
    fun getPredictedPosition(futureTimeMs: Long = predictionTimeMs): Pair<Float, Float> {
        if (!isInitialized) return Pair(0f, 0f)
        val dt = futureTimeMs / 1000f
        return Pair(x + vx * dt, y + vy * dt)
    }
    
    /**
     * Get current velocity estimate.
     */
    fun getVelocity(): Pair<Float, Float> = Pair(vx, vy)
    
    /**
     * Get current position estimate.
     */
    fun getPosition(): Pair<Float, Float> = Pair(x, y)
    
    /**
     * Check if scrolling fast (velocity above threshold).
     */
    fun isFastScrolling(threshold: Float = 500f): Boolean {
        return kotlin.math.sqrt(vx * vx + vy * vy) > threshold
    }
    
    /**
     * Check if scrolling vertically.
     */
    fun isScrollingDown(): Boolean = vy > 50f
    
    /**
     * Check if scrolling up.
     */
    fun isScrollingUp(): Boolean = vy < -50f
}

/**
 * Pool of KalmanFilter2D instances for memory efficiency.
 * 
 * Usage:
 * ```kotlin
 * val filter = KalmanFilter2DPool.acquire()
 * try {
 *     filter.update(x, y, time)
 * } finally {
 *     KalmanFilter2DPool.release(filter)
 * }
 * ```
 */
object KalmanFilter2DPool {
    
    @PublishedApi
    internal val pool = ObjectPool(
        maxSize = 128,
        factory = { KalmanFilter2D.create() },
        reset = { it.reset() }
    )
    
    /**
     * Acquire a filter from the pool.
     */
    fun acquire(): KalmanFilter2D = pool.acquire()
    
    /**
     * Release a filter back to the pool.
     */
    fun release(filter: KalmanFilter2D): Boolean = pool.release(filter)
    
    /**
     * Use a filter temporarily with automatic release.
     */
    inline fun <R> use(block: (KalmanFilter2D) -> R): R = pool.use(block)
    
    /**
     * Prefill the pool with filters.
     */
    fun prefill(count: Int = 64) = pool.prefill(count)
    
    /**
     * Clear the pool.
     */
    fun clear() = pool.clear()
    
    /**
     * Get pool statistics.
     */
    val stats: ObjectPool.PoolStats get() = pool.stats
}
