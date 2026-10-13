package com.zyntap.app.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.zyntap.app.R
import com.zyntap.app.data.AdbInput
import com.zyntap.app.overlay.OverlayUi.dp
import com.zyntap.app.overlay.OverlayUi.setBackgroundKeepPadding

/**
 * Dịch vụ nổi của ZynTap:
 *  - Tay cầm nhỏ ở mép trái: chạm hoặc vuốt để mở menu.
 *  - Menu cố định kích thước, tab dọc bên trái, kéo được theo thanh đầu.
 *  - "−" thu menu thành thanh nhỏ (logo, tên, −, ×); chạm ra ngoài menu cũng thu lại.
 *  - Các nút Main nổi trên màn hình; khi menu mở rộng thì kéo được để sắp xếp.
 */
class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var runner: MacroRunner
    private val main = Handler(Looper.getMainLooper())

    private var handleView: View? = null
    private var panelFrame: PanelFrame? = null
    private var panelView: View? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var pin: PickPin? = null
    private val buttons = mutableListOf<FloatingMainButton>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForegroundWithNotification()
        OverlayFocus.listener = { focusable -> setPanelFocusable(focusable) }
        runner = MacroRunner(
            tap = { x, y -> AdbInput.tap(this, x, y) },
            pixelFor = { p -> pixel(p) },
            onError = { toast(it) },
        )
        MacroStore.load(this).forEach { addFloating(it) }
        showHandle()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        else if (handleView == null && panelView == null) showHandle()
        return START_STICKY
    }

    override fun onDestroy() {
        runner.stop()
        removeAllViews()
        OverlayFocus.listener = null
        AdbInput.disconnect(this)
        super.onDestroy()
    }

    // ---- thông báo đang chạy ----------------------------------------------

    private fun startForegroundWithNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "zyntap_overlay"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "ZynTap menu nổi", NotificationManager.IMPORTANCE_MIN),
            )
        }
        val openIntent = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("ZynTap đang chạy")
            .setContentText("Menu nổi sẵn sàng trên ứng dụng khác")
            .setContentIntent(openIntent)
            .addAction(0, "Tắt", stopIntent)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    // ---- tay cầm ----------------------------------------------------------

    private fun showHandle() {
        if (handleView != null) return
        val params = WindowManager.LayoutParams(
            dp(this, 22f), dp(this, 64f), overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL }

        val handle = ImageView(this).apply {
            setImageResource(R.drawable.logo_transparent)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundKeepPadding(OverlayUi.rounded(OverlayUi.SURFACE, dp(this@OverlayService, 12f).toFloat(), OverlayUi.CYAN, dp(this@OverlayService, 1f)))
            setPadding(dp(this@OverlayService, 3f), dp(this@OverlayService, 10f), dp(this@OverlayService, 3f), dp(this@OverlayService, 10f))
            setOnTouchListener { _, e ->
                if (e.action == MotionEvent.ACTION_UP) openPanel()
                true
            }
        }
        handleView = handle
        runCatching { wm.addView(handle, params) }
    }

    private fun hideHandle() {
        handleView?.let { runCatching { wm.removeView(it) } }
        handleView = null
    }

    // ---- menu -------------------------------------------------------------

    private fun openPanel() {
        if (panelView != null) return
        hideHandle()

        val params = WindowManager.LayoutParams(
            dp(this, EXPANDED_W_DP), dp(this, EXPANDED_H_DP), overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.START or Gravity.TOP
            x = dp(this@OverlayService, 12f)
            y = dp(this@OverlayService, 80f)
        }
        panelParams = params

        val frame = PanelFrame(
            context = this,
            tabNames = listOf("Nút Main", "Đặt điểm"),
            contentFor = { index -> contentFor(index) },
            onCollapsedChanged = { collapsed -> applyMode(collapsed) },
            onCloseConfirmed = { stopSelf() },
            attachDrag = { v -> attachDrag(v) },
        )
        panelFrame = frame

        // Chạm ra ngoài menu mở rộng -> thu thành thanh (trừ khi chạm vào nút Main hoặc ghim)
        frame.root.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_OUTSIDE && !frame.isCollapsed) {
                if (!touchesOverlayElement(e.rawX, e.rawY)) frame.setCollapsed(true)
            }
            false
        }

        panelView = frame.root
        runCatching { wm.addView(frame.root, params) }
        buttons.forEach { it.applyModel() }
    }

    private fun applyMode(collapsed: Boolean) {
        val p = panelParams ?: return
        if (collapsed) {
            cancelPick()
            p.width = WindowManager.LayoutParams.WRAP_CONTENT
            p.height = WindowManager.LayoutParams.WRAP_CONTENT
            p.flags = p.flags and WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH.inv()
        } else {
            p.width = dp(this, EXPANDED_W_DP)
            p.height = dp(this, EXPANDED_H_DP)
            p.flags = p.flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        }
        panelView?.let { runCatching { wm.updateViewLayout(it, p) } }
        buttons.forEach { it.applyModel() }
    }

    /** Bật/tắt lấy tiêu điểm cho menu để bàn phím hiện được. */
    private fun setPanelFocusable(focusable: Boolean) {
        val p = panelParams ?: return
        val view = panelView ?: return
        p.flags = if (focusable) {
            p.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            p.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        runCatching { wm.updateViewLayout(view, p) }
    }

    /** Kéo cả menu (hoặc thanh thu gọn) bằng thanh đầu; giữ trong màn hình. */
    private fun attachDrag(v: View) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        v.setOnTouchListener { _, e ->
            val p = panelParams ?: return@setOnTouchListener false
            val view = panelView ?: return@setOnTouchListener false
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = p.x; startY = p.y
                }
                MotionEvent.ACTION_MOVE -> {
                    val maxX = (resources.displayMetrics.widthPixels - view.width).coerceAtLeast(0)
                    val maxY = (resources.displayMetrics.heightPixels - view.height).coerceAtLeast(0)
                    p.x = (startX + (e.rawX - downX).toInt()).coerceIn(0, maxX)
                    p.y = (startY + (e.rawY - downY).toInt()).coerceIn(0, maxY)
                    runCatching { wm.updateViewLayout(view, p) }
                }
            }
            true
        }
    }

    private fun touchesOverlayElement(rawX: Float, rawY: Float): Boolean =
        buttons.any { it.contains(rawX, rawY) } || (pin?.contains(rawX, rawY) == true)

    private fun isExpanded(): Boolean = panelFrame?.let { !it.isCollapsed } ?: false

    private fun closePanelWindow() {
        cancelPick()
        panelView?.let { runCatching { wm.removeView(it) } }
        panelView = null
        panelFrame = null
        panelParams = null
    }

    private fun contentFor(index: Int): View = if (index == 0) {
        MainTab(this, { snapshotButtons() }, mainHost)
    } else {
        PointsTab(this, { snapshotButtons() }, pointsHost)
    }

    private fun snapshotButtons(): List<MainButton> = buttons.map { it.model }

    private fun saveAll() = MacroStore.save(this, snapshotButtons())

    private val mainHost = object : MainHost {
        override fun addButton() {
            val b = MainButton(name = "Main ${buttons.size + 1}", screenX = 120, screenY = 300)
            addFloating(b)
            buttons.forEach { it.applyModel() }
            saveAll()
        }

        override fun buttonChanged(button: MainButton) {
            buttons.firstOrNull { it.model.id == button.id }?.applyModel()
            saveAll()
        }

        override fun deleteButton(button: MainButton) {
            buttons.firstOrNull { it.model.id == button.id }?.let { it.hide(); buttons.remove(it) }
            saveAll()
        }

        override fun resetButton(button: MainButton) {
            val d = MainButton(id = button.id, name = "Main", screenX = button.screenX, screenY = button.screenY)
            button.name = d.name
            button.sizeDp = d.sizeDp
            button.alpha = d.alpha
            button.passThrough = d.passThrough
            button.mode = d.mode
            button.delayMs = d.delayMs
            buttons.firstOrNull { it.model.id == button.id }?.applyModel()
            saveAll()
        }
    }

    private val pointsHost = object : PointsHost {
        override fun startPick(initialX: Int, initialY: Int, onPicked: (px: Int, py: Int) -> Unit) {
            cancelPick()
            val p = PickPin(this@OverlayService, wm, initialX, initialY) { px, py ->
                pin = null
                onPicked(px, py)
            }
            pin = p
            p.show()
        }

        override fun cancelPick() = this@OverlayService.cancelPick()

        override fun pointsChanged(button: MainButton) {
            saveAll()
        }

        override fun screenSize(): Pair<Int, Int> =
            resources.displayMetrics.widthPixels to resources.displayMetrics.heightPixels
    }

    private fun cancelPick() {
        pin?.cancel()
        pin = null
    }

    // ---- nút Main ---------------------------------------------------------

    private fun addFloating(model: MainButton) {
        val fb = FloatingMainButton(
            context = this,
            wm = wm,
            model = model,
            runner = runner,
            isEditable = { isExpanded() },
            onMoved = { saveAll() },
            notify = { toast(it) },
        )
        buttons.add(fb)
        fb.show()
    }

    private fun pixel(p: TapPoint): Pair<Int, Int> {
        val w = resources.displayMetrics.widthPixels
        val h = resources.displayMetrics.heightPixels
        return (p.fx * w).toInt() to (p.fy * h).toInt()
    }

    private fun toast(message: String) {
        main.post { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    }

    private fun removeAllViews() {
        cancelPick()
        buttons.forEach { it.hide() }
        hideHandle()
        closePanelWindow()
    }

    companion object {
        const val ACTION_STOP = "com.zyntap.app.overlay.STOP"
        private const val NOTIF_ID = 1001
        private const val EXPANDED_W_DP = 340f
        private const val EXPANDED_H_DP = 470f

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
