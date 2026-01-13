package com.leapmotor.translator.di

import com.leapmotor.translator.domain.repository.TranslationRepository
import com.leapmotor.translator.domain.usecase.InitializeTranslationUseCase
import com.leapmotor.translator.domain.usecase.ManageUserDictionaryUseCase
import com.leapmotor.translator.domain.usecase.TranslateElementsUseCase
import com.leapmotor.translator.translation.CommonTranslations
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for use cases.
 * 
 * Provides domain layer use cases for business logic.
 */
@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {
    
    @Provides
    @Singleton
    fun provideTranslateElementsUseCase(
        translationRepository: TranslationRepository
    ): TranslateElementsUseCase {
        return TranslateElementsUseCase(translationRepository)
    }
    
    @Provides
    @Singleton
    fun provideInitializeTranslationUseCase(
        translationRepository: TranslationRepository
    ): InitializeTranslationUseCase {
        return InitializeTranslationUseCase(
            translationRepository = translationRepository,
            commonTranslations = CommonTranslations.LEAPMOTOR_UI
        )
    }
    
    @Provides
    @Singleton
    fun provideManageUserDictionaryUseCase(
        translationRepository: TranslationRepository
    ): ManageUserDictionaryUseCase {
        return ManageUserDictionaryUseCase(translationRepository)
    }
}
