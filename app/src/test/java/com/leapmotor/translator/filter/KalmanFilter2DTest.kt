package com.leapmotor.translator.filter

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for KalmanFilter2D.
 */
class KalmanFilter2DTest {
    
    private lateinit var filter: KalmanFilter2D
    
    @Before
    fun setup() {
        filter = KalmanFilter2D.create()
    }
    
    // ========================================================================
    // INITIALIZATION TESTS
    // ========================================================================
    
    @Test
    fun `first update returns measurement directly`() {
        val (x, y) = filter.update(100f, 200f, 1000L)
        
        assertEquals(100f, x, 0.1f)
        assertEquals(200f, y, 0.1f)
    }
    
    @Test
    fun `reset clears state`() {
        filter.update(100f, 200f, 1000L)
        filter.update(110f, 210f, 1050L)
        
        filter.reset()
        
        val pos = filter.getPosition()
        assertEquals(0f, pos.first, 0.001f)
        assertEquals(0f, pos.second, 0.001f)
    }
    
    // ========================================================================
    // PREDICTION TESTS
    // ========================================================================
    
    @Test
    fun `stationary object has minimal prediction offset`() {
        // Same position over multiple updates
        filter.update(100f, 200f, 1000L)
        filter.update(100f, 200f, 1050L)
        filter.update(100f, 200f, 1100L)
        val (x, y) = filter.update(100f, 200f, 1150L)
        
        // Should be very close to actual position
        assertEquals(100f, x, 5f)
        assertEquals(200f, y, 5f)
    }
    
    @Test
    fun `moving object prediction includes velocity`() {
        // Object moving down at ~100px per 50ms = 2000px/s
        filter.update(100f, 200f, 1000L)
        filter.update(100f, 250f, 1050L)
        filter.update(100f, 300f, 1100L)
        val (_, y) = filter.update(100f, 350f, 1150L)
        
        // Prediction should be ahead of measurement
        assertTrue("Predicted Y should be ahead of measurement", y > 350f)
    }
    
    @Test
    fun `velocity is calculated correctly`() {
        filter.update(0f, 0f, 0L)
        filter.update(0f, 100f, 100L) // 100px in 100ms = 1000px/s
        
        val (_, vy) = filter.getVelocity()
        
        // Velocity should be positive (moving down)
        assertTrue("Velocity should be positive", vy > 0)
    }
    
    // ========================================================================
    // SCROLL DETECTION TESTS
    // ========================================================================
    
    @Test
    fun `isFastScrolling detects fast movement`() {
        filter.update(0f, 0f, 0L)
        filter.update(0f, 100f, 50L) // Very fast scroll
        filter.update(0f, 200f, 100L)
        
        assertTrue(filter.isFastScrolling(500f))
    }
    
    @Test
    fun `isFastScrolling detects slow movement`() {
        filter.update(0f, 0f, 0L)
        filter.update(0f, 10f, 100L) // Slow scroll
        
        assertFalse(filter.isFastScrolling(500f))
    }
    
    @Test
    fun `isScrollingDown detects downward movement`() {
        filter.update(0f, 0f, 0L)
        filter.update(0f, 100f, 50L)
        
        assertTrue(filter.isScrollingDown())
        assertFalse(filter.isScrollingUp())
    }
    
    @Test
    fun `isScrollingUp detects upward movement`() {
        filter.update(0f, 100f, 0L)
        filter.update(0f, 0f, 50L)
        
        assertTrue(filter.isScrollingUp())
        assertFalse(filter.isScrollingDown())
    }
    
    // ========================================================================
    // Y-ONLY UPDATE TESTS
    // ========================================================================
    
    @Test
    fun `updateY only affects Y coordinate`() {
        filter.update(100f, 200f, 0L)
        filter.updateY(250f, 50L)
        
        val (x, _) = filter.getPosition()
        assertEquals(100f, x, 1f)
    }
    
    // ========================================================================
    // FUTURE PREDICTION TESTS
    // ========================================================================
    
    @Test
    fun `getPredictedPosition extrapolates correctly`() {
        filter.update(0f, 0f, 0L)
        filter.update(0f, 100f, 100L)
        
        val (_, y) = filter.getPredictedPosition(100L) // 100ms in future
        
        // Should be further ahead than current
        assertTrue("Prediction should be ahead", y > 100f)
    }
    
    // ========================================================================
    // CONFIGURATION TESTS
    // ========================================================================
    
    @Test
    fun `configuration affects behavior`() {
        val smoothFilter = KalmanFilter2D.create(
            measurementNoise = 10f, // Higher = smoother
            predictionTimeMs = 20L
        )
        
        val responsiveFilter = KalmanFilter2D.create(
            measurementNoise = 0.5f, // Lower = more responsive
            predictionTimeMs = 20L
        )
        
        // Both initialized
        smoothFilter.update(0f, 0f, 0L)
        responsiveFilter.update(0f, 0f, 0L)
        
        // Same jump
        smoothFilter.update(0f, 100f, 50L)
        responsiveFilter.update(0f, 100f, 50L)
        
        // Responsive should react faster
        val smooth = smoothFilter.getPosition().second
        val responsive = responsiveFilter.getPosition().second
        
        assertTrue("Responsive filter should track faster", responsive > smooth)
    }
    
    // ========================================================================
    // POOL TESTS
    // ========================================================================
    
    @Test
    fun `pool acquires and releases correctly`() {
        val filter1 = KalmanFilter2DPool.acquire()
        val filter2 = KalmanFilter2DPool.acquire()
        
        assertNotSame("Should return different instances", filter1, filter2)
        
        KalmanFilter2DPool.release(filter1)
        KalmanFilter2DPool.release(filter2)
    }
    
    @Test
    fun `pool resets filter on release`() {
        val filter = KalmanFilter2DPool.acquire()
        filter.update(100f, 200f, 1000L)
        
        KalmanFilter2DPool.release(filter)
        
        // After release, filter should be reset
        val pos = filter.getPosition()
        assertEquals(0f, pos.first, 0.001f)
        assertEquals(0f, pos.second, 0.001f)
    }
    
    @Test
    fun `pool use block acquires and releases`() {
        var acquired: KalmanFilter2D? = null
        
        KalmanFilter2DPool.use { filter ->
            acquired = filter
            filter.update(100f, 200f, 0L)
        }
        
        // Filter should be reset after use
        assertEquals(0f, acquired?.getPosition()?.first ?: -1f, 0.001f)
    }
}
