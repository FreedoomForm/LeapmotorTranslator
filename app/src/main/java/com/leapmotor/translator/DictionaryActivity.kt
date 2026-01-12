package com.leapmotor.translator

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.leapmotor.translator.translation.TranslationManager
import android.graphics.Color

/**
 * Activity for viewing and editing the translation dictionary.
 */
class DictionaryActivity : AppCompatActivity() {

    private lateinit var contentLayout: LinearLayout
    private val translationManager = TranslationManager.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Create UI programmatically
        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#121212"))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, 
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        
        scrollView.addView(rootLayout)
        setContentView(scrollView)

        // Header
        val header = TextView(this).apply {
            text = "Словарь Перевода"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 32)
        }
        rootLayout.addView(header)

        // Add New Button
        val addButton = Button(this).apply {
            text = "+ Добавить слово"
            setOnClickListener { showEditDialog("", "") }
            setBackgroundColor(Color.parseColor("#333333"))
            setTextColor(Color.WHITE)
        }
        rootLayout.addView(addButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 32 })

        // List Container
        contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        rootLayout.addView(contentLayout)

        // Initial Load
        refreshList()
    }

    private fun refreshList() {
        contentLayout.removeAllViews()
        val allTranslations = translationManager.getAllTranslations()
            .toList()
            .sortedBy { it.first } // Sort by Chinese text

        if (allTranslations.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = "Словарь пуст"
                setTextColor(Color.GRAY)
                gravity = Gravity.CENTER
                setPadding(0, 64, 0, 0)
            }
            contentLayout.addView(emptyView)
            return
        }

        for ((original, translated) in allTranslations) {
            val itemLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 24, 24, 24)
                setBackgroundColor(Color.parseColor("#1E1E1E"))
                setOnClickListener { showEditDialog(original, translated) }
            }
            
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16 }
            
            // Original (Chinese)
            val originalText = TextView(this).apply {
                text = original
                textSize = 18f
                setTextColor(Color.parseColor("#AAAAAA"))
            }
            itemLayout.addView(originalText)
            
            // Translated (Russian)
            val translatedText = TextView(this).apply {
                text = translated
                textSize = 20f
                setTextColor(Color.WHITE)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            itemLayout.addView(translatedText)
            
            contentLayout.addView(itemLayout, params)
        }
    }

    private fun showEditDialog(original: String, currentTranslation: String) {
        val isNew = original.isEmpty()
        
        val dialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 32)
        }

        val originalInput = EditText(this).apply {
            hint = "Китайский текст (оригинал)"
            setText(original)
            isEnabled = isNew // Only allow editing original if adding new
        }
        dialogLayout.addView(originalInput)

        val translationInput = EditText(this).apply {
            hint = "Русский перевод"
            setText(currentTranslation)
            marginTop(32)
        }
        dialogLayout.addView(translationInput)

        AlertDialog.Builder(this)
            .setTitle(if (isNew) "Добавить перевод" else "Редактировать перевод")
            .setView(dialogLayout)
            .setPositiveButton("Сохранить") { _, _ ->
                val newOriginal = originalInput.text.toString().trim()
                val newTranslation = translationInput.text.toString().trim()
                
                if (newOriginal.isNotEmpty() && newTranslation.isNotEmpty()) {
                    translationManager.updateTranslation(newOriginal, newTranslation)
                    refreshList()
                    Toast.makeText(this, "Сохранено", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
    
    private fun View.marginTop(margin: Int) {
        (layoutParams as? LinearLayout.LayoutParams)?.topMargin = margin
    }
}
