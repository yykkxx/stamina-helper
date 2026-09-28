package com.stamina.helper.root

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * 检测 Root 权限，并通过 Root 自动授权悬浮窗和通知权限
 */
object RootChecker {

    private const val TAG = "RootChecker"
    private var cachedResult: Boolean? = null

    fun isRootAvailable(): Boolean {
        cachedResult?.let { return it }
        val result = try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val input = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            input.isNotEmpty()
        } catch (e: Exception) {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
                val input = process.inputStream.bufferedReader().readText().trim()
                process.waitFor()
                input.contains("uid=0")
            } catch (e2: Exception) {
                false
            }
        }
        cachedResult = result
        return result
    }

    /**
     * 通过 Root 授权悬浮窗权限
     * 使用 appops set 命令授予 SYSTEM_ALERT_WINDOW 权限
     * @return true 表示命令执行成功
     */
    fun grantOverlayPermission(context: Context): Boolean {
        if (!isRootAvailable()) return false
        val pkg = context.packageName
        return try {
            // Android 6.0+ 使用 appops
            val cmd = "appops set $pkg android:SYSTEM_ALERT_WINDOW allow"
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            process.waitFor()
            // 验证是否生效
            Settings.canDrawOverlays(context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to grant overlay permission via root", e)
            false
        }
    }

    /**
     * 通过 Root 授权通知权限（Android 13+ POST_NOTIFICATIONS）
     * @return true 表示命令执行成功
     */
    fun grantNotificationPermission(context: Context): Boolean {
        if (!isRootAvailable()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        val pkg = context.packageName
        return try {
            val cmd = "pm grant $pkg android.permission.POST_NOTIFICATIONS"
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            process.waitFor()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to grant notification permission via root", e)
            false
        }
    }

    /**
     * 一键授权所有可通过 Root 授权的权限
     * @return GrantResult 包含各项权限的授权结果
     */
    data class GrantResult(
        val overlayGranted: Boolean,
        val notificationGranted: Boolean
    )

    fun grantAllPermissions(context: Context): GrantResult {
        val overlay = grantOverlayPermission(context)
        val notif = grantNotificationPermission(context)
        return GrantResult(overlay, notif)
    }
}