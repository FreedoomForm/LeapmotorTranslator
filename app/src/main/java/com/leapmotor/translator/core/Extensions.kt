package com.leapmotor.translator.core

import android.content.Context
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ============================================================================
// STRING EXTENSIONS
// ============================================================================

/**
 * Check if string contains Chinese characters.
 */
fun String.containsChinese(): Boolean = any { char ->
    val code = char.code
    code in 0x4E00..0x9FFF ||  // CJK Unified Ideographs
    code in 0x3400..0x4DBF ||  // CJK Extension A
    code in 0x3000..0x303F     // CJK Punctuation
}

/**
 * Truncate string with ellipsis if exceeds max length.
 */
fun String.truncate(maxLength: Int, ellipsis: String = "…"): String {
    return if (length <= maxLength) this
    else take(maxLength - ellipsis.length) + ellipsis
}

/**
 * Capitalize first letter only (for Russian text).
 */
fun String.capitalizeFirst(): String {
    return if (isEmpty()) this
    else this[0].uppercaseChar() + substring(1)
}

// ============================================================================
// NUMBER EXTENSIONS
// ============================================================================

/**
 * Clamp float to range.
 */
fun Float.clamp(min: Float, max: Float): Float = coerceIn(min, max)

/**
 * Linear interpolation.
 */
fun Float.lerp(target: Float, fraction: Float): Float {
    return this + (target - this) * fraction.coerceIn(0f, 1f)
}

/**
 * Map value from one range to another.
 */
fun Float.mapRange(
    fromMin: Float, fromMax: Float,
    toMin: Float, toMax: Float
): Float {
    if (fromMax - fromMin == 0f) return toMin
    return toMin + (this - fromMin) / (fromMax - fromMin) * (toMax - toMin)
}

/**
 * Format milliseconds as human readable duration.
 */
fun Long.formatDuration(): String = when {
    this < 1000 -> "${this}ms"
    this < 60_000 -> "${this / 1000}s"
    this < 3600_000 -> "${this / 60_000}m ${(this % 60_000) / 1000}s"
    else -> "${this / 3600_000}h ${(this % 3600_000) / 60_000}m"
}

// ============================================================================
// RECTF EXTENSIONS
// ============================================================================

/**
 * Check if RectF is valid (positive dimensions, on-screen).
 */
fun RectF.isValid(screenWidth: Float = 10000f, screenHeight: Float = 10000f): Boolean {
    return width() > 10 && height() > 10 &&
           left >= 0 && top >= 0 &&
           right <= screenWidth && bottom <= screenHeight
}

/**
 * Create a copy with offset Y position.
 */
fun RectF.withOffsetY(offsetY: Float): RectF = RectF(left, offsetY, right, offsetY + height())

/**
 * Add padding to all sides.
 */
fun RectF.withPadding(padding: Float): RectF = RectF(
    left - padding,
    top - padding,
    right + padding,
    bottom + padding
)

/**
 * Scale rect from center.
 */
fun RectF.scale(factor: Float): RectF {
    val cx = centerX()
    val cy = centerY()
    val halfWidth = width() * factor / 2
    val halfHeight = height() * factor / 2
    return RectF(cx - halfWidth, cy - halfHeight, cx + halfWidth, cy + halfHeight)
}

// ============================================================================
// VIEW EXTENSIONS
// ============================================================================

/**
 * Post a delayed action on the main thread.
 */
fun View.postDelayed(delayMs: Long, action: () -> Unit) {
    postDelayed(action, delayMs)
}

/**
 * Set visibility with optional animation.
 */
fun View.setVisibleAnimated(visible: Boolean, duration: Long = 200) {
    if (visible && visibility != View.VISIBLE) {
        alpha = 0f
        visibility = View.VISIBLE
        animate().alpha(1f).setDuration(duration).start()
    } else if (!visible && visibility == View.VISIBLE) {
        animate().alpha(0f).setDuration(duration).withEndAction {
            visibility = View.GONE
        }.start()
    }
}

// ============================================================================
// CONTEXT EXTENSIONS
// ============================================================================

/**
 * Show a toast message.
 */
fun Context.toast(message: String, long: Boolean = false) {
    Toast.makeText(this, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
}

/**
 * Show toast on main thread from any context.
 */
fun Context.toastOnMain(message: String, long: Boolean = false) {
    Handler(Looper.getMainLooper()).post {
        Toast.makeText(this, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }
}

/**
 * Get screen dimensions.
 */
fun Context.getScreenSize(): Pair<Int, Int> {
    val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val metrics = android.util.DisplayMetrics()
    windowManager.defaultDisplay.getRealMetrics(metrics)
    return Pair(metrics.widthPixels, metrics.heightPixels)
}

/**
 * Check if device is a Xiaomi/Redmi device.
 */
fun isXiaomiDevice(): Boolean {
    return Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ||
           Build.MANUFACTURER.equals("Redmi", ignoreCase = true) ||
           Build.MANUFACTURER.equals("Poco", ignoreCase = true)
}

// ============================================================================
// COROUTINE EXTENSIONS
// ============================================================================

/**
 * Launch on main thread.
 */
fun CoroutineScope.launchMain(block: suspend CoroutineScope.() -> Unit) =
    launch(Dispatchers.Main, block = block)

/**
 * Launch on IO thread.
 */
fun CoroutineScope.launchIO(block: suspend CoroutineScope.() -> Unit) =
    launch(Dispatchers.IO, block = block)

/**
 * Retry a suspend operation with exponential backoff.
 */
suspend fun <T> retry(
    times: Int = 3,
    initialDelayMs: Long = 100,
    maxDelayMs: Long = 1000,
    factor: Double = 2.0,
    block: suspend () -> T
): T {
    var currentDelay = initialDelayMs
    repeat(times - 1) {
        try {
            return block()
        } catch (e: Exception) {
            delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelayMs)
        }
    }
    return block() // Last attempt
}

/**
 * Execute on main thread from suspend function.
 */
suspend fun <T> onMain(block: () -> T): T = withContext(Dispatchers.Main) { block() }

/**
 * Execute on IO thread from suspend function.
 */
suspend fun <T> onIO(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

// ============================================================================
// COLLECTION EXTENSIONS
// ============================================================================

/**
 * Returns the element with the maximum value of the given selector, or null if empty.
 * Uses reified type for better performance than inline maxByOrNull.
 */
inline fun <T, R : Comparable<R>> Iterable<T>.maxByOrNullOptimized(selector: (T) -> R): T? {
    val iterator = iterator()
    if (!iterator.hasNext()) return null
    var maxElement = iterator.next()
    var maxValue = selector(maxElement)
    while (iterator.hasNext()) {
        val element = iterator.next()
        val value = selector(element)
        if (value > maxValue) {
            maxElement = element
            maxValue = value
        }
    }
    return maxElement
}

/**
 * Partition a list into chunks for parallel processing.
 */
fun <T> List<T>.partition(partitions: Int): List<List<T>> {
    if (partitions <= 0 || isEmpty()) return listOf(this)
    val chunkSize = (size + partitions - 1) / partitions
    return chunked(chunkSize)
}
