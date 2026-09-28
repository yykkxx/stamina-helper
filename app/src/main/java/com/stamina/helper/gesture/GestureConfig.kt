package com.stamina.helper.gesture

/**
 * 手势参数配置
 * 坐标全部为归一化比例 (0~1)，便于适配不同分辨率
 */
data class GestureConfig(
    /** 拖拽起点X比例 (0~1) */
    val startXRatio: Float,
    /** 拖拽起点Y比例 (0~1) */
    val startYRatio: Float,
    /** 拖拽终点X比例 (0~1) */
    val endXRatio: Float,
    /** 拖拽终点Y比例 (0~1) */
    val endYRatio: Float,
    /** 单次滑动时长(ms) */
    val swipeDurationMs: Long,
    /** 两次滑动间隔(ms) */
    val swipeIntervalMs: Long
)