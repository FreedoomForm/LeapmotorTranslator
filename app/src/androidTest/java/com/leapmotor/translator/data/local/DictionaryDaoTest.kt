package com.leapmotor.translator.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.leapmotor.translator.data.local.dao.DictionaryDao
import com.leapmotor.translator.data.local.entity.DictionaryEntryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Instrumented tests for Room database operations.
 * 
 * Tests dictionary CRUD operations using an in-memory database.
 */
@RunWith(AndroidJUnit4::class)
class DictionaryDaoTest {
    
    private lateinit var dictionaryDao: DictionaryDao
    private lateinit var database: TranslatorDatabase
    
    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            TranslatorDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
        dictionaryDao = database.dictionaryDao()
    }
    
    @After
    @Throws(IOException::class)
    fun closeDb() {
        database.close()
    }
    
    // ========================================================================
    // INSERT TESTS
    // ========================================================================
    
    @Test
    fun insertEntry_andReadBack() = runBlocking {
        val entry = DictionaryEntryEntity(
            originalText = "你好",
            translatedText = "Привет",
            isUserDefined = true
        )
        
        dictionaryDao.insertEntry(entry)
        
        val retrieved = dictionaryDao.getEntryByText("你好")
        
        assertNotNull(retrieved)
        assertEquals("Привет", retrieved?.translatedText)
        assertTrue(retrieved?.isUserDefined == true)
    }
    
    @Test
    fun insertMultipleEntries_returnsAll() = runBlocking {
        val entries = listOf(
            DictionaryEntryEntity(originalText = "导航", translatedText = "Навигация", isUserDefined = false),
            DictionaryEntryEntity(originalText = "空调", translatedText = "Климат", isUserDefined = true),
            DictionaryEntryEntity(originalText = "音乐", translatedText = "Музыка", isUserDefined = false)
        )
        
        dictionaryDao.insertEntries(entries)
        
        val allEntries = dictionaryDao.getAllEntries().first()
        
        assertEquals(3, allEntries.size)
    }
    
    @Test
    fun insertOrUpdateEntry_updatesExisting() = runBlocking {
        // Insert initial entry
        dictionaryDao.insertOrUpdateEntry("测试", "Тест 1", false)
        
        // Update same entry
        dictionaryDao.insertOrUpdateEntry("测试", "Тест 2", false)
        
        val entry = dictionaryDao.getEntryByText("测试")
        
        assertEquals("Тест 2", entry?.translatedText)
    }
    
    // ========================================================================
    // QUERY TESTS
    // ========================================================================
    
    @Test
    fun getUserDefinedEntries_filtersCorrectly() = runBlocking {
        val entries = listOf(
            DictionaryEntryEntity(originalText = "用户1", translatedText = "User 1", isUserDefined = true),
            DictionaryEntryEntity(originalText = "缓存1", translatedText = "Cache 1", isUserDefined = false),
            DictionaryEntryEntity(originalText = "用户2", translatedText = "User 2", isUserDefined = true)
        )
        
        dictionaryDao.insertEntries(entries)
        
        val userEntries = dictionaryDao.getUserDefinedEntries().first()
        
        assertEquals(2, userEntries.size)
        assertTrue(userEntries.all { it.isUserDefined })
    }
    
    @Test
    fun getCachedEntries_filtersCorrectly() = runBlocking {
        val entries = listOf(
            DictionaryEntryEntity(originalText = "用户1", translatedText = "User 1", isUserDefined = true),
            DictionaryEntryEntity(originalText = "缓存1", translatedText = "Cache 1", isUserDefined = false),
            DictionaryEntryEntity(originalText = "缓存2", translatedText = "Cache 2", isUserDefined = false)
        )
        
        dictionaryDao.insertEntries(entries)
        
        val cachedEntries = dictionaryDao.getCachedEntries().first()
        
        assertEquals(2, cachedEntries.size)
        assertTrue(cachedEntries.none { it.isUserDefined })
    }
    
    @Test
    fun searchEntries_findsByOriginalText() = runBlocking {
        val entries = listOf(
            DictionaryEntryEntity(originalText = "导航系统", translatedText = "Система навигации"),
            DictionaryEntryEntity(originalText = "导航设置", translatedText = "Настройки навигации"),
            DictionaryEntryEntity(originalText = "空调控制", translatedText = "Управление климатом")
        )
        
        dictionaryDao.insertEntries(entries)
        
        val results = dictionaryDao.searchEntries("导航")
        
        assertEquals(2, results.size)
    }
    
    @Test
    fun searchEntries_findsByTranslatedText() = runBlocking {
        val entries = listOf(
            DictionaryEntryEntity(originalText = "导航", translatedText = "Навигация"),
            DictionaryEntryEntity(originalText = "空调", translatedText = "Климат"),
            DictionaryEntryEntity(originalText = "温度", translatedText = "Температура")
        )
        
        dictionaryDao.insertEntries(entries)
        
        val results = dictionaryDao.searchEntries("Клим")
        
        assertEquals(1, results.size)
        assertEquals("空调", results[0].originalText)
    }
    
    // ========================================================================
    // UPDATE TESTS
    // ========================================================================
    
    @Test
    fun incrementUsageCount_updatesCount() = runBlocking {
        val entry = DictionaryEntryEntity(originalText = "测试", translatedText = "Тест", usageCount = 0)
        dictionaryDao.insertEntry(entry)
        
        dictionaryDao.incrementUsageCount("测试")
        dictionaryDao.incrementUsageCount("测试")
        dictionaryDao.incrementUsageCount("测试")
        
        val updated = dictionaryDao.getEntryByText("测试")
        
        assertEquals(3, updated?.usageCount)
    }
    
    @Test
    fun updateTranslation_changesTranslatedText() = runBlocking {
        dictionaryDao.insertEntry(
            DictionaryEntryEntity(originalText = "原文", translatedText = "Старый перевод")
        )
        
        dictionaryDao.updateTranslation("原文", "Новый перевод")
        
        val updated = dictionaryDao.getEntryByText("原文")
        
        assertEquals("Новый перевод", updated?.translatedText)
    }
    
    // ========================================================================
    // DELETE TESTS
    // ========================================================================
    
    @Test
    fun deleteEntry_removesEntry() = runBlocking {
        val entry = DictionaryEntryEntity(originalText = "删除", translatedText = "Удалить")
        dictionaryDao.insertEntry(entry)
        
        dictionaryDao.deleteEntryByText("删除")
        
        val result = dictionaryDao.getEntryByText("删除")
        
        assertNull(result)
    }
    
    @Test
    fun clearCache_keepsUserDefined() = runBlocking {
        val entries = listOf(
            DictionaryEntryEntity(originalText = "用户", translatedText = "User", isUserDefined = true),
            DictionaryEntryEntity(originalText = "缓存1", translatedText = "Cache 1", isUserDefined = false),
            DictionaryEntryEntity(originalText = "缓存2", translatedText = "Cache 2", isUserDefined = false)
        )
        
        dictionaryDao.insertEntries(entries)
        
        dictionaryDao.clearCache()
        
        val remaining = dictionaryDao.getAllEntries().first()
        
        assertEquals(1, remaining.size)
        assertEquals("用户", remaining[0].originalText)
    }
    
    @Test
    fun deleteAll_removesEverything() = runBlocking {
        val entries = listOf(
            DictionaryEntryEntity(originalText = "1", translatedText = "One", isUserDefined = true),
            DictionaryEntryEntity(originalText = "2", translatedText = "Two", isUserDefined = false)
        )
        
        dictionaryDao.insertEntries(entries)
        dictionaryDao.deleteAll()
        
        val count = dictionaryDao.getEntryCount()
        
        assertEquals(0, count)
    }
    
    // ========================================================================
    // STATISTICS TESTS
    // ========================================================================
    
    @Test
    fun getEntryCount_returnsCorrectCount() = runBlocking {
        val entries = listOf(
            DictionaryEntryEntity(originalText = "1", translatedText = "One"),
            DictionaryEntryEntity(originalText = "2", translatedText = "Two"),
            DictionaryEntryEntity(originalText = "3", translatedText = "Three")
        )
        
        dictionaryDao.insertEntries(entries)
        
        val count = dictionaryDao.getEntryCount()
        
        assertEquals(3, count)
    }
    
    @Test
    fun getUserDefinedCount_returnsCorrectCount() = runBlocking {
        val entries = listOf(
            DictionaryEntryEntity(originalText = "1", translatedText = "One", isUserDefined = true),
            DictionaryEntryEntity(originalText = "2", translatedText = "Two", isUserDefined = false),
            DictionaryEntryEntity(originalText = "3", translatedText = "Three", isUserDefined = true)
        )
        
        dictionaryDao.insertEntries(entries)
        
        val count = dictionaryDao.getUserDefinedCount()
        
        assertEquals(2, count)
    }
}
