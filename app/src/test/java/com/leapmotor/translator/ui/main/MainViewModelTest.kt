package com.leapmotor.translator.ui.main

import app.cash.turbine.test
import com.leapmotor.translator.core.Result
import com.leapmotor.translator.domain.repository.TranslationRepository
import com.leapmotor.translator.domain.usecase.InitializeTranslationUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for MainViewModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    
    private val testDispatcher = StandardTestDispatcher()
    
    private lateinit var viewModel: MainViewModel
    private lateinit var initializeTranslationUseCase: InitializeTranslationUseCase
    private lateinit var translationRepository: TranslationRepository
    
    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        // Mock dependencies
        initializeTranslationUseCase = mockk()
        translationRepository = mockk()
        
        // Setup default mock behavior
        every { translationRepository.modelState } returns MutableStateFlow(
            TranslationRepository.ModelState.NotInitialized
        )
        every { translationRepository.getCacheStats() } returns TranslationRepository.CacheStats(
            size = 100,
            hits = 50,
            misses = 10,
            hitRate = 0.83f,
            userDictionarySize = 20
        )
    }
    
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }
    
    private fun createViewModel(): MainViewModel {
        return MainViewModel(
            initializeTranslationUseCase = initializeTranslationUseCase,
            translationRepository = translationRepository,
            ioDispatcher = testDispatcher
        )
    }
    
    // ========================================================================
    // INITIALIZATION TESTS
    // ========================================================================
    
    @Test
    fun `initial state is not initialized`() = runTest {
        viewModel = createViewModel()
        
        assertEquals(
            MainViewModel.ModelStatus.NotInitialized,
            viewModel.modelState.value
        )
    }
    
    @Test
    fun `initializeTranslation updates state to ready on success`() = runTest {
        coEvery { initializeTranslationUseCase() } returns Result.success(Unit)
        
        viewModel = createViewModel()
        viewModel.initializeTranslation()
        
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertEquals(
            MainViewModel.ModelStatus.Ready,
            viewModel.modelState.value
        )
    }
    
    @Test
    fun `initializeTranslation updates state to error on failure`() = runTest {
        coEvery { initializeTranslationUseCase() } returns Result.error("Download failed")
        
        viewModel = createViewModel()
        viewModel.initializeTranslation()
        
        testDispatcher.scheduler.advanceUntilIdle()
        
        val state = viewModel.modelState.value
        assertTrue(state is MainViewModel.ModelStatus.Error)
        assertEquals("Download failed", (state as MainViewModel.ModelStatus.Error).message)
    }
    
    // ========================================================================
    // EVENTS TESTS
    // ========================================================================
    
    @Test
    fun `initializeTranslation emits success toast on ready`() = runTest {
        coEvery { initializeTranslationUseCase() } returns Result.success(Unit)
        
        viewModel = createViewModel()
        
        viewModel.events.test {
            viewModel.initializeTranslation()
            testDispatcher.scheduler.advanceUntilIdle()
            
            val event = awaitItem()
            assertTrue(event is MainViewModel.MainEvent.ShowToast)
            assertTrue((event as MainViewModel.MainEvent.ShowToast).message.contains("готова"))
            
            cancelAndConsumeRemainingEvents()
        }
    }
    
    @Test
    fun `initializeTranslation emits error toast on failure`() = runTest {
        coEvery { initializeTranslationUseCase() } returns Result.error("Network error")
        
        viewModel = createViewModel()
        
        viewModel.events.test {
            viewModel.initializeTranslation()
            testDispatcher.scheduler.advanceUntilIdle()
            
            val event = awaitItem()
            assertTrue(event is MainViewModel.MainEvent.ShowToast)
            assertTrue((event as MainViewModel.MainEvent.ShowToast).message.contains("Ошибка"))
            
            cancelAndConsumeRemainingEvents()
        }
    }
    
    // ========================================================================
    // CACHE TESTS
    // ========================================================================
    
    @Test
    fun `clearCache updates cache stats`() = runTest {
        every { translationRepository.clearCache() } returns Unit
        
        viewModel = createViewModel()
        
        viewModel.events.test {
            viewModel.clearCache()
            testDispatcher.scheduler.advanceUntilIdle()
            
            val event = awaitItem()
            assertTrue(event is MainViewModel.MainEvent.ShowToast)
            assertTrue((event as MainViewModel.MainEvent.ShowToast).message.contains("Кэш"))
            
            cancelAndConsumeRemainingEvents()
        }
    }
    
    @Test
    fun `refresh updates ui state`() = runTest {
        viewModel = createViewModel()
        viewModel.refresh()
        
        testDispatcher.scheduler.advanceUntilIdle()
        
        val stats = viewModel.cacheStats.value
        assertEquals(100, stats.size)
        assertEquals(50, stats.hits)
        assertEquals(0.83f, stats.hitRate, 0.01f)
    }
    
    // ========================================================================
    // DEBUG MODE TESTS
    // ========================================================================
    
    @Test
    fun `toggleDebugMode emits toggle event`() = runTest {
        viewModel = createViewModel()
        
        viewModel.events.test {
            viewModel.toggleDebugMode()
            testDispatcher.scheduler.advanceUntilIdle()
            
            val event = awaitItem()
            assertEquals(MainViewModel.MainEvent.ToggleDebugMode, event)
            
            cancelAndConsumeRemainingEvents()
        }
    }
}
