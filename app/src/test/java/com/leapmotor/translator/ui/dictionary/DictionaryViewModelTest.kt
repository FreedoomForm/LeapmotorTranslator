package com.leapmotor.translator.ui.dictionary

import app.cash.turbine.test
import com.leapmotor.translator.data.local.dao.DictionaryDao
import com.leapmotor.translator.data.local.entity.DictionaryEntryEntity
import com.leapmotor.translator.domain.usecase.ManageUserDictionaryUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for DictionaryViewModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DictionaryViewModelTest {
    
    private val testDispatcher = StandardTestDispatcher()
    
    private lateinit var viewModel: DictionaryViewModel
    private lateinit var dictionaryDao: DictionaryDao
    private lateinit var manageUserDictionaryUseCase: ManageUserDictionaryUseCase
    
    private val testEntries = listOf(
        DictionaryEntryEntity(id = 1, originalText = "导航", translatedText = "Навигация", isUserDefined = true),
        DictionaryEntryEntity(id = 2, originalText = "空调", translatedText = "Климат", isUserDefined = false),
        DictionaryEntryEntity(id = 3, originalText = "音乐", translatedText = "Музыка", isUserDefined = true)
    )
    
    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        dictionaryDao = mockk(relaxed = true)
        manageUserDictionaryUseCase = mockk(relaxed = true)
        
        every { dictionaryDao.getAllEntries() } returns flowOf(testEntries)
    }
    
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }
    
    private fun createViewModel(): DictionaryViewModel {
        return DictionaryViewModel(
            dictionaryDao = dictionaryDao,
            manageUserDictionaryUseCase = manageUserDictionaryUseCase,
            ioDispatcher = testDispatcher
        )
    }
    
    // ========================================================================
    // LOADING TESTS
    // ========================================================================
    
    @Test
    fun `entries are loaded on init`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        
        val entries = viewModel.entries.value
        assertEquals(3, entries.size)
    }
    
    // ========================================================================
    // SEARCH TESTS
    // ========================================================================
    
    @Test
    fun `setSearchQuery filters entries by original text`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        
        viewModel.setSearchQuery("导航")
        testDispatcher.scheduler.advanceUntilIdle()
        
        val entries = viewModel.entries.value
        assertEquals(1, entries.size)
        assertEquals("导航", entries[0].originalText)
    }
    
    @Test
    fun `setSearchQuery filters entries by translated text`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        
        viewModel.setSearchQuery("Климат")
        testDispatcher.scheduler.advanceUntilIdle()
        
        val entries = viewModel.entries.value
        assertEquals(1, entries.size)
        assertEquals("空调", entries[0].originalText)
    }
    
    @Test
    fun `empty search query shows all entries`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        
        viewModel.setSearchQuery("test")
        testDispatcher.scheduler.advanceUntilIdle()
        
        viewModel.setSearchQuery("")
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertEquals(3, viewModel.entries.value.size)
    }
    
    // ========================================================================
    // FILTER TESTS
    // ========================================================================
    
    @Test
    fun `setFilterMode USER_DEFINED filters correctly`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        
        viewModel.setFilterMode(DictionaryViewModel.FilterMode.USER_DEFINED)
        testDispatcher.scheduler.advanceUntilIdle()
        
        val entries = viewModel.entries.value
        assertEquals(2, entries.size)
        assertTrue(entries.all { it.isUserDefined })
    }
    
    @Test
    fun `setFilterMode CACHED filters correctly`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        
        viewModel.setFilterMode(DictionaryViewModel.FilterMode.CACHED)
        testDispatcher.scheduler.advanceUntilIdle()
        
        val entries = viewModel.entries.value
        assertEquals(1, entries.size)
        assertFalse(entries[0].isUserDefined)
    }
    
    // ========================================================================
    // SAVE TESTS
    // ========================================================================
    
    @Test
    fun `saveEntry with empty original shows error`() = runTest {
        viewModel = createViewModel()
        
        viewModel.events.test {
            viewModel.saveEntry("", "Translation")
            
            val event = awaitItem()
            assertTrue(event is DictionaryViewModel.DictionaryEvent.ShowError)
            
            cancelAndConsumeRemainingEvents()
        }
    }
    
    @Test
    fun `saveEntry with empty translation shows error`() = runTest {
        viewModel = createViewModel()
        
        viewModel.events.test {
            viewModel.saveEntry("Original", "")
            
            val event = awaitItem()
            assertTrue(event is DictionaryViewModel.DictionaryEvent.ShowError)
            
            cancelAndConsumeRemainingEvents()
        }
    }
    
    @Test
    fun `saveEntry with valid data saves and shows success`() = runTest {
        viewModel = createViewModel()
        
        viewModel.events.test {
            viewModel.saveEntry("新词", "Новое слово")
            testDispatcher.scheduler.advanceUntilIdle()
            
            // Should have success and dismiss events
            val event1 = awaitItem()
            assertTrue(event1 is DictionaryViewModel.DictionaryEvent.ShowSuccess)
            
            val event2 = awaitItem()
            assertEquals(DictionaryViewModel.DictionaryEvent.DismissDialog, event2)
            
            cancelAndConsumeRemainingEvents()
        }
        
        // Verify DAO was called
        coVerify { dictionaryDao.insertOrUpdateEntry("新词", "Новое слово", true) }
    }
    
    // ========================================================================
    // DELETE TESTS
    // ========================================================================
    
    @Test
    fun `deleteEntry calls dao and shows success`() = runTest {
        val entry = testEntries[0]
        viewModel = createViewModel()
        
        viewModel.events.test {
            viewModel.deleteEntry(entry)
            testDispatcher.scheduler.advanceUntilIdle()
            
            val event = awaitItem()
            assertTrue(event is DictionaryViewModel.DictionaryEvent.ShowSuccess)
            assertTrue((event as DictionaryViewModel.DictionaryEvent.ShowSuccess).message.contains("Удалено"))
            
            cancelAndConsumeRemainingEvents()
        }
        
        coVerify { dictionaryDao.deleteEntry(entry) }
    }
    
    // ========================================================================
    // EXPORT TESTS
    // ========================================================================
    
    @Test
    fun `exportDictionary returns JSON`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        
        val json = viewModel.exportDictionary()
        
        assertTrue(json.contains("entries"))
        assertTrue(json.contains("导航"))
        assertTrue(json.contains("Навигация"))
    }
}
