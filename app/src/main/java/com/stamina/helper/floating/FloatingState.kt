package com.stamina.helper.floating

/**
 * 悬浮窗状态枚举
 */
enum class FloatingState {
    /** 空闲，显示「开始回复体力」按钮 + 关闭按钮 */
    IDLE,
    /** 正在执行滑动，显示「正在引出退出弹窗...」 */
    SWIPING,
    /** 倒计时中，显示剩余秒数 */
    COUNTDOWN,
    /** 倒计时结束，显示「时间到！点击取消返回游戏」 */
    DONE
}