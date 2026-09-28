package com.stamina.helper.timer

import android.os.CountDownTimer

/**
 * 体力回复倒计时管理器
 * 自适应 tick 间隔：>=10s 用 1000ms，>=1s 用 100ms，<1s 用 10ms
 * 减少短时倒计时的高频 UI 更新，降低 CPU 消耗
 */
class StaminaTimer(
    private val totalSeconds: Float,
    private val callback: TimerCallback
) {
    private var countDownTimer: CountDownTimer? = null

    fun start() {
        cancel()
        val totalMillis = (totalSeconds * 1000).toLong()
        // 自适应 tick 间隔
        val tickMs = when {
            totalSeconds >= 10f -> 1000L
            totalSeconds >= 1f -> 100L
            else -> 10L
        }
        countDownTimer = object : CountDownTimer(totalMillis, tickMs) {
            override fun onTick(millisUntilFinished: Long) {
                callback.onTick(millisUntilFinished / 1000.0f)
            }
            override fun onFinish() {
                callback.onTick(0f)
                callback.onFinish()
            }
        }.start()
    }

    fun cancel() {
        countDownTimer?.cancel()
        countDownTimer = null
    }

    val isRunning: Boolean get() = countDownTimer != null
}

interface TimerCallback {
    fun onTick(remainingSeconds: Float)
    fun onFinish()
}