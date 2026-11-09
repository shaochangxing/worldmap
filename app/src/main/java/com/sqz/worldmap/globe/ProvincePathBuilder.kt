package com.sqz.worldmap.globe

import androidx.compose.ui.graphics.Path
import com.sqz.worldmap.data.Ring

/**
 * 把中国省界弧（开放折线）投影成 Compose Path。
 *
 * 与 [GlobePathBuilder] 的区别：省界只描边、不闭合，所以：
 * - 顶点被地平线裁掉时不补圆弧，而是把这条弧断成若干可见子段（避免线条沿地平线拉出横线）；
 * - 穿越地平线的位置按深度插值得到精确交点，再投影到屏幕。
 */
class ProvincePathBuilder {

    /** 地平线交点（复用一个字段，避免每帧为交点分配数组）。 */
    private var hitX = 0f
    private var hitY = 0f

    /** 把一条弧写入 [out]（先清空）；没有任何可见子段时返回 false。 */
    fun buildInto(arc: Ring, cam: GlobeCamera, out: Path): Boolean {
        out.rewind()
        val n = arc.size
        var any = false
        var pen = false
        var prevDepth = 0.0
        var prevX = 0.0
        var prevY = 0.0
        var prevZ = 0.0
        for (i in 0 until n) {
            val x = arc.ux[i].toDouble()
            val y = arc.uy[i].toDouble()
            val z = arc.uz[i].toDouble()
            val d = cam.depth(x, y, z)
            if (d >= 0.0) {
                val sx = cam.px(cam.screenX(x, y, z))
                val sy = cam.py(cam.screenY(x, y, z))
                if (pen) {
                    out.lineTo(sx, sy)
                } else {
                    if (i > 0 && prevDepth < 0.0) {
                        // 从背面进入正面：先落到地平线上的精确交点
                        projectHorizonCrossing(cam, prevX, prevY, prevZ, x, y, z, prevDepth / (prevDepth - d))
                        out.moveTo(hitX, hitY)
                        out.lineTo(sx, sy)
                    } else {
                        out.moveTo(sx, sy)
                    }
                }
                pen = true
                any = true
            } else {
                if (pen && prevDepth >= 0.0) {
                    // 从正面走向背面：画到地平线交点后断笔
                    projectHorizonCrossing(cam, prevX, prevY, prevZ, x, y, z, prevDepth / (prevDepth - d))
                    out.lineTo(hitX, hitY)
                }
                pen = false
            }
            prevDepth = d
            prevX = x
            prevY = y
            prevZ = z
        }
        return any
    }

    private fun projectHorizonCrossing(
        cam: GlobeCamera,
        x0: Double, y0: Double, z0: Double,
        x1: Double, y1: Double, z1: Double,
        t: Double,
    ) {
        var x = x0 + (x1 - x0) * t
        var y = y0 + (y1 - y0) * t
        var z = z0 + (z1 - z0) * t
        val len = Math.sqrt(x * x + y * y + z * z)
        if (len > 1e-9) {
            x /= len
            y /= len
            z /= len
        }
        hitX = cam.px(cam.screenX(x, y, z))
        hitY = cam.py(cam.screenY(x, y, z))
    }
}
