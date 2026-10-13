package com.zyntap.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.zyntap.app.overlay.OverlayUi.dp
import com.zyntap.app.overlay.OverlayUi.setBackgroundKeepPadding
import kotlin.math.abs

/**
 * Nút tròn "Main" nổi trên màn hình.
 *  - Menu đang MỞ rộng (editable): kéo thả để di chuyển nút.
 *  - Menu đóng/thu gọn: nhấn / giữ / thả theo chế độ để chạy chuỗi điểm.
 *  - passThrough: chạm xuyên xuống app bên dưới (khi không đang chỉnh sửa).
 */
class FloatingMainButton(
    private val context: Context,
    private val wm: WindowManager,
    var model: MainButton,
    private val runner: MacroRunner,
    private val isEditable: () -> Boolean,
    private val onMoved: (MainButton) -> Unit,
    private val notify: (String) -> Unit,
) {
    private val view = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextColor(OverlayUi.TEXT)
        textSize = 13f
    }
    private val params = WindowManager.LayoutParams(
        0, 0,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.START or Gravity.TOP }

    private var added = false

    init {
        view.setOnTouchListener { _, e -> onTouch(e) }
        applyModel()
    }

    /** Áp cài đặt (tên, kích thước, độ mờ, chạm xuyên) lên nút đang hiển thị. */
    fun applyModel() {
        val px = dp(context, model.sizeDp.toFloat())
        params.width = px
        params.height = px
        params.x = model.screenX
        params.y = model.screenY
        view.text = model.name
        view.alpha = model.alpha
        view.setBackgroundKeepPadding(OverlayUi.circle(0xCC142036.toInt(), OverlayUi.CYAN, dp(context, 2f)))
        // Đang chỉnh sửa thì luôn chạm được để kéo; ngoài ra mới áp chạm xuyên
        val touchable = isEditable() || !model.passThrough
        params.flags = if (touchable) {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        if (added) runCatching { wm.updateViewLayout(view, params) }
    }

    fun show() {
        if (added) return
        runCatching { wm.addView(view, params) }.onSuccess { added = true }
    }

    fun hide() {
        if (!added) return
        runCatching { wm.removeView(view) }
        added = false
    }

    /** Toạ độ chạm (màn hình) có nằm trong nút này không, dùng để không thu menu khi chạm vào nút. */
    fun contains(rawX: Float, rawY: Float): Boolean =
        added && rawX >= params.x && rawX <= params.x + params.width &&
            rawY >= params.y && rawY <= params.y + params.height

    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0
    private var moved = false

    private fun onTouch(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX; downY = e.rawY
                startX = params.x; startY = params.y
                moved = false
                if (!isEditable() && model.mode == PressMode.HOLD) fire(repeat = true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.rawX - downX
                val dy = e.rawY - downY
                if (abs(dx) > dp(context, 6f) || abs(dy) > dp(context, 6f)) moved = true
                if (isEditable() && moved) {
                    params.x = startX + dx.toInt()
                    params.y = startY + dy.toInt()
                    runCatching { wm.updateViewLayout(view, params) }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (isEditable()) {
                    if (moved) {
                        model.screenX = params.x
                        model.screenY = params.y
                        onMoved(model)
                    }
                } else {
                    when (model.mode) {
                        PressMode.PRESS -> fire(repeat = false)
                        PressMode.HOLD -> runner.stop()
                        PressMode.RELEASE -> fire(repeat = false)
                    }
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (model.mode == PressMode.HOLD) runner.stop()
                return true
            }
        }
        return false
    }

    private fun fire(repeat: Boolean) {
        if (runner.isRunning && !repeat) return
        if (!runner.start(model, repeat)) {
            notify("“${model.name}” chưa có điểm click nào")
        }
    }
}
