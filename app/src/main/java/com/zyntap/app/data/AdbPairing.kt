package com.zyntap.app.data

import kotlinx.coroutines.delay

sealed interface PairState {
    data object Idle : PairState
    data object Working : PairState
    data object Success : PairState
    data class Failed(val message: String) : PairState
}

/** Điểm cắm cho việc ghép nối gỡ lỗi không dây (ADB over Wi-Fi) bằng mã 6 số. */
interface AdbPairing {
    suspend fun pair(code: String): Result<Unit>
}

/**
 * Bản hiện tại CHƯA có logic ghép nối thật — mới có giao diện và luồng trạng thái.
 * Khi làm phần ghép nối thật, chỉ cần viết một lớp mới implement AdbPairing
 * rồi thay vào MainViewModel.
 */
class PendingAdbPairing : AdbPairing {
    override suspend fun pair(code: String): Result<Unit> {
        delay(700)
        return Result.failure(
            IllegalStateException("Bản này mới có giao diện. Phần ghép nối thật sẽ được thêm ở bản sau."),
        )
    }
}
