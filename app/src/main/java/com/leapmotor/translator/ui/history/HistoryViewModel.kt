package com.leapmotor.translator.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leapmotor.translator.core.UiState
import com.leapmotor.translator.data.local.dao.TranslationHistoryDao
import com.leapmotor.translator.data.local.entity.TranslationHistoryEntity
import com.leapmotor.translator.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/**
 * ViewModel for translation history (debug activity).
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyDao: TranslationHistoryDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {
    
    // ========================================================================
    // STATE
    // ========================================================================
    
    private val _uiState = MutableStateFlow<UiState<HistoryUiModel>>(UiState.Loading)
    val uiState: StateFlow<UiState<HistoryUiModel>> = _uiState.asStateFlow()
    
    val historyItems: StateFlow<List<HistoryItemModel>> = historyDao.getRecentHistory(200)
        .map { entities ->
            entities.map { entity ->
                HistoryItemModel(
                    id = entity.id,
                    originalText = entity.originalText,
                    translatedText = entity.translatedText,
                    formattedTime = formatTime(entity.timestamp),
                    bounds = "${entity.boundsLeft.toInt()},${entity.boundsTop.toInt()} - ${entity.boundsRight.toInt()},${entity.boundsBottom.toInt()}"
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    private val _events = MutableSharedFlow<HistoryEvent>()
    val events: SharedFlow<HistoryEvent> = _events.asSharedFlow()
    
    // ========================================================================
    // INITIALIZATION
    // ========================================================================
    
    init {
        loadHistory()
    }
    
    private fun loadHistory() {
        viewModelScope.launch {
            historyItems.collect { items ->
                _uiState.value = if (items.isEmpty()) {
                    UiState.Empty
                } else {
                    UiState.Success(HistoryUiModel(
                        items = items,
                        totalCount = items.size
                    ))
                }
            }
        }
    }
    
    // ========================================================================
    // ACTIONS
    // ========================================================================
    
    /**
     * Clear all history.
     */
    fun clearHistory() {
        viewModelScope.launch(ioDispatcher) {
            try {
                historyDao.clearHistory()
                _events.emit(HistoryEvent.ShowSuccess("История очищена"))
            } catch (e: Exception) {
                _events.emit(HistoryEvent.ShowError("Ошибка: ${e.message}"))
            }
        }
    }
    
    /**
     * Export history as text.
     */
    fun exportHistory(): String {
        return historyItems.value.joinToString("\n\n") { item ->
            "[${item.formattedTime}] ${item.originalText} → ${item.translatedText}"
        }
    }
    
    /**
     * Clean old history entries (older than N days).
     */
    fun cleanOldHistory(daysToKeep: Int = 7) {
        viewModelScope.launch(ioDispatcher) {
            val cutoff = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L)
            historyDao.deleteHistoryBefore(cutoff)
            _events.emit(HistoryEvent.ShowSuccess("Старые записи удалены"))
        }
    }
    
    // ========================================================================
    // HELPERS
    // ========================================================================
    
    private fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
    
    // ========================================================================
    // DATA CLASSES
    // ========================================================================
    
    data class HistoryUiModel(
        val items: List<HistoryItemModel>,
        val totalCount: Int
    )
    
    data class HistoryItemModel(
        val id: Long,
        val originalText: String,
        val translatedText: String,
        val formattedTime: String,
        val bounds: String
    )
    
    sealed class HistoryEvent {
        data class ShowSuccess(val message: String) : HistoryEvent()
        data class ShowError(val message: String) : HistoryEvent()
    }
}
