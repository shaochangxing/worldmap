package com.sqz.worldmap.globe

import androidx.compose.ui.geometry.Rect

/**
 * 国家名标签的分级显示与重叠剔除（纯计算，便于单元测试）。
 *
 * 分级：全球视野只画面积最大的若干个；中等缩放放宽；放大后接近全画。
 * 重叠：按“面积从大到小”的顺序贪心放置，与已放置矩形/遮挡区（搜索栏、底部卡片）相交就丢弃。
 */
object CountryLabelLayout {

    /** 小于该缩放视为“全球视野”。 */
    const val WIDE_ZOOM = 1.35f

    /** 大于等于该缩放视为“局部放大”。 */
    const val MID_ZOOM = 2.20f

    /** 国名字号（sp）：屏幕恒定，不随 zoom 变化。 */
    const val LABEL_SP = 12.5f

    /**
     * 标签字号：屏幕恒定（sp 本身就是密度无关单位，不乘 zoom），
     * 放大到 10x 时国名不会跟着变成巨字，只增加显示密度。
     */
    fun fontSizeSp(zoom: Float): Float = LABEL_SP

    /** 全球视野 / 中等 / 局部三档标签数量上限。 */
    fun maxLabels(zoom: Float): Int = when {
        zoom < WIDE_ZOOM -> 40
        zoom < MID_ZOOM -> 90
        else -> 240
    }

    /**
     * 该缩放档下参与排布的最小球面面积（球面度，1 sr ≈ 4057 万 km²）。
     * 全球视野只给中大国标注，避免太平洋/加勒比的小岛国名把画面糊满；
     * 放大后逐步放开，最终全部可见国家都可标注。
     */
    fun minAreaSr(zoom: Float): Double = when {
        zoom < WIDE_ZOOM -> 0.008 // ≈ 32 万 km²
        zoom < MID_ZOOM -> 0.002  // ≈ 8 万 km²
        else -> 0.0
    }

    /** 标签矩形：以锚点为中心。 */
    fun boxFor(anchorX: Float, anchorY: Float, width: Int, height: Int): Rect =
        Rect(anchorX - width / 2f, anchorY - height / 2f, anchorX + width / 2f, anchorY + height / 2f)

    /**
     * 贪心接受互不重叠、不压遮挡区、且完全在视口内的标签。
     * 输入顺序即优先级（面积从大到小），返回被接受的下标。
     */
    fun accept(
        boxes: List<Rect>,
        blocked: List<Rect>,
        viewport: Rect,
        limit: Int,
    ): List<Int> {
        val accepted = ArrayList<Rect>(limit.coerceAtMost(boxes.size))
        val out = ArrayList<Int>(limit.coerceAtMost(boxes.size))
        for (i in boxes.indices) {
            if (out.size >= limit) break
            val b = boxes[i]
            if (b.left < viewport.left || b.right > viewport.right || b.top < viewport.top || b.bottom > viewport.bottom) continue
            if (blocked.any { it.overlaps(b) }) continue
            if (accepted.any { it.overlaps(b) }) continue
            accepted.add(b)
            out.add(i)
        }
        return out
    }
}

/** ISO 三位码哈希到陆地色板下标，使相邻国家颜色尽量不同。 */
fun landColorIndex(iso: String, size: Int): Int {
    if (size <= 1) return 0
    if (iso.isEmpty()) return 0
    var h = 17
    for (ch in iso) h = (h * 37 + ch.code) and 0x7fffffff
    return h % size
}
