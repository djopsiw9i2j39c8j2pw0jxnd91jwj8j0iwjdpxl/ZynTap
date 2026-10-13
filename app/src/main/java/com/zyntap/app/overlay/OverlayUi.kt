package com.zyntap.app.overlay

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View

/** Bảng màu và các hàm dựng nền dùng chung cho toàn bộ menu nổi (khớp Zyn trong ui/Theme.kt). */
internal object OverlayUi {
    const val BG = 0xF20D1524.toInt()
    const val SURFACE = 0xFF142036.toInt()
    const val BORDER = 0xFF1E2B44.toInt()
    const val CYAN = 0xFF19D3FF.toInt()
    const val BLUE = 0xFF2A6BFF.toInt()
    const val TEXT = 0xFFE8EEFA.toInt()
    const val TEXT_DIM = 0xFF8D9DBA.toInt()
    const val DANGER = 0xFFEF5B5B.toInt()

    fun dp(context: Context, v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, context.resources.displayMetrics).toInt()

    fun rounded(color: Int, radiusPx: Float, strokeColor: Int? = null, strokePx: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
            setColor(color)
            if (strokeColor != null && strokePx > 0) setStroke(strokePx, strokeColor)
        }

    fun circle(color: Int, strokeColor: Int? = null, strokePx: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            if (strokeColor != null && strokePx > 0) setStroke(strokePx, strokeColor)
        }

    /** Hiệu ứng gợn khi chạm, lấy từ hệ thống. */
    fun ripple(context: Context): android.graphics.drawable.Drawable? {
        val attrs = intArrayOf(android.R.attr.selectableItemBackground)
        val ta = context.obtainStyledAttributes(attrs)
        val d = ta.getDrawable(0)
        ta.recycle()
        return d
    }

    fun View.setBackgroundKeepPadding(d: android.graphics.drawable.Drawable?) {
        val l = paddingLeft; val t = paddingTop; val r = paddingRight; val b = paddingBottom
        background = d
        setPadding(l, t, r, b)
    }
}
