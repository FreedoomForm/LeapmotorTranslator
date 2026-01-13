package com.leapmotor.translator.ui.base

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Base Activity with common utilities for all activities.
 * 
 * Provides:
 * - Flow collection helpers
 * - Toast utilities
 * - Common lifecycle patterns
 */
abstract class BaseActivity : AppCompatActivity() {
    
    /**
     * Collect a Flow when the activity is in STARTED state.
     * Automatically pauses collection when activity is stopped.
     */
    protected fun <T> Flow<T>.collectWhenStarted(
        action: suspend (T) -> Unit
    ) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                collect { action(it) }
            }
        }
    }
    
    /**
     * Collect a Flow with collectLatest when the activity is in STARTED state.
     */
    protected fun <T> Flow<T>.collectLatestWhenStarted(
        action: suspend (T) -> Unit
    ) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                collectLatest { action(it) }
            }
        }
    }
    
    /**
     * Show a short toast message.
     */
    protected fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    /**
     * Show a long toast message.
     */
    protected fun showLongToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    /**
     * Safe back navigation.
     */
    protected fun navigateBack() {
        if (!isFinishing) {
            onBackPressedDispatcher.onBackPressed()
        }
    }
}

/**
 * Extension function to collect Flow with lifecycle awareness.
 */
fun <T> Flow<T>.collectWithLifecycle(
    lifecycleOwner: LifecycleOwner,
    state: Lifecycle.State = Lifecycle.State.STARTED,
    action: suspend (T) -> Unit
) {
    lifecycleOwner.lifecycleScope.launch {
        lifecycleOwner.repeatOnLifecycle(state) {
            collect { action(it) }
        }
    }
}

/**
 * Extension function to collect Flow Latest with lifecycle awareness.
 */
fun <T> Flow<T>.collectLatestWithLifecycle(
    lifecycleOwner: LifecycleOwner,
    state: Lifecycle.State = Lifecycle.State.STARTED,
    action: suspend (T) -> Unit
) {
    lifecycleOwner.lifecycleScope.launch {
        lifecycleOwner.repeatOnLifecycle(state) {
            collectLatest { action(it) }
        }
    }
}
