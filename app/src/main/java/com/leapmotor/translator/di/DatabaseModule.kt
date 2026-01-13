package com.leapmotor.translator.di

import android.content.Context
import com.leapmotor.translator.data.local.TranslatorDatabase
import com.leapmotor.translator.data.local.dao.DictionaryDao
import com.leapmotor.translator.data.local.dao.TranslationHistoryDao
import com.leapmotor.translator.data.local.dao.TranslationStatsDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for database dependencies.
 * 
 * Provides Room database and DAOs as singletons.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): TranslatorDatabase {
        return TranslatorDatabase.create(context)
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
