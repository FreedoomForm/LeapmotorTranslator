package com.leapmotor.translator

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color
import android.view.Gravity
import android.widget.ScrollView

/**
 * Main Activity for controlling the Translation Overlay.
 * 
 * Provides:
 * - Permission status and setup
 * - Service status display
 * - Cache statistics
 * - Debug mode toggle
 */
class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val OVERLAY_PERMISSION_REQUEST = 1001
    }
    
    private lateinit var statusText: TextView
    private lateinit var cacheStatsText: TextView
    private lateinit var overlayPermissionBtn: Button
    private lateinit var accessibilityBtn: Button
    private lateinit var toggleOverlayBtn: Button
    private lateinit var debugModeBtn: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Build UI programmatically (no XML layout needed)
        setContentView(createContentView())
        
        // Initial status update
        updateStatus()
    }
    
    override fun onResume() {
        super.onResume()
        updateStatus()
    }
    
    private fun createContentView(): View {
        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#1a1a2e"))
            setPadding(48, 48, 48, 48)
        }
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        
        // Title
        val title = TextView(this).apply {
            text = "Leapmotor C11\nПереводчик"
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 48)
        }
        layout.addView(title)
        
        // Status text
        statusText = TextView(this).apply {
            text = "Проверка статуса..."
            textSize = 16f
            setTextColor(Color.parseColor("#888888"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        }
        layout.addView(statusText)
        
        // Overlay permission button
        overlayPermissionBtn = Button(this).apply {
            text = "Разрешение на наложение"
            setOnClickListener { requestOverlayPermission() }
            setPadding(32, 24, 32, 24)
        }
        layout.addView(overlayPermissionBtn, createButtonParams())
        
        // Accessibility settings button
        accessibilityBtn = Button(this).apply {
            text = "Настройки доступности"
            setOnClickListener { openAccessibilitySettings() }
            setPadding(32, 24, 32, 24)
        }
        layout.addView(accessibilityBtn, createButtonParams())
        
        // Toggle overlay button
        toggleOverlayBtn = Button(this).apply {
            text = "Вкл/Выкл наложение"
            setOnClickListener { toggleOverlay() }
            setPadding(32, 24, 32, 24)
            isEnabled = false
        }
        layout.addView(toggleOverlayBtn, createButtonParams())
        
        // Debug mode button
        debugModeBtn = Button(this).apply {
            text = "Режим отладки"
            setOnClickListener { toggleDebugMode() }
            setPadding(32, 24, 32, 24)
            isEnabled = false
        }
        layout.addView(debugModeBtn, createButtonParams())
        
        // Cache statistics
        cacheStatsText = TextView(this).apply {
            text = ""
            textSize = 14f
            setTextColor(Color.parseColor("#666666"))
            gravity = Gravity.CENTER
            setPadding(0, 48, 0, 0)
        }
        layout.addView(cacheStatsText)
        
        // Instructions
        val instructions = TextView(this).apply {
            text = """
                Инструкция по настройке:
                
                1. Нажмите "Разрешение на наложение" и включите разрешение для этого приложения
                
                2. Нажмите "Настройки доступности", найдите "Переводчик интерфейса" и включите его
                
                3. Вернитесь в это приложение - перевод должен работать автоматически
                
                Приложение переводит китайский текст интерфейса на русский язык в реальном времени.
            """.trimIndent()
            textSize = 14f
            setTextColor(Color.parseColor("#aaaaaa"))
            setPadding(0, 64, 0, 0)
        }
        layout.addView(instructions)
        
        scrollView.addView(layout)
        return scrollView
    }
    
    private fun createButtonParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 16, 0, 16)
        }
    }
    
    private fun updateStatus() {
        val hasOverlayPermission = Settings.canDrawOverlays(this)
        val isServiceRunning = TranslationService.instance != null
        
        val statusBuilder = StringBuilder()
        
        // Overlay permission status
        statusBuilder.append("Наложение: ")
        if (hasOverlayPermission) {
            statusBuilder.append("✓ Разрешено\n")
            overlayPermissionBtn.isEnabled = false
            overlayPermissionBtn.alpha = 0.5f
        } else {
            statusBuilder.append("✗ Требуется разрешение\n")
            overlayPermissionBtn.isEnabled = true
            overlayPermissionBtn.alpha = 1f
        }
        
        // Accessibility service status
        statusBuilder.append("Сервис: ")
        if (isServiceRunning) {
            statusBuilder.append("✓ Запущен\n")
            accessibilityBtn.alpha = 0.5f
            toggleOverlayBtn.isEnabled = true
            debugModeBtn.isEnabled = true
        } else {
            statusBuilder.append("✗ Не активирован\n")
            accessibilityBtn.alpha = 1f
            toggleOverlayBtn.isEnabled = false
            debugModeBtn.isEnabled = false
        }
        
        // Translation model status
        statusBuilder.append("Перевод: ")
        if (isServiceRunning) {
            val stats = TranslationService.instance?.getCacheStats()
            if (stats != null) {
                statusBuilder.append("✓ Готов\n")
                cacheStatsText.text = "Кэш: ${stats.size} записей | " +
                        "Попадания: ${stats.hits} | " +
                        "Промахи: ${stats.misses} | " +
                        "Эффективность: ${(stats.hitRate * 100).toInt()}%"
            } else {
                statusBuilder.append("Загрузка...\n")
            }
        } else {
            statusBuilder.append("Ожидание сервиса\n")
            cacheStatsText.text = ""
        }
        
        statusText.text = statusBuilder.toString()
    }
    
    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST)
        }
    }
    
    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
        Toast.makeText(
            this,
            "Найдите 'Переводчик интерфейса' и включите его",
            Toast.LENGTH_LONG
        ).show()
    }
    
    private fun toggleOverlay() {
        TranslationService.instance?.toggleOverlay()
        Toast.makeText(this, "Наложение переключено", Toast.LENGTH_SHORT).show()
    }
    
    private var debugMode = false
    
    private fun toggleDebugMode() {
        debugMode = !debugMode
        TranslationService.instance?.setDebugMode(debugMode)
        debugModeBtn.text = if (debugMode) "Отладка: ВКЛ" else "Отладка: ВЫКЛ"
        Toast.makeText(
            this,
            if (debugMode) "Режим отладки включен" else "Режим отладки выключен",
            Toast.LENGTH_SHORT
        ).show()
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST) {
            updateStatus()
        }
    }
}
