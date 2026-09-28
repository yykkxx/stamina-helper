package com.stamina.helper.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.util.Log

/**
 * 无障碍服务，用于模拟手势（滑动/点击）
 */
class StaminaAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "StaminaAccessibilityService connected")

        // 配置服务信息
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            // 不设置 flags，保持默认，canPerformGestures 由 XML 配置控制
            notificationTimeout = 100
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 可选：监听窗口状态变化，检测退出弹窗是否出现
    }

    override fun onInterrupt() {
        Log.i(TAG, "StaminaAccessibilityService interrupted")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        Log.i(TAG, "StaminaAccessibilityService unbound")
        return super.onUnbind(intent)
    }

    companion object {
        private const val TAG = "StaminaA11y"

        @Volatile
        var instance: StaminaAccessibilityService? = null
            private set

        /**
         * 检查无障碍服务是否已启用
         */
        fun isServiceEnabled(context: android.content.Context): Boolean {
            val enabledServices = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val componentName = "${context.packageName}/${StaminaAccessibilityService::class.java.name}"
            return enabledServices.contains(componentName)
        }
    }
}