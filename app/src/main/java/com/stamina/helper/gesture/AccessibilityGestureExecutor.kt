package com.stamina.helper.gesture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.util.Log
import com.stamina.helper.service.StaminaAccessibilityService

/**
 * 无障碍模式手势执行器，使用 AccessibilityService.dispatchGesture 模拟滑动
 */
class AccessibilityGestureExecutor : GestureExecutor {

    private val service: AccessibilityService?
        get() = StaminaAccessibilityService.instance

    override fun performEdgeSwipe(config: GestureConfig, screenWidth: Int, screenHeight: Int): Boolean {
        val svc = service ?: run {
            Log.e(TAG, "AccessibilityService not connected")
            return false
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.e(TAG, "dispatchGesture requires API 24+")
            return false
        }

        val x1 = screenWidth * config.startXRatio
        val y1 = screenHeight * config.startYRatio
        val x2 = screenWidth * config.endXRatio
        val y2 = screenHeight * config.endYRatio

        return try {
            // 第一次滑动
            val result1 = dispatchSwipe(svc, x1, y1, x2, y2, config.swipeDurationMs)
            Thread.sleep(config.swipeIntervalMs)
            // 第二次滑动
            val result2 = dispatchSwipe(svc, x1, y1, x2, y2, config.swipeDurationMs)
            Thread.sleep(config.swipeIntervalMs)
            result1 && result2
        } catch (e: Exception) {
            Log.e(TAG, "Accessibility swipe failed", e)
            false
        }
    }

    override fun performClick(x: Float, y: Float): Boolean {
        val svc = service ?: return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        return try {
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0, 50)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchGestureSync(svc, gesture)
        } catch (e: Exception) {
            Log.e(TAG, "Accessibility tap failed", e)
            false
        }
    }

    override fun isAvailable(): Boolean {
        return service != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
    }

    private fun dispatchSwipe(
        svc: AccessibilityService, x1: Float, y1: Float, x2: Float, y2: Float, duration: Long
    ): Boolean {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, duration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGestureSync(svc, gesture)
    }

    /**
     * 同步执行手势（阻塞等待回调结果）
     */
    private fun dispatchGestureSync(svc: AccessibilityService, gesture: GestureDescription): Boolean {
        val result = BooleanArray(1)
        val latch = java.util.concurrent.CountDownLatch(1)
        svc.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) {
                result[0] = true
                latch.countDown()
            }

            override fun onCancelled(g: GestureDescription?) {
                result[0] = false
                latch.countDown()
            }
        }, null)
        latch.await(5, java.util.concurrent.TimeUnit.SECONDS)
        return result[0]
    }

    companion object {
        private const val TAG = "A11yGestureExec"
    }
}