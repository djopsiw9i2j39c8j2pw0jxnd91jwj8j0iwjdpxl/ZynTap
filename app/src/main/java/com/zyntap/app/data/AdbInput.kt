package com.zyntap.app.data

import android.content.Context
import android.os.Build

/**
 * Gửi cú chạm thật vào máy bằng lệnh `input tap x y` qua kết nối ADB Wi-Fi đã ghép nối.
 *
 * CHƯA KIỂM CHỨNG: API libadb-android 3.1.1 (openStream, disconnect) chưa được xác nhận
 * vì môi trường không tải được thư viện. Cần kiểm tra khi build.
 */
object AdbInput {

    @Volatile
    private var connected = false

    /** Trả về null nếu gửi thành công, ngược lại trả về thông báo lỗi để hiện cho người dùng. */
    fun tap(context: Context, x: Int, y: Int): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return "Cần Android 11 trở lên"
        return try {
            val manager = ZynAdbConnectionManager.get(context)
            if (!connected) {
                connected = manager.connectTls(context.applicationContext, 8_000L)
            }
            if (!connected) return "Chưa kết nối được ADB: kiểm tra Gỡ lỗi Wi-Fi và ghép nối"
            manager.openStream("shell:input tap $x $y").use { _ -> }
            null
        } catch (t: Throwable) {
            connected = false
            "Gửi cú chạm thất bại: ${t.message ?: t.javaClass.simpleName}"
        }
    }

    fun disconnect(context: Context) {
        runCatching { ZynAdbConnectionManager.get(context).disconnect() }
        connected = false
    }
}
