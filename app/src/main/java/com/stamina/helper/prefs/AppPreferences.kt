package com.stamina.helper.prefs

import android.content.Context
import android.content.SharedPreferences

/**
 * SharedPreferences 封装，管理所有用户可配置参数
 */
class AppPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 等待时间（秒，精确到0.01） */
    var waitSeconds: Float
        get() = prefs.getFloat(KEY_WAIT_SECONDS, DEFAULT_WAIT_SECONDS)
        set(value) = prefs.edit().putFloat(KEY_WAIT_SECONDS, value).apply()

    /** 单次滑动时长(ms)，范围100~500 */
    var swipeDurationMs: Long
        get() = prefs.getLong(KEY_SWIPE_DURATION, DEFAULT_SWIPE_DURATION)
        set(value) = prefs.edit().putLong(KEY_SWIPE_DURATION, value.coerceIn(100, 500)).apply()

    /** 两次滑动间隔(ms) */
    var swipeIntervalMs: Long
        get() = prefs.getLong(KEY_SWIPE_INTERVAL, DEFAULT_SWIPE_INTERVAL)
        set(value) = prefs.edit().putLong(KEY_SWIPE_INTERVAL, value).apply()

    /** 执行模式: "root" 或 "accessibility" */
    var executionMode: String
        get() = prefs.getString(KEY_EXEC_MODE, DEFAULT_EXEC_MODE) ?: DEFAULT_EXEC_MODE
        set(value) = prefs.edit().putString(KEY_EXEC_MODE, value).apply()

    /** 倒计时结束后是否自动点击取消（保留兼容，新逻辑用 autoActionMode） */
    var autoReturn: Boolean
        get() = prefs.getBoolean(KEY_AUTO_RETURN, DEFAULT_AUTO_RETURN)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_RETURN, value).apply()

    /**
     * 倒计时结束后的自动动作模式
     * MODE_AUTO_OFF    = 0 关闭：全部手动
     * MODE_AUTO_CANCEL = 1 仅点击取消：滑动→倒计时→点取消，结束
     * MODE_AUTO_LOOP   = 2 循环：滑动→倒计时→点取消→再滑动… 持续循环
     */
    var autoActionMode: Int
        get() = prefs.getInt(KEY_AUTO_ACTION, MODE_AUTO_OFF)
        set(value) = prefs.edit().putInt(KEY_AUTO_ACTION, value).apply()

    /** 循环模式下，两轮之间额外等待时间(ms) */
    var loopGapMs: Long
        get() = prefs.getLong(KEY_LOOP_GAP, DEFAULT_LOOP_GAP)
        set(value) = prefs.edit().putLong(KEY_LOOP_GAP, value.coerceIn(500, 10000)).apply()

    /** 循环最大轮数，0=无限 */
    var maxLoopCount: Int
        get() = prefs.getInt(KEY_MAX_LOOP, DEFAULT_MAX_LOOP)
        set(value) = prefs.edit().putInt(KEY_MAX_LOOP, value.coerceIn(0, 999)).apply()

    // ===== 定位器坐标（归一化比例 0~1，适配不同分辨率）=====

    /**
     * 【定位器1】取消按钮位置
     * 只需在游戏中手动校准一次，之后永久复用
     */
    var cancelPointX: Float
        get() = prefs.getFloat(KEY_CANCEL_X, DEFAULT_CANCEL_X)
        set(value) = prefs.edit().putFloat(KEY_CANCEL_X, value).apply()

    var cancelPointY: Float
        get() = prefs.getFloat(KEY_CANCEL_Y, DEFAULT_CANCEL_Y)
        set(value) = prefs.edit().putFloat(KEY_CANCEL_Y, value).apply()

    /** 取消定位点是否已校准 */
    var cancelCalibrated: Boolean
        get() = prefs.getBoolean(KEY_CANCEL_CALIBRATED, false)
        set(value) = prefs.edit().putBoolean(KEY_CANCEL_CALIBRATED, value).apply()

    /**
     * 【定位器2】前进键拖拽路径
     * 需要起点和终点两个定位点
     */
    var swipeStartX: Float
        get() = prefs.getFloat(KEY_SWIPE_START_X, DEFAULT_SWIPE_START_X)
        set(value) = prefs.edit().putFloat(KEY_SWIPE_START_X, value).apply()

    var swipeStartY: Float
        get() = prefs.getFloat(KEY_SWIPE_START_Y, DEFAULT_SWIPE_START_Y)
        set(value) = prefs.edit().putFloat(KEY_SWIPE_START_Y, value).apply()

    var swipeEndX: Float
        get() = prefs.getFloat(KEY_SWIPE_END_X, DEFAULT_SWIPE_END_X)
        set(value) = prefs.edit().putFloat(KEY_SWIPE_END_X, value).apply()

    var swipeEndY: Float
        get() = prefs.getFloat(KEY_SWIPE_END_Y, DEFAULT_SWIPE_END_Y)
        set(value) = prefs.edit().putFloat(KEY_SWIPE_END_Y, value).apply()

    /** 前进键拖拽定位点是否已校准 */
    var swipeCalibrated: Boolean
        get() = prefs.getBoolean(KEY_SWIPE_CALIBRATED, false)
        set(value) = prefs.edit().putBoolean(KEY_SWIPE_CALIBRATED, value).apply()

    /** 清空所有校准数据 */
    fun clearCalibration() {
        prefs.edit()
            .remove(KEY_CANCEL_X).remove(KEY_CANCEL_Y).remove(KEY_CANCEL_CALIBRATED)
            .remove(KEY_SWIPE_START_X).remove(KEY_SWIPE_START_Y)
            .remove(KEY_SWIPE_END_X).remove(KEY_SWIPE_END_Y)
            .remove(KEY_SWIPE_CALIBRATED)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "stamina_helper_prefs"

        private const val KEY_WAIT_SECONDS = "wait_seconds"
        private const val KEY_SWIPE_DURATION = "swipe_duration"
        private const val KEY_SWIPE_INTERVAL = "swipe_interval"
        private const val KEY_EXEC_MODE = "exec_mode"
        private const val KEY_AUTO_RETURN = "auto_return"
        private const val KEY_AUTO_ACTION = "auto_action"
        private const val KEY_LOOP_GAP = "loop_gap"
        private const val KEY_MAX_LOOP = "max_loop"

        private const val KEY_CANCEL_X = "cancel_x"
        private const val KEY_CANCEL_Y = "cancel_y"
        private const val KEY_CANCEL_CALIBRATED = "cancel_calibrated"

        private const val KEY_SWIPE_START_X = "swipe_start_x"
        private const val KEY_SWIPE_START_Y = "swipe_start_y"
        private const val KEY_SWIPE_END_X = "swipe_end_x"
        private const val KEY_SWIPE_END_Y = "swipe_end_y"
        private const val KEY_SWIPE_CALIBRATED = "swipe_calibrated"

        const val DEFAULT_WAIT_SECONDS = 5.0f
        const val DEFAULT_SWIPE_DURATION = 200L   // 0.2秒，快速滑动
        const val DEFAULT_SWIPE_INTERVAL = 300L
        const val DEFAULT_EXEC_MODE = "root"
        const val DEFAULT_AUTO_RETURN = false

        // 取消按钮默认值（弹窗中央偏下）
        const val DEFAULT_CANCEL_X = 0.5f
        const val DEFAULT_CANCEL_Y = 0.55f

        // 前进键拖拽默认值（左边缘向右拖）
        const val DEFAULT_SWIPE_START_X = 0.02f
        const val DEFAULT_SWIPE_START_Y = 0.5f
        const val DEFAULT_SWIPE_END_X = 0.35f
        const val DEFAULT_SWIPE_END_Y = 0.5f

        const val MODE_ROOT = "root"
        const val MODE_ACCESSIBILITY = "accessibility"

        // ===== 自动动作模式 =====
        /** 关闭自动点击，全程手动 */
        const val MODE_AUTO_OFF = 0
        /** 仅点击取消：滑动→倒计时→点取消，结束 */
        const val MODE_AUTO_CANCEL = 1
        /** 循环：滑动→倒计时→点取消→等待→再滑动… */
        const val MODE_AUTO_LOOP = 2

        const val DEFAULT_LOOP_GAP = 2000L   // 循环间隔 2 秒
        const val DEFAULT_MAX_LOOP = 0   // 0=无限

        /** 滑动时长最小值(ms) */
        const val MIN_SWIPE_DURATION = 100L
        /** 滑动时长最大值(ms) */
        const val MAX_SWIPE_DURATION = 500L
    }
}