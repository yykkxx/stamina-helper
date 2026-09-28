package com.stamina.helper.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import com.stamina.helper.floating.FloatingState
import com.stamina.helper.floating.FloatingWindowCallback
import com.stamina.helper.floating.FloatingWindowManager
import com.stamina.helper.gesture.AccessibilityGestureExecutor
import com.stamina.helper.gesture.GestureConfig
import com.stamina.helper.gesture.GestureExecutor
import com.stamina.helper.gesture.RootGestureExecutor
import com.stamina.helper.prefs.AppPreferences
import com.stamina.helper.timer.StaminaTimer
import com.stamina.helper.timer.TimerCallback

/**
 * 前台服务，管理悬浮窗生命周期、手势执行、倒计时
 */
class FloatingWindowService : Service(), FloatingWindowCallback, TimerCallback {

    private lateinit var prefs: AppPreferences
    private lateinit var windowManager: WindowManager
    private lateinit var floatingManager: FloatingWindowManager
    private var gestureExecutor: GestureExecutor? = null
    private var timer: StaminaTimer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var currentState = FloatingState.IDLE
    private var loopCount = 0          // 当前已循环轮数
    private var isLooping = false      // 是否正在循环中

    // 单线程执行器，懒加载
    private var executorService: java.util.concurrent.ExecutorService? = null
    private fun getExecutor(): java.util.concurrent.ExecutorService {
        if (executorService == null) {
            executorService = java.util.concurrent.Executors.newSingleThreadExecutor()
        }
        return executorService!!
    }

    override fun onCreate() {
        super.onCreate()
        prefs = AppPreferences(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        floatingManager = FloatingWindowManager(this, windowManager, this)
        createNotificationChannel()
        Log.i(TAG, "FloatingWindowService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SERVICE -> {
                startForeground(NOTIFICATION_ID, createNotification(true))
                floatingManager.show()
                floatingManager.updateState(FloatingState.IDLE)
            }
            ACTION_SHOW_FLOATING -> {
                floatingManager.show()
                // 恢复当前状态显示
                floatingManager.updateState(currentState)
                refreshNotification(true)
            }
            ACTION_HIDE_FLOATING -> {
                floatingManager.hide()
                refreshNotification(false)
            }
            ACTION_STOP_SERVICE -> {
                floatingManager.destroy()
                stopForeground(true)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        timer?.cancel()
        floatingManager.destroy()
        executorService?.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ===== FloatingWindowCallback =====

    override fun onActionClicked() {
        when (currentState) {
            FloatingState.IDLE -> {
                loopCount = 0
                isLooping = prefs.autoActionMode == AppPreferences.MODE_AUTO_LOOP
                startStaminaRecovery()
            }
            FloatingState.COUNTDOWN -> {
                // 倒计时中点按钮 = 停止循环
                if (isLooping) {
                    isLooping = false
                    timer?.cancel()
                    currentState = FloatingState.IDLE
                    floatingManager.updateState(FloatingState.IDLE)
                }
            }
            FloatingState.DONE -> {
                currentState = FloatingState.IDLE
                isLooping = false
                floatingManager.updateState(FloatingState.IDLE)
            }
            else -> { }
        }
    }

    override fun onStopLoopClicked() {
        isLooping = false
        timer?.cancel()
        currentState = FloatingState.IDLE
        floatingManager.updateState(FloatingState.IDLE)
    }

    override fun onCloseButtonClicked() {
        // 隐藏悬浮窗，但服务继续运行
        floatingManager.hide()
        refreshNotification(false)
        Toast.makeText(this, "悬浮窗已隐藏，点击通知可重新显示", Toast.LENGTH_SHORT).show()
    }

    override fun getTotalWaitSeconds(): Float = prefs.waitSeconds

    // ===== TimerCallback =====

    override fun onTick(remainingSeconds: Float) {
        handler.post {
            floatingManager.updateCountdown(remainingSeconds)
        }
    }

    override fun onFinish() {
        handler.post {
            currentState = FloatingState.DONE
            floatingManager.updateState(FloatingState.DONE)
        }
        // 发送通知提醒用户
        sendDoneNotification()

        // ===== 自动动作模式 =====
        when (prefs.autoActionMode) {
            AppPreferences.MODE_AUTO_CANCEL -> {
                // 仅点击取消，结束
                if (prefs.cancelCalibrated) {
                    getExecutor().execute {
                        Thread.sleep(300)
                        clickCancel()
                    }
                }
            }

            AppPreferences.MODE_AUTO_LOOP -> {
                if (prefs.cancelCalibrated && prefs.swipeCalibrated && isLooping) {
                    loopCount++
                    val maxLoop = prefs.maxLoopCount
                    // 检查是否达到最大轮数（0=无限）
                    if (maxLoop > 0 && loopCount >= maxLoop) {
                        // 达到上限，执行最后一次点取消后停止
                        getExecutor().execute {
                            Thread.sleep(300)
                            clickCancel()
                        }
                        isLooping = false
                        handler.post {
                            floatingManager.updateState(FloatingState.IDLE)
                            currentState = FloatingState.IDLE
                        }
                        return
                    }
                    getExecutor().execute {
                        Thread.sleep(300)
                        clickCancel()
                        Thread.sleep(prefs.loopGapMs)
                        if (isLooping) {
                            performSwipeOnly()
                        }
                    }
                }
            }
        }
    }

    /**
     * 执行滑动（仅滑动，不启动倒计时），用于循环模式
     */
    private fun performSwipeOnly() {
        if (gestureExecutor == null) gestureExecutor = createGestureExecutor()
        val ex = gestureExecutor ?: return
        if (!ex.isAvailable()) return

        val dm = Resources.getSystem().displayMetrics
        val config = GestureConfig(
            startXRatio = prefs.swipeStartX,
            startYRatio = prefs.swipeStartY,
            endXRatio = prefs.swipeEndX,
            endYRatio = prefs.swipeEndY,
            swipeDurationMs = prefs.swipeDurationMs,
            swipeIntervalMs = prefs.swipeIntervalMs
        )
        ex.performEdgeSwipe(config, dm.widthPixels, dm.heightPixels)

        // 滑动后自动开始下一轮倒计时
        handler.post {
            if (prefs.autoActionMode == AppPreferences.MODE_AUTO_LOOP && isLooping) {
                startCountdown()
            }
        }
    }

    /** 点击已校准的取消按钮（内部线程） */
    private fun clickCancel() {
        val dm = Resources.getSystem().displayMetrics
        val x = dm.widthPixels * prefs.cancelPointX
        val y = dm.heightPixels * prefs.cancelPointY
        gestureExecutor?.performClick(x, y)
    }

    // ===== 核心逻辑 =====

    /**
     * 开始体力回复流程：滑动 → 倒计时
     */
    private fun startStaminaRecovery() {
        // 复用手势执行器，避免重复创建
        if (gestureExecutor == null) {
            gestureExecutor = createGestureExecutor()
        }
        if (gestureExecutor == null || !gestureExecutor!!.isAvailable()) {
            Toast.makeText(this, "手势执行器不可用，请检查权限设置", Toast.LENGTH_LONG).show()
            return
        }

        currentState = FloatingState.SWIPING
        floatingManager.updateState(FloatingState.SWIPING)

        getExecutor().execute {
            // 使用用户校准的起点/终点定位点
            val config = GestureConfig(
                startXRatio = prefs.swipeStartX,
                startYRatio = prefs.swipeStartY,
                endXRatio = prefs.swipeEndX,
                endYRatio = prefs.swipeEndY,
                swipeDurationMs = prefs.swipeDurationMs,
                swipeIntervalMs = prefs.swipeIntervalMs
            )

            val displayMetrics = Resources.getSystem().displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels

            Log.i(TAG, "Starting edge swipe: screen=${screenWidth}x${screenHeight}")
            val success = gestureExecutor!!.performEdgeSwipe(config, screenWidth, screenHeight)

            handler.post {
                if (success) {
                    // 开始倒计时
                    startCountdown()
                } else {
                    Toast.makeText(this, "滑动执行失败，请重试", Toast.LENGTH_LONG).show()
                    currentState = FloatingState.IDLE
                    floatingManager.updateState(FloatingState.IDLE)
                }
            }
        }
    }

    /**
     * 开始倒计时
     */
    private fun startCountdown() {
        val seconds = prefs.waitSeconds
        timer = StaminaTimer(seconds, this)
        timer?.start()
        currentState = FloatingState.COUNTDOWN
        floatingManager.updateState(FloatingState.COUNTDOWN, seconds)
        // 如果正在循环，更新轮数显示
        if (isLooping) {
            floatingManager.updateLoopInfo(loopCount + 1, prefs.maxLoopCount)
        }
    }

    /**
     * 根据用户设置创建手势执行器
     */
    private fun createGestureExecutor(): GestureExecutor? {
        return when (prefs.executionMode) {
            AppPreferences.MODE_ROOT -> RootGestureExecutor()
            AppPreferences.MODE_ACCESSIBILITY -> AccessibilityGestureExecutor()
            else -> AccessibilityGestureExecutor()
        }
    }

    // ===== 通知管理 =====

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "体力小助手",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "前台服务通知，管理悬浮窗显示"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    /**
     * 创建通知
     * @param windowVisible 悬浮窗是否可见，决定通知文案和点击行为
     */
    private fun createNotification(windowVisible: Boolean): Notification {
        val intent = Intent(this, FloatingWindowService::class.java).apply {
            action = if (windowVisible) ACTION_HIDE_FLOATING else ACTION_SHOW_FLOATING
        }
        val pendingIntent = PendingIntent.getService(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "体力小助手"
        val text = if (windowVisible) "运行中 · 点击隐藏悬浮窗" else "点击显示悬浮窗"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        }
    }

    /**
     * 更新通知（悬浮窗显示/隐藏状态切换时调用）
     */
    private fun refreshNotification(windowVisible: Boolean) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, createNotification(windowVisible))
    }

    /**
     * 发送倒计时完成通知（高优先级，提醒用户）
     */
    private fun sendDoneNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_DONE_ID,
                "体力回复完成",
                NotificationManager.IMPORTANCE_HIGH
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }

        val intent = Intent(this, FloatingWindowService::class.java).apply {
            action = ACTION_SHOW_FLOATING
        }
        val pendingIntent = PendingIntent.getService(
            this, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_DONE_ID)
                .setContentTitle("体力回复完成！")
                .setContentText("时间到了，请点击「取消」返回游戏")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("体力回复完成！")
                .setContentText("时间到了，请点击「取消」返回游戏")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
        }

        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_DONE_ID, notification)
    }

    companion object {
        private const val TAG = "FloatWindowService"

        const val ACTION_START_SERVICE = "com.stamina.helper.START_SERVICE"
        const val ACTION_STOP_SERVICE = "com.stamina.helper.STOP_SERVICE"
        const val ACTION_SHOW_FLOATING = "com.stamina.helper.SHOW_FLOATING"
        const val ACTION_HIDE_FLOATING = "com.stamina.helper.HIDE_FLOATING"

        private const val CHANNEL_ID = "stamina_helper_channel"
        private const val CHANNEL_DONE_ID = "stamina_helper_done_channel"
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_DONE_ID = 2

        /**
         * 启动服务
         */
        fun start(context: Context) {
            val intent = Intent(context, FloatingWindowService::class.java).apply {
                action = ACTION_START_SERVICE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * 停止服务
         */
        fun stop(context: Context) {
            val intent = Intent(context, FloatingWindowService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }
}