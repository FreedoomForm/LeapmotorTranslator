package com.leapmotor.translator.filter

/**
 * 1D Kalman Filter for Y-coordinate motion prediction.
 * 
 * Compensates for ~20ms accessibility coordinate lag during scrolling
 * by predicting the next frame's Y position.
 * 
 * Optimized for Snapdragon 8155 with minimal allocations.
 * 
 * State vector: [position, velocity]
 * Measurement: position only
 */
class KalmanFilter(
    private val processNoise: Float = 0.1f,      // Q: Process noise covariance
    private val measurementNoise: Float = 1.0f,  // R: Measurement noise covariance
    private val estimationError: Float = 1.0f    // Initial P: Estimation error covariance
) {
    // State variables
    private var x: Float = 0f           // Estimated position
    private var v: Float = 0f           // Estimated velocity
    private var p: Float = estimationError  // Estimation error covariance
    private var pv: Float = estimationError // Velocity error covariance
    
    // Timing
    private var lastUpdateTime: Long = 0L
    private var isInitialized: Boolean = false
    
    // Constants for prediction
    companion object {
        private const val MIN_DT = 0.001f      // 1ms minimum delta time
        private const val MAX_DT = 0.1f        // 100ms maximum delta time
        private const val PREDICTION_TIME_MS = 20L  // Target prediction ahead time
    }
    
    /**
     * Reset the filter to initial state.
     */
    fun reset() {
        x = 0f
        v = 0f
        p = estimationError
        pv = estimationError
        lastUpdateTime = 0L
        isInitialized = false
    }
    
    /**
     * Update the filter with a new measurement and return the predicted position.
     * 
     * @param measurement The measured Y coordinate from accessibility
     * @param currentTimeMs Current system time in milliseconds
     * @return Predicted Y coordinate for the next frame
     */
    fun update(measurement: Float, currentTimeMs: Long): Float {
        if (!isInitialized) {
            // Initialize with first measurement
            x = measurement
            v = 0f
            lastUpdateTime = currentTimeMs
            isInitialized = true
            return measurement
        }
        
        // Calculate delta time in seconds
        val dtMs = (currentTimeMs - lastUpdateTime).coerceIn(1L, 100L)
        val dt = (dtMs / 1000f).coerceIn(MIN_DT, MAX_DT)
        lastUpdateTime = currentTimeMs
        
        // === PREDICTION STEP ===
        // State prediction: x_pred = x + v * dt
        val xPred = x + v * dt
        
        // Error covariance prediction: P_pred = P + Q
        val pPred = p + processNoise * dt
        val pvPred = pv + processNoise * dt
        
        // === UPDATE STEP ===
        // Innovation (measurement residual)
        val innovation = measurement - xPred
        
        // Kalman gain: K = P_pred / (P_pred + R)
        val kalmanGain = pPred / (pPred + measurementNoise)
        
        // State update
        x = xPred + kalmanGain * innovation
        
        // Velocity estimation using exponential smoothing
        val measuredVelocity = innovation / dt
        val velocityGain = pvPred / (pvPred + measurementNoise * 10f)
        v = v + velocityGain * (measuredVelocity - v)
        
        // Error covariance update
        p = (1f - kalmanGain) * pPred
        pv = (1f - velocityGain) * pvPred
        
        // === PREDICT AHEAD ===
        // Predict position for next frame (~20ms ahead)
        val predictionDt = PREDICTION_TIME_MS / 1000f
        return x + v * predictionDt
    }
    
    /**
     * Get the current estimated velocity.
     * Useful for detecting fast scrolls.
     */
    fun getVelocity(): Float = v
    
    /**
     * Get the current estimated position without updating.
     */
    fun getPosition(): Float = x
    
    /**
     * Predict position at a future time without updating state.
     * 
     * @param futureTimeMs How many milliseconds ahead to predict
     * @return Predicted Y coordinate
     */
    fun predictAt(futureTimeMs: Long): Float {
        val dt = futureTimeMs / 1000f
        return x + v * dt
    }
    
    /**
     * Check if the filter is tracking a fast scroll.
     * 
     * @param threshold Velocity threshold in pixels/second
     * @return true if scrolling faster than threshold
     */
    fun isFastScrolling(threshold: Float = 500f): Boolean {
        return kotlin.math.abs(v) > threshold
    }
}

/**
 * Pool of Kalman filters for multiple tracked elements.
 * Reduces allocation overhead during runtime.
 */
class KalmanFilterPool(private val poolSize: Int = 64) {
    private val filters = Array(poolSize) { KalmanFilter() }
    private val inUse = BooleanArray(poolSize)
    private var nextIndex = 0
    
    /**
     * Acquire a filter from the pool.
     * @return Filter instance or null if pool is exhausted
     */
    @Synchronized
    fun acquire(): KalmanFilter? {
        for (i in 0 until poolSize) {
            val index = (nextIndex + i) % poolSize
            if (!inUse[index]) {
                inUse[index] = true
                nextIndex = (index + 1) % poolSize
                filters[index].reset()
                return filters[index]
            }
        }
        return null
    }
    
    /**
     * Release a filter back to the pool.
     */
    @Synchronized
    fun release(filter: KalmanFilter) {
        for (i in 0 until poolSize) {
            if (filters[i] === filter) {
                inUse[i] = false
                filter.reset()
                return
            }
        }
    }
    
    /**
     * Release all filters in the pool.
     */
    @Synchronized
    fun releaseAll() {
        for (i in 0 until poolSize) {
            inUse[i] = false
            filters[i].reset()
        }
    }
}
