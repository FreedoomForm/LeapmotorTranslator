package com.leapmotor.translator.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * Represents the state of UI components.
 * Unified state management pattern for all UI screens.
 *
 * This pattern ensures:
 * - Consistent state representation across the app
 * - Easy testing of UI states
 * - Clear separation between data and presentation
 *
 * @param T The type of data in success state
 */
sealed interface UiState<out T> {
    /**
     * Initial state before any data is loaded.
     */
    object Idle : UiState<Nothing>
    
    /**
     * Loading state - operation in progress.
     */
    object Loading : UiState<Nothing>
    
    /**
     * Success state with data.
     */
    data class Success<T>(val data: T) : UiState<T>
    
    /**
     * Error state with error information.
     */
    data class Error(
        val message: String,
        val cause: Throwable? = null,
        val retryAction: (() -> Unit)? = null
    ) : UiState<Nothing>
    
    /**
     * Empty state - operation succeeded but no data available.
     */
    object Empty : UiState<Nothing>
}

// ============================================================================
// EXTENSION FUNCTIONS
// ============================================================================

/**
 * Returns the data if Success, null otherwise.
 */
fun <T> UiState<T>.getDataOrNull(): T? = when (this) {
    is UiState.Success -> data
    else -> null
}

/**
 * Returns true if this is a loading state.
 */
val UiState<*>.isLoading: Boolean get() = this is UiState.Loading

/**
 * Returns true if this is an error state.
 */
val UiState<*>.isError: Boolean get() = this is UiState.Error

/**
 * Returns true if this is a success state.
 */
val UiState<*>.isSuccess: Boolean get() = this is UiState.Success

/**
 * Maps the success data to a new type.
 */
inline fun <T, R> UiState<T>.map(transform: (T) -> R): UiState<R> = when (this) {
    is UiState.Idle -> UiState.Idle
    is UiState.Loading -> UiState.Loading
    is UiState.Success -> UiState.Success(transform(data))
    is UiState.Error -> this
    is UiState.Empty -> UiState.Empty
}

/**
 * Executes block if state is Success.
 */
inline fun <T> UiState<T>.onSuccess(block: (T) -> Unit): UiState<T> {
    if (this is UiState.Success) block(data)
    return this
}

/**
 * Executes block if state is Error.
 */
inline fun <T> UiState<T>.onError(block: (UiState.Error) -> Unit): UiState<T> {
    if (this is UiState.Error) block(this)
    return this
}

/**
 * Executes block if state is Loading.
 */
inline fun <T> UiState<T>.onLoading(block: () -> Unit): UiState<T> {
    if (this is UiState.Loading) block()
    return this
}

// ============================================================================
// FLOW EXTENSIONS
// ============================================================================

/**
 * Converts a Flow<T> to Flow<UiState<T>> with proper error handling.
 */
fun <T> Flow<T>.asUiState(): Flow<UiState<T>> = this
    .map<T, UiState<T>> { UiState.Success(it) }
    .onStart { emit(UiState.Loading) }
    .catch { emit(UiState.Error(it.message ?: "Unknown error", it)) }

/**
 * Converts a Result<T> to UiState<T>.
 */
fun <T> Result<T>.toUiState(): UiState<T> = when (this) {
    is Result.Success -> UiState.Success(data)
    is Result.Error -> UiState.Error(message, exception)
    is Result.Loading -> UiState.Loading
}

/**
 * Converts a UiState<T> to Result<T>.
 */
fun <T> UiState<T>.toResult(): Result<T> = when (this) {
    is UiState.Success -> Result.Success(data)
    is UiState.Error -> Result.Error(cause ?: Exception(message), message)
    is UiState.Loading -> Result.Loading
    else -> Result.Error(IllegalStateException("Invalid state"), "Invalid state")
}
