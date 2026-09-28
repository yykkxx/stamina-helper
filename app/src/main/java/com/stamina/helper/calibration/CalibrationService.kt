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
 * 定位器校准浮层 —— 极简白色版
 *
 * 窗口组成：
 *  1. 控制面板（固定宽度，可拖动，初始在屏幕左侧偏上）
 *  2. 圆形定位按钮（1 或 2 个，默认穿透）
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
    private var statusTv: TextView? = null
    private var editMode = false

    private val pointViews = mutableMapOf<Target, PointView>()
    private val pointLps = mutableMapOf<Target, WindowManager.LayoutParams>()

    private var cancelX = prefs.cancelPointX; private var cancelY = prefs.cancelPointY
    private var startX  = prefs.swipeStartX;  private var startY  = prefs.swipeStartY
    private var endX    = prefs.swipeEndX;    private var endY    = prefs.swipeEndY

    private enum class Target { CANCEL, START, END }
    private var dragging: Target? = null

    /** 定位按钮直径 */
    private val btnSize = (36 * dp).roundToInt()
    /** 面板固定宽高 */
    private val panelW = (96 * dp).roundToInt()
    private val panelH = (116 * dp).roundToInt()

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

    // ===================== 屏幕尺寸（用 context 而非 Resources.getSystem） =====================

    private fun screenWidth(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.resources.displayMetrics.widthPixels
        } else {
            context.resources.displayMetrics.widthPixels
        }
    }

    private fun screenHeight(): Int {
        return context.resources.displayMetrics.heightPixels
    }

    // ===================== 窗口参数 =====================

    private fun overlayParams(w: Int, h: Int, x: Int, y: Int, touchable: Boolean) =
        WindowManager.LayoutParams(w, h).apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP or Gravity.START
            this.x = x; this.y = y
            // 不用 FLAG_LAYOUT_NO_LIMITS，避免 ROM 异常扩展
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
        private val touchSlop = 6 * dp

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
                        val dx = kotlin.math.abs(ev.rawX - touchX)
                        val dy = kotlin.math.abs(ev.rawY - touchY)
                        if (dx > touchSlop || dy > touchSlop) draggingPanel = true
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
                cornerRadius = 14 * dp
                setColor(Color.parseColor("#F0FFFFFF"))
                setStroke((1 * dp).toInt(), Color.parseColor("#20000000"))
            }
            setPadding((7 * dp).toInt(), (7 * dp).toInt(), (7 * dp).toInt(), (7 * dp).toInt())
        }

        // 标题 + 关闭
        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleTv = TextView(context).apply {
            text = if (mode == CalibMode.CANCEL) "取消" else "滑动"
            setTextColor(Color.parseColor("#333333")); textSize = 10f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val closeTv = TextView(context).apply {
            text = "×"; setTextColor(Color.parseColor("#FF5252")); textSize = 14f
            gravity = Gravity.CENTER
            setOnClickListener { dismiss(); onFinish(false) }
        }
        titleRow.addView(titleTv)
        titleRow.addView(closeTv, LinearLayout.LayoutParams((22 * dp).toInt(), (22 * dp).toInt()))
        panel.addView(titleRow)

        // 状态
        statusTv = TextView(context).apply {
            text = "穿透中"
            setTextColor(Color.parseColor("#999999")); textSize = 8f
            setPadding(0, (2 * dp).toInt(), 0, (3 * dp).toInt())
        }
        panel.addView(statusTv)

        // 分隔线
        panel.addView(View(context).apply {
            setBackgroundColor(Color.parseColor("#15000000"))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (0.5f * dp).toInt()).apply {
            bottomMargin = (5 * dp).toInt()
        })

        // 拖动按钮
        dragToggleBtn = TextView(context).apply {
            text = "拖动"
            setTextColor(Color.parseColor("#333333")); textSize = 11f; gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = 10 * dp
                setColor(Color.parseColor("#E8E8E8"))
            }
            setOnClickListener { toggleEditMode() }
        }
        panel.addView(dragToggleBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (28 * dp).toInt()
        ).apply { bottomMargin = (5 * dp).toInt() })

        // 确认按钮
        panel.addView(TextView(context).apply {
            text = "✓"
            setTextColor(Color.parseColor("#333333")); textSize = 11f; gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = 10 * dp
                setColor(Color.parseColor("#E8E8E8"))
            }
            setOnClickListener { saveAndExit() }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (28 * dp).toInt()
        ))

        panelView = panel
        // 面板初始位置：屏幕左侧偏上
        val initX = (4 * dp).toInt()
        val initY = (screenHeight() - panelH) / 2
        val lp = overlayParams(panelW, panelH, initX, initY, touchable = true)
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

    private fun currentRatio(t: Target): Pair<Float, Float> = when (t) {
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
                if (editMode) Color.parseColor("#FFE0B2") else Color.parseColor("#E8E8E8")
            )
        }
        statusTv?.text = if (editMode) "拖动中" else "穿透中"

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
    private inner class PointView(
        ctx: Context, private val target: Target
    ) : View(ctx) {

        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#333333"); textAlign = Paint.Align.CENTER; isFakeBoldText = true
        }
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

        private var grabDX = 0f; private var grabDY = 0f

        override fun onDraw(c: Canvas) {
            val cx = width / 2f; val cy = height / 2f
            val radius = width / 2f

            // SWIPE 连线
            if (mode == CalibMode.SWIPE) drawConnection(c, cx, cy)

            // 阴影圈
            ring.color = Color.parseColor("#18000000")
            ring.strokeWidth = 1f * dp
            c.drawCircle(cx, cy + 1f * dp, radius - 1f * dp, ring)

            // 白色主体
            fill.color = if (editMode) Color.WHITE else Color.parseColor("#F0F0F0")
            c.drawCircle(cx, cy, radius - 2f * dp, fill)

            // 深灰描边
            ring.color = Color.parseColor("#333333")
            ring.strokeWidth = if (dragging == target) 2.5f * dp else 1.5f * dp
            ring.alpha = if (editMode) 255 else 130
            c.drawCircle(cx, cy, radius - 2f * dp, ring)

            // 数字
            txt.textSize = 12f * dp
            val label = when (target) {
                Target.CANCEL -> "✓"
                Target.START -> "1"
                Target.END -> "2"
            }
            c.drawText(label, cx, cy + 4f * dp, txt)
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
                         else Color.parseColor("#55333333")
            line.strokeWidth = if (dragging != null) 2f * dp else 1.2f * dp
            c.drawLine(cx, cy, localX, localY, line)

            fill.color = Color.parseColor("#333333")
            fill.alpha = if (dragging != null) 200 else 80
            c.drawCircle(localX, localY, 3.5f * dp, fill)
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

    companion object {
        private const val TAG = "CalibService"
    }
}