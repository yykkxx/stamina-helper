package com.stamina.helper.floating

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/**
 * 液态玻璃风格悬浮窗
 * 顶行：应用图标 + "体力回复" + 关闭按钮
 * 中部：状态文本 + 进度条
 * 底部：操作按钮（回复/停止/OK）
 */
class FloatingWindowManager(
    private val context: Context,
    private val windowManager: WindowManager,
    private val callback: FloatingWindowCallback
) {
    private var rootView: View? = null
    private var statusText: TextView? = null
    private var actionButton: TextView? = null
    private var closeButton: TextView? = null
    private var countdownProgress: ProgressBar? = null
    private var headerText: TextView? = null
    private var loopIndicator: TextView? = null

    private var currentState = FloatingState.IDLE
    var isShowing = false
        private set

    private val dp: Float get() = context.resources.displayMetrics.density
    private val isDark: Boolean
        get() = (context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

    private val layoutParams = WindowManager.LayoutParams().apply {
        type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        format = PixelFormat.RGBA_8888
        flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        gravity = Gravity.TOP or Gravity.START
        x = 0
        y = 200
        width = WindowManager.LayoutParams.WRAP_CONTENT
        height = WindowManager.LayoutParams.WRAP_CONTENT
    }

    fun show() {
        if (isShowing) return
        val view = createView()
        rootView = view
        try {
            windowManager.addView(view, layoutParams)
            isShowing = true
            updateState(currentState)
        } catch (e: Exception) {
            Log.e(TAG, "show failed", e)
        }
    }

    fun hide() {
        if (!isShowing) return
        rootView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) {
                Log.e(TAG, "hide failed", e)
            }
        }
        rootView = null
        isShowing = false
    }

    fun destroy() {
        hide()
        currentState = FloatingState.IDLE
    }

    fun updateState(state: FloatingState, remainingSeconds: Float = 0f) {
        currentState = state
        if (!isShowing) return

        when (state) {
            FloatingState.IDLE -> {
                headerText?.text = "体力回复"
                headerText?.setTextColor(headerColor())
                statusText?.visibility = View.GONE
                countdownProgress?.visibility = View.GONE
                loopIndicator?.visibility = View.GONE
                actionButton?.text = "回复"
                actionButton?.visibility = View.VISIBLE
                actionButton?.isEnabled = true
                setBtnColor("#22C55E")
                closeButton?.visibility = View.VISIBLE
            }
            FloatingState.SWIPING -> {
                headerText?.text = "体力回复"
                headerText?.setTextColor(Color.parseColor("#F59E0B"))
                statusText?.text = "滑动中..."
                statusText?.visibility = View.VISIBLE
                countdownProgress?.visibility = View.GONE
                loopIndicator?.visibility = View.GONE
                actionButton?.visibility = View.GONE
                closeButton?.visibility = View.VISIBLE
            }
            FloatingState.COUNTDOWN -> {
                headerText?.text = "体力回复"
                headerText?.setTextColor(Color.parseColor("#22C55E"))
                statusText?.text = formatSec(remainingSeconds) + "s"
                statusText?.visibility = View.VISIBLE
                countdownProgress?.visibility = View.VISIBLE
                // 倒计时时如果正在循环，显示停止按钮
                actionButton?.text = "停止"
                actionButton?.visibility = View.VISIBLE
                actionButton?.isEnabled = true
                setBtnColor("#EF4444")
                closeButton?.visibility = View.VISIBLE
            }
            FloatingState.DONE -> {
                headerText?.text = "体力回复"
                headerText?.setTextColor(Color.parseColor("#3B82F6"))
                statusText?.text = "已完成"
                statusText?.visibility = View.VISIBLE
                countdownProgress?.visibility = View.GONE
                loopIndicator?.visibility = View.GONE
                actionButton?.text = "OK"
                actionButton?.visibility = View.VISIBLE
                actionButton?.isEnabled = true
                setBtnColor("#3B82F6")
                closeButton?.visibility = View.VISIBLE
            }
        }
    }

    fun updateCountdown(remainingSeconds: Float) {
        if (!isShowing) return
        statusText?.text = formatSec(remainingSeconds) + "s"
        val total = callback.getTotalWaitSeconds()
        if (total > 0) {
            countdownProgress?.progress =
                ((total - remainingSeconds) / total * 100).toInt().coerceIn(0, 100)
        }
    }

    /** 显示循环轮数指示器 */
    fun updateLoopInfo(current: Int, max: Int) {
        if (!isShowing) return
        loopIndicator?.let {
            it.visibility = View.VISIBLE
            it.text = if (max > 0) "第 $current / $max 轮" else "第 $current 轮"
        }
    }

    private fun formatSec(s: Float): String =
        if (s >= 10f) String.format("%.0f", s) else String.format("%.1f", s)

    private fun headerColor(): Int =
        if (isDark) Color.parseColor("#E8ECF2") else Color.parseColor("#11151C")

    private fun setBtnColor(color: String) {
        actionButton?.let {
            (it.background as? GradientDrawable)?.setColor(Color.parseColor(color))
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createView(): View {
        val root = FrameLayout(context)

        val bgDrawable = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            if (isDark) intArrayOf(
                Color.parseColor("#F21A2B20"),
                Color.parseColor("#F2121A16")
            ) else intArrayOf(
                Color.parseColor("#F7FFFFFF"),
                Color.parseColor("#EAF3FCEF")
            )
        ).apply {
            cornerRadius = 16 * dp
            setStroke(
                (1 * dp).toInt(),
                if (isDark) Color.parseColor("#4DFFFFFF") else Color.parseColor("#B3FFFFFF")
            )
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = bgDrawable
            setPadding((9 * dp).toInt(), (7 * dp).toInt(), (9 * dp).toInt(), (7 * dp).toInt())
            layoutParams = FrameLayout.LayoutParams(
                (108 * dp).toInt(), FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // ===== 顶行 =====
        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val icon = ImageView(context).apply {
            setImageDrawable(context.getDrawable(com.stamina.helper.R.mipmap.ic_launcher))
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams((18 * dp).toInt(), (18 * dp).toInt())
        }

        headerText = TextView(context).apply {
            text = "体力回复"
            setTextColor(headerColor())
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.marginStart = (4 * dp).toInt()
            layoutParams = lp
        }

        closeButton = TextView(context).apply {
            text = "✕"
            setTextColor(if (isDark) Color.parseColor("#9AA6B6") else Color.parseColor("#8A93A0"))
            textSize = 10f
            setPadding((2 * dp).toInt(), 0, 0, 0)
            setOnClickListener { callback.onCloseButtonClicked() }
        }

        topRow.addView(icon)
        topRow.addView(headerText)
        topRow.addView(closeButton)
        container.addView(topRow)

        // ===== 循环指示器 =====
        loopIndicator = TextView(context).apply {
            setTextColor(Color.parseColor("#F59E0B"))
            textSize = 8.5f
            gravity = Gravity.CENTER
            maxLines = 1
            visibility = View.GONE
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = (2 * dp).toInt()
            layoutParams = lp
        }
        container.addView(loopIndicator)

        // ===== 状态文本 =====
        statusText = TextView(context).apply {
            setTextColor(if (isDark) Color.parseColor("#9AA6B6") else Color.parseColor("#5B6675"))
            textSize = 9f
            gravity = Gravity.CENTER
            visibility = View.GONE
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = (3 * dp).toInt()
            layoutParams = lp
        }
        container.addView(statusText)

        // ===== 进度条 =====
        countdownProgress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            visibility = View.GONE
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (3 * dp).toInt()
            )
            lp.topMargin = (2 * dp).toInt()
            layoutParams = lp
            progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#22C55E"))
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(
                if (isDark) Color.parseColor("#33FFFFFF") else Color.parseColor("#1A000000")
            )
        }
        container.addView(countdownProgress)

        // ===== 操作按钮 =====
        actionButton = TextView(context).apply {
            text = "回复"
            setTextColor(Color.WHITE)
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, (5 * dp).toInt(), 0, (5 * dp).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 8 * dp
                setColor(Color.parseColor("#22C55E"))
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = (4 * dp).toInt()
            layoutParams = lp
            setOnClickListener { callback.onActionClicked() }
        }
        container.addView(actionButton)

        root.addView(container)
        root.setOnTouchListener { v, event -> handleTouch(v, event) }
        return root
    }

    private var ix = 0; private var iy = 0
    private var itx = 0f; private var ity = 0f
    private var dragging = false

    @SuppressLint("ClickableViewAccessibility")
    private fun handleTouch(view: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                ix = layoutParams.x; iy = layoutParams.y
                itx = event.rawX; ity = event.rawY
                dragging = false; return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - itx; val dy = event.rawY - ity
                if (dx > 5 || dy > 5 || dx < -5 || dy < -5) dragging = true
                if (dragging) {
                    layoutParams.x = ix + dx.toInt()
                    layoutParams.y = iy + dy.toInt()
                    try { windowManager.updateViewLayout(view, layoutParams) } catch (_: Exception) {}
                }
                return true
            }
            MotionEvent.ACTION_UP -> return dragging
        }
        return false
    }

    companion object { private const val TAG = "FloatWindowMgr" }
}

interface FloatingWindowCallback {
    fun onActionClicked()
    fun onCloseButtonClicked()
    fun getTotalWaitSeconds(): Float
    fun onStopLoopClicked()
}