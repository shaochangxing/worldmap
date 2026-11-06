package com.sqz.worldmap.globe

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import com.sqz.worldmap.data.Country
import kotlin.math.atan2

/**
 * 把国家轮廓投影成 Compose Path：
 * - 完全可见的环直接连线；
 * - 跨越地平线的环先用 [SphereProjector] 裁剪，再在“相邻两个裁剪点”之间沿地平线圆弧补线，
 *   避免出现横穿屏幕的直线拉丝。
 *
 * 每个国家单独一个 Path，Path 对象由调用方缓存复用（[buildInto] 会先 rewind），
 * 避免每帧为 238 个国家分配新对象。
 */
class GlobePathBuilder {
    private val scratch = ProjectedRing(128)

    /** 把国家所有环写入 [out]（先清空），没有任何可见环时返回 false。 */
    fun buildInto(country: Country, cam: GlobeCamera, out: Path): Boolean {
        out.rewind()
        var any = false
        for (ring in country.rings) {
            if (!SphereProjector.project(ring, cam, scratch)) continue
            appendRing(out, cam)
            any = true
        }
        return any
    }

    private fun appendRing(path: Path, cam: GlobeCamera) {
        val n = scratch.count
        if (n < 3) return

        path.moveTo(scratch.x[0], scratch.y[0])
        for (i in 1 until n) {
            val hPrev = scratch.onHorizon[i - 1]
            val hCur = scratch.onHorizon[i]
            if (hPrev && hCur) {
                appendArc(path, cam, scratch.x[i - 1], scratch.y[i - 1], scratch.x[i], scratch.y[i])
            } else {
                path.lineTo(scratch.x[i], scratch.y[i])
            }
        }
        // 首尾相接处同样需要处理（闭合边）
        if (scratch.onHorizon[0] && scratch.onHorizon[n - 1]) {
            appendArc(path, cam, scratch.x[n - 1], scratch.y[n - 1], scratch.x[0], scratch.y[0])
        }
        path.close()
    }

    private fun appendArc(path: Path, cam: GlobeCamera, x0: Float, y0: Float, x1: Float, y1: Float) {
        val a0 = angleDeg(x0, y0, cam)
        val a1 = angleDeg(x1, y1, cam)
        var sweep = (a1 - a0 + 540f) % 360f - 180f
        if (sweep == 0f) {
            path.lineTo(x1, y1)
            return
        }
        path.arcTo(circleRect(cam), a0, sweep, false)
    }

    private fun angleDeg(x: Float, y: Float, cam: GlobeCamera): Float =
        Math.toDegrees(atan2((y - cam.centerY).toDouble(), (x - cam.centerX).toDouble())).toFloat()

    private fun circleRect(cam: GlobeCamera): Rect =
        Rect(cam.centerX - cam.radiusPx, cam.centerY - cam.radiusPx, cam.centerX + cam.radiusPx, cam.centerY + cam.radiusPx)
}
