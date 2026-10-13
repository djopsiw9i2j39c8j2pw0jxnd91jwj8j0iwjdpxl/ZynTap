package com.zyntap.app.overlay

import android.content.Context
import android.graphics.Typeface
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import com.zyntap.app.R
import com.zyntap.app.overlay.OverlayUi.dp
import com.zyntap.app.overlay.OverlayUi.setBackgroundKeepPadding

/** Callback từ tab "Đặt điểm" lên dịch vụ nổi. */
interface PointsHost {
    /** Hiện ghim ngắm tại (px, py). Khi chốt, gọi onPicked với toạ độ pixel. */
    fun startPick(initialX: Int, initialY: Int, onPicked: (px: Int, py: Int) -> Unit)
    fun cancelPick()
    fun pointsChanged(button: MainButton)
    fun screenSize(): Pair<Int, Int>
}

/**
 * Tab "Đặt điểm": mỗi dòng là một điểm click dạng
 *   [Click tên]  [x]  [y]  ×
 * Chạm ô x hoặc y lần 1 -> hiện ghim để điều khiển; chạm ghim -> điền toạ độ vào ô.
 * Chạm ô đó lần 2 (khi ghim đang hiện) -> hiện bàn phím để gõ tay.
 * Ô Delay: chạm một lần là hiện bàn phím.
 */
class PointsTab(
    private val context: Context,
    private val buttons: () -> List<MainButton>,
    private val host: PointsHost,
) : LinearLayout(context) {

    private var current: MainButton? = null
    /** Ô đang có ghim: (chỉ số dòng, 0 = x / 1 = y). */
    private var pinKey: Pair<Int, Int>? = null

    init {
        orientation = VERTICAL
        build()
    }

    private fun build() {
        removeAllViews()
        if (buttons().isEmpty()) {
            addView(hint("Hãy tạo nút Main ở tab “Nút Main” trước."))
            return
        }
        if (current == null || buttons().none { it.id == current?.id }) current = buttons()[0]

        addView(label("Nút Main"))
        val spinner = Spinner(context)
        spinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, buttons().map { it.name })
        spinner.setSelection(buttons().indexOfFirst { it.id == current?.id }.coerceAtLeast(0))
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val picked = buttons()[pos]
                if (picked.id != current?.id) {
                    current = picked
                    host.cancelPick(); pinKey = null
                    build()
                }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        addView(spinner)

        val b = current ?: return
        addView(label("Các điểm click"))
        b.points.forEachIndexed { i, _ -> addView(pointRow(b, i)) }

        addView(button("+ Thêm click") {
            b.points.add(TapPoint(name = "Click ${b.points.size + 1}"))
            host.pointsChanged(b)
            build()
        }, lp(top = 10))

        addView(label("Delay"))
        val delayRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        delayRow.addView(TextView(context).apply {
            text = "Delay"
            setTextColor(OverlayUi.TEXT)
            textSize = 14f
        })
        val delay = field(b.delayMs.toString(), "300", numeric = true)
        delay.setOnTouchListener { v, e ->
            if (e.action == MotionEvent.ACTION_UP) (v as EditText).focusWithKeyboard()
            false
        }
        delay.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val v = s?.toString()?.toLongOrNull()?.coerceIn(0L, 60_000L) ?: return
                if (v != b.delayMs) {
                    b.delayMs = v
                    host.pointsChanged(b)
                }
            }
        })
        delayRow.addView(delay, LinearLayout.LayoutParams(dp(context, 90f), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(context, 10f)
        })
        delayRow.addView(TextView(context).apply {
            text = "ms"
            setTextColor(OverlayUi.TEXT_DIM)
            textSize = 13f
            setPadding(dp(context, 8f), 0, 0, 0)
        })
        addView(delayRow, lp(top = 4))
    }

    private fun pointRow(b: MainButton, index: Int): LinearLayout {
        val point = b.points[index]
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundKeepPadding(OverlayUi.rounded(OverlayUi.SURFACE, dp(context, 12f).toFloat(), OverlayUi.BORDER, dp(context, 1f)))
            setPadding(dp(context, 8f), dp(context, 6f), dp(context, 8f), dp(context, 6f))
        }

        // Tên điểm: "Click ..."
        val name = field(point.name, "Click", numeric = false, width = 84f)
        name.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val v = s?.toString()?.take(12).orEmpty().ifBlank { "Click" }
                if (v != point.name) { point.name = v; host.pointsChanged(b) }
            }
        })
        name.setOnTouchListener { v, e ->
            if (e.action == MotionEvent.ACTION_UP) (v as EditText).focusWithKeyboard()
            false
        }
        row.addView(name, LinearLayout.LayoutParams(dp(context, 84f), LinearLayout.LayoutParams.WRAP_CONTENT))

        val (px, py) = pixelsOf(point)
        val fx = pointField(index, isX = true, initial = px)
        val fy = pointField(index, isX = false, initial = py)
        row.addView(fx, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(context, 6f) })
        row.addView(fy, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(context, 6f) })

        row.addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_lucide_x)
            setColorFilter(OverlayUi.DANGER)
            contentDescription = "Xoá điểm"
            setPadding(dp(context, 6f), dp(context, 6f), dp(context, 6f), dp(context, 6f))
            setOnClickListener {
                host.cancelPick(); pinKey = null
                b.points.removeAt(index)
                host.pointsChanged(b)
                build()
            }
        }, LinearLayout.LayoutParams(dp(context, 30f), dp(context, 30f)).apply { marginStart = dp(context, 4f) })
        return row
    }

    /** Ô toạ độ. Chạm không tự bật bàn phím: logic nằm trong onFieldTap. */
    private fun pointField(index: Int, isX: Boolean, initial: Int?): EditText {
        val field = field(initial?.toString().orEmpty(), if (isX) "x" else "y", numeric = true)
        field.keyboardAware()
        field.setOnTouchListener { v, e ->
            if (e.action == MotionEvent.ACTION_UP) onFieldTap(index, isX, v as EditText)
            true
        }
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                typedChanged(index, isX, s?.toString().orEmpty())
            }
        })
        return field
    }

    private fun onFieldTap(index: Int, isX: Boolean, field: EditText) {
        val key = index to (if (isX) 0 else 1)
        if (pinKey == key) {
            // Chạm lần 2 vào đúng ô đó khi ghim đang hiện -> bỏ ghim, nhập tay
            host.cancelPick()
            pinKey = null
            field.focusWithKeyboard()
            return
        }
        val b = current ?: return
        val point = b.points.getOrNull(index) ?: return
        host.cancelPick()
        pinKey = key
        val (w, h) = host.screenSize()
        val startX = (if (point.isSet) (point.fx * w).toInt() else w / 2)
        val startY = (if (point.isSet) (point.fy * h).toInt() else h / 2)
        host.startPick(startX, startY) { px, py ->
            val (sw, sh) = host.screenSize()
            point.fx = (px.toFloat() / sw).coerceIn(0f, 1f)
            point.fy = (py.toFloat() / sh).coerceIn(0f, 1f)
            pinKey = null
            host.pointsChanged(b)
            build()
        }
    }

    /** Người dùng gõ tay vào ô: đổi pixel sang tỉ lệ rồi lưu. */
    private fun typedChanged(index: Int, isX: Boolean, text: String) {
        val b = current ?: return
        val point = b.points.getOrNull(index) ?: return
        val value = text.toIntOrNull()
        val (w, h) = host.screenSize()
        val before = point.copy()
        if (isX) {
            point.fx = if (value == null) -1f else (value.toFloat() / w).coerceIn(0f, 1f)
        } else {
            point.fy = if (value == null) -1f else (value.toFloat() / h).coerceIn(0f, 1f)
        }
        if (before != point) host.pointsChanged(b)
    }

    private fun pixelsOf(p: TapPoint): Pair<Int?, Int?> {
        if (!p.isSet) return null to null
        val (w, h) = host.screenSize()
        return (p.fx * w).toInt() to (p.fy * h).toInt()
    }

    private fun field(value: String, hintText: String, numeric: Boolean, width: Float? = null): EditText =
        EditText(context).apply {
            setText(value)
            hint = hintText
            setTextColor(OverlayUi.TEXT)
            setHintTextColor(OverlayUi.TEXT_DIM)
            textSize = 14f
            inputType = if (numeric) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
            else InputType.TYPE_CLASS_TEXT
            isSingleLine = true
            setBackgroundKeepPadding(OverlayUi.rounded(OverlayUi.BG, dp(context, 10f).toFloat(), OverlayUi.BORDER, dp(context, 1f)))
            setPadding(dp(context, 10f), dp(context, 8f), dp(context, 10f), dp(context, 8f))
            if (width != null) minWidth = dp(context, width)
        }

    private fun button(label: String, onClick: () -> Unit): TextView = TextView(context).apply {
        text = label
        gravity = Gravity.CENTER
        setTextColor(OverlayUi.TEXT)
        setTypeface(typeface, Typeface.BOLD)
        setBackgroundKeepPadding(OverlayUi.rounded(OverlayUi.BLUE, dp(context, 12f).toFloat()))
        setPadding(0, dp(context, 11f), 0, dp(context, 11f))
        setOnClickListener { onClick() }
    }

    private fun label(text: String): TextView = TextView(context).apply {
        this.text = text
        setTextColor(OverlayUi.TEXT_DIM)
        textSize = 12f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(context, 12f), 0, dp(context, 6f))
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
