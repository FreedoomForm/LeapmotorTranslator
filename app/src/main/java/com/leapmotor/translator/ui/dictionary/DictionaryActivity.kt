package com.leapmotor.translator.ui.dictionary

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.viewModels
import com.leapmotor.translator.data.local.entity.DictionaryEntryEntity
import com.leapmotor.translator.ui.base.BaseActivity
import com.leapmotor.translator.ui.base.collectLatestWithLifecycle
import dagger.hilt.android.AndroidEntryPoint

/**
 * Activity for managing the translation dictionary.
 * Uses Hilt for dependency injection and ViewModel for state management.
 */
@AndroidEntryPoint
class DictionaryActivity : BaseActivity() {
    
    private val viewModel: DictionaryViewModel by viewModels()
    
    // UI components
    private lateinit var searchEditText: EditText
    private lateinit var filterSpinner: Spinner
    private lateinit var entriesContainer: LinearLayout
    private lateinit var addButton: Button
    private lateinit var statsTextView: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setupUI()
        observeViewModel()
    }
    
    private fun setupUI() {
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding(32, 32, 32, 32)
            setBackgroundColor(0xFF1a1a2e.toInt())
        }
        
        // Title
        rootLayout.addView(TextView(this).apply {
            text = "📚 Словарь переводов"
            textSize = 24f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 0, 0, 24)
        })
        
        // Search
        searchEditText = EditText(this).apply {
            hint = "🔍 Поиск..."
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF888888.toInt())
            setBackgroundColor(0xFF2a2a4e.toInt())
            setPadding(24, 16, 24, 16)
        }
        rootLayout.addView(searchEditText)
        
        // Search listener
        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                viewModel.setSearchQuery(s?.toString() ?: "")
            }
        })
        
        // Filter spinner
        filterSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@DictionaryActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("Все", "Пользовательские", "Кэш")
            )
            setBackgroundColor(0xFF2a2a4e.toInt())
        }
        rootLayout.addView(filterSpinner)
        
        filterSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val mode = when (position) {
                    1 -> DictionaryViewModel.FilterMode.USER_DEFINED
                    2 -> DictionaryViewModel.FilterMode.CACHED
                    else -> DictionaryViewModel.FilterMode.ALL
                }
                viewModel.setFilterMode(mode)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        // Stats
        statsTextView = TextView(this).apply {
            setTextColor(0xFF888888.toInt())
            textSize = 12f
            setPadding(0, 16, 0, 16)
        }
        rootLayout.addView(statsTextView)
        
        // Scrollable entries container
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        
        entriesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        scrollView.addView(entriesContainer)
        rootLayout.addView(scrollView)
        
        // Add button
        addButton = Button(this).apply {
            text = "+ Добавить слово"
            setBackgroundColor(0xFF4CAF50.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener { showAddDialog() }
        }
        rootLayout.addView(addButton)
        
        setContentView(rootLayout)
    }
    
    private fun observeViewModel() {
        // Observe entries
        viewModel.entries.collectLatestWithLifecycle(this) { entries ->
            updateEntriesList(entries)
        }
        
        // Observe UI state for stats
        viewModel.uiState.collectLatestWithLifecycle(this) { state ->
            when (state) {
                is com.leapmotor.translator.core.UiState.Success -> {
                    val data = state.data
                    statsTextView.text = "Всего: ${data.totalCount} | Пользовательские: ${data.userDefinedCount} | Кэш: ${data.cachedCount}"
                }
                else -> {}
            }
        }
        
        // Observe events
        viewModel.events.collectLatestWithLifecycle(this) { event ->
            when (event) {
                is DictionaryViewModel.DictionaryEvent.ShowSuccess -> showToast(event.message)
                is DictionaryViewModel.DictionaryEvent.ShowError -> showToast(event.message)
                is DictionaryViewModel.DictionaryEvent.DismissDialog -> { /* Dialog dismissed */ }
                is DictionaryViewModel.DictionaryEvent.ConfirmDelete -> showDeleteConfirmation(event.entry)
            }
        }
    }
    
    private fun updateEntriesList(entries: List<DictionaryEntryEntity>) {
        entriesContainer.removeAllViews()
        
        if (entries.isEmpty()) {
            entriesContainer.addView(TextView(this).apply {
                text = "Нет записей"
                setTextColor(0xFF888888.toInt())
                textSize = 16f
                setPadding(0, 32, 0, 32)
            })
            return
        }
        
        entries.forEach { entry ->
            entriesContainer.addView(createEntryView(entry))
        }
    }
    
    private fun createEntryView(entry: DictionaryEntryEntity): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 8, 0, 8)
            }
            setBackgroundColor(if (entry.isUserDefined) 0xFF2a3a4e.toInt() else 0xFF2a2a3e.toInt())
            setPadding(16, 16, 16, 16)
            
            // Text content
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                
                addView(TextView(context).apply {
                    text = entry.originalText
                    setTextColor(0xFFFFFFFF.toInt())
                    textSize = 16f
                })
                
                addView(TextView(context).apply {
                    text = "→ ${entry.translatedText}"
                    setTextColor(0xFF88FF88.toInt())
                    textSize = 14f
                })
                
                if (entry.isUserDefined) {
                    addView(TextView(context).apply {
                        text = "⭐ Пользовательский"
                        setTextColor(0xFFFFD700.toInt())
                        textSize = 10f
                    })
                }
            })
            
            // Delete button
            addView(Button(context).apply {
                text = "🗑"
                setBackgroundColor(0xFFFF5555.toInt())
                setOnClickListener { viewModel.deleteEntry(entry) }
            })
            
            // Edit button (for user-defined entries)
            if (entry.isUserDefined) {
                addView(Button(context).apply {
                    text = "✏"
                    setBackgroundColor(0xFF5588FF.toInt())
                    setOnClickListener { showEditDialog(entry) }
                })
            }
        }
    }
    
    private fun showAddDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }
        
        val originalInput = EditText(this).apply { hint = "Китайский текст" }
        val translationInput = EditText(this).apply { hint = "Русский перевод" }
        
        layout.addView(originalInput)
        layout.addView(translationInput)
        
        AlertDialog.Builder(this)
            .setTitle("Добавить перевод")
            .setView(layout)
            .setPositiveButton("Сохранить") { _, _ ->
                viewModel.saveEntry(
                    originalInput.text.toString(),
                    translationInput.text.toString()
                )
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
    
    private fun showEditDialog(entry: DictionaryEntryEntity) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }
        
        val originalInput = EditText(this).apply {
            setText(entry.originalText)
            isEnabled = false // Can't change original
        }
        val translationInput = EditText(this).apply {
            setText(entry.translatedText)
        }
        
        layout.addView(originalInput)
        layout.addView(translationInput)
        
        AlertDialog.Builder(this)
            .setTitle("Редактировать перевод")
            .setView(layout)
            .setPositiveButton("Сохранить") { _, _ ->
                viewModel.saveEntry(
                    entry.originalText,
                    translationInput.text.toString()
                )
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
    
    private fun showDeleteConfirmation(entry: DictionaryEntryEntity) {
        AlertDialog.Builder(this)
            .setTitle("Удалить?")
            .setMessage("Удалить \"${entry.originalText}\"?")
            .setPositiveButton("Удалить") { _, _ ->
                viewModel.deleteEntry(entry)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
}
