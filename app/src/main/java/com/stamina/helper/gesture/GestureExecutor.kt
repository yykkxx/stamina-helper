package com.stamina.helper.gesture

/**
 * 手势执行器接口
 */
interface GestureExecutor {

    /**
     * 执行两次边缘滑动（从左边缘向右滑动），用于引出游戏退出弹窗
     * @param config 手势参数
     * @param screenWidth 屏幕宽度(px)
     * @param screenHeight 屏幕高度(px)
     * @return true 表示两次滑动都执行成功
     */
    fun performEdgeSwipe(config: GestureConfig, screenWidth: Int, screenHeight: Int): Boolean

    /**
     * 执行一次点击（用于自动返回时点击取消按钮）
     * @param x 点击X坐标
     * @param y 点击Y坐标
     * @return true 表示执行成功
     */
    fun performClick(x: Float, y: Float): Boolean

    /**
     * 当前执行器是否可用
     */
    fun isAvailable(): Boolean
}
