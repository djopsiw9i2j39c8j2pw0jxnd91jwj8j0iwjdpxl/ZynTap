package com.zyntap.app.overlay

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.zyntap.app.R
import com.zyntap.app.overlay.OverlayUi.dp
import com.zyntap.app.overlay.OverlayUi.setBackgroundKeepPadding

/**
 * Khung menu nổi: thanh đầu (logo + tên ZynTap + nút thu / đóng), thanh tab, vùng nội dung.
 * Nút "−" thu menu về tay cầm; nút "×" hỏi xác nhận trước khi đóng hẳn.
 */
class PanelFrame(
    private val context: Context,
    private val tabNames: List<String>,
    private val contentFor: (index: Int) -> View,
    private val onCollapse: () -> Unit,
    private val onCloseConfirmed: () -> Unit,
) {
    val root: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundKeepPadding(OverlayUi.rounded(OverlayUi.BG, dp(context, 22f).toFloat(), OverlayUi.BORDER, dp(context, 1f)))
        setPadding(dp(context, 14f), dp(context, 14f), dp(context, 14f), dp(context, 14f))
    }

    private val contentHolder = FrameLayout(context)
    private val tabViews = mutableListOf<TextView>()
    private val confirmBar: LinearLayout
    private var selected = 0

    init {
        root.addView(header())
        root.addView(tabBar())
        root.addView(contentHolder, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(context, 12f) })

        confirmBar = confirmBar()
        confirmBar.visibility = View.GONE
        root.addView(confirmBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(context, 12f) })

        select(0)
    }

    private fun header(): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(ImageView(context).apply {
            setImageResource(R.drawable.logo_transparent)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(dp(context, 30f), dp(context, 30f)))
        row.addView(TextView(context).apply {
            text = "ZynTap"
            setTextColor(OverlayUi.TEXT)
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(context, 10f)
        })
        row.addView(iconButton(R.drawable.ic_lucide_minus, "Thu menu") { onCollapse() })
        row.addView(iconButton(R.drawable.ic_lucide_x, "Đóng menu") { showConfirm(true) }.apply {
            (layoutParams as LinearLayout.LayoutParams).marginStart = dp(context, 8f)
        })
        return row
    }

    private fun iconButton(iconRes: Int, desc: String, onClick: () -> Unit): ImageView =
        ImageView(context).apply {
            setImageResource(iconRes)
            setColorFilter(OverlayUi.TEXT)
            contentDescription = desc
            scaleType = ImageView.ScaleType.CENTER
            setBackgroundKeepPadding(OverlayUi.circle(OverlayUi.SURFACE))
            setPadding(dp(context, 8f), dp(context, 8f), dp(context, 8f), dp(context, 8f))
            layoutParams = LinearLayout.LayoutParams(dp(context, 36f), dp(context, 36f))
            setOnClickListener { onClick() }
        }

    private fun tabBar(): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundKeepPadding(OverlayUi.rounded(OverlayUi.SURFACE, dp(context, 14f).toFloat()))
            setPadding(dp(context, 4f), dp(context, 4f), dp(context, 4f), dp(context, 4f))
        }
        tabNames.forEachIndexed { i, name ->
            val tab = TextView(context).apply {
                text = name
                gravity = Gravity.CENTER
                textSize = 14f
                setPadding(0, dp(context, 9f), 0, dp(context, 9f))
                setOnClickListener { select(i) }
            }
            tabViews += tab
            row.addView(tab, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        return row
    }

    private fun select(index: Int) {
        selected = index
        tabViews.forEachIndexed { i, tab ->
            val on = i == index
            tab.setTextColor(if (on) OverlayUi.TEXT else OverlayUi.TEXT_DIM)
            tab.setTypeface(tab.typeface, if (on) Typeface.BOLD else Typeface.NORMAL)
            tab.setBackgroundKeepPadding(if (on) OverlayUi.rounded(OverlayUi.BLUE, dp(context, 10f).toFloat()) else null)
        }
        contentHolder.removeAllViews()
        contentHolder.addView(contentFor(index))
    }

    /** Thanh xác nhận đóng: tránh bấm nhầm. */
    private fun confirmBar(): LinearLayout {
        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundKeepPadding(OverlayUi.rounded(OverlayUi.SURFACE, dp(context, 14f).toFloat(), OverlayUi.BORDER, dp(context, 1f)))
            setPadding(dp(context, 14f), dp(context, 12f), dp(context, 14f), dp(context, 12f))
        }
        bar.addView(TextView(context).apply {
            text = "Đóng ZynTap và ẩn toàn bộ menu nổi?"
            setTextColor(OverlayUi.TEXT)
            textSize = 14f
        })
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(actionText("Huỷ", OverlayUi.TEXT_DIM) { showConfirm(false) },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(actionText("Đóng", OverlayUi.DANGER) { onCloseConfirmed() },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(context, 8f)
        })
        return bar
    }

    private fun actionText(label: String, color: Int, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(color)
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(context, 8f), 0, dp(context, 8f))
            setOnClickListener { onClick() }
        }

    fun showConfirm(show: Boolean) {
        confirmBar.visibility = if (show) View.VISIBLE else View.GONE
    }
}
