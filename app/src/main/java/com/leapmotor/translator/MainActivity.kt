package com.leapmotor.translator

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.activity.viewModels
import com.leapmotor.translator.core.UiState
import com.leapmotor.translator.ui.base.BaseActivity
import com.leapmotor.translator.ui.base.collectLatestWithLifecycle
import com.leapmotor.translator.ui.dictionary.DictionaryActivity
import com.leapmotor.translator.ui.main.MainViewModel
import com.leapmotor.translator.util.PermissionUtils
import dagger.hilt.android.AndroidEntryPoint

/**
 * Main activity for the translator app.
 * 
 * Uses Hilt for dependency injection and ViewModel for state management.
 * 
 * Responsibilities:
 * - Permission management (overlay, accessibility)
 * - Service status display
 * - Navigation to other screens
 * - Debug mode toggle
 */
@AndroidEntryPoint
class MainActivity : BaseActivity() {
    
    private val viewModel: MainViewModel by viewModels()
    
    // UI References
    private lateinit var overlayStatusText: TextView
    private lateinit var accessibilityStatusText: TextView
    private lateinit var modelStatusText: TextView
    private lateinit var cacheStatsText: TextView
    private lateinit var debugCheckbox: CheckBox
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setupUI()
        observeViewModel()
        checkCrashLog()
        
        // Initialize translation model
        viewModel.initializeTranslation()
    }
    
    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
        viewModel.refresh()
    }
    
    private fun setupUI() {
        val rootLayout = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0xFF0f0f23.toInt())
        }
        
        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(48, 48, 48, 48)
        }
        
        // Header
        contentLayout.addView(createHeader())
        
        // Permission Cards
        contentLayout.addView(createSectionTitle("🔐 Разрешения"))
        contentLayout.addView(createOverlayPermissionCard())
        contentLayout.addView(createAccessibilityCard())
        
        // Status Cards
        contentLayout.addView(createSectionTitle("📊 Статус"))
        contentLayout.addView(createModelStatusCard())
        contentLayout.addView(createCacheStatsCard())
        
        // Actions
        contentLayout.addView(createSectionTitle("⚙️ Действия"))
        contentLayout.addView(createActionsCard())
        
        // Debug Mode
        contentLayout.addView(createDebugCard())
        
        // MIUI specific
        if (PermissionUtils.isXiaomiDevice()) {
            contentLayout.addView(createMIUICard())
        }
        
        rootLayout.addView(contentLayout)
        setContentView(rootLayout)
    }
    
    private fun createHeader(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 48)
            
            addView(TextView(this@MainActivity).apply {
                text = "🚗"
                textSize = 48f
                gravity = Gravity.CENTER
            })
            
            addView(TextView(this@MainActivity).apply {
                text = "Leapmotor Translator"
                textSize = 24f
                setTextColor(0xFFFFFFFF.toInt())
                gravity = Gravity.CENTER
            })
            
            addView(TextView(this@MainActivity).apply {
                text = "Перевод китайского интерфейса на русский"
                textSize = 14f
                setTextColor(0xFF888888.toInt())
                gravity = Gravity.CENTER
            })
        }
    }
    
    private fun createSectionTitle(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 32, 0, 16)
        }
    }
    
    private fun createOverlayPermissionCard(): LinearLayout {
        return createCard().apply {
            addView(TextView(this@MainActivity).apply {
                text = "Разрешение на наложение"
                textSize = 16f
                setTextColor(0xFFFFFFFF.toInt())
            })
            
            overlayStatusText = TextView(this@MainActivity).apply {
                text = "Проверка..."
                textSize = 12f
                setTextColor(0xFF888888.toInt())
            }
            addView(overlayStatusText)
            
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
            }
        }
    }
    
    private fun createAccessibilityCard(): LinearLayout {
        return createCard().apply {
            addView(TextView(this@MainActivity).apply {
                text = "Сервис специальных возможностей"
                textSize = 16f
                setTextColor(0xFFFFFFFF.toInt())
            })
            
            accessibilityStatusText = TextView(this@MainActivity).apply {
                text = "Проверка..."
                textSize = 12f
                setTextColor(0xFF888888.toInt())
            }
            addView(accessibilityStatusText)
            
            addView(Button(this@MainActivity).apply {
                text = "Настройки доступности"
                setBackgroundColor(0xFF3366FF.toInt())
                setTextColor(0xFFFFFFFF.toInt())
                setOnClickListener {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            })
        }
    }
    
    private fun createModelStatusCard(): LinearLayout {
        return createCard().apply {
            addView(TextView(this@MainActivity).apply {
                text = "Модель перевода"
                textSize = 16f
                setTextColor(0xFFFFFFFF.toInt())
            })
            
            modelStatusText = TextView(this@MainActivity).apply {
                text = "Инициализация..."
                textSize = 12f
                setTextColor(0xFF888888.toInt())
            }
            addView(modelStatusText)
        }
    }
    
    private fun createCacheStatsCard(): LinearLayout {
        return createCard().apply {
            addView(TextView(this@MainActivity).apply {
                text = "Статистика кэша"
                textSize = 16f
                setTextColor(0xFFFFFFFF.toInt())
            })
            
            cacheStatsText = TextView(this@MainActivity).apply {
                text = "Загрузка..."
                textSize = 12f
                setTextColor(0xFF888888.toInt())
            }
            addView(cacheStatsText)
            
            addView(Button(this@MainActivity).apply {
                text = "Очистить кэш"
                setBackgroundColor(0xFFFF5555.toInt())
                setTextColor(0xFFFFFFFF.toInt())
                setOnClickListener { viewModel.clearCache() }
            })
        }
    }
    
    private fun createActionsCard(): LinearLayout {
        return createCard().apply {
            addView(Button(this@MainActivity).apply {
                text = "📚 Словарь / Редактор"
                setBackgroundColor(0xFF4CAF50.toInt())
                setTextColor(0xFFFFFFFF.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 8, 0, 8) }
                setOnClickListener {
                    startActivity(Intent(this@MainActivity, DictionaryActivity::class.java))
                }
            })
            
            addView(Button(this@MainActivity).apply {
                text = "📝 История распознаваний"
                setBackgroundColor(0xFF2196F3.toInt())
                setTextColor(0xFFFFFFFF.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 8, 0, 8) }
                setOnClickListener {
                    startActivity(Intent(this@MainActivity, RecognizedWordsActivity::class.java))
                }
            })
        }
    }
    
    private fun createDebugCard(): LinearLayout {
        return createCard().apply {
            debugCheckbox = CheckBox(this@MainActivity).apply {
                text = "Отладка: показать границы"
                setTextColor(0xFFFFFFFF.toInt())
                setOnCheckedChangeListener { _, isChecked ->
                    TranslationService.instance?.setDebugMode(isChecked)
                }
            }
            addView(debugCheckbox)
        }
    }
    
    private fun createMIUICard(): LinearLayout {
        return createCard().apply {
            addView(TextView(this@MainActivity).apply {
                text = "⚠️ Настройки MIUI"
                textSize = 16f
                setTextColor(0xFFFFD700.toInt())
            })
            
            addView(TextView(this@MainActivity).apply {
                text = "На Xiaomi/Redmi требуются дополнительные разрешения"
                textSize = 12f
                setTextColor(0xFF888888.toInt())
            })
            
            addView(Button(this@MainActivity).apply {
                text = "Открыть настройки MIUI"
                setBackgroundColor(0xFFFF9800.toInt())
                setTextColor(0xFFFFFFFF.toInt())
                setOnClickListener {
                    PermissionUtils.openMIUIPermissionSettings(this@MainActivity)
                }
            })
        }
    }
    
    private fun createCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 8, 0, 8) }
            setBackgroundColor(0xFF1a1a2e.toInt())
            setPadding(32, 24, 32, 24)
        }
    }
    
    private fun observeViewModel() {
        // Observe model state
        viewModel.modelState.collectLatestWithLifecycle(this) { state ->
            updateModelStatus(state)
        }
        
        // Observe cache stats
        viewModel.cacheStats.collectLatestWithLifecycle(this) { stats ->
            cacheStatsText.text = "Размер: ${stats.size} | Попаданий: ${stats.hits} | " +
                "Промахов: ${stats.misses} | Hit rate: ${(stats.hitRate * 100).toInt()}%"
        }
        
        // Observe events
        viewModel.events.collectLatestWithLifecycle(this) { event ->
            when (event) {
                is MainViewModel.MainEvent.ShowToast -> showToast(event.message)
                is MainViewModel.MainEvent.ToggleDebugMode -> {
                    debugCheckbox.isChecked = !debugCheckbox.isChecked
                }
                is MainViewModel.MainEvent.NavigateToDictionary -> {
                    startActivity(Intent(this, DictionaryActivity::class.java))
                }
                else -> {}
            }
        }
    }
    
    private fun updatePermissionStatus() {
        // Overlay permission
        val hasOverlay = Settings.canDrawOverlays(this)
        overlayStatusText.text = if (hasOverlay) "✅ Разрешено" else "❌ Требуется"
        overlayStatusText.setTextColor(if (hasOverlay) 0xFF00FF00.toInt() else 0xFFFF0000.toInt())
        
        // Accessibility service
        val serviceRunning = TranslationService.instance != null
        accessibilityStatusText.text = if (serviceRunning) "✅ Активен" else "❌ Не активен"
        accessibilityStatusText.setTextColor(if (serviceRunning) 0xFF00FF00.toInt() else 0xFFFF0000.toInt())
    }
    
    private fun updateModelStatus(state: MainViewModel.ModelStatus) {
        val (text, color) = when (state) {
            is MainViewModel.ModelStatus.NotInitialized -> "⏳ Не инициализирован" to 0xFF888888
            is MainViewModel.ModelStatus.Initializing -> "⏳ Инициализация..." to 0xFFFFD700
            is MainViewModel.ModelStatus.Downloading -> "⬇️ Загрузка модели..." to 0xFF2196F3
            is MainViewModel.ModelStatus.Ready -> "✅ Готова к работе" to 0xFF00FF00
            is MainViewModel.ModelStatus.Error -> "❌ Ошибка: ${state.message}" to 0xFFFF0000
        }
        modelStatusText.text = text
        modelStatusText.setTextColor(color.toInt())
    }
    
    private fun checkCrashLog() {
        val crashFile = java.io.File(filesDir, "crash_log.txt")
        if (crashFile.exists()) {
            val content = crashFile.readText().take(500)
            
            android.app.AlertDialog.Builder(this)
                .setTitle("⚠️ Обнаружен сбой")
                .setMessage("Приложение завершилось с ошибкой:\n\n$content...")
                .setPositiveButton("OK") { _, _ ->
                    crashFile.delete()
                }
                .show()
        }
    }
}
