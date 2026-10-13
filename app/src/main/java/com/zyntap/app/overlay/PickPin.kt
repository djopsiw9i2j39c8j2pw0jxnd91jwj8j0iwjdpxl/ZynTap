package com.zyntap.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.ImageView
import com.zyntap.app.R
import com.zyntap.app.overlay.OverlayUi.dp
import kotlin.math.abs

/**
 * Icon ghim toạ độ, nền trong suốt 100%.
 * Đầu nhọn của ghim là toạ độ. Kéo để di chuyển; chạm (không kéo) để chốt.
 */
class PickPin(
    private val context: Context,
    private val wm: WindowManager,
    initialX: Int,
    initialY: Int,
    private val onPicked: (px: Int, py: Int) -> Unit,
) {
    private val size = dp(context, 36f)
    private val view = ImageView(context).apply {
        setImageResource(R.drawable.ic_lucide_map_pin)
        background = null
        scaleType = ImageView.ScaleType.FIT_CENTER
    }
    private val params = WindowManager.LayoutParams(
        size, size,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.START or Gravity.TOP
        x = initialX - size / 2
        y = initialY - tipOffset()
    }

    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0
    private var moved = false
    private var added = false

    init {
        view.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = params.x; startY = params.y
                    moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (abs(dx) > dp(context, 4f) || abs(dy) > dp(context, 4f)) moved = true
                    if (moved) {
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        runCatching { wm.updateViewLayout(view, params) }
                    }
                }
                MotionEvent.ACTION_UP -> if (!moved) confirm()
            }
            true
        }
    }

    /** Vị trí đầu nhọn tính từ đỉnh trên của icon (icon 24x24, đầu nhọn ở y = 22). */
    private fun tipOffset(): Int = size * 22 / 24

    fun show() {
        if (added) return
        runCatching { wm.addView(view, params) }.onSuccess { added = true }
    }

    fun cancel() {
        if (!added) return
        runCatching { wm.removeView(view) }
        added = false
    }

    fun contains(rawX: Float, rawY: Float): Boolean =
        added && rawX >= params.x && rawX <= params.x + size && rawY >= params.y && rawY <= params.y + size

    private fun confirm() {
        val tipX = params.x + size / 2
        val tipY = params.y + tipOffset()
        cancel()
        onPicked(tipX, tipY)
    }
}
