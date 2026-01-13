package com.leapmotor.translator.core

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Type-safe preferences manager with reactive updates.
 * 
 * Features:
 * - Type-safe preference keys
 * - Flow-based reactive updates
 * - Default value support
 * - Batch editing
 * 
 * Usage:
 * ```kotlin
 * // Define preferences
 * object AppPrefs : PreferencesManager("app_prefs") {
 *     val debugMode = booleanPref("debug_mode", false)
 *     val fontSize = floatPref("font_size", 24f)
 * }
 * 
 * // Initialize in Application
 * AppPrefs.init(context)
 * 
 * // Use preferences
 * AppPrefs.debugMode.value = true
 * val isDebug = AppPrefs.debugMode.value
 * 
 * // Observe changes
 * AppPrefs.debugMode.flow.collect { isDebug -> ... }
 * ```
 */
abstract class PreferencesManager(private val prefsName: String) {
    
    private lateinit var prefs: SharedPreferences
    private val preferenceItems = mutableListOf<PreferenceItem<*>>()
    
    /**
     * Initialize with context. Must be called before using preferences.
     */
    fun init(context: Context) {
        prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        preferenceItems.forEach { it.load() }
    }
    
    /**
     * Clear all preferences.
     */
    fun clear() {
        prefs.edit { clear() }
        preferenceItems.forEach { it.resetToDefault() }
    }
    
    // ============================================================================
    // PREFERENCE ITEM DEFINITIONS
    // ============================================================================
    
    /**
     * Base class for a preference item.
     */
    abstract inner class PreferenceItem<T>(
        protected val key: String,
        protected val defaultValue: T
    ) {
        private val _flow = MutableStateFlow(defaultValue)
        val flow: StateFlow<T> = _flow.asStateFlow()
        
        var value: T
            get() = _flow.value
            set(newValue) {
                _flow.value = newValue
                save(newValue)
            }
        
        init {
            @Suppress("LeakingThis")
            preferenceItems.add(this)
        }
        
        internal fun load() {
            _flow.value = readFromPrefs()
        }
        
        internal fun resetToDefault() {
            _flow.value = defaultValue
        }
        
        protected abstract fun readFromPrefs(): T
        protected abstract fun save(value: T)
    }
    
    // ============================================================================
    // TYPED PREFERENCE FACTORIES
    // ============================================================================
    
    protected fun booleanPref(key: String, defaultValue: Boolean = false) = 
        object : PreferenceItem<Boolean>(key, defaultValue) {
            override fun readFromPrefs() = prefs.getBoolean(key, defaultValue)
            override fun save(value: Boolean) = prefs.edit { putBoolean(key, value) }
        }
    
    protected fun intPref(key: String, defaultValue: Int = 0) = 
        object : PreferenceItem<Int>(key, defaultValue) {
            override fun readFromPrefs() = prefs.getInt(key, defaultValue)
            override fun save(value: Int) = prefs.edit { putInt(key, value) }
        }
    
    protected fun longPref(key: String, defaultValue: Long = 0L) = 
        object : PreferenceItem<Long>(key, defaultValue) {
            override fun readFromPrefs() = prefs.getLong(key, defaultValue)
            override fun save(value: Long) = prefs.edit { putLong(key, value) }
        }
    
    protected fun floatPref(key: String, defaultValue: Float = 0f) = 
        object : PreferenceItem<Float>(key, defaultValue) {
            override fun readFromPrefs() = prefs.getFloat(key, defaultValue)
            override fun save(value: Float) = prefs.edit { putFloat(key, value) }
        }
    
    protected fun stringPref(key: String, defaultValue: String = "") = 
        object : PreferenceItem<String>(key, defaultValue) {
            override fun readFromPrefs() = prefs.getString(key, defaultValue) ?: defaultValue
            override fun save(value: String) = prefs.edit { putString(key, value) }
        }
    
    protected fun stringSetPref(key: String, defaultValue: Set<String> = emptySet()) = 
        object : PreferenceItem<Set<String>>(key, defaultValue) {
            override fun readFromPrefs() = prefs.getStringSet(key, defaultValue) ?: defaultValue
            override fun save(value: Set<String>) = prefs.edit { putStringSet(key, value) }
        }
    
    protected fun nullableStringPref(key: String) = 
        object : PreferenceItem<String?>(key, null) {
            override fun readFromPrefs(): String? = prefs.getString(key, null)
            override fun save(value: String?) = prefs.edit { 
                if (value == null) remove(key) else putString(key, value) 
            }
        }
}

/**
 * App-wide preferences for the translator.
 */
object AppPreferences : PreferencesManager("translator_prefs") {
    
    // Debug settings
    val debugMode = booleanPref("debug_mode", false)
    val showStatusIndicator = booleanPref("show_status", true)
    
    // Translation settings  
    val translationCacheSize = intPref("cache_size", 5000)
    val autoTranslate = booleanPref("auto_translate", true)
    
    // Overlay settings
    val overlayEnabled = booleanPref("overlay_enabled", true)
    val textShadowEnabled = booleanPref("text_shadow", true)
    val fontSize = floatPref("font_size", 24f)
    
    // Performance settings
    val kalmanFilterEnabled = booleanPref("kalman_filter", true)
    val updateDebounceMs = longPref("update_debounce", 50L)
    val maxNodesPerFrame = intPref("max_nodes", 128)
    
    // Statistics (persisted for debugging)
    val totalTranslations = longPref("total_translations", 0L)
    val lastSessionTimestamp = longPref("last_session", 0L)
}
