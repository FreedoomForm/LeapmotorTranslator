package com.leapmotor.translator.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Provides centralized Coroutine Dispatchers for the entire app.
 * 
 * Benefits:
 * - Easy to mock for testing
 * - Consistent dispatcher usage across the app
 * - Single place to configure dispatcher behavior
 */
object DispatcherProvider {
    
    /**
     * Main/UI thread dispatcher.
     */
    val Main: CoroutineDispatcher get() = Dispatchers.Main
    
    /**
     * Main immediate dispatcher - skips dispatch if already on main thread.
     */
    val MainImmediate: CoroutineDispatcher get() = Dispatchers.Main.immediate
    
    /**
     * Default dispatcher for CPU-intensive work.
     */
    val Default: CoroutineDispatcher get() = Dispatchers.Default
    
    /**
     * IO dispatcher for IO-bound work (network, disk).
     */
    val IO: CoroutineDispatcher get() = Dispatchers.IO
    
    /**
     * Unconfined dispatcher - starts in caller thread, resumes in suspension thread.
     */
    val Unconfined: CoroutineDispatcher get() = Dispatchers.Unconfined
}

/**
 * Interface for classes that manage a CoroutineScope.
 * Provides consistent lifecycle management across components.
 */
interface ScopedComponent {
    val scope: CoroutineScope
    
    /**
     * Cancel all coroutines in this scope.
     */
    fun cancelScope() {
        scope.cancel()
    }
}

/**
 * Creates a SupervisorJob-backed scope with the given dispatcher.
 * Failed children don't cancel siblings.
 */
fun createSupervisedScope(
    dispatcher: CoroutineDispatcher = DispatcherProvider.Default
): CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)

/**
 * Creates a scope that cancels all children on first failure.
 */
fun createScope(
    dispatcher: CoroutineDispatcher = DispatcherProvider.Default
): CoroutineScope = CoroutineScope(Job() + dispatcher)

/**
 * Application-level scope that lives for the entire app lifecycle.
 * Use sparingly - prefer component-scoped coroutines when possible.
 */
object AppScope : ScopedComponent {
    override val scope: CoroutineScope = createSupervisedScope(DispatcherProvider.Default)
}
