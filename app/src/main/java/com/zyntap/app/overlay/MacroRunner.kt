package com.zyntap.app.overlay

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Chạy chuỗi điểm của một nút Main.
 *  - Nhấn : chạy các điểm lần lượt, cách nhau delay, rồi dừng.
 *  - Giữ  : chạy lặp liên tục cho tới khi gọi stop() (thả tay).
 *  - Thả  : chạy đúng một lần khi thả tay.
 *
 * [tap] trả về null nếu thành công, hoặc thông báo lỗi; khi có lỗi chuỗi sẽ dừng.
 */
class MacroRunner(
    private val tap: (x: Int, y: Int) -> String?,
    private val pixelFor: (TapPoint) -> Pair<Int, Int>,
    private val onError: (String) -> Unit,
) {
    private val running = AtomicBoolean(false)
    @Volatile private var worker: Thread? = null

    val isRunning: Boolean get() = running.get()

    /** Trả về false nếu không có điểm nào hợp lệ để chạy. */
    fun start(button: MainButton, repeat: Boolean): Boolean {
        val points = button.points.filter { it.isSet }.toList()
        if (points.isEmpty()) return false
        if (!running.compareAndSet(false, true)) return true
        val delay = button.delayMs.coerceAtLeast(0L)
        worker = Thread {
            try {
                do {
                    for (p in points) {
                        if (!running.get()) break
                        val (x, y) = pixelFor(p)
                        val error = tap(x, y)
                        if (error != null) {
                            onError(error)
                            return@Thread
                        }
                        if (delay > 0) Thread.sleep(delay)
                    }
                } while (repeat && running.get())
            } catch (_: InterruptedException) {
                // bị dừng
            } finally {
                running.set(false)
            }
        }.also { it.isDaemon = true; it.start() }
        return true
    }

    fun stop() {
        running.set(false)
        worker?.interrupt()
        worker = null
    }
}
