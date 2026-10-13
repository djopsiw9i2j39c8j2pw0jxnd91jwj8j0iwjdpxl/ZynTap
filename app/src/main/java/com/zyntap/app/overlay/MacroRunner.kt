package com.zyntap.app.overlay

import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Chạy chuỗi điểm của một nút Main.
 *  - Nhấn : chạy lần lượt các điểm, mỗi điểm cách nhau [MainButton.delayMs], rồi dừng.
 *  - Giữ  : đang giữ nút thì lặp lại cả chuỗi liên tục cho tới khi thả.
 *  - Thả  : chỉ chạy chuỗi đúng một lần khi thả nút.
 *
 * [tap] nhận toạ độ pixel (x, y) và phải thực hiện cú chạm thật (qua ADB shell).
 */
class MacroRunner(
    private val tap: (x: Int, y: Int) -> Unit,
    private val pixelFor: (TapPoint) -> Pair<Int, Int>,
) {
    private val main = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    private var worker: Thread? = null

    val isRunning: Boolean get() = running.get()

    /** Bắt đầu chạy chuỗi. Với PRESS/RELEASE chạy một lần; HOLD chạy lặp đến khi gọi [stop]. */
    fun start(button: MainButton, repeat: Boolean) {
        if (button.points.isEmpty()) return
        if (!running.compareAndSet(false, true)) return
        val points = button.points.toList()
        val delay = button.delayMs.coerceAtLeast(0L)
        worker = Thread {
            try {
                do {
                    for (p in points) {
                        if (!running.get()) break
                        val (x, y) = pixelFor(p)
                        tap(x, y)
                        if (delay > 0) Thread.sleep(delay)
                    }
                } while (repeat && running.get())
            } catch (_: InterruptedException) {
                // bị dừng
            } finally {
                running.set(false)
            }
        }.also { it.isDaemon = true; it.start() }
    }

    fun stop() {
        running.set(false)
        worker?.interrupt()
        worker = null
    }
}
