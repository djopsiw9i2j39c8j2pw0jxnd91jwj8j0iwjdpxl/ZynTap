package com.zyntap.app.overlay

import android.content.Context
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import com.zyntap.app.overlay.OverlayUi.dp
import com.zyntap.app.overlay.OverlayUi.setBackgroundKeepPadding

/** Callback từ tab "Nút Main" lên dịch vụ nổi. */
interface MainHost {
    fun addButton()
    fun buttonChanged(button: MainButton)
    fun deleteButton(button: MainButton)
    fun resetButton(button: MainButton)
}

/** Tab 1: danh sách nút Main; chạm một nút để mở bảng cài đặt của nút đó. */
class MainTab(
    private val context: Context,
    private val buttons: () -> List<MainButton>,
    private val host: MainHost,
) : LinearLayout(context) {

    init {
        orientation = VERTICAL
        showList()
    }

    private fun showList() {
        removeAllViews()
        addView(addRow())
        if (buttons().isEmpty()) {
            addView(hint("Chưa có nút nào. Bấm “+ Thêm nút Main” để tạo."))
        }
        buttons().forEach { b -> addView(buttonRow(b)) }
    }

    private fun addRow(): TextView = TextView(context).apply {
        text = "+ Thêm nút Main"
        gravity = Gravity.CENTER
        setTextColor(OverlayUi.CYAN)
        textSize = 15f
        setTypeface(typeface, Typeface.BOLD)
        setBackgroundKeepPadding(OverlayUi.rounded(OverlayUi.SURFACE, dp(context, 14f).toFloat(), OverlayUi.BORDER, dp(context, 1f)))
        setPadding(0, dp(context, 12f), 0, dp(context, 12f))
        setOnClickListener { host.addButton(); showList() }
        layoutParams = lp(bottom = 10)
    }

    private fun buttonRow(b: MainButton): View = LinearLayout(context).apply {
        orientation = VERTICAL
        setBackgroundKeepPadding(OverlayUi.rounded(OverlayUi.SURFACE, dp(context, 14f).toFloat(), OverlayUi.BORDER, dp(context, 1f)))
        setPadding(dp(context, 14f), dp(context, 10f), dp(context, 14f), dp(context, 10f))
        addView(TextView(context).apply {
            text = b.name
            setTextColor(OverlayUi.TEXT)
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
        })
        addView(TextView(context).apply {
            text = "${b.mode.label} • ${b.points.size} điểm"
            setTextColor(OverlayUi.TEXT_DIM)
            textSize = 12f
        })
        setOnClickListener { showSettings(b) }
        layoutParams = lp(bottom = 8)
    }

    private fun showSettings(b: MainButton) {
        removeAllViews()
        val back = TextView(context).apply {
            text = "‹ Quay lại"
            setTextColor(OverlayUi.CYAN)
            textSize = 14f
            setOnClickListener { showList() }
            setPadding(0, 0, 0, dp(context, 8f))
        }
        addView(back)

        val nameBox = EditText(context).apply {
            setText(b.name)
            hint = "Tên nút"
            setTextColor(OverlayUi.TEXT)
            setHintTextColor(OverlayUi.TEXT_DIM)
            inputType = InputType.TYPE_CLASS_TEXT
            setBackgroundKeepPadding(OverlayUi.rounded(OverlayUi.SURFACE, dp(context, 10f).toFloat(), OverlayUi.BORDER, dp(context, 1f)))
            setPadding(dp(context, 12f), dp(context, 8f), dp(context, 12f), dp(context, 8f))
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b2: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b2: Int, c: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    b.name = s?.toString()?.take(12).orEmpty().ifBlank { "Main" }
                    host.buttonChanged(b)
                }
            })
        }
        addView(labelled("Tên", nameBox))

        addView(labelled("Kích thước: ${b.sizeDp}dp", slider(36, 96, b.sizeDp) { v ->
            b.sizeDp = v; host.buttonChanged(b)
        }))
        addView(labelled("Độ mờ: ${(b.alpha * 100).toInt()}%", slider(10, 100, (b.alpha * 100).toInt()) { v ->
            b.alpha = v / 100f; host.buttonChanged(b)
        }))

        val pass = Switch(context).apply {
            text = "Cho chạm xuyên xuống dưới"
            setTextColor(OverlayUi.TEXT)
            isChecked = b.passThrough
            setOnCheckedChangeListener { _, on -> b.passThrough = on; host.buttonChanged(b) }
        }
        addView(pass)

        val modes = PressMode.values()
        val spinner = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, modes.map { it.label })
            setSelection(modes.indexOf(b.mode))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    if (b.mode != modes[pos]) { b.mode = modes[pos]; host.buttonChanged(b) }
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }
        addView(labelled("Chế độ", spinner))

        addView(actionRow(
            "Khôi phục cài đặt gốc" to OverlayUi.TEXT_DIM,
            "Xoá nút này" to OverlayUi.DANGER,
        ) { index ->
            if (index == 0) {
                host.resetButton(b); showSettings(b)
            } else {
                host.deleteButton(b); showList()
            }
        })
    }

    private fun slider(min: Int, max: Int, value: Int, onChange: (Int) -> Unit): SeekBar =
        SeekBar(context).apply {
            this.max = max - min
            progress = value - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) onChange(p + min)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }

    private fun labelled(label: String, child: View): LinearLayout = LinearLayout(context).apply {
        orientation = VERTICAL
        addView(TextView(context).apply {
            text = label
            setTextColor(OverlayUi.TEXT_DIM)
            textSize = 12f
        })
        addView(child)
        layoutParams = lp(top = 10)
    }

    private fun actionRow(vararg items: Pair<String, Int>, onClick: (Int) -> Unit): LinearLayout =
        LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = lp(top = 14)
            items.forEachIndexed { i, (label, color) ->
                addView(TextView(context).apply {
                    text = label
                    gravity = Gravity.CENTER
                    setTextColor(color)
                    textSize = 13f
                    setTypeface(typeface, Typeface.BOLD)
                    setPadding(0, dp(context, 10f), 0, dp(context, 10f))
                    setOnClickListener { onClick(i) }
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
        }

    private fun hint(text: String): TextView = TextView(context).apply {
        this.text = text
        setTextColor(OverlayUi.TEXT_DIM)
        textSize = 13f
        setPadding(0, dp(context, 8f), 0, 0)
    }

    private fun lp(top: Int = 0, bottom: Int = 0) =
        LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(context, top.toFloat())
            bottomMargin = dp(context, bottom.toFloat())
        }
}
