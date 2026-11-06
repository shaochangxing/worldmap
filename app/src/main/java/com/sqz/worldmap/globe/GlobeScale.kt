package com.sqz.worldmap.globe

/**
 * 缩放相关的纯计算，便于单元测试：
 * - 缩放范围 1..10 倍（乘法步进，手势与按钮共用）；
 * - 国界描边按屏幕近似恒定宽度，避免 10 倍时变成粗线；
 * - 搜索定位的缩放档位：国土越小放得越大。
 */
object GlobeScale {

    const val MIN_ZOOM = 1f
    const val MAX_ZOOM = 10f

    fun clampZoom(zoom: Float): Float = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)

    /**
     * 国界描边宽度（dp）。1x 时 1.25dp，10x 时 1.10dp：
     * 随缩放缓慢变细而不是等比放大，视觉粗细基本恒定，且变化连续（不会跳变）。
     */
    fun borderWidthDp(zoom: Float): Float {
        val z = clampZoom(zoom)
        return 1.25f - 0.15f * ((z - MIN_ZOOM) / (MAX_ZOOM - MIN_ZOOM))
    }

    /** 定位到国家时的缩放：大国 1.9~3.2，小国/岛国最多 5.5 倍以保证轮廓完整。 */
    fun zoomForSpan(span: Double): Float = when {
        span >= 70.0 -> 1.9f
        span >= 40.0 -> 2.1f
        span >= 20.0 -> 2.6f
        span >= 8.0 -> 3.2f
        span >= 3.0 -> 4.2f
        span >= 1.0 -> 5.0f
        else -> 5.5f
    }
}
