package com.leapmotor.translator

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

/**
 * Debug Activity: Shows what the app "sees" in real-time.
 */
class RecognizedWordsActivity : AppCompatActivity() {

    private lateinit var contentLayout: LinearLayout
    private val dateFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, 
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }
        
        scrollView.addView(rootLayout)
        setContentView(scrollView)

        // Header
        val header = TextView(this).apply {
            text = "История Распознавания"
            textSize = 20f
            setTextColor(Color.YELLOW)
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 16)
        }
        rootLayout.addView(header)

        // Refresh Button
        val refreshBtn = Button(this).apply {
            text = "Обновить"
            setOnClickListener { refreshLog() }
        }
        rootLayout.addView(refreshBtn)

        // List Container
        contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        rootLayout.addView(contentLayout)

        refreshLog()
    }

    private fun refreshLog() {
        contentLayout.removeAllViews()
        
        // Get snapshot of log
        val log = TranslationService.historyLog.toList().reversed() // Newest first

        if (log.isEmpty()) {
            val empty = TextView(this).apply {
                text = "Лог пуст. Включите сервис и перейдите на экран с китайским текстом."
                setTextColor(Color.GRAY)
                setPadding(0, 32, 0, 0)
            }
            contentLayout.addView(empty)
            return
        }

        for (item in log) {
            val itemLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 16, 16, 16)
                setBackgroundColor(Color.parseColor("#222222"))
            }
            
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8 }
            
            // Time & Bounds
            val metaText = TextView(this).apply {
                text = "[${dateFormatter.format(Date(item.time))}] Bounds: ${item.bounds.toShortString()}"
                textSize = 12f
                setTextColor(Color.LTGRAY)
            }
            itemLayout.addView(metaText)
            
            // Text Content
            val contentText = TextView(this).apply {
                text = "${item.original} -> ${item.translated}"
                textSize = 16f
                setTextColor(Color.WHITE)
            }
            itemLayout.addView(contentText)
            
            contentLayout.addView(itemLayout, params)
        }
    }
}
