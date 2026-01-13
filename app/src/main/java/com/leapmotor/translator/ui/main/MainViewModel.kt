package com.leapmotor.translator.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leapmotor.translator.core.Logger
import com.leapmotor.translator.core.UiState
import com.leapmotor.translator.di.IoDispatcher
import com.leapmotor.translator.domain.repository.TranslationRepository
import com.leapmotor.translator.domain.usecase.InitializeTranslationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for MainActivity.
 * 
 * Manages UI state for:
 * - Permission status
 * - Service status
 * - Translation model status
 * - Cache statistics
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val initializeTranslationUseCase: InitializeTranslationUseCase,
    private val translationRepository: TranslationRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {
    
    companion object {
        private const val TAG = "MainViewModel"
    }
    
    // ========================================================================
    // STATE
    // ========================================================================
    
    private val _uiState = MutableStateFlow<UiState<MainUiModel>>(UiState.Idle)
    val uiState: StateFlow<UiState<MainUiModel>> = _uiState.asStateFlow()
    
    private val _modelState = MutableStateFlow<ModelStatus>(ModelStatus.NotInitialized)
    val modelState: StateFlow<ModelStatus> = _modelState.asStateFlow()
    
    private val _cacheStats = MutableStateFlow(CacheStatsModel())
    val cacheStats: StateFlow<CacheStatsModel> = _cacheStats.asStateFlow()
    
    private val _events = MutableSharedFlow<MainEvent>()
    val events: SharedFlow<MainEvent> = _events.asSharedFlow()
    
    // ========================================================================
    // INITIALIZATION
    // ========================================================================
    
    init {
        observeModelState()
    }
    
    private fun observeModelState() {
        viewModelScope.launch {
            translationRepository.modelState.collect { state ->
                _modelState.value = when (state) {
                    is TranslationRepository.ModelState.NotInitialized -> ModelStatus.NotInitialized
                    is TranslationRepository.ModelState.Initializing -> ModelStatus.Initializing
                    is TranslationRepository.ModelState.Downloading -> ModelStatus.Downloading
                    is TranslationRepository.ModelState.Ready -> ModelStatus.Ready
                    is TranslationRepository.ModelState.Error -> ModelStatus.Error(state.message)
                }
            }
        }
    }
    
    // ========================================================================
    // ACTIONS
    // ========================================================================
    
    /**
     * Initialize the translation model.
     */
    fun initializeTranslation() {
        viewModelScope.launch(ioDispatcher) {
            _modelState.value = ModelStatus.Initializing
            
            val result = initializeTranslationUseCase()
            
            result
                .onSuccess {
                    _modelState.value = ModelStatus.Ready
                    _events.emit(MainEvent.ShowToast("Модель перевода готова"))
                    updateCacheStats()
                }
                .onError { error ->
                    _modelState.value = ModelStatus.Error(error.message)
                    _events.emit(MainEvent.ShowToast("Ошибка: ${error.message}"))
                }
        }
    }
    
    /**
     * Refresh UI state.
     */
    fun refresh() {
        viewModelScope.launch {
            updateCacheStats()
            
            _uiState.value = UiState.Success(
                MainUiModel(
                    modelStatus = _modelState.value,
                    cacheStats = _cacheStats.value,
                    isServiceRunning = checkServiceRunning()
                )
            )
        }
    }
    
    /**
     * Update cache statistics.
     */
    private fun updateCacheStats() {
        val stats = translationRepository.getCacheStats()
        _cacheStats.value = CacheStatsModel(
            size = stats.size,
            hits = stats.hits,
            misses = stats.misses,
            hitRate = stats.hitRate,
            userDictionarySize = stats.userDictionarySize
        )
    }
    
    /**
     * Clear translation cache.
     */
    fun clearCache() {
        viewModelScope.launch {
            translationRepository.clearCache()
            updateCacheStats()
            _events.emit(MainEvent.ShowToast("Кэш очищен"))
        }
    }
    
    /**
     * Toggle debug mode.
     */
    fun toggleDebugMode() {
        viewModelScope.launch {
            _events.emit(MainEvent.ToggleDebugMode)
        }
    }
    
    /**
     * Check if the accessibility service is running.
     */
    private fun checkServiceRunning(): Boolean {
        // This will be set from the Activity based on actual service state
        return com.leapmotor.translator.TranslationService.instance != null
    }
    
    // ========================================================================
    // DATA CLASSES
    // ========================================================================
    
    data class MainUiModel(
        val modelStatus: ModelStatus,
        val cacheStats: CacheStatsModel,
        val isServiceRunning: Boolean
    )
    
    data class CacheStatsModel(
        val size: Int = 0,
        val hits: Long = 0,
        val misses: Long = 0,
        val hitRate: Float = 0f,
        val userDictionarySize: Int = 0
    )
    
    sealed class ModelStatus {
        object NotInitialized : ModelStatus()
        object Initializing : ModelStatus()
        object Downloading : ModelStatus()
        object Ready : ModelStatus()
        data class Error(val message: String) : ModelStatus()
    }
    
    sealed class MainEvent {
        data class ShowToast(val message: String) : MainEvent()
        object ToggleDebugMode : MainEvent()
        object NavigateToSettings : MainEvent()
        object NavigateToDictionary : MainEvent()
    }
}
