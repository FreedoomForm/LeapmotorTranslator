package com.leapmotor.translator.core

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for the Result sealed class.
 * Demonstrates proper testing practices.
 */
class ResultTest {
    
    // ========================================================================
    // SUCCESS TESTS
    // ========================================================================
    
    @Test
    fun `success result contains data`() {
        val result = Result.success("test data")
        
        assertTrue(result.isSuccess)
        assertFalse(result.isError)
        assertFalse(result.isLoading)
        assertEquals("test data", result.getOrNull())
    }
    
    @Test
    fun `success result getOrThrow returns data`() {
        val result = Result.success(42)
        
        assertEquals(42, result.getOrThrow())
    }
    
    @Test
    fun `success result getOrDefault returns data not default`() {
        val result = Result.success("actual")
        
        assertEquals("actual", result.getOrDefault("default"))
    }
    
    // ========================================================================
    // ERROR TESTS
    // ========================================================================
    
    @Test
    fun `error result contains exception`() {
        val exception = RuntimeException("Test error")
        val result = Result.error(exception)
        
        assertTrue(result.isError)
        assertFalse(result.isSuccess)
        assertNull(result.getOrNull())
        assertEquals(exception, result.exceptionOrNull())
    }
    
    @Test(expected = RuntimeException::class)
    fun `error result getOrThrow throws exception`() {
        val result = Result.error(RuntimeException("Test"))
        result.getOrThrow()
    }
    
    @Test
    fun `error result getOrDefault returns default`() {
        val result: Result<String> = Result.error("Error occurred")
        
        assertEquals("default", result.getOrDefault("default"))
    }
    
    @Test
    fun `error from message creates exception`() {
        val result = Result.error("Something went wrong")
        
        assertTrue(result.isError)
        assertEquals("Something went wrong", (result as Result.Error).message)
    }
    
    // ========================================================================
    // LOADING TESTS
    // ========================================================================
    
    @Test
    fun `loading result has correct state`() {
        val result: Result<String> = Result.loading()
        
        assertTrue(result.isLoading)
        assertFalse(result.isSuccess)
        assertFalse(result.isError)
        assertNull(result.getOrNull())
    }
    
    @Test(expected = IllegalStateException::class)
    fun `loading result getOrThrow throws`() {
        val result: Result<String> = Result.loading()
        result.getOrThrow()
    }
    
    // ========================================================================
    // TRANSFORMATION TESTS
    // ========================================================================
    
    @Test
    fun `map transforms success data`() {
        val result = Result.success(5)
            .map { it * 2 }
        
        assertTrue(result.isSuccess)
        assertEquals(10, result.getOrNull())
    }
    
    @Test
    fun `map preserves error`() {
        val result: Result<Int> = Result.error("Error")
        val mapped = result.map { it * 2 }
        
        assertTrue(mapped.isError)
    }
    
    @Test
    fun `map preserves loading`() {
        val result: Result<Int> = Result.loading()
        val mapped = result.map { it * 2 }
        
        assertTrue(mapped.isLoading)
    }
    
    @Test
    fun `flatMap chains success`() {
        val result = Result.success(5)
            .flatMap { Result.success(it * 2) }
        
        assertTrue(result.isSuccess)
        assertEquals(10, result.getOrNull())
    }
    
    @Test
    fun `flatMap short-circuits on error`() {
        var called = false
        val result: Result<Int> = Result.error("Error")
        val chained = result.flatMap { 
            called = true
            Result.success(it * 2) 
        }
        
        assertTrue(chained.isError)
        assertFalse(called)
    }
    
    // ========================================================================
    // CALLBACK TESTS
    // ========================================================================
    
    @Test
    fun `onSuccess called for success`() {
        var called = false
        var value: String? = null
        
        Result.success("test")
            .onSuccess { 
                called = true
                value = it
            }
        
        assertTrue(called)
        assertEquals("test", value)
    }
    
    @Test
    fun `onSuccess not called for error`() {
        var called = false
        
        Result.error<String>("Error")
            .onSuccess { called = true }
        
        assertFalse(called)
    }
    
    @Test
    fun `onError called for error`() {
        var called = false
        var message: String? = null
        
        Result.error<String>("Test error")
            .onError { 
                called = true
                message = it.message
            }
        
        assertTrue(called)
        assertEquals("Test error", message)
    }
    
    @Test
    fun `onError not called for success`() {
        var called = false
        
        Result.success("test")
            .onError { called = true }
        
        assertFalse(called)
    }
    
    // ========================================================================
    // RECOVERY TESTS
    // ========================================================================
    
    @Test
    fun `recover provides fallback for error`() {
        val result: Result<String> = Result.error("Error")
            .recover { "fallback" }
        
        assertTrue(result.isSuccess)
        assertEquals("fallback", result.getOrNull())
    }
    
    @Test
    fun `recover does not change success`() {
        val result = Result.success("original")
            .recover { "fallback" }
        
        assertEquals("original", result.getOrNull())
    }
    
    @Test
    fun `recoverWith chains recovery`() {
        val result: Result<String> = Result.error("Error")
            .recoverWith { Result.success("recovered") }
        
        assertTrue(result.isSuccess)
        assertEquals("recovered", result.getOrNull())
    }
    
    // ========================================================================
    // RUN CATCHING TESTS
    // ========================================================================
    
    @Test
    fun `runCatching wraps success`() {
        val result = Result.runCatching {
            "computed value"
        }
        
        assertTrue(result.isSuccess)
        assertEquals("computed value", result.getOrNull())
    }
    
    @Test
    fun `runCatching wraps exception`() {
        val result = Result.runCatching {
            throw IllegalArgumentException("Test")
        }
        
        assertTrue(result.isError)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }
    
    // ========================================================================
    // EXTENSION TESTS
    // ========================================================================
    
    @Test
    fun `toResult converts non-null to success`() {
        val value: String? = "test"
        val result = value.toResult()
        
        assertTrue(result.isSuccess)
        assertEquals("test", result.getOrNull())
    }
    
    @Test
    fun `toResult converts null to error`() {
        val value: String? = null
        val result = value.toResult("Custom error")
        
        assertTrue(result.isError)
        assertEquals("Custom error", (result as Result.Error).message)
    }
    
    @Test
    fun `zip combines two success results`() {
        val a = Result.success(1)
        val b = Result.success("two")
        
        val combined = a.zip(b)
        
        assertTrue(combined.isSuccess)
        assertEquals(Pair(1, "two"), combined.getOrNull())
    }
    
    @Test
    fun `zip fails if first is error`() {
        val a: Result<Int> = Result.error("Error A")
        val b = Result.success("two")
        
        val combined = a.zip(b)
        
        assertTrue(combined.isError)
    }
    
    @Test
    fun `zip fails if second is error`() {
        val a = Result.success(1)
        val b: Result<String> = Result.error("Error B")
        
        val combined = a.zip(b)
        
        assertTrue(combined.isError)
    }
}
