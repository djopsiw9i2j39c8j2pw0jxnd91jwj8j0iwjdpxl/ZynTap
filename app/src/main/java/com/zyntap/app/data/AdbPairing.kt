package com.zyntap.app.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.conscrypt.Conscrypt
import org.conscrypt.PSKKeyManager
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

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
 * Ghép nối THẬT với "Gỡ lỗi không dây" (Android 11+), làm đúng cách hệ thống:
 *  1. Dò dịch vụ `_adb-tls-pairing._tcp` qua mDNS (NSD) — dịch vụ này chỉ phát
 *     khi bảng "Ghép nối thiết bị bằng mã ghép nối" còn mở.
 *  2. Bắt tay TLS 1.3 với PSK (cipher TLS_PSK_WITH_AES_128_GCM_SHA256),
 *     khoá PSK chính là mã 6 số — dùng Conscrypt (TLS của Google).
 *  3. Gửi gói PeerInfo (header "SPCK") để adbd ghi nhận khoá, rồi chờ máy trả lời.
 *  4. Pair xong thì dò cổng `_adb-tls-connect._tcp` để lưu lại cho lần kết nối sau.
 */
@RequiresApi(Build.VERSION_CODES.R)
class WifiAdbPairing(private val context: Context) : AdbPairing {

    override suspend fun pair(code: String): Result<Unit> = withContext(Dispatchers.IO) {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        var lock: WifiManager.MulticastLock? = null
        try {
            lock = wifi.createMulticastLock("zyntap_pair").apply {
                setReferenceCounted(true)
                acquire()
            }
            // Bước 1: tìm dịch vụ pairing qua mDNS
            val service = discoverService(nsd, SERVICE_PAIRING, PAIR_TIMEOUT_MS)
            val host = pickHost(service)
                ?: return@withContext Result.failure(
                    IllegalStateException("Không đọc được địa chỉ dịch vụ ghép nối."),
                )
            val port = service.port
            if (port <= 0) {
                return@withContext Result.failure(IllegalStateException("Không đọc được cổng ghép nối."))
            }

            // Bước 2 + 3: TLS-PSK bằng mã 6 số, gửi PeerInfo, chờ máy xác nhận
            val address = host.hostAddress
                ?: return@withContext Result.failure(
                    IllegalStateException("Địa chỉ dịch vụ ghép nối không hợp lệ."),
                )
            pairOverTls(address, port, code)

            // Bước 4: lấy cổng kết nối để lưu lại (dùng cho lần kết nối sau)
            runCatching {
                val conn = discoverService(nsd, SERVICE_CONNECT, CONNECT_TIMEOUT_MS)
                conn.port.takeIf { it > 0 }?.let { AdbLinkStore.saveConnectPort(context, it) }
            }
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(
                IllegalStateException(
                    "Ghép nối thất bại: ${t.message ?: t.javaClass.simpleName}. " +
                        "Hãy chắc bảng mã ghép nối còn mở và nhập đúng 6 số.",
                    t,
                ),
            )
        } finally {
            runCatching { lock?.release() }
        }
    }

    /** Bắt tay TLS-PSK và trao đổi gói PeerInfo với adbd. */
    private fun pairOverTls(host: String, port: Int, code: String) {
        val sslContext = SSLContext.getInstance("TLSv1.3", Conscrypt.newProvider()).apply {
            init(null, arrayOf(TrustAll), SecureRandom())
        }
        val socket = sslContext.socketFactory.createSocket() as SSLSocket
        try {
            socket.connect(InetSocketAddress(host, port), SOCKET_TIMEOUT_MS)
            socket.soTimeout = SOCKET_TIMEOUT_MS
            Conscrypt.setPSKKeyManager(socket, PairingPsk(code))
            socket.enabledProtocols = arrayOf("TLSv1.3")
            socket.enabledCipherSuites = arrayOf("TLS_PSK_WITH_AES_128_GCM_SHA256")
            socket.startHandshake()

            sendPeerInfo(socket)
            readPeerInfoReply(socket)
        } finally {
            runCatching { socket.close() }
        }
    }

    /** Gửi PeerInfo (tên thiết bị) theo đúng định dạng AOSP: header "SPCK" + payload. */
    private fun sendPeerInfo(socket: SSLSocket) {
        val name = PEER_NAME.toByteArray(Charsets.UTF_8)
        val payload = ByteArray(1 + name.size)
        payload[0] = PEER_INFO_DEVICE_NAME
        System.arraycopy(name, 0, payload, 1, name.size)

        val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            .put(SPCK_MAGIC)
            .put(PAIRING_VERSION)
            .put(PACKET_PEER_INFO)
            .putShort(payload.size.toShort())
            .array()

        val out = DataOutputStream(socket.getOutputStream())
        out.write(header)
        out.write(payload)
        out.flush()
    }

    /** Đọc gói PeerInfo trả lời từ máy — có gói này nghĩa là adbd đã ghi nhận ghép nối. */
    private fun readPeerInfoReply(socket: SSLSocket) {
        val input = DataInputStream(socket.getInputStream())
        val header = ByteArray(8)
        input.readFully(header)
        val magic = header.copyOfRange(0, 4)
        if (!magic.contentEquals(SPCK_MAGIC)) {
            throw IllegalStateException("Máy trả lời sai gói ghép nối.")
        }
        val size = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).getShort(6).toInt() and 0xFFFF
        if (size in 1..MAX_PEER_INFO) {
            val payload = ByteArray(size)
            input.readFully(payload)
        }
    }

    private fun pickHost(info: NsdServiceInfo): InetAddress? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            info.hostAddresses?.firstOrNull()?.let { return it }
        }
        @Suppress("DEPRECATION")
        return info.host
    }

    private suspend fun discoverService(
        nsd: NsdManager,
        serviceType: String,
        timeoutMs: Long,
    ): NsdServiceInfo = withContext(Dispatchers.IO) {
        val deferred = CompletableDeferred<NsdServiceInfo>()
        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onDiscoveryStopped(regType: String) {}
            override fun onStartDiscoveryFailed(regType: String, errorCode: Int) {
                deferred.completeExceptionally(
                    IllegalStateException("Không dò được dịch vụ mDNS (lỗi $errorCode)."),
                )
            }
            override fun onStopDiscoveryFailed(regType: String, errorCode: Int) {}
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                runCatching {
                    nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            deferred.completeExceptionally(
                                IllegalStateException("Resolve mDNS thất bại (lỗi $errorCode)."),
                            )
                        }
                        override fun onServiceResolved(resolved: NsdServiceInfo) {
                            deferred.complete(resolved)
                        }
                    })
                }.onFailure { deferred.completeExceptionally(it) }
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
        }
        nsd.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        try {
            withTimeout(timeoutMs) { deferred.await() }
        } finally {
            runCatching { nsd.stopServiceDiscovery(discoveryListener) }
        }
    }

    /** Khoá PSK của phiên ghép nối = đúng 6 số người dùng nhập. */
    private class PairingPsk(code: String) : PSKKeyManager {
        private val key: SecretKey = SecretKeySpec(code.toByteArray(Charsets.UTF_8), "AES")
        override fun chooseServerKeyIdentityHint(socket: Socket?): String? = null
        override fun chooseServerKeyIdentityHint(engine: SSLEngine?): String? = null
        override fun chooseClientKeyIdentity(identityHint: String?, socket: Socket?): String = ""
        override fun chooseClientKeyIdentity(identityHint: String?, engine: SSLEngine?): String = ""
        override fun getKey(identityHint: String?, identity: String?, socket: Socket?): SecretKey = key
        override fun getKey(identityHint: String?, identity: String?, engine: SSLEngine?): SecretKey = key
    }

    /** adbd dùng chứng chỉ tự ký nên chấp nhận mọi chứng chỉ phía server. */
    private object TrustAll : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    companion object {
        const val SERVICE_PAIRING = "_adb-tls-pairing._tcp."
        const val SERVICE_CONNECT = "_adb-tls-connect._tcp."

        private val SPCK_MAGIC = "SPCK".toByteArray(Charsets.US_ASCII)
        private const val PAIRING_VERSION: Byte = 1
        private const val PACKET_PEER_INFO: Byte = 0
        private const val PEER_INFO_DEVICE_NAME: Byte = 1
        private const val MAX_PEER_INFO = 8192
        private const val PEER_NAME = "ZynTap"

        private const val PAIR_TIMEOUT_MS = 20_000L
        private const val CONNECT_TIMEOUT_MS = 10_000L
        private const val SOCKET_TIMEOUT_MS = 10_000
    }
}

/** Bản dựng giao diện (không logic thật) — giữ lại để fallback cho máy Android < 11. */
class PendingAdbPairing : AdbPairing {
    override suspend fun pair(code: String): Result<Unit> {
        kotlinx.coroutines.delay(700)
        return Result.failure(
            IllegalStateException("Cần Android 11 trở lên mới ghép nối gỡ lỗi Wi-Fi được."),
        )
    }
}
