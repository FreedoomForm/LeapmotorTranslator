package com.leapmotor.translator.di

import android.content.Context
import androidx.room.Room
import com.leapmotor.translator.data.local.TranslatorDatabase
import com.leapmotor.translator.data.local.dao.DictionaryDao
import com.leapmotor.translator.data.local.dao.TranslationHistoryDao
import com.leapmotor.translator.data.local.dao.TranslationStatsDao
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/**
 * Test module that provides an in-memory database for testing.
 * Replaces the production DatabaseModule.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DatabaseModule::class]
)
object TestDatabaseModule {
    
    @Provides
    @Singleton
    fun provideTestDatabase(
        @ApplicationContext context: Context
    ): TranslatorDatabase {
        return Room.inMemoryDatabaseBuilder(
            context,
            TranslatorDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
    }
    
    @Provides
    @Singleton
    fun provideDictionaryDao(database: TranslatorDatabase): DictionaryDao {
        return database.dictionaryDao()
    }
    
    @Provides
    @Singleton
    fun provideTranslationHistoryDao(database: TranslatorDatabase): TranslationHistoryDao {
        return database.translationHistoryDao()
    }
    
    @Provides
    @Singleton
    fun provideTranslationStatsDao(database: TranslatorDatabase): TranslationStatsDao {
        return database.translationStatsDao()
    }
}
