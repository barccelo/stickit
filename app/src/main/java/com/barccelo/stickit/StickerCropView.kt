package com.barccelo.stickit

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/** Normalized crop region, applied identically to every animation frame. */
data class StickerCropArea(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f
) {
    fun valid(): Boolean = left >= 0f && top >= 0f &&
        right <= 1f && bottom <= 1f &&
        right - left >= 0.12f && bottom - top >= 0.12f
}

/**
 * Lightweight native editor overlay with all eight crop handles.
 * Drag a corner, an edge, or the center of the region. No zoom gimmick.
 * Uses the SAME normalized coordinates as StickerArtworkEditor.render().
 */
class StickerCropView(context: Context) : View(context) {
    var crop: StickerCropArea = StickerCropArea()
        private set
    var onCropChange: ((StickerCropArea) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val mask = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(155, 8, 12, 20)
    }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2.3f * density
    }
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(155, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 0.8f * density
    }
    private val handle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val handleOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(68, 83, 173)
        style = Paint.Style.STROKE
        strokeWidth = 1.4f * density
    }

    fun setCrop(area: StickerCropArea) {
        if (crop != area && area.valid()) {
            crop = area
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        val x0 = crop.left * w
        val y0 = crop.top * h
        val x1 = crop.right * w
        val y1 = crop.bottom * h
        canvas.drawRect(0f, 0f, w, y0, mask)
        canvas.drawRect(0f, y1, w, h, mask)
        canvas.drawRect(0f, y0, x0, y1, mask)
        canvas.drawRect(x1, y0, w, y1, mask)
        canvas.drawRect(x0, y0, x1, y1, border)
        for (fraction in listOf(1f / 3f, 2f / 3f)) {
            val x = x0 + (x1 - x0) * fraction
            val y = y0 + (y1 - y0) * fraction
            canvas.drawLine(x, y0, x, y1, grid)
            canvas.drawLine(x0, y, x1, y, grid)
        }
        val mx = (x0 + x1) / 2f
        val my = (y0 + y1) / 2f
        for ((x, y) in listOf(x0 to y0, mx to y0, x1 to y0,
            x0 to my, x1 to my, x0 to y1, mx to y1, x1 to y1)) {
            val radius = 5.7f * density
            canvas.drawCircle(x, y, radius, handle)
            canvas.drawCircle(x, y, radius, handleOutline)
        }
    }

    private var dragMode = -1
    private var lastX = 0f
    private var lastY = 0f
    private val minCrop = 0.12f
    private val leftBit = 1
    private val rightBit = 2
    private val topBit = 4
    private val bottomBit = 8
    private val moveMode = 0

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x / w
                lastY = event.y / h
                val thresholdPx = 24f * density
                val coordinates = listOf(
                    Triple(crop.left, crop.top, leftBit or topBit),
                    Triple((crop.left + crop.right) / 2, crop.top, topBit),
                    Triple(crop.right, crop.top, rightBit or topBit),
                    Triple(crop.left, (crop.top + crop.bottom) / 2, leftBit),
                    Triple(crop.right, (crop.top + crop.bottom) / 2, rightBit),
                    Triple(crop.left, crop.bottom, leftBit or bottomBit),
                    Triple((crop.left + crop.right) / 2, crop.bottom, bottomBit),
                    Triple(crop.right, crop.bottom, rightBit or bottomBit)
                )
                val nearest = coordinates.minByOrNull { (x, y, _) ->
                    hypot((event.x - x * w).toDouble(),
                        (event.y - y * h).toDouble())
                }
                val distance = nearest?.let { (x, y, _) ->
                    hypot((event.x - x * w).toDouble(),
                        (event.y - y * h).toDouble())
                } ?: Double.MAX_VALUE
                dragMode = when {
                    nearest != null && distance <= thresholdPx -> nearest.third
                    lastX in crop.left..crop.right &&
                        lastY in crop.top..crop.bottom -> moveMode
                    else -> -1
                }
                if (dragMode >= 0) parent?.requestDisallowInterceptTouchEvent(true)
                return dragMode >= 0
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragMode < 0) return false
                val dx = event.x / w - lastX
                val dy = event.y / h - lastY
                lastX = event.x / w
                lastY = event.y / h
                val next = if (dragMode == moveMode) {
                    val tx = dx.coerceIn(-crop.left, 1f - crop.right)
                    val ty = dy.coerceIn(-crop.top, 1f - crop.bottom)
                    crop.copy(left = crop.left + tx, right = crop.right + tx,
                        top = crop.top + ty, bottom = crop.bottom + ty)
                } else {
                    crop.copy(
                        left = if (dragMode and leftBit != 0)
                            (crop.left + dx).coerceIn(0f, crop.right - minCrop)
                        else crop.left,
                        right = if (dragMode and rightBit != 0)
                            (crop.right + dx).coerceIn(crop.left + minCrop, 1f)
                        else crop.right,
                        top = if (dragMode and topBit != 0)
                            (crop.top + dy).coerceIn(0f, crop.bottom - minCrop)
                        else crop.top,
                        bottom = if (dragMode and bottomBit != 0)
                            (crop.bottom + dy).coerceIn(crop.top + minCrop, 1f)
                        else crop.bottom
                    )
                }
                if (next.valid()) {
                    crop = next
                    invalidate()
                    onCropChange?.invoke(next)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val handled = dragMode >= 0
                dragMode = -1
                parent?.requestDisallowInterceptTouchEvent(false)
                return handled
            }
        }
        return false
    }
}
