package com.zyntap.app.overlay

import android.content.Context
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import com.zyntap.app.overlay.OverlayUi.dp
import com.zyntap.app.overlay.OverlayUi.setBackgroundKeepPadding

/** Callback từ tab "Đặt điểm" lên dịch vụ nổi. */
interface PointsHost {
    /** Bật ghim ngắm; khi người dùng chốt vị trí, trả về tỉ lệ (fx, fy) của màn hình. */
    fun startPick(onPicked: (fx: Float, fy: Float) -> Unit)
    fun pointsChanged(button: MainButton)
    /** Kích thước màn hình hiện tại (pixel), để đổi tỉ lệ <-> toạ độ. */
    fun screenSize(): Pair<Int, Int>
}

/** Tab 2: chọn nút Main, nhập / chọn toạ độ điểm chạm và delay giữa các điểm. */
class PointsTab(
    private val context: Context,
    private val buttons: () -> List<MainButton>,
    private val host: PointsHost,
) : LinearLayout(context) {

    private var current: MainButton? = null
    private val coords = EditText(context)
    private val delayBox = EditText(context)
    private val spinner = Spinner(context)

    init {
        orientation = VERTICAL
        build()
    }

    private fun build() {
        if (buttons().isEmpty()) {
            addView(hint("Hãy tạo nút Main ở tab “Nút Main” trước."))
            return
        }

        addView(label("Nút Main"))
        spinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, buttons().map { it.name })
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                select(buttons()[pos])
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        addView(spinner)

        addView(label("Toạ độ điểm (mỗi dòng một điểm: x, y)"))
        coords.apply {
            setTextColor(OverlayUi.TEXT)
            setHintTextColor(OverlayUi.TEXT_DIM)
            hint = "540, 1200\n620, 1350"
            minLines = 4
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setBackgroundKeepPadding(OverlayUi.rounded(OverlayUi.SURFACE, dp(context, 12f).toFloat(), OverlayUi.BORDER, dp(context, 1f)))
            setPadding(dp(context, 12f), dp(context, 10f), dp(context, 12f), dp(context, 10f))
        }
        addView(coords, lp(top = 4))
        // Sửa tay xong thì lưu lại
        coords.setOnFocusChangeListener { _, focused -> if (!focused) commitTyped() }

        val row = LinearLayout(context).apply { orientation = HORIZONTAL }
        row.addView(button("Chọn điểm") { current?.let { commitTyped(); startPick(it) } },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(button("Xoá điểm cuối") { removeLast() },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(context, 8f) })
        addView(row, lp(top = 10))

        addView(label("Delay giữa các điểm (ms)"))
        delayBox.apply {
            setTextColor(OverlayUi.TEXT)
            setHintTextColor(OverlayUi.TEXT_DIM)
            hint = "300"
            inputType = InputType.TYPE_CLASS_NUMBER
            setBackgroundKeepPadding(OverlayUi.rounded(OverlayUi.SURFACE, dp(context, 12f).toFloat(), OverlayUi.BORDER, dp(context, 1f)))
            setPadding(dp(context, 12f), dp(context, 8f), dp(context, 12f), dp(context, 8f))
            setOnFocusChangeListener { _, focused -> if (!focused) commitDelay() }
        }
        addView(delayBox, lp(top = 4))

        select(buttons()[0])
    }

    private fun select(b: MainButton) {
        current = b
        coords.setText(b.points.joinToString("\n") { p ->
            val (w, h) = host.screenSize()
            "${(p.fx * w).toInt()}, ${(p.fy * h).toInt()}"
        })
        delayBox.setText(b.delayMs.toString())
    }

    private fun startPick(b: MainButton) {
        host.startPick { fx, fy ->
            b.points.add(TapPoint(fx, fy))
            select(b)
            host.pointsChanged(b)
        }
    }

    private fun removeLast() {
        val b = current ?: return
        if (b.points.isNotEmpty()) {
            b.points.removeAt(b.points.lastIndex)
            select(b)
            host.pointsChanged(b)
        }
    }

    /** Đọc các dòng "x, y" người dùng gõ tay, đổi sang tỉ lệ rồi lưu. */
    private fun commitTyped() {
        val b = current ?: return
        val (w, h) = host.screenSize()
        val parsed = coords.text.toString().lines().mapNotNull { line ->
            val nums = Regex("-?\\d+").findAll(line).map { it.value.toInt() }.toList()
            if (nums.size >= 2 && w > 0 && h > 0) {
                TapPoint((nums[0].toFloat() / w).coerceIn(0f, 1f), (nums[1].toFloat() / h).coerceIn(0f, 1f))
            } else null
        }
        if (parsed != b.points.toList()) {
            b.points.clear()
            b.points.addAll(parsed)
            host.pointsChanged(b)
        }
    }

    private fun commitDelay() {
        val b = current ?: return
        val v = delayBox.text.toString().toLongOrNull()?.coerceIn(0L, 60_000L) ?: return
        if (v != b.delayMs) {
            b.delayMs = v
            host.pointsChanged(b)
        }
    }

    private fun button(label: String, onClick: () -> Unit): Button = Button(context).apply {
        text = label
        setTextColor(OverlayUi.TEXT)
        isAllCaps = false
        setBackgroundKeepPadding(OverlayUi.rounded(OverlayUi.BLUE, dp(context, 12f).toFloat()))
        setOnClickListener { onClick() }
    }

    private fun label(text: String): TextView = TextView(context).apply {
        this.text = text
        setTextColor(OverlayUi.TEXT_DIM)
        textSize = 12f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(context, 12f), 0, 0)
    }

    private fun hint(text: String): TextView = TextView(context).apply {
        this.text = text
        setTextColor(OverlayUi.TEXT_DIM)
        textSize = 13f
    }

    private fun lp(top: Int = 0) = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
        topMargin = dp(context, top.toFloat())
    }
}
