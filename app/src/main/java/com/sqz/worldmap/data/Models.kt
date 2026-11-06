package com.sqz.worldmap.data

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val DEG_TO_RAD = Math.PI / 180.0

private fun wrapDelta(d: Double): Double {
    var v = d
    while (v > Math.PI) v -= 2 * Math.PI
    while (v < -Math.PI) v += 2 * Math.PI
    return v
}

/**
 * 一个国家的一段闭合轮廓（可能是一块大陆，也可能是海外岛屿）。
 * 顶点已预计算为单位球面向量（世界坐标：x=cosLat*cosLon, y=sinLat, z=cosLat*sinLon），
 * 渲染时只需点乘相机基向量，避免每帧重复解析 GeoJSON / 三角函数。
 */
class Ring(
    val lats: FloatArray,
    val lons: FloatArray,
    val ux: FloatArray,
    val uy: FloatArray,
    val uz: FloatArray,
) {
    val size: Int get() = lats.size
    val minLat: Float
    val maxLat: Float
    val minLon: Float
    val maxLon: Float

    /** 轮廓包围球心与阈值（用于点选时的快速剔除）。 */
    val centerX: Double
    val centerY: Double
    val centerZ: Double
    val capThreshold: Double

    init {
        var laMin = Float.MAX_VALUE
        var laMax = -Float.MAX_VALUE
        var loMin = Float.MAX_VALUE
        var loMax = -Float.MAX_VALUE
        var sx = 0.0
        var sy = 0.0
        var sz = 0.0
        for (i in lats.indices) {
            if (lats[i] < laMin) laMin = lats[i]
            if (lats[i] > laMax) laMax = lats[i]
            if (lons[i] < loMin) loMin = lons[i]
            if (lons[i] > loMax) loMax = lons[i]
            sx += ux[i]
            sy += uy[i]
            sz += uz[i]
        }
        minLat = laMin
        maxLat = laMax
        minLon = loMin
        maxLon = loMax

        val len = sqrt(sx * sx + sy * sy + sz * sz)
        if (len < 1e-9) {
            centerX = 1.0; centerY = 0.0; centerZ = 0.0
        } else {
            centerX = sx / len
            centerY = sy / len
            centerZ = sz / len
        }
        var maxAngle = 0.0
        for (i in 0 until size) {
            val d = (ux[i] * centerX + uy[i] * centerY + uz[i] * centerZ).coerceIn(-1.0, 1.0)
            val a = acos(d)
            if (a > maxAngle) maxAngle = a
        }
        capThreshold = (-sin(maxAngle)).coerceAtLeast(-1.0)
    }

    /**
     * 轮廓相对于球面点 q 的方位角绕数（单位：弧度）。
     *
     * 做法：在 q 的切平面内取一组正交基，把每个顶点投影到该平面后累加相邻方位角差，
     * 闭合环的累计值在 q 位于环内时约为 ±2π，否则约为 0。
     * 与经度是否跨 180°、是否围住极点均无关。
     */
    fun winding(qx: Double, qy: Double, qz: Double): Double {
        // 参考轴：q 不接近极点时用 y 轴，否则用 x 轴，保证 e1 不退化
        val refX = if (abs(qy) < 0.9) 0.0 else 1.0
        val refY = if (abs(qy) < 0.9) 1.0 else 0.0
        var e1x = qy * 0.0 - qz * refY
        var e1y = qz * refX - qx * 0.0
        var e1z = qx * refY - qy * refX
        val e1Len = sqrt(e1x * e1x + e1y * e1y + e1z * e1z)
        if (e1Len < 1e-9) return 0.0
        e1x /= e1Len
        e1y /= e1Len
        e1z /= e1Len
        // e2 = q × e1
        val e2x = qy * e1z - qz * e1y
        val e2y = qz * e1x - qx * e1z
        val e2z = qx * e1y - qy * e1x

        var total = 0.0
        var prev = 0.0
        var first = 0.0
        var hasPrev = false
        for (i in 0 until size) {
            val px = ux[i].toDouble()
            val py = uy[i].toDouble()
            val pz = uz[i].toDouble()
            val t = px * qx + py * qy + pz * qz
            val vx = px - t * qx
            val vy = py - t * qy
            val vz = pz - t * qz
            val angle = atan2(vx * e2x + vy * e2y + vz * e2z, vx * e1x + vy * e1y + vz * e1z)
            if (!hasPrev) {
                first = angle
                prev = angle
                hasPrev = true
            } else {
                total += wrapDelta(angle - prev)
                prev = angle
            }
        }
        // 闭合边
        total += wrapDelta(first - prev)
        return total
    }

    /** 球面点是否在轮廓内（先用包围球剔除，再用切平面绕数判定）。 */
    fun containsVector(qx: Double, qy: Double, qz: Double): Boolean {
        val d = qx * centerX + qy * centerY + qz * centerZ
        if (d < capThreshold) return false
        return abs(winding(qx, qy, qz)) > Math.PI
    }
}

/** 国家（Natural Earth 国家层面，仅到国家精度）。 */
class Country(
    val id: Int,
    val nameZh: String,
    val nameEn: String,
    val iso: String,
    val continent: String,
    val aliases: List<String>,
    val rings: List<Ring>,
    /** 球面面积（球面度）。用于国家名标签的优先级与分级显示。 */
    val area: Double = 0.0,
    /** 国家名标注锚点（最大环的面积质心）经纬度；数据缺失时退回包围球心。 */
    anchorLat: Double = Double.NaN,
    anchorLon: Double = Double.NaN,
) {
    val minLat: Float = rings.minOf { it.minLat }
    val maxLat: Float = rings.maxOf { it.maxLat }
    val minLon: Float = rings.minOf { it.minLon }
    val maxLon: Float = rings.maxOf { it.maxLon }

    /** 包围球心（单位向量）与裁剪阈值，用于“整个国家是否在背面”的快速剔除。 */
    val centerX: Double
    val centerY: Double
    val centerZ: Double
    val capThreshold: Double

    init {
        var sx = 0.0
        var sy = 0.0
        var sz = 0.0
        for (ring in rings) {
            for (i in 0 until ring.size) {
                sx += ring.ux[i]
                sy += ring.uy[i]
                sz += ring.uz[i]
            }
        }
        val len = sqrt(sx * sx + sy * sy + sz * sz)
        if (len < 1e-9) {
            centerX = 1.0; centerY = 0.0; centerZ = 0.0
        } else {
            centerX = sx / len
            centerY = sy / len
            centerZ = sz / len
        }
        var maxAngle = 0.0
        for (ring in rings) {
            for (i in 0 until ring.size) {
                val d = (ring.ux[i] * centerX + ring.uy[i] * centerY + ring.uz[i] * centerZ).coerceIn(-1.0, 1.0)
                val a = acos(d)
                if (a > maxAngle) maxAngle = a
            }
        }
        capThreshold = (-sin(maxAngle)).coerceAtLeast(-1.0)
    }

    fun containsVector(x: Double, y: Double, z: Double): Boolean {
        for (ring in rings) if (ring.containsVector(x, y, z)) return true
        return false
    }

    /** 标注锚点的单位球面向量（数据缺失时退回包围球心）。 */
    val anchorX: Double
    val anchorY: Double
    val anchorZ: Double

    init {
        if (anchorLat.isNaN() || anchorLon.isNaN()) {
            anchorX = centerX
            anchorY = centerY
            anchorZ = centerZ
        } else {
            val la = anchorLat * DEG_TO_RAD
            val lo = anchorLon * DEG_TO_RAD
            anchorX = cos(la) * cos(lo)
            anchorY = sin(la)
            anchorZ = cos(la) * sin(lo)
        }
    }

    /** 纬向跨度（用于重叠国家时优先选面积更小的）。 */
    val latSpan: Double get() = (maxLat - minLat).toDouble()

    /**
     * 主体环（顶点最多的那块轮廓）。
     * 搜索定位时用它决定视野：荷兰这类含海外属地的国家，若用整个 feature 的 bbox 会被属地撑大，
     * 缩放档位被压到很低、中心点落到属地与本土之间的大西洋上。
     */
    val mainRing: Ring get() = rings.maxByOrNull { it.size } ?: rings.first()

    val anchorLonDeg: Double get() = atan2(anchorZ, anchorX) * 180.0 / Math.PI
    val anchorLatDeg: Double get() = asin(anchorY.coerceIn(-1.0, 1.0)) * 180.0 / Math.PI
}

/** 城市（各国首都 + 少量名城），字段含经纬度与所属国家。 */
class City(
    val id: Int,
    val nameZh: String,
    val nameEn: String,
    val countryIso: String,
    val countryZh: String,
    val lat: Double,
    val lon: Double,
    val isCapital: Boolean,
) {
    val ux: Double = cos(lat * DEG_TO_RAD) * cos(lon * DEG_TO_RAD)
    val uy: Double = sin(lat * DEG_TO_RAD)
    val uz: Double = cos(lat * DEG_TO_RAD) * sin(lon * DEG_TO_RAD)
}
