package com.leapmotor.translator.ui.dictionary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leapmotor.translator.core.UiState
import com.leapmotor.translator.data.local.dao.DictionaryDao
import com.leapmotor.translator.data.local.entity.DictionaryEntryEntity
import com.leapmotor.translator.di.IoDispatcher
import com.leapmotor.translator.domain.usecase.ManageUserDictionaryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for DictionaryActivity.
 * 
 * Manages dictionary CRUD operations with reactive updates.
 */
@HiltViewModel
class DictionaryViewModel @Inject constructor(
    private val dictionaryDao: DictionaryDao,
    private val manageUserDictionaryUseCase: ManageUserDictionaryUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {
    
    // ========================================================================
    // STATE
    // ========================================================================
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _filterMode = MutableStateFlow(FilterMode.ALL)
    val filterMode: StateFlow<FilterMode> = _filterMode.asStateFlow()
    
    private val _uiState = MutableStateFlow<UiState<DictionaryUiModel>>(UiState.Loading)
    val uiState: StateFlow<UiState<DictionaryUiModel>> = _uiState.asStateFlow()
    
    private val _events = MutableSharedFlow<DictionaryEvent>()
    val events: SharedFlow<DictionaryEvent> = _events.asSharedFlow()
    
    // ========================================================================
    // REACTIVE DATA
    // ========================================================================
    
    @OptIn(ExperimentalCoroutinesApi::class)
    val entries: StateFlow<List<DictionaryEntryEntity>> = combine(
        _searchQuery,
        _filterMode,
        dictionaryDao.getAllEntries()
    ) { query, filter, allEntries ->
        allEntries
            .filter { entry ->
                when (filter) {
                    FilterMode.ALL -> true
                    FilterMode.USER_DEFINED -> entry.isUserDefined
                    FilterMode.CACHED -> !entry.isUserDefined
                }
            }
            .filter { entry ->
                if (query.isBlank()) true
                else entry.originalText.contains(query, ignoreCase = true) ||
                     entry.translatedText.contains(query, ignoreCase = true)
            }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    
    // ========================================================================
    // INITIALIZATION
    // ========================================================================
    
    init {
        observeEntries()
    }
    
    private fun observeEntries() {
        viewModelScope.launch {
            entries.collect { list ->
                val userCount = list.count { it.isUserDefined }
                val cachedCount = list.count { !it.isUserDefined }
                
                _uiState.value = UiState.Success(
                    DictionaryUiModel(
                        entries = list,
                        totalCount = list.size,
                        userDefinedCount = userCount,
                        cachedCount = cachedCount,
                        isSearching = _searchQuery.value.isNotBlank()
                    )
                )
            }
        }
    }
    
    // ========================================================================
    // ACTIONS
    // ========================================================================
    
    /**
     * Update search query.
     */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }
    
    /**
     * Set filter mode.
     */
    fun setFilterMode(mode: FilterMode) {
        _filterMode.value = mode
    }
    
    /**
     * Add or update a dictionary entry.
     */
    fun saveEntry(originalText: String, translatedText: String) {
        if (originalText.isBlank() || translatedText.isBlank()) {
            viewModelScope.launch {
                _events.emit(DictionaryEvent.ShowError("Заполните оба поля"))
            }
            return
        }
        
        viewModelScope.launch(ioDispatcher) {
            try {
                dictionaryDao.insertOrUpdateEntry(
                    originalText = originalText.trim(),
                    translatedText = translatedText.trim(),
                    isUserDefined = true
                )
                
                // Also update in translation repository cache
                manageUserDictionaryUseCase.updateTranslation(
                    originalText.trim(),
                    translatedText.trim()
                )
                
                _events.emit(DictionaryEvent.ShowSuccess("Сохранено"))
                _events.emit(DictionaryEvent.DismissDialog)
                
            } catch (e: Exception) {
                _events.emit(DictionaryEvent.ShowError("Ошибка: ${e.message}"))
            }
        }
    }
    
    /**
     * Delete a dictionary entry.
     */
    fun deleteEntry(entry: DictionaryEntryEntity) {
        viewModelScope.launch(ioDispatcher) {
            try {
                dictionaryDao.deleteEntry(entry)
                _events.emit(DictionaryEvent.ShowSuccess("Удалено"))
            } catch (e: Exception) {
                _events.emit(DictionaryEvent.ShowError("Ошибка: ${e.message}"))
            }
        }
    }
    
    /**
     * Clear all cached entries (keep user-defined).
     */
    fun clearCache() {
        viewModelScope.launch(ioDispatcher) {
            try {
                dictionaryDao.clearCache()
                _events.emit(DictionaryEvent.ShowSuccess("Кэш очищен"))
            } catch (e: Exception) {
                _events.emit(DictionaryEvent.ShowError("Ошибка: ${e.message}"))
            }
        }
    }
    
    /**
     * Export dictionary as JSON.
     */
    fun exportDictionary(): String {
        val allEntries = entries.value
        return buildString {
            appendLine("{")
            appendLine("  \"entries\": [")
            allEntries.forEachIndexed { index, entry ->
                val comma = if (index < allEntries.size - 1) "," else ""
                appendLine("    {\"original\": \"${entry.originalText}\", \"translated\": \"${entry.translatedText}\"}$comma")
            }
            appendLine("  ]")
            appendLine("}")
        }
    }
    
    /**
     * Import entries from map.
     */
    fun importEntries(entries: Map<String, String>) {
        viewModelScope.launch(ioDispatcher) {
            try {
                entries.forEach { (original, translated) ->
                    dictionaryDao.insertOrUpdateEntry(original, translated, isUserDefined = true)
                }
                _events.emit(DictionaryEvent.ShowSuccess("Импортировано ${entries.size} записей"))
            } catch (e: Exception) {
                _events.emit(DictionaryEvent.ShowError("Ошибка импорта: ${e.message}"))
            }
        }
    }
    
    // ========================================================================
    // DATA CLASSES
    // ========================================================================
    
    data class DictionaryUiModel(
        val entries: List<DictionaryEntryEntity>,
        val totalCount: Int,
        val userDefinedCount: Int,
        val cachedCount: Int,
        val isSearching: Boolean
    )
    
    enum class FilterMode {
        ALL,
        USER_DEFINED,
        CACHED
    }
    
    sealed class DictionaryEvent {
        data class ShowSuccess(val message: String) : DictionaryEvent()
        data class ShowError(val message: String) : DictionaryEvent()
        object DismissDialog : DictionaryEvent()
        data class ConfirmDelete(val entry: DictionaryEntryEntity) : DictionaryEvent()
    }
}
