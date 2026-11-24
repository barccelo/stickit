package com.barccelo.stickit

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.IntentFilter
import androidx.core.content.ContextCompat
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
import java.lang.ref.WeakReference
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
    private var pickerController: StickerFloatingPicker? = null
    private var homeReceiverRegistered = false
    private val homeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_CLOSE_SYSTEM_DIALOGS) return
            val reason = intent.getStringExtra("reason").orEmpty()
            if (reason == "homekey" || reason == "recentapps") {
                pickerController?.dismiss()
                StickerShortcutService.active()?.closePicker()
            }
        }
    }
    private val preferences by lazy { getSharedPreferences("stickit_ui", Context.MODE_PRIVATE) }

    companion object {
        const val ACTION_HIDE = "com.barccelo.stickit.HIDE_SELECTOR"
        const val ACTION_SHOW = "com.barccelo.stickit.SHOW_SELECTOR"
        private var active: WeakReference<OverlayService>? = null
        fun dismissOpenPicker() {
            active?.get()?.pickerController?.dismiss()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        active = WeakReference(this)
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        windows = getSystemService(WINDOW_SERVICE) as WindowManager
        store = StickerStore(this)
        pickerController = StickerFloatingPicker(
            this, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, store
        )
        // Supplement accessibility window changes on devices that still emit
        // a system-dialog closing broadcast for Home/Recents.
        try {
            ContextCompat.registerReceiver(this, homeReceiver,
                IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS),
                ContextCompat.RECEIVER_EXPORTED)
            homeReceiverRegistered = true
        } catch (_: Exception) { }
        startPersistentNotification()
        showBubble()
        preferences.edit().putBoolean("overlay_enabled", true).apply()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> stopSelf()
            ACTION_SHOW -> {
                val accessible = StickerShortcutService.active()
                if (accessible != null) accessible.openPicker()
                else pickerController?.showPicker()
            }
        }
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

    private fun togglePanel() {
        // When permission has been explicitly enabled, the accessibility
        // window can appear at the height of the keyboard. Without it, use
        // the regular application overlay as a manual fallback.
        val accessible = StickerShortcutService.active()
        if (accessible != null) accessible.togglePicker()
        else {
            val picker = pickerController ?: return
            if (picker.isOpen) picker.hidePicker() else picker.showPicker()
        }
    }

    override fun onDestroy() {
        if (homeReceiverRegistered) {
            try { unregisterReceiver(homeReceiver) } catch (_: Exception) { }
            homeReceiverRegistered = false
        }
        if (active?.get() === this) active = null
        preferences.edit().putBoolean("overlay_enabled", false).apply()
        pickerController?.dismiss()
        pickerController = null
        bubble?.let { try { windows.removeView(it) } catch (_: Exception) { } }
        bubble = null
        super.onDestroy()
    }
}
