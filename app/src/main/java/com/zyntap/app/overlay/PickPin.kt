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
 * Ghim ngắm để chọn điểm: kéo ghim tới đâu thì đầu nhọn của ghim là toạ độ.
 * Chạm (không kéo) vào ghim lần nữa để chốt toạ độ; ghim sẽ tự biến mất.
 */
class PickPin(
    private val context: Context,
    private val wm: WindowManager,
    private val onPicked: (fx: Float, fy: Float) -> Unit,
) {
    private val size = dp(context, 44f)
    private val view = ImageView(context).apply {
        setImageResource(R.drawable.ic_lucide_map_pin)
        setBackgroundColor(0x66000000)
        setPadding(dp(context, 6f), dp(context, 6f), dp(context, 6f), dp(context, 6f))
    }
    private val params = WindowManager.LayoutParams(
        size, size,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.START or Gravity.TOP
        x = context.resources.displayMetrics.widthPixels / 2 - size / 2
        y = context.resources.displayMetrics.heightPixels / 2 - size / 2
    }

    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0
    private var moved = false

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
                    if (abs(dx) > dp(context, 6f) || abs(dy) > dp(context, 6f)) moved = true
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

    fun show() {
        runCatching { wm.addView(view, params) }
    }

    fun cancel() {
        runCatching { wm.removeView(view) }
    }

    /** Đầu nhọn của ghim (giữa cạnh dưới) đổi ra tỉ lệ màn hình. */
    private fun confirm() {
        val w = context.resources.displayMetrics.widthPixels.toFloat()
        val h = context.resources.displayMetrics.heightPixels.toFloat()
        val tipX = params.x + size / 2f
        val tipY = params.y + size.toFloat()
        cancel()
        onPicked((tipX / w).coerceIn(0f, 1f), (tipY / h).coerceIn(0f, 1f))
    }
}
