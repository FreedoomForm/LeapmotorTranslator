package com.leapmotor.translator.core

import java.util.concurrent.atomic.AtomicInteger

/**
 * High-performance object pool for reducing allocation overhead.
 * 
 * Features:
 * - Thread-safe operations using lock-free CAS
 * - Automatic pool expansion (up to max capacity)
 * - Object recycling with reset callbacks
 * - Memory-efficient array-based storage
 * 
 * Usage:
 * ```kotlin
 * val pool = ObjectPool(
 *     maxSize = 64,
 *     factory = { StringBuilder() },
 *     reset = { it.clear() }
 * )
 * 
 * val sb = pool.acquire()
 * try {
 *     sb.append("Hello")
 *     println(sb.toString())
 * } finally {
 *     pool.release(sb)
 * }
 * ```
 *
 * @param T The type of objects in the pool
 * @param maxSize Maximum pool capacity
 * @param factory Creates new instances when pool is empty
 * @param reset Resets objects to clean state before reuse
 */
class ObjectPool<T : Any>(
    private val maxSize: Int,
    private val factory: () -> T,
    private val reset: ((T) -> Unit)? = null
) {
    // Array-based storage with atomic index
    @Suppress("UNCHECKED_CAST")
    private val pool = arrayOfNulls<Any>(maxSize) as Array<T?>
    private val poolSize = AtomicInteger(0)
    
    // Statistics
    private val acquireCount = AtomicInteger(0)
    private val releaseCount = AtomicInteger(0)
    private val createCount = AtomicInteger(0)
    
    /**
     * Acquire an object from the pool or create a new one.
     * 
     * @return Acquired or newly created object
     */
    fun acquire(): T {
        acquireCount.incrementAndGet()
        
        // Try to get from pool
        while (true) {
            val currentSize = poolSize.get()
            if (currentSize == 0) {
                // Pool empty, create new
                createCount.incrementAndGet()
                return factory()
            }
            
            // Try to decrement size and get object
            if (poolSize.compareAndSet(currentSize, currentSize - 1)) {
                val obj = pool[currentSize - 1]
                pool[currentSize - 1] = null
                if (obj != null) {
                    return obj
                }
                // Object was null (race condition), continue
            }
        }
    }
    
    /**
     * Return an object to the pool for reuse.
     * If pool is full, object is discarded.
     * 
     * @param obj Object to return
     * @return true if object was added to pool, false if discarded
     */
    fun release(obj: T): Boolean {
        releaseCount.incrementAndGet()
        reset?.invoke(obj)
        
        // Try to add to pool
        while (true) {
            val currentSize = poolSize.get()
            if (currentSize >= maxSize) {
                // Pool full, discard
                return false
            }
            
            if (poolSize.compareAndSet(currentSize, currentSize + 1)) {
                pool[currentSize] = obj
                return true
            }
        }
    }
    
    /**
     * Prefill the pool with objects.
     * Useful for avoiding allocation spikes at startup.
     * 
     * @param count Number of objects to create
     */
    fun prefill(count: Int = maxSize) {
        val toCreate = minOf(count, maxSize - poolSize.get())
        repeat(toCreate) {
            val obj = factory()
            release(obj)
        }
    }
    
    /**
     * Clear all objects from the pool.
     */
    fun clear() {
        while (true) {
            val currentSize = poolSize.get()
            if (currentSize == 0) return
            
            if (poolSize.compareAndSet(currentSize, 0)) {
                repeat(currentSize) { pool[it] = null }
                return
            }
        }
    }
    
    /**
     * Execute a block with a pooled object, automatically releasing afterward.
     */
    inline fun <R> use(block: (T) -> R): R {
        val obj = acquire()
        return try {
            block(obj)
        } finally {
            release(obj)
        }
    }
    
    /**
     * Current number of objects in the pool.
     */
    val currentSize: Int get() = poolSize.get()
    
    /**
     * Pool statistics.
     */
    val stats: PoolStats get() = PoolStats(
        poolSize = currentSize,
        maxSize = maxSize,
        acquires = acquireCount.get(),
        releases = releaseCount.get(),
        creates = createCount.get(),
        hitRate = if (acquireCount.get() > 0) {
            1f - (createCount.get().toFloat() / acquireCount.get())
        } else 0f
    )
    
    /**
     * Pool statistics data class.
     */
    data class PoolStats(
        val poolSize: Int,
        val maxSize: Int,
        val acquires: Int,
        val releases: Int,
        val creates: Int,
        val hitRate: Float
    )
}

/**
 * Pool of RectF objects for overlay rendering.
 * Pre-configured with appropriate pool size and reset logic.
 */
object RectFPool {
    @PublishedApi
    internal val pool = ObjectPool(
        maxSize = 128,
        factory = { android.graphics.RectF() },
        reset = { it.setEmpty() }
    )
    
    fun acquire(): android.graphics.RectF = pool.acquire()
    fun release(rect: android.graphics.RectF) = pool.release(rect)
    inline fun <R> use(block: (android.graphics.RectF) -> R): R = pool.use(block)
    val stats: ObjectPool.PoolStats get() = pool.stats
}

/**
 * Pool of StringBuilder objects for text operations.
 */
object StringBuilderPool {
    @PublishedApi
    internal val pool = ObjectPool(
        maxSize = 32,
        factory = { StringBuilder(256) },
        reset = { it.clear() }
    )
    
    fun acquire(): StringBuilder = pool.acquire()
    fun release(sb: StringBuilder) = pool.release(sb)
    inline fun <R> use(block: (StringBuilder) -> R): R = pool.use(block)
    val stats: ObjectPool.PoolStats get() = pool.stats
}
