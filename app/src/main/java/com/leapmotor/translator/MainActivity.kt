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
import com.google.mlkit.nl.translate.TranslateLanguage
import java.util.Locale

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
        
        // Initialize translation model with saved languages
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val source = prefs.getString("source_lang", "zh") ?: "zh"
        val target = prefs.getString("target_lang", "ru") ?: "ru"
        
        viewModel.updateLanguages(source, target)
    }
    
    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
        viewModel.refresh()
        loadSettings() // Apply saved settings on resume
    }
    
    private fun loadSettings() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val scale = prefs.getFloat("text_scale", 1.0f)
        val opacity = prefs.getInt("box_opacity", 230)
        val textColor = prefs.getInt("text_color", 0)
        val boxStyle = prefs.getInt("box_style", 0)
        
        // Apply to service immediately if running
        TranslationService.instance?.updateSettings(scale, opacity, textColor, boxStyle)
    }
    
    private fun saveAndApplySettings(scale: Float, opacity: Int, textColor: Int, boxStyle: Int) {
        getSharedPreferences("app_prefs", MODE_PRIVATE).edit().apply {
            putFloat("text_scale", scale)
            putInt("box_opacity", opacity)
            putInt("text_color", textColor)
            putInt("box_style", boxStyle)
            apply()
        }
        TranslationService.instance?.updateSettings(scale, opacity, textColor, boxStyle)
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
        
        // Language Settings
        contentLayout.addView(createSectionTitle("🌐 Языки"))
        contentLayout.addView(createLanguageCard())

        // Status Cards
        contentLayout.addView(createSectionTitle("📊 Статус"))
        contentLayout.addView(createModelStatusCard())
        contentLayout.addView(createCacheStatsCard())
        
        // Actions
        contentLayout.addView(createSectionTitle("⚙️ Действия"))
        contentLayout.addView(createActionsCard())
        
        // Appearance Settings
        contentLayout.addView(createSectionTitle("🎨 Внешний вид"))
        contentLayout.addView(createAppearanceCard())
        
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
    
    private fun createLanguageCard(): LinearLayout {
        return createCard().apply {
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            var currentSource = prefs.getString("source_lang", "zh") ?: "zh"
            var currentTarget = prefs.getString("target_lang", "ru") ?: "ru"
            
            // Get all supported languages from ML Kit
            val allLanguages = TranslateLanguage.getAllLanguages()
            
            // Create a list of pairs (Code, Display Name)
            val langList = allLanguages.map { code ->
                val locale = Locale(code)
                val displayName = locale.getDisplayLanguage(Locale.getDefault())
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                code to "$displayName ($code)"
            }.sortedBy { it.second } // Sort alphabetically by name
            
            val langCodes = langList.map { it.first }
            val langNames = langList.map { it.second }
            
            // Source Language
            addView(TextView(this@MainActivity).apply {
                text = "Язык оригинала (Система)"
                setTextColor(0xFFFFFFFF.toInt())
            })
            
            addView(Spinner(this@MainActivity).apply {
                adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, langNames)
                val srcIndex = langCodes.indexOf(currentSource).coerceAtLeast(0)
                setSelection(srcIndex)
                
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(p0: AdapterView<*>?, p1: android.view.View?, position: Int, p3: Long) {
                        val newCode = langCodes[position]
                        if (currentSource != newCode) {
                            currentSource = newCode
                            prefs.edit().putString("source_lang", newCode).apply()
                            viewModel.updateLanguages(currentSource, currentTarget)
                        }
                    }
                    override fun onNothingSelected(p0: AdapterView<*>?) {}
                }
                background.setTint(0xFFFFFFFF.toInt())
            })
            
            // Target Language
            addView(TextView(this@MainActivity).apply {
                text = "Язык перевода"
                setTextColor(0xFFFFFFFF.toInt())
                setPadding(0, 16, 0, 0)
            })
            
            addView(Spinner(this@MainActivity).apply {
                adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, langNames)
                // If target language is not found (e.g. was 'uz' but model doesn't support it, or user changed default), fallback safely
                var tgtIndex = langCodes.indexOf(currentTarget)
                if (tgtIndex == -1) { 
                    // Fallback to Russian or first available
                    currentTarget = "ru" 
                    tgtIndex = langCodes.indexOf("ru").coerceAtLeast(0)
                }
                setSelection(tgtIndex)
                
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(p0: AdapterView<*>?, p1: android.view.View?, position: Int, p3: Long) {
                        val newCode = langCodes[position]
                        if (currentTarget != newCode) {
                            currentTarget = newCode
                            prefs.edit().putString("target_lang", newCode).apply()
                            viewModel.updateLanguages(currentSource, currentTarget)
                        }
                    }
                    override fun onNothingSelected(p0: AdapterView<*>?) {}
                }
                background.setTint(0xFFFFFFFF.toInt())
            })
            
            addView(TextView(this@MainActivity).apply {
                text = "⚠️ Смена языка требует загрузки модели (~30MB)"
                textSize = 10f
                setTextColor(0xFF888888.toInt())
                setPadding(0, 8, 0, 0)
            })
            
            // Show count
            addView(TextView(this@MainActivity).apply {
                text = "Доступно языков: ${langCodes.size}"
                textSize = 10f
                setTextColor(0xFF888888.toInt())
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
    
    private fun createAppearanceCard(): LinearLayout {
        return createCard().apply {
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            var currentScale = prefs.getFloat("text_scale", 1.0f)
            var currentOpacity = prefs.getInt("box_opacity", 230)
            var currentTextColor = prefs.getInt("text_color", 0)
            var currentBoxStyle = prefs.getInt("box_style", 0)

            // 1. Text Size Slider
            addView(TextView(this@MainActivity).apply {
                text = "Размер текста"
                setTextColor(0xFFFFFFFF.toInt())
            })
            val sizeLabel = TextView(this@MainActivity).apply {
                text = "${(currentScale * 100).toInt()}%"
                setTextColor(0xFF888888.toInt())
                textSize = 12f
            }
            addView(sizeLabel)
            
            addView(SeekBar(this@MainActivity).apply {
                max = 15 // 0.5 to 2.0 (step 0.1)
                progress = ((currentScale - 0.5f) * 10).toInt()
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        currentScale = 0.5f + (progress / 10f)
                        sizeLabel.text = "${(currentScale * 100).toInt()}%"
                        if (fromUser) saveAndApplySettings(currentScale, currentOpacity, currentTextColor, currentBoxStyle)
                    }
                    override fun onStartTrackingTouch(p0: SeekBar?) {}
                    override fun onStopTrackingTouch(p0: SeekBar?) {}
                })
            })
            
            // 2. Box Opacity Slider
            addView(TextView(this@MainActivity).apply {
                text = "Прозрачность фона"
                setTextColor(0xFFFFFFFF.toInt())
                setPadding(0, 16, 0, 0)
            })
            val opacityLabel = TextView(this@MainActivity).apply {
                text = "${(currentOpacity / 2.55f).toInt()}%"
                setTextColor(0xFF888888.toInt())
                textSize = 12f
            }
            addView(opacityLabel)
            
            addView(SeekBar(this@MainActivity).apply {
                max = 255
                progress = currentOpacity
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        currentOpacity = progress
                        opacityLabel.text = "${(progress / 2.55f).toInt()}%"
                        if (fromUser) saveAndApplySettings(currentScale, currentOpacity, currentTextColor, currentBoxStyle)
                    }
                    override fun onStartTrackingTouch(p0: SeekBar?) {}
                    override fun onStopTrackingTouch(p0: SeekBar?) {}
                })
            })
            
            // 3. Text Color Spinner
            addView(TextView(this@MainActivity).apply {
                text = "Цвет текста"
                setTextColor(0xFFFFFFFF.toInt())
                setPadding(0, 16, 0, 0)
            })
            
            val colors = listOf("🌈 Default Gradient", "🟨 Yellow", "🟩 Green", "🟦 Cyan")
            addView(Spinner(this@MainActivity).apply {
                adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, colors)
                setSelection(currentTextColor)
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(p0: AdapterView<*>?, p1: android.view.View?, position: Int, p3: Long) {
                        if (currentTextColor != position) {
                            currentTextColor = position
                            saveAndApplySettings(currentScale, currentOpacity, currentTextColor, currentBoxStyle)
                        }
                    }
                    override fun onNothingSelected(p0: AdapterView<*>?) {}
                }
                background.setTint(0xFFFFFFFF.toInt())
            })
            
            // 4. Box Style Spinner
            addView(TextView(this@MainActivity).apply {
                text = "Стиль фона"
                setTextColor(0xFFFFFFFF.toInt())
                setPadding(0, 16, 0, 0)
            })
            
            val styles = listOf("🤖 Auto Glass", "🌑 Dark Glass", "☀️ Light Glass")
            addView(Spinner(this@MainActivity).apply {
                adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, styles)
                setSelection(currentBoxStyle)
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(p0: AdapterView<*>?, p1: android.view.View?, position: Int, p3: Long) {
                        if (currentBoxStyle != position) {
                            currentBoxStyle = position
                            saveAndApplySettings(currentScale, currentOpacity, currentTextColor, currentBoxStyle)
                        }
                    }
                    override fun onNothingSelected(p0: AdapterView<*>?) {}
                }
                background.setTint(0xFFFFFFFF.toInt())
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
