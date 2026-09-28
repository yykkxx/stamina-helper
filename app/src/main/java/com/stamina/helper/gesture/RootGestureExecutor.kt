package com.stamina.helper.gesture

import android.util.Log
import com.stamina.helper.root.RootChecker

/**
 * Root 模式手势执行器，通过 su -c input swipe 模拟滑动
 */
class RootGestureExecutor : GestureExecutor {

    override fun performEdgeSwipe(config: GestureConfig, screenWidth: Int, screenHeight: Int): Boolean {
        if (!isAvailable()) return false

        val x1 = (screenWidth * config.startXRatio).toInt()
        val y1 = (screenHeight * config.startYRatio).toInt()
        val x2 = (screenWidth * config.endXRatio).toInt()
        val y2 = (screenHeight * config.endYRatio).toInt()
        val duration = config.swipeDurationMs

        return try {
            // 第一次滑动
            execSwipe(x1, y1, x2, y2, duration)
            Thread.sleep(config.swipeIntervalMs)
            // 第二次滑动
            execSwipe(x1, y1, x2, y2, duration)
            Thread.sleep(config.swipeIntervalMs)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Root swipe failed", e)
            false
        }
    }

    override fun performClick(x: Float, y: Float): Boolean {
        if (!isAvailable()) return false
        return try {
            val process = Runtime.getRuntime().exec(
                arrayOf("su", "-c", "input tap ${x.toInt()} ${y.toInt()}")
            )
            process.waitFor()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Root tap failed", e)
            false
        }
    }

    override fun isAvailable(): Boolean = RootChecker.isRootAvailable()

    private fun execSwipe(x1: Int, y1: Int, x2: Int, y2: Int, duration: Long) {
        val cmd = "input swipe $x1 $y1 $x2 $y2 $duration"
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
        process.waitFor()
    }

    companion object {
        private const val TAG = "RootGestureExec"
    }
}