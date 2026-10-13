package com.zyntap.app.overlay

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.zyntap.app.R
import com.zyntap.app.overlay.OverlayUi.dp
import com.zyntap.app.overlay.OverlayUi.setBackgroundKeepPadding

/**
 * Bảng menu nổi, có 3 trạng thái:
 *  - Mở rộng: thanh đầu (logo, tên, bút chì, −, ×), tab dọc bên trái, nội dung bên phải.
 *  - Chỉnh sửa (bút chì bật): cùng giao diện, nhưng chạm ra ngoài không đóng menu,
 *    để kéo thả các nút Main.
 *  - Thu gọn (−): chỉ còn thanh nhỏ gồm logo, tên, −, ×.
 * Nút × luôn hỏi xác nhận trước khi đóng.
 */
class OverlayPanel(
    private val context: Context,
    private val tabNames: List<String>,
    private val contentFor: (index: Int) -> View,
    private val onCollapsedChanged: (collapsed: Boolean) -> Unit,
    private val onEditingChanged: (editing: Boolean) -> Unit,
    private val onCloseConfirmed: () -> Unit,
    private val attachDrag: (View) -> Unit,
) {
    val root = FrameLayout(context)

    private val expanded = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val bar = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val rail = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val contentHolder = FrameLayout(context)
    private val confirmBar: LinearLayout
    private val pencil: ImageView
    private val tabViews = mutableListOf<TextView>()
    private var selected = 0

    var isCollapsed = false
        private set

    var isEditing = false
        private set

    init {
        expanded.setBackgroundKeepPadding(OverlayUi.rounded(OverlayUi.BG, dp(context, 22f).toFloat(), OverlayUi.BORDER, dp(context, 1f)))
        expanded.setPadding(dp(context, 12f), dp(context, 12f), dp(context, 12f), dp(context, 12f))

        pencil = iconButton(R.drawable.ic_lucide_pencil, "Chỉnh sửa vị trí nút") { toggleEditing() }
        expanded.addView(header())

        val body = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        buildRail()
        body.addView(rail, LinearLayout.LayoutParams(dp(context, 84f), LinearLayout.LayoutParams.MATCH_PARENT))
        body.addView(contentHolder, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
            marginStart = dp(context, 10f)
        })
        expanded.addView(body, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            topMargin = dp(context, 12f)
        })

        confirmBar = confirmBar().apply { visibility = View.GONE }
        expanded.addView(confirmBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(context, 12f)
        })

        buildBar()

        root.addView(expanded, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(bar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))

        select(0)
        applyVisibility()
    }

    /** Thu gọn thành thanh (true) hoặc mở rộng lại (false). */
    fun setCollapsed(collapsed: Boolean) {
        isCollapsed = collapsed
        if (collapsed) setEditing(false)
        applyVisibility()
        onCollapsedChanged(collapsed)
    }

    fun setEditing(editing: Boolean) {
        if (isEditing == editing) return
        isEditing = editing
        pencil.setColorFilter(if (editing) OverlayUi.CYAN else OverlayUi.TEXT)
        onEditingChanged(editing)
    }

    fun toggleEditing() {
        if (!isCollapsed) setEditing(!isEditing)
    }

    fun showConfirm(show: Boolean) {
        confirmBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun applyVisibility() {
        expanded.visibility = if (isCollapsed) View.GONE else View.VISIBLE
        bar.visibility = if (isCollapsed) View.VISIBLE else View.GONE
    }

    // ---- thanh đầu (mở rộng): logo, tên, bút chì, −, × ----
    private fun header(): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(logo(30f))
        row.addView(appName(17f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(context, 10f)
        })
        row.addView(pencil, LinearLayout.LayoutParams(dp(context, 36f), dp(context, 36f)))
        row.addView(iconButton(R.drawable.ic_lucide_minus, "Thu gọn menu") { setCollapsed(true) }.apply {
            (layoutParams as LinearLayout.LayoutParams).marginStart = dp(context, 8f)
        })
        row.addView(iconButton(R.drawable.ic_lucide_x, "Đóng menu") { showConfirm(true) }.apply {
            (layoutParams as LinearLayout.LayoutParams).marginStart = dp(context, 8f)
        })
        attachDrag(row)
        return row
    }

    // ---- thanh thu gọn: logo, tên, −, × ----
    private fun buildBar() {
        bar.setBackgroundKeepPadding(OverlayUi.rounded(OverlayUi.BG, dp(context, 18f).toFloat(), OverlayUi.BORDER, dp(context, 1f)))
        bar.setPadding(dp(context, 10f), dp(context, 8f), dp(context, 10f), dp(context, 8f))
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.addView(logo(24f))
        bar.addView(appName(15f).apply { setPadding(dp(context, 10f), 0, dp(context, 12f), 0) })
        bar.addView(iconButton(R.drawable.ic_lucide_minus, "Mở menu") { setCollapsed(false) })
        bar.addView(iconButton(R.drawable.ic_lucide_x, "Đóng menu") {
            setCollapsed(false)
            showConfirm(true)
        }.apply { (layoutParams as LinearLayout.LayoutParams).marginStart = dp(context, 6f) })
        attachDrag(bar)
    }

    private fun logo(sizeDp: Float): ImageView = ImageView(context).apply {
        setImageResource(R.drawable.logo_transparent)
        scaleType = ImageView.ScaleType.FIT_CENTER
        layoutParams = LinearLayout.LayoutParams(dp(context, sizeDp), dp(context, sizeDp))
    }

    private fun appName(sp: Float): TextView = TextView(context).apply {
        text = "ZynTap"
        setTextColor(OverlayUi.TEXT)
        textSize = sp
        setTypeface(typeface, Typeface.BOLD)
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

    // ---- tab dọc bên trái ----
    private fun buildRail() {
        tabNames.forEachIndexed { i, name ->
            val tab = TextView(context).apply {
                text = name
                gravity = Gravity.CENTER
                textSize = 14f
                setPadding(dp(context, 6f), dp(context, 12f), dp(context, 6f), dp(context, 12f))
                setOnClickListener { select(i) }
            }
            tabViews += tab
            rail.addView(tab, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(context, 8f)
            })
        }
    }

    private fun select(index: Int) {
        selected = index
        tabViews.forEachIndexed { i, tab ->
            val on = i == index
            tab.setTextColor(if (on) OverlayUi.TEXT else OverlayUi.TEXT_DIM)
            tab.setTypeface(tab.typeface, if (on) Typeface.BOLD else Typeface.NORMAL)
            tab.setBackgroundKeepPadding(
                if (on) OverlayUi.rounded(OverlayUi.BLUE, dp(context, 12f).toFloat())
                else OverlayUi.rounded(OverlayUi.SURFACE, dp(context, 12f).toFloat()),
            )
        }
        contentHolder.removeAllViews()
        contentHolder.addView(ScrollView(context).apply {
            isFillViewport = true
            addView(contentFor(index))
        }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    // ---- xác nhận đóng ----
    private fun confirmBar(): LinearLayout {
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundKeepPadding(OverlayUi.rounded(OverlayUi.SURFACE, dp(context, 14f).toFloat(), OverlayUi.BORDER, dp(context, 1f)))
            setPadding(dp(context, 14f), dp(context, 12f), dp(context, 14f), dp(context, 12f))
        }
        box.addView(TextView(context).apply {
            text = "Đóng ZynTap và ẩn toàn bộ menu nổi?"
            setTextColor(OverlayUi.TEXT)
            textSize = 14f
        })
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(actionText("Huỷ", OverlayUi.TEXT_DIM) { showConfirm(false) },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(actionText("Đóng", OverlayUi.DANGER) { onCloseConfirmed() },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        box.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(context, 8f)
        })
        return box
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
}
