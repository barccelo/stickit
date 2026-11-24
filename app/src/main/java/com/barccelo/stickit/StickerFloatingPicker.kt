package com.barccelo.stickit

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.PixelFormat
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import kotlin.math.roundToInt

/**
 * A keyboard-sized native picker shared by the floating bubble and the
 * explicitly enabled accessibility shortcut. No keyboard replacement.
 *
 * A TYPE_ACCESSIBILITY_OVERLAY is used only by the system-enabled
 * AccessibilityService; the regular floating bubble uses application overlay.
 */
class StickerFloatingPicker(
    private val context: Context,
    private val windowType: Int,
    private val store: StickerStore = StickerStore(context)
) {
    private val windows = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var picker: View? = null
    private var pickerParams: WindowManager.LayoutParams? = null
    private var suggestions: View? = null
    private var suggestionSignature = ""
    private var suggestionParams: WindowManager.LayoutParams? = null

    val isOpen: Boolean get() = picker != null

    private val dark: Boolean get() = AppearanceSettings.isDark(context)
    private val ink: Int get() = if (dark) Color.rgb(240, 242, 250)
        else Color.rgb(28, 34, 51)
    private val accent: Int get() = if (dark) Color.rgb(174, 187, 255)
        else Color.rgb(72, 85, 178)
    private val accentInk: Int get() = if (dark) Color.rgb(22, 31, 62)
        else Color.WHITE
    private val muted: Int get() = if (dark) Color.rgb(170, 179, 200)
        else Color.rgb(127, 135, 153)
    private val panelBg: Int get() = if (dark) Color.rgb(27, 30, 41)
        else Color.rgb(250, 250, 253)
    private val fieldBg: Int get() = if (dark) Color.rgb(40, 44, 59)
        else Color.WHITE
    private val tileBg: Int get() = if (dark) Color.rgb(42, 47, 63)
        else Color.rgb(241, 242, 248)
    private val edge: Int get() = if (dark) Color.rgb(61, 68, 87)
        else Color.rgb(220, 223, 236)
    private val chipBg: Int get() = if (dark) Color.rgb(47, 53, 73)
        else Color.rgb(236, 238, 249)
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).roundToInt()
    private fun unDp(value: Int) = (value / context.resources.displayMetrics.density).roundToInt()

    private fun shape(color: Int, radius: Int = 18, lineColor: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (lineColor != null) setStroke(dp(1), lineColor)
        }

    private fun label(
        value: String, size: Float = 14f, color: Int = ink
    ) = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER_VERTICAL
        includeFontPadding = false
    }

    fun showPicker() {
        hideSuggestions()
        if (picker != null) return
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = shape(panelBg, 24, edge)
            setPadding(dp(12), dp(6), dp(12), dp(10))
            isFocusableInTouchMode = true
        }
        val height = ShortcutSettings.panelHeightDp(context)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            dp(height),
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        }

        val header = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }
        val handle = label("━━━━", 16f, muted).apply {
            gravity = Gravity.CENTER
            contentDescription = "Arrastrar hacia arriba o abajo para ajustar el tamaño"
        }
        header.addView(handle, LinearLayout.LayoutParams(dp(56), dp(38)))
        header.addView(label("stickit", 17f).apply {
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, dp(38), 1f))

        fun action(title: String, description: String, callback: () -> Unit) {
            header.addView(label(title, 22f, accent).apply {
                gravity = Gravity.CENTER
                contentDescription = description
                setOnClickListener { callback() }
            }, LinearLayout.LayoutParams(dp(42), dp(38)))
        }
        var showQuickCreation: (() -> Unit)? = null
        action("⊕", "Abrir opciones de creación") { showQuickCreation?.invoke() }
        action("×", "Cerrar selector") { hidePicker() }
        root.addView(header)
        var startY = 0f
        var startHeight = 0
        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    startHeight = unDp(params.height)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val delta = unDp((startY - event.rawY).roundToInt())
                    val newSize = (startHeight + delta).coerceIn(230, 540)
                    params.height = dp(newSize)
                    try { windows.updateViewLayout(root, params) } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    ShortcutSettings.setPanelHeightDp(context, unDp(params.height))
                    true
                }
                else -> false
            }
        }

        val search = EditText(context).apply {
            hint = "Buscar por nombre, etiqueta, emoji o paquete"
            textSize = 14f
            setSingleLine(true)
            setTextColor(ink)
            setHintTextColor(muted)
            setPadding(dp(14), 0, dp(14), 0)
            background = shape(fieldBg, 13, edge)
            contentDescription = "Buscar stickers"
        }
        root.addView(search, LinearLayout.LayoutParams(-1, dp(45)))
        val filters = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(7), 0, dp(7))
        }
        val filterScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(filters)
        }
        root.addView(filterScroll, LinearLayout.LayoutParams(-1, dp(45)))
        val grid = android.widget.GridView(context).apply {
            numColumns = 4
            verticalSpacing = dp(6)
            horizontalSpacing = dp(6)
            setPadding(dp(2), dp(4), dp(2), dp(4))
            clipToPadding = false
            isVerticalScrollBarEnabled = false
            selector = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
            stretchMode = android.widget.GridView.STRETCH_COLUMN_WIDTH
        }
        val noResults = label("No hay stickers para esta búsqueda", 14f,
            muted).apply {
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        val holder = FrameLayout(context).apply {
            addView(grid, FrameLayout.LayoutParams(-1, -1))
            addView(noResults, FrameLayout.LayoutParams(-1, -1))
        }
        root.addView(holder, LinearLayout.LayoutParams(-1, 0, 1f))
        val footer = label("Toca para copiar · después pega en WhatsApp", 11f,
            muted).apply { gravity = Gravity.CENTER }
        root.addView(footer, LinearLayout.LayoutParams(-1, dp(26)))

        // Keep the options within the overlay instead of opening Files
        // immediately; clipboard access itself happens in the foreground app.
        val createContents = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(7), dp(4), dp(7))
        }
        val createMenu = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            visibility = View.GONE
            addView(createContents)
        }
        fun showLibrary(show: Boolean) {
            createMenu.visibility = if (show) View.GONE else View.VISIBLE
            val state = if (show) View.VISIBLE else View.GONE
            search.visibility = state
            filterScroll.visibility = state
            holder.visibility = state
            footer.visibility = state
        }
        showQuickCreation = { showLibrary(false) }
        createContents.addView(label("‹  Volver a mis stickers", 13f, accent).apply {
            setPadding(dp(8), 0, 0, 0)
            setOnClickListener { showLibrary(true) }
        }, LinearLayout.LayoutParams(-1, dp(33)))
        createContents.addView(label("Crear sticker", 17f, ink).apply {
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(8), 0, 0, 0)
        }, LinearLayout.LayoutParams(-1, dp(35)))
        fun quickAction(actionName: String) {
            try {
                context.startActivity(Intent(context, MainActivity::class.java).apply {
                    action = actionName
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                })
                hidePicker()
            } catch (_: Exception) {
                Toast.makeText(context, "No se pudo abrir el creador",
                    Toast.LENGTH_SHORT).show()
            }
        }
        fun option(symbol: String, title: String, description: String, actionName: String) {
            val line = LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                orientation = LinearLayout.HORIZONTAL
                background = shape(fieldBg, 14, edge)
                setPadding(dp(12), dp(5), dp(12), dp(5))
                contentDescription = title
                isClickable = true
                isFocusable = true
                setOnClickListener { quickAction(actionName) }
            }
            line.addView(label(symbol, 21f, accent).apply { gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(dp(43), -1))
            line.addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                orientation = LinearLayout.VERTICAL
                addView(label(title, 14f, ink).apply {
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })
                addView(label(description, 11f, muted))
            }, LinearLayout.LayoutParams(0, -1, 1f))
            createContents.addView(line, LinearLayout.LayoutParams(-1, dp(67)).apply {
                bottomMargin = dp(9)
            })
        }
        option("▣", "Pegar imagen copiada", "Desde el portapapeles",
            MainActivity.ACTION_QUICK_PASTE)
        option("▧", "Elegir imagen, GIF o vídeo", "Galería, archivos o descargas",
            MainActivity.ACTION_QUICK_IMPORT)
        root.addView(createMenu, 1, LinearLayout.LayoutParams(-1, 0, 1f))

        val packs = store.packNames()
        var activeFilter = "Todos"
        val choices = listOf("Todos", "Favoritos") + packs.map { "Paquete: $it" }
        var visibleItems = emptyList<Sticker>()
        // Recycle views so even a large collection does not decode hundreds
        // of animated GIFs simultaneously. Only visible cells are loaded.
        val adapter = object : android.widget.BaseAdapter() {
            override fun getCount(): Int = visibleItems.size
            override fun getItem(position: Int): Any = visibleItems[position]
            override fun getItemId(position: Int): Long = visibleItems[position].id
            override fun getView(position: Int, convertView: View?,
                parent: android.view.ViewGroup): View {
                val view = (convertView as? ImageView) ?: ImageView(context).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setPadding(dp(5), dp(5), dp(5), dp(5))
                    background = shape(tileBg, 12)
                    layoutParams = android.widget.AbsListView.LayoutParams(-1, dp(82))
                }
                (view.drawable as? AnimatedImageDrawable)?.stop()
                view.setImageDrawable(null)
                val sticker = visibleItems[position]
                view.contentDescription = "Copiar ${sticker.name}"
                try {
                    val drawable = ImageDecoder.decodeDrawable(
                        ImageDecoder.createSource(store.stickerFile(sticker)))
                    view.setImageDrawable(drawable)
                    (drawable as? AnimatedImageDrawable)?.start()
                } catch (_: Exception) { }
                view.setOnClickListener { copyAndClose(sticker) }
                return view
            }
        }
        grid.adapter = adapter
        fun render() {
            val query = search.text.toString().trim()
            visibleItems = store.all().asSequence()
                .filter { it.matches(query) }
                .filter {
                    when {
                        activeFilter == "Favoritos" -> it.favorite
                        activeFilter.startsWith("Paquete: ") ->
                            it.pack == activeFilter.removePrefix("Paquete: ")
                        else -> true
                    }
                }
                .sortedWith(compareByDescending<Sticker> { it.favorite }
                    .thenByDescending { it.lastUsed })
                .toList()
            adapter.notifyDataSetChanged()
            noResults.visibility = if (visibleItems.isEmpty()) View.VISIBLE else View.GONE
            grid.visibility = if (visibleItems.isEmpty()) View.GONE else View.VISIBLE
        }
        for (choice in choices) {
            filters.addView(label(if (choice.startsWith("Paquete: ")) choice.removePrefix("Paquete: ")
                else choice, 12f, if (choice == activeFilter) accentInk
                else accent).apply {
                gravity = Gravity.CENTER
                setPadding(dp(12), 0, dp(12), 0)
                background = shape(if (choice == activeFilter) accent
                    else chipBg, 14)
                setOnClickListener {
                    activeFilter = choice
                    filters.children().forEachIndexed { index, view ->
                        view.background = shape(
                            if (choices[index] == choice) accent
                            else chipBg, 14)
                        (view as TextView).setTextColor(
                            if (choices[index] == choice) accentInk
                            else accent)
                    }
                    render()
                }
            }, LinearLayout.LayoutParams(-2, dp(32)).apply {
                setMargins(0, 0, dp(6), 0)
            })
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                render()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        render()
        try {
            windows.addView(root, params)
            picker = root
            pickerParams = params
            root.requestFocus()
        } catch (_: Exception) {
            Toast.makeText(context, "No se pudo abrir el selector", Toast.LENGTH_SHORT).show()
        }
    }

    private fun LinearLayout.children(): List<View> =
        (0 until childCount).map { getChildAt(it) }

    private fun imageView(sticker: Sticker): ImageView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        setPadding(dp(5), dp(5), dp(5), dp(5))
        background = shape(tileBg, 12)
        try {
            val drawable = ImageDecoder.decodeDrawable(
                ImageDecoder.createSource(store.stickerFile(sticker))
            )
            setImageDrawable(drawable)
            (drawable as? AnimatedImageDrawable)?.start()
        } catch (_: Exception) { }
    }

    private fun copyAndClose(sticker: Sticker, removeEmoji: (() -> Boolean)? = null) {
        try {
            val file = store.stickerFile(sticker)
            ClipboardBridge.copyGif(context, file)
            // Deleting a trigger requires a guarded accessibility action.
            // Do it only for an emoji suggestion, never for ordinary search.
            val cleared = removeEmoji?.invoke() ?: true
            store.markUsed(sticker)
            hidePicker()
            hideSuggestions()
            Toast.makeText(context, if (cleared)
                "Sticker copiado · pégalo en WhatsApp"
                else "Sticker copiado; borra el emoji manualmente",
                Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "No se pudo copiar: ${e.message}",
                Toast.LENGTH_SHORT).show()
        }
    }

    fun hidePicker() {
        picker?.let { try { windows.removeView(it) } catch (_: Exception) {} }
        picker = null
        pickerParams = null
    }

    /**
     * Compact draggable suggestion strip. Its position, including vertical
     * offset above the keyboard, is saved per device, never per conversation.
     */
    fun showSuggestions(matchedEmoji: String, stickers: List<Sticker>,
        removeEmoji: (() -> Boolean)? = null) {
        if (isOpen || stickers.isEmpty()) {
            hideSuggestions()
            return
        }
        val latest = matchedEmoji + ":" + stickers.take(5).joinToString { it.id.toString() }
        if (suggestions != null && latest == suggestionSignature) return
        hideSuggestions()
        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(7), dp(6), dp(7), dp(6))
            background = shape(panelBg, 19, edge)
            elevation = dp(4).toFloat()
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            dp(64),
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            x = dp(ShortcutSettings.suggestX(context))
            y = dp(ShortcutSettings.suggestY(context))
        }
        val grab = label("⋮⋮", 18f, muted).apply {
            gravity = Gravity.CENTER
            contentDescription = "Mover sugerencias de stickers"
        }
        bar.addView(grab, LinearLayout.LayoutParams(dp(22), -1))
        var xStart = 0f
        var yStart = 0f
        var originX = 0
        var originY = 0
        grab.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    xStart = event.rawX; yStart = event.rawY
                    originX = params.x; originY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val available = context.resources.displayMetrics.widthPixels
                    params.x = (originX + (event.rawX - xStart).roundToInt())
                        .coerceIn(0, (available - dp(100)).coerceAtLeast(0))
                    params.y = (originY - (event.rawY - yStart).roundToInt())
                        .coerceIn(0, context.resources.displayMetrics.heightPixels - dp(80))
                    try { windows.updateViewLayout(bar, params) } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    ShortcutSettings.saveSuggestionPosition(
                        context, unDp(params.x), unDp(params.y))
                    true
                }
                else -> false
            }
        }
        stickers.take(5).forEach { sticker ->
            val view = imageView(sticker)
            view.contentDescription = "Copiar sugerencia: ${sticker.name}"
            view.setOnClickListener { copyAndClose(sticker, removeEmoji) }
            bar.addView(view, LinearLayout.LayoutParams(dp(50), dp(50)).apply {
                setMargins(dp(3), 0, dp(3), 0)
            })
        }
        val close = label("×", 22f, muted).apply {
            gravity = Gravity.CENTER
            contentDescription = "Cerrar sugerencias"
            setOnClickListener { hideSuggestions() }
        }
        bar.addView(close, LinearLayout.LayoutParams(dp(28), -1))
        try {
            windows.addView(bar, params)
            suggestions = bar
            suggestionParams = params
            suggestionSignature = latest
        } catch (_: Exception) {
            suggestionSignature = ""
        }
    }

    fun hideSuggestions() {
        suggestions?.let { try { windows.removeView(it) } catch (_: Exception) {} }
        suggestions = null
        suggestionParams = null
        suggestionSignature = ""
    }

    fun dismiss() {
        hidePicker()
        hideSuggestions()
    }
}
