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
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.app.NotificationCompat
import com.zyntap.app.R
import com.zyntap.app.data.AdbInput
import com.zyntap.app.overlay.OverlayUi.dp
import com.zyntap.app.overlay.OverlayUi.setBackgroundKeepPadding

/**
 * Dịch vụ nổi của ZynTap:
 *  - Tay cầm nhỏ ở mép trái: vuốt sang phải (hoặc chạm) để mở menu.
 *  - Menu có 2 tab: "Nút Main" và "Đặt điểm".
 *  - Các nút Main nổi trên màn hình, chạy chuỗi điểm bằng ADB.
 */
class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private var handleView: View? = null
    private var panelView: View? = null
    private var panel: PanelFrame? = null
    private var pin: PickPin? = null
    private val buttons = mutableListOf<FloatingMainButton>()
    private lateinit var runner: MacroRunner

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForegroundWithNotification()
        runner = MacroRunner(
            tap = { x, y -> AdbInput.tap(this, x, y) },
            pixelFor = { p -> pixel(p) },
        )
        MacroStore.load(this).forEach { addFloating(it) }
        showHandle()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            else -> if (handleView == null) showHandle()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runner.stop()
        removeAllViews()
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

    // ---- tay cầm ----------------------------------------------------------

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun showHandle() {
        if (handleView != null) return
        val w = dp(this, 22f)
        val h = dp(this, 64f)
        val params = WindowManager.LayoutParams(
            w, h, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL }

        val handle = ImageView(this).apply {
            setImageResource(R.drawable.logo_transparent)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundKeepPadding(OverlayUi.rounded(OverlayUi.SURFACE, dp(this@OverlayService, 12f).toFloat(), OverlayUi.CYAN, dp(this@OverlayService, 1f)))
            setPadding(dp(this@OverlayService, 3f), dp(this@OverlayService, 10f), dp(this@OverlayService, 3f), dp(this@OverlayService, 10f))
        }
        // Chạm hoặc vuốt sang phải đều mở menu
        handle.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_UP) openPanel()
            true
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

        val frame = PanelFrame(
            context = this,
            tabNames = listOf("Nút Main", "Đặt điểm"),
            contentFor = { index -> contentFor(index) },
            onCollapse = { closePanel() },
            onCloseConfirmed = { stopSelf() },
        )
        panel = frame

        val screenW = resources.displayMetrics.widthPixels
        val panelW = (screenW * 0.82f).toInt().coerceAtMost(dp(this, 340f))
        val params = WindowManager.LayoutParams(
            panelW,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            x = dp(this@OverlayService, 8f)
        }

        // Chạm ra ngoài menu -> thu menu về tay cầm (chạm xuyên vẫn xuống app bên dưới)
        frame.root.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_OUTSIDE) closePanel()
            false
        }

        panelView = frame.root
        runCatching { wm.addView(frame.root, params) }
    }

    private fun closePanel() {
        pin?.cancel()
        pin = null
        panelView?.let { runCatching { wm.removeView(it) } }
        panelView = null
        panel = null
        showHandle()
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
        override fun startPick(onPicked: (fx: Float, fy: Float) -> Unit) {
            pin?.cancel()
            pin = PickPin(this@OverlayService, wm) { fx, fy ->
                pin = null
                onPicked(fx, fy)
            }.also { it.show() }
        }

        override fun pointsChanged(button: MainButton) {
            saveAll()
        }

        override fun screenSize(): Pair<Int, Int> =
            resources.displayMetrics.widthPixels to resources.displayMetrics.heightPixels
    }

    // ---- nút Main ---------------------------------------------------------

    private fun addFloating(model: MainButton) {
        val fb = FloatingMainButton(
            context = this,
            wm = wm,
            model = model,
            runner = runner,
            isMenuOpen = { panelView != null },
            onMoved = { saveAll() },
        )
        buttons.add(fb)
        fb.show()
    }

    private fun pixel(p: TapPoint): Pair<Int, Int> {
        val w = resources.displayMetrics.widthPixels
        val h = resources.displayMetrics.heightPixels
        return (p.fx * w).toInt() to (p.fy * h).toInt()
    }

    private fun removeAllViews() {
        pin?.cancel()
        buttons.forEach { it.hide() }
        hideHandle()
        panelView?.let { runCatching { wm.removeView(it) } }
        panelView = null
    }

    companion object {
        const val ACTION_STOP = "com.zyntap.app.overlay.STOP"
        private const val NOTIF_ID = 1001

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
