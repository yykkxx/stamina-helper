package com.stamina.helper.calibration

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.stamina.helper.prefs.AppPreferences
import kotlin.math.roundToInt

enum class CalibMode { CANCEL, SWIPE }

/**
 * 定位器校准浮层 —— 极简版
 *
 * 界面组成（仅两个元素，不覆盖全屏）：
 *  1. 贴边控制面板（宽 80dp，高 ~90dp，可拖动）
 *     - 标题 + × 关闭
 *     - 「确定位置」按钮 → 保存并退出
 *  2. 圆形定位按钮（直径 32dp，直接可拖，默认穿透）
 *     - CANCEL: 1 个按钮 ✓
 *     - SWIPE: 2 个按钮 1→2，中间画指示连线
 *
 * 定位按钮默认 FLAG_NOT_TOUCHABLE 穿透，点面板「拖动」切换为可触摸。
 */
class CalibrationService(
    private val context: Context,
    private val mode: CalibMode,
    private val onFinish: (Boolean) -> Unit
) {
    private val prefs = AppPreferences(context)
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val dp = context.resources.displayMetrics.density

    private var panelView: View? = null
    private var dragToggleBtn: TextView? = null
    private var editMode = false

    private val pointViews = mutableMapOf<Target, PointView>()
    private val pointLps = mutableMapOf<Target, WindowManager.LayoutParams>()

    private var cancelX = prefs.cancelPointX; private var cancelY = prefs.cancelPointY
    private var startX  = prefs.swipeStartX;  private var startY  = prefs.swipeStartY
    private var endX    = prefs.swipeEndX;    private var endY    = prefs.swipeEndY

    private enum class Target { CANCEL, START, END }
    private var dragging: Target? = null

    private val btnSize = (32 * dp).roundToInt()
    private val panelW = (82 * dp).roundToInt()

    // ===================== 对外 =====================

    fun show() {
        if (panelView != null) return
        showPanel()
        showPoints()
    }

    fun dismiss() {
        pointViews.values.forEach { v -> try { wm.removeView(v) } catch (_: Exception) {} }
        pointViews.clear(); pointLps.clear()
        panelView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        panelView = null
        dragging = null
    }

    private fun saveAndExit() {
        when (mode) {
            CalibMode.CANCEL -> {
                prefs.cancelPointX = cancelX; prefs.cancelPointY = cancelY
                prefs.cancelCalibrated = true
            }
            CalibMode.SWIPE -> {
                prefs.swipeStartX = startX; prefs.swipeStartY = startY
                prefs.swipeEndX = endX; prefs.swipeEndY = endY
                prefs.swipeCalibrated = true
            }
        }
        dismiss()
        onFinish(true)
    }

    private fun screenWidth() = context.resources.displayMetrics.widthPixels
    private fun screenHeight() = context.resources.displayMetrics.heightPixels

    // ===================== 窗口参数 =====================

    private fun overlayParams(w: Int, h: Int, x: Int, y: Int, touchable: Boolean) =
        WindowManager.LayoutParams(w, h).apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP or Gravity.START
            this.x = x; this.y = y
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }

    // ===================== 可拖动面板 =====================

    @SuppressLint("ViewConstructor")
    private inner class PanelLayout(ctx: Context) : LinearLayout(ctx) {
        private var startLX = 0; private var startLY = 0
        private var touchX = 0f; private var touchY = 0f
        private var draggingPanel = false
        private val slop = 6 * dp

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val lp = panelLp ?: return false
                    startLX = lp.x; startLY = lp.y
                    touchX = ev.rawX; touchY = ev.rawY
                    draggingPanel = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!draggingPanel) {
                        if (kotlin.math.abs(ev.rawX - touchX) > slop ||
                            kotlin.math.abs(ev.rawY - touchY) > slop)
                            draggingPanel = true
                    }
                    if (draggingPanel) return true
                }
            }
            return super.onInterceptTouchEvent(ev)
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            val lp = panelLp ?: return false
            when (ev.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    if (draggingPanel) {
                        lp.x = (startLX + (ev.rawX - touchX)).toInt()
                        lp.y = (startLY + (ev.rawY - touchY)).toInt()
                        try { wm.updateViewLayout(this, lp) } catch (_: Exception) {}
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> draggingPanel = false
            }
            return super.onTouchEvent(ev)
        }
    }

    private var panelLp: WindowManager.LayoutParams? = null

    private fun showPanel() {
        val panel = PanelLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable().apply {
                cornerRadius = 12 * dp
                setColor(Color.parseColor("#F0FFFFFF"))
                setStroke((1 * dp).toInt(), Color.parseColor("#20000000"))
            }
            setPadding((6 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt())
        }

        // 标题行
        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(TextView(context).apply {
            text = if (mode == CalibMode.CANCEL) "定位" else "滑动"
            setTextColor(Color.parseColor("#333333")); textSize = 10f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        titleRow.addView(TextView(context).apply {
            text = "×"; setTextColor(Color.parseColor("#FF5252")); textSize = 13f
            gravity = Gravity.CENTER
            setOnClickListener { dismiss(); onFinish(false) }
        }, LinearLayout.LayoutParams((20 * dp).toInt(), (20 * dp).toInt()))
        panel.addView(titleRow)

        // 拖动切换按钮
        dragToggleBtn = TextView(context).apply {
            text = "拖动"
            setTextColor(Color.parseColor("#333333")); textSize = 10f; gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = 8 * dp
                setColor(Color.parseColor("#E0E0E0"))
            }
            setOnClickListener { toggleEditMode() }
        }
        panel.addView(dragToggleBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (24 * dp).toInt()
        ).apply { topMargin = (4 * dp).toInt(); bottomMargin = (4 * dp).toInt() })

        // 确定按钮
        panel.addView(TextView(context).apply {
            text = "确定位置"
            setTextColor(Color.WHITE); textSize = 10f; gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = 8 * dp
                setColor(Color.parseColor("#333333"))
            }
            setOnClickListener { saveAndExit() }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (24 * dp).toInt()
        ))

        panelView = panel
        val initX = (4 * dp).toInt()
        val initY = (screenHeight() / 3)
        val lp = overlayParams(panelW, WindowManager.LayoutParams.WRAP_CONTENT, initX, initY, touchable = true)
        panelLp = lp
        addView(panel, lp, "panel")
    }

    // ===================== 定位按钮 =====================

    private fun showPoints() {
        when (mode) {
            CalibMode.CANCEL -> listOf(Target.CANCEL)
            CalibMode.SWIPE -> listOf(Target.START, Target.END)
        }.forEach { addPoint(it) }
    }

    private fun addPoint(target: Target) {
        val view = PointView(context, target)
        val lp = overlayParams(btnSize, btnSize, 0, 0, touchable = false)
        updatePointLp(target, lp)
        pointViews[target] = view
        pointLps[target] = lp
        addView(view, lp, "point-$target")
    }

    private fun currentRatio(t: Target) = when (t) {
        Target.CANCEL -> cancelX to cancelY
        Target.START  -> startX  to startY
        Target.END    -> endX    to endY
    }

    private fun setRatio(t: Target, x: Float, y: Float) {
        when (t) {
            Target.CANCEL -> { cancelX = x; cancelY = y }
            Target.START  -> { startX = x;  startY = y }
            Target.END    -> { endX = x;    endY = y }
        }
    }

    private fun updatePointLp(t: Target, lp: WindowManager.LayoutParams) {
        val (rx, ry) = currentRatio(t)
        lp.x = (rx * screenWidth() - lp.width / 2f).roundToInt()
        lp.y = (ry * screenHeight() - lp.height / 2f).roundToInt()
    }

    private fun addView(v: View, lp: WindowManager.LayoutParams, tag: String) {
        try { wm.addView(v, lp) } catch (e: Exception) { Log.e(TAG, "addView failed: $tag", e) }
    }

    private fun toggleEditMode() {
        editMode = !editMode
        dragToggleBtn?.text = if (editMode) "完成" else "拖动"
        dragToggleBtn?.let {
            (it.background as? GradientDrawable)?.setColor(
                if (editMode) Color.parseColor("#FFE0B2") else Color.parseColor("#E0E0E0")
            )
        }
        pointViews.forEach { (t, v) ->
            val lp = pointLps[t] ?: return@forEach
            lp.flags = if (editMode)
                lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            else
                lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            try { wm.updateViewLayout(v, lp) } catch (_: Exception) {}
            v.invalidate()
        }
    }

    // ===================== 定位按钮视图 =====================

    @SuppressLint("ViewConstructor")
    private inner class PointView(ctx: Context, private val target: Target) : View(ctx) {

        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#333333"); textAlign = Paint.Align.CENTER; isFakeBoldText = true
        }
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

        private var grabDX = 0f; private var grabDY = 0f

        override fun onDraw(c: Canvas) {
            val cx = width / 2f; val cy = height / 2f
            val r = width / 2f

            if (mode == CalibMode.SWIPE) drawConnection(c, cx, cy)

            // 阴影
            ring.color = Color.parseColor("#15000000")
            ring.strokeWidth = 1f * dp
            c.drawCircle(cx, cy + 1f * dp, r - 1f * dp, ring)

            // 白色主体
            fill.color = if (editMode) Color.WHITE else Color.parseColor("#E8E8E8")
            c.drawCircle(cx, cy, r - 2f * dp, fill)

            // 深灰描边
            ring.color = Color.parseColor("#333333")
            ring.strokeWidth = if (dragging == target) 2.5f * dp else 1.5f * dp
            ring.alpha = if (editMode) 255 else 120
            c.drawCircle(cx, cy, r - 2f * dp, ring)

            // 数字
            txt.textSize = 11f * dp
            val label = when (target) {
                Target.CANCEL -> "✓"
                Target.START -> "1"
                Target.END -> "2"
            }
            c.drawText(label, cx, cy + 3.5f * dp, txt)
        }

        private fun drawConnection(c: Canvas, cx: Float, cy: Float) {
            val other = if (target == Target.START) Target.END else Target.START
            val myLp = pointLps[target] ?: return
            val otherLp = pointLps[other] ?: return

            val myCx = myLp.x + btnSize / 2f
            val myCy = myLp.y + btnSize / 2f
            val otherCx = otherLp.x + btnSize / 2f
            val otherCy = otherLp.y + btnSize / 2f

            val localX = otherCx - myCx + cx
            val localY = otherCy - myCy + cy

            line.color = if (dragging != null) Color.parseColor("#AA333333")
                         else Color.parseColor("#50333333")
            line.strokeWidth = if (dragging != null) 2f * dp else 1.2f * dp
            c.drawLine(cx, cy, localX, localY, line)

            fill.color = Color.parseColor("#333333")
            fill.alpha = if (dragging != null) 180 else 70
            c.drawCircle(localX, localY, 3f * dp, fill)
            fill.alpha = 255
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragging = target
                    val lp = pointLps[target] ?: return false
                    grabDX = e.rawX - (lp.x + width / 2f)
                    grabDY = e.rawY - (lp.y + height / 2f)
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (dragging == target) {
                        moveTo(e.rawX - grabDX, e.rawY - grabDY)
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging == target) {
                        dragging = null
                        moveTo(e.rawX - grabDX, e.rawY - grabDY)
                        invalidate()
                    }
                    return true
                }
            }
            return super.onTouchEvent(e)
        }
    }

    private fun moveTo(screenCx: Float, screenCy: Float) {
        val t = dragging ?: return
        val sx = (screenCx / screenWidth().toFloat()).coerceIn(0f, 1f)
        val sy = (screenCy / screenHeight().toFloat()).coerceIn(0f, 1f)
        setRatio(t, sx, sy)
        val lp = pointLps[t]; val v = pointViews[t]
        if (lp != null && v != null) {
            lp.x = (screenCx - lp.width / 2f).roundToInt()
            lp.y = (screenCy - lp.height / 2f).roundToInt()
            try { wm.updateViewLayout(v, lp) } catch (e: Exception) { Log.e(TAG, "update failed", e) }
        }
        pointViews.values.forEach { it.invalidate() }
    }

    companion object { private const val TAG = "CalibService" }
}