package com.leapmotor.translator.di

import android.content.Context
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.leapmotor.translator.data.local.dao.DictionaryDao
import com.leapmotor.translator.data.repository.TranslationRepositoryImpl
import com.leapmotor.translator.domain.repository.TranslationRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for translation dependencies.
 * 
 * Provides ML Kit translator and repository.
 */
@Module
@InstallIn(SingletonComponent::class)
object TranslationModule {
    
    @Provides
    @Singleton
    fun provideTranslationRepository(
        @ApplicationContext context: Context,
        dictionaryDao: DictionaryDao
    ): TranslationRepository {
        return TranslationRepositoryImpl(context, dictionaryDao)
    }
}
