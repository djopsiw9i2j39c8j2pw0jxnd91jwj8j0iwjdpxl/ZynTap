package com.zyntap.app.data

import android.content.Context
import android.os.Build

/**
 * Gửi cú chạm thật vào máy bằng lệnh `input tap` qua kết nối ADB Wi-Fi đã ghép nối.
 *
 * CHƯA KIỂM CHỨNG: dùng API libadb-android 3.1.1 (openStream / shell). Chưa build được
 * ở môi trường này, cần xác nhận tên hàm đúng khi build. Mọi lỗi được nuốt và trả false.
 */
object AdbInput {

    /** Mở kết nối shell một lần rồi tái sử dụng cho các lần chạm sau. */
    @Volatile
    private var connected = false

    fun tap(context: Context, x: Int, y: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        return try {
            val manager = ZynAdbConnectionManager.get(context)
            if (!connected) {
                connected = manager.connectTls(context.applicationContext, 8_000L)
            }
            if (!connected) return false
            // Lệnh shell trên ADB: input tap <x> <y>
            manager.openStream("shell:input tap $x $y").use { _ -> }
            true
        } catch (t: Throwable) {
            connected = false
            false
        }
    }

    fun disconnect(context: Context) {
        runCatching { ZynAdbConnectionManager.get(context).disconnect() }
        connected = false
    }
}
