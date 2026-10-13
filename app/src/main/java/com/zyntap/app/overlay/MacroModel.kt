package com.zyntap.app.overlay

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Cách nút "Main" kích hoạt chuỗi điểm. */
enum class PressMode(val label: String) {
    PRESS("Nhấn"),
    HOLD("Giữ"),
    RELEASE("Thả");

    companion object {
        fun fromName(name: String?): PressMode = values().firstOrNull { it.name == name } ?: PRESS
    }
}

/**
 * Điểm chạm. Toạ độ lưu theo TỈ LỆ (0..1) của màn hình, nên khi xoay màn hình
 * vẫn đúng vị trí tương đối; lúc chạy sẽ quy đổi về pixel theo hướng hiện tại.
 */
data class TapPoint(val fx: Float, val fy: Float)

data class MainButton(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "Main",
    var sizeDp: Int = 56,
    var alpha: Float = 0.9f,
    var passThrough: Boolean = false,
    var mode: PressMode = PressMode.PRESS,
    var delayMs: Long = 300L,
    var screenX: Int = 40,
    var screenY: Int = 300,
    val points: MutableList<TapPoint> = mutableListOf(),
)

/** Lưu danh sách nút Main vào SharedPreferences dưới dạng JSON (không cần thư viện ngoài). */
object MacroStore {
    private const val PREFS = "zyntap_overlay"
    private const val KEY = "main_buttons"

    fun load(context: Context): MutableList<MainButton> = try {
        val raw = prefs(context).getString(KEY, null) ?: return mutableListOf()
        val arr = JSONArray(raw)
        MutableList(arr.length()) { i -> fromJson(arr.getJSONObject(i)) }
    } catch (e: Exception) {
        mutableListOf()
    }

    fun save(context: Context, buttons: List<MainButton>) {
        val arr = JSONArray()
        buttons.forEach { arr.put(toJson(it)) }
        prefs(context).edit().putString(KEY, arr.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun toJson(b: MainButton): JSONObject = JSONObject().apply {
        put("id", b.id)
        put("name", b.name)
        put("sizeDp", b.sizeDp)
        put("alpha", b.alpha.toDouble())
        put("passThrough", b.passThrough)
        put("mode", b.mode.name)
        put("delayMs", b.delayMs)
        put("screenX", b.screenX)
        put("screenY", b.screenY)
        val pts = JSONArray()
        b.points.forEach { pts.put(JSONObject().put("fx", it.fx.toDouble()).put("fy", it.fy.toDouble())) }
        put("points", pts)
    }

    private fun fromJson(o: JSONObject): MainButton {
        val pts = o.optJSONArray("points") ?: JSONArray()
        val points = MutableList(pts.length()) { i ->
            val p = pts.getJSONObject(i)
            TapPoint(p.getDouble("fx").toFloat(), p.getDouble("fy").toFloat())
        }
        return MainButton(
            id = o.optString("id", UUID.randomUUID().toString()),
            name = o.optString("name", "Main"),
            sizeDp = o.optInt("sizeDp", 56),
            alpha = o.optDouble("alpha", 0.9).toFloat(),
            passThrough = o.optBoolean("passThrough", false),
            mode = PressMode.fromName(o.optString("mode")),
            delayMs = o.optLong("delayMs", 300L),
            screenX = o.optInt("screenX", 40),
            screenY = o.optInt("screenY", 300),
            points = points,
        )
    }
}
