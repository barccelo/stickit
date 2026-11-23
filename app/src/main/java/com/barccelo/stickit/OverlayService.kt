package com.barccelo.stickit

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.PixelFormat
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import kotlin.math.abs

/**
 * Floating selector remains separate from Gboard / Samsung Keyboard.
 * Window TYPE_APPLICATION_OVERLAY does not inject UI inside a third-party IME.
 */
class OverlayService : Service() {
    private lateinit var windows: WindowManager
    private lateinit var store: StickerStore
    private var bubble: View? = null
    private var panel: View? = null
    private val preferences by lazy { getSharedPreferences("stickit_ui", Context.MODE_PRIVATE) }

    companion object {
        const val ACTION_HIDE = "com.barccelo.stickit.HIDE_SELECTOR"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        windows = getSystemService(WINDOW_SERVICE) as WindowManager
        store = StickerStore(this)
        startPersistentNotification()
        showBubble()
        preferences.edit().putBoolean("overlay_enabled", true).apply()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HIDE) stopSelf()
        return START_NOT_STICKY
    }

    private fun startPersistentNotification() {
        val channelId = "stickit_overlay"
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(channelId, "Selector flotante", NotificationManager.IMPORTANCE_LOW)
        )
        val openIntent = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val hideIntent = PendingIntent.getService(this, 1,
            Intent(this, OverlayService::class.java).setAction(ACTION_HIDE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_stickit)
            .setContentTitle("Stickit")
            .setContentText("Selector flotante activo")
            .setContentIntent(openIntent)
            .addAction(Notification.Action.Builder(
                android.R.drawable.ic_menu_close_clear_cancel, "Ocultar selector", hideIntent
            ).build())
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(41, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(41, notification)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }

    private fun showBubble() {
        val button = ImageView(this).apply {
            setImageResource(R.drawable.ic_stickit)
            setPadding(dp(11), dp(11), dp(11), dp(11))
            background = rounded(Color.rgb(71, 83, 174), 50)
            elevation = dp(6).toFloat()
            contentDescription = "Abrir selector. Mantener pulsado para ocultar."
        }
        val params = WindowManager.LayoutParams(
            dp(54), dp(54),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = preferences.getInt("bubble_x", dp(12))
            y = preferences.getInt("bubble_y", dp(260))
        }
        var x0 = 0
        var y0 = 0
        var fingerX = 0f
        var fingerY = 0f
        button.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    x0 = params.x; y0 = params.y
                    fingerX = event.rawX; fingerY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = x0 + (event.rawX - fingerX).toInt()
                    params.y = y0 + (event.rawY - fingerY).toInt()
                    windows.updateViewLayout(button, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val moved = abs(event.rawX - fingerX) >= dp(12) ||
                        abs(event.rawY - fingerY) >= dp(12)
                    if (moved) {
                        preferences.edit().putInt("bubble_x", params.x)
                            .putInt("bubble_y", params.y).apply()
                    } else if (event.eventTime - event.downTime >= 600L) {
                        stopSelf()
                    } else {
                        togglePanel()
                    }
                    true
                }
                else -> true
            }
        }
        bubble = button
        windows.addView(button, params)
    }

    private fun togglePanel() { if (panel == null) showPanel() else closePanel() }

    private fun showPanel() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(Color.WHITE, 22)
            elevation = dp(16).toFloat()
        }
        val toolbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val title = TextView(this).apply {
            text = "Stickit · Mis stickers"; textSize = 17f
            setTextColor(Color.rgb(25, 26, 30)); gravity = Gravity.CENTER_VERTICAL
        }
        toolbar.addView(title, LinearLayout.LayoutParams(0, dp(46), 1f))
        val close = TextView(this).apply {
            text = "×"; textSize = 30f; setTextColor(Color.DKGRAY); gravity = Gravity.CENTER
            setOnClickListener { closePanel() }
            contentDescription = "Cerrar selector"
        }
        toolbar.addView(close, LinearLayout.LayoutParams(dp(45), dp(46)))
        root.addView(toolbar)

        val search = EditText(this).apply {
            textSize = 15f
            hint = "Buscar etiquetas, nombres o paquetes"
            setSingleLine(true)
            setPadding(dp(12), dp(4), dp(12), dp(4))
            background = rounded(Color.rgb(242, 243, 246), 12)
        }
        root.addView(search, LinearLayout.LayoutParams(-1, dp(48)))
        val scroll = ScrollView(this).apply { isFillViewport = false }
        val grid = GridLayout(this).apply {
            columnCount = 4
            setPadding(0, dp(12), 0, dp(8))
        }
        scroll.addView(grid)
        root.addView(scroll, LinearLayout.LayoutParams(-1, dp(276)))
        val hint = TextView(this).apply {
            text = "Toca para copiar · Mantén pulsada la burbuja para ocultarla"
            textSize = 11f
            setTextColor(Color.rgb(125, 129, 145))
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(6))
        }
        root.addView(hint)
        val hide = TextView(this).apply {
            text = "Ocultar selector"
            textSize = 14f
            setTextColor(Color.rgb(71, 83, 174))
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(239, 241, 251), 14)
            setOnClickListener { stopSelf() }
        }
        root.addView(hide, LinearLayout.LayoutParams(-1, dp(44)))

        fun render(query: String) {
            grid.removeAllViews()
            val all = store.all()
            val filtered = all.filter { it.matches(query) }
                .sortedWith(compareByDescending<Sticker> { it.favorite }.thenByDescending { it.lastUsed })
                .take(64)
            val side = (resources.displayMetrics.widthPixels - dp(52)) / 4
            for (sticker in filtered) {
                val image = ImageView(this).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setPadding(dp(5), dp(5), dp(5), dp(5))
                    background = rounded(Color.rgb(245, 246, 248), 9)
                    contentDescription = sticker.name
                    try {
                        val drawable = ImageDecoder.decodeDrawable(
                            ImageDecoder.createSource(store.stickerFile(sticker))
                        )
                        setImageDrawable(drawable)
                        (drawable as? AnimatedImageDrawable)?.start()
                    } catch (_: Exception) { }
                    setOnClickListener {
                        try {
                            ClipboardBridge.copyGif(this@OverlayService, store.stickerFile(sticker))
                            store.markUsed(sticker)
                            Toast.makeText(this@OverlayService,
                                "GIF copiado · pega en WhatsApp", Toast.LENGTH_SHORT).show()
                            closePanel()
                        } catch (e: Exception) {
                            Toast.makeText(this@OverlayService,
                                "No se pudo copiar: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                grid.addView(image, GridLayout.LayoutParams().apply {
                    width = side; height = side
                    setMargins(dp(2), dp(2), dp(2), dp(2))
                })
            }
            if (filtered.isEmpty()) {
                grid.addView(TextView(this).apply {
                    text = "Sin resultados"; textSize = 15f
                    setTextColor(Color.GRAY)
                    setPadding(dp(10), dp(15), dp(10), dp(15))
                })
            }
        }
        render("")
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { render(s.toString()) }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        val params = WindowManager.LayoutParams(
            resources.displayMetrics.widthPixels - dp(16),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(66)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        panel = root
        try {
            windows.addView(root, params)
        } catch (e: Exception) {
            panel = null
            Toast.makeText(this, "No se pudo abrir el selector", Toast.LENGTH_SHORT).show()
        }
    }

    private fun closePanel() {
        panel?.let {
            try { windows.removeView(it) } catch (_: Exception) { }
        }
        panel = null
    }

    override fun onDestroy() {
        preferences.edit().putBoolean("overlay_enabled", false).apply()
        closePanel()
        bubble?.let { try { windows.removeView(it) } catch (_: Exception) { } }
        bubble = null
        super.onDestroy()
    }
}
