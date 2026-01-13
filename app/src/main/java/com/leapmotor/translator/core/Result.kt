package com.leapmotor.translator.core

/**
 * A sealed class representing the result of an operation.
 * Implements functional error handling pattern used in modern Android development.
 * 
 * Benefits:
 * - Type-safe error handling
 * - Forces explicit error handling at call sites
 * - Composable with map/flatMap operations
 * - No exceptions for expected failures
 *
 * @param T The type of successful result
 */
sealed class Result<out T> {
    
    /**
     * Represents a successful operation with data.
     */
    data class Success<out T>(val data: T) : Result<T>()
    
    /**
     * Represents a failed operation with error information.
     */
    data class Error(
        val exception: Throwable,
        val message: String = exception.message ?: "Unknown error"
    ) : Result<Nothing>()
    
    /**
     * Represents an operation in progress.
     */
    object Loading : Result<Nothing>()
    
    // ============================================================================
    // FUNCTIONAL OPERATORS
    // ============================================================================
    
    /**
     * Returns true if this is a Success.
     */
    val isSuccess: Boolean get() = this is Success
    
    /**
     * Returns true if this is an Error.
     */
    val isError: Boolean get() = this is Error
    
    /**
     * Returns true if this is Loading.
     */
    val isLoading: Boolean get() = this is Loading
    
    /**
     * Returns the data if Success, null otherwise.
     */
    fun getOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }
    
    /**
     * Returns the data if Success, or the default value otherwise.
     */
    fun getOrDefault(default: @UnsafeVariance T): T = when (this) {
        is Success -> data
        else -> default
    }
    
    /**
     * Returns the data if Success, or throws the exception if Error.
     */
    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw exception
        is Loading -> throw IllegalStateException("Result is still loading")
    }
    
    /**
     * Returns the exception if Error, null otherwise.
     */
    fun exceptionOrNull(): Throwable? = when (this) {
        is Error -> exception
        else -> null
    }
    
    /**
     * Transforms the success value using the given function.
     */
    inline fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
        is Loading -> Loading
    }
    
    /**
     * Transforms the success value using a function that returns a Result.
     */
    inline fun <R> flatMap(transform: (T) -> Result<R>): Result<R> = when (this) {
        is Success -> transform(data)
        is Error -> this
        is Loading -> Loading
    }
    
    /**
     * Executes the given block if this is Success.
     */
    inline fun onSuccess(block: (T) -> Unit): Result<T> {
        if (this is Success) block(data)
        return this
    }
    
    /**
     * Executes the given block if this is Error.
     */
    inline fun onError(block: (Error) -> Unit): Result<T> {
        if (this is Error) block(this)
        return this
    }
    
    /**
     * Executes the given block if this is Loading.
     */
    inline fun onLoading(block: () -> Unit): Result<T> {
        if (this is Loading) block()
        return this
    }
    
    /**
     * Recovers from an error by providing an alternative value.
     */
    inline fun recover(transform: (Error) -> @UnsafeVariance T): Result<T> = when (this) {
        is Success -> this
        is Error -> Success(transform(this))
        is Loading -> this
    }
    
    /**
     * Recovers from an error by providing an alternative Result.
     */
    inline fun recoverWith(transform: (Error) -> Result<@UnsafeVariance T>): Result<T> = when (this) {
        is Success -> this
        is Error -> transform(this)
        is Loading -> this
    }
    
    companion object {
        /**
         * Creates a Success result.
         */
        fun <T> success(data: T): Result<T> = Success(data)
        
        /**
         * Creates an Error result.
         */
        fun error(exception: Throwable, message: String? = null): Result<Nothing> = 
            Error(exception, message ?: exception.message ?: "Unknown error")
        
        /**
         * Creates an Error result from a message.
         */
        fun error(message: String): Result<Nothing> = 
            Error(Exception(message), message)
        
        /**
         * Creates a Loading result.
         */
        fun loading(): Result<Nothing> = Loading
        
        /**
         * Safely executes a block and wraps the result.
         */
        inline fun <T> runCatching(block: () -> T): Result<T> = try {
            Success(block())
        } catch (e: Exception) {
            Error(e)
        }
        
        /**
         * Safely executes a suspend block and wraps the result.
         */
        suspend inline fun <T> runCatchingSuspend(crossinline block: suspend () -> T): Result<T> = try {
            Success(block())
        } catch (e: Exception) {
            Error(e)
        }
    }
}

/**
 * Extension to convert nullable value to Result.
 */
fun <T : Any> T?.toResult(errorMessage: String = "Value is null"): Result<T> =
    this?.let { Result.Success(it) } ?: Result.Error(NullPointerException(errorMessage), errorMessage)

/**
 * Combines two Results into a Pair.
 */
fun <A, B> Result<A>.zip(other: Result<B>): Result<Pair<A, B>> = when {
    this is Result.Success && other is Result.Success -> Result.Success(this.data to other.data)
    this is Result.Error -> this
    other is Result.Error -> other
    else -> Result.Loading
}

/**
 * Combines three Results into a Triple.
 */
fun <A, B, C> Result<A>.zip(
    second: Result<B>,
    third: Result<C>
): Result<Triple<A, B, C>> = when {
    this is Result.Success && second is Result.Success && third is Result.Success -> 
        Result.Success(Triple(this.data, second.data, third.data))
    this is Result.Error -> this
    second is Result.Error -> second
    third is Result.Error -> third
    else -> Result.Loading
}
