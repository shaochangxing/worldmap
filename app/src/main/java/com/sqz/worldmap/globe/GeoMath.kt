package com.sqz.worldmap.globe

import com.sqz.worldmap.data.Country
import com.sqz.worldmap.data.Ring
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val DEG = Math.PI / 180.0

/** 判断“两个屏幕点重合”的阈值（像素）。低于该值的线段没有可见长度。 */
private const val EPS_PX = 1e-3f

/** 屏幕上的一个点（不依赖 Compose 类型，方便纯 JVM 单测）。 */
data class ScreenPoint(val x: Float, val y: Float)

data class LonLat(val lat: Double, val lon: Double)

/**
 * 正交球面相机。
 *
 * 世界坐标（右手系）：x = cosLat·cosLon, y = sinLat（北极为 +y）, z = cosLat·sinLon。
 * 相机位于球体正前方 f（观察者方向），屏幕基向量为 right（向东）与 up（向北）。
 * 可见半球判据为 depth = p·f >= 0（背面剔除），投影为
 *   screenX = centerX + R·(p·right)
 *   screenY = centerY - R·(p·up)
 */
class GlobeCamera(
    val latDeg: Double,
    val lonDeg: Double,
    val centerX: Float,
    val centerY: Float,
    val radiusPx: Float,
) {
    val fx: Double
    val fy: Double
    val fz: Double
    val rx: Double
    val ry: Double
    val rz: Double
    val ux: Double
    val uy: Double
    val uz: Double

    init {
        val la = latDeg * DEG
        val lo = lonDeg * DEG
        fx = cos(la) * cos(lo)
        fy = sin(la)
        fz = cos(la) * sin(lo)

        // right = normalize(f × north)，north=(0,1,0) → f×n = (-fz, 0, fx)
        var rx0 = -fz
        var ry0 = 0.0
        var rz0 = fx
        var len = sqrt(rx0 * rx0 + rz0 * rz0)
        if (len < 1e-6) {
            // 极点附近退化：取固定的东向基准
            rx0 = 0.0
            ry0 = 0.0
            rz0 = 1.0
            len = 1.0
        }
        rx = rx0 / len
        ry = ry0 / len
        rz = rz0 / len

        // up = normalize(north - (north·f)·f)
        var ux0 = -fy * fx
        var uy0 = 1.0 - fy * fy
        var uz0 = -fy * fz
        len = sqrt(ux0 * ux0 + uy0 * uy0 + uz0 * uz0)
        if (len < 1e-6) {
            // 正对极点时取 f × right 作为上方
            ux0 = fy * rz - fz * ry
            uy0 = fz * rx - fx * rz
            uz0 = fx * ry - fy * rx
            len = sqrt(ux0 * ux0 + uy0 * uy0 + uz0 * uz0)
        }
        ux = ux0 / len
        uy = uy0 / len
        uz = uz0 / len
    }

    fun depth(x: Double, y: Double, z: Double): Double = x * fx + y * fy + z * fz

    fun screenX(x: Double, y: Double, z: Double): Float = (x * rx + y * ry + z * rz).toFloat()

    fun screenY(x: Double, y: Double, z: Double): Float = (x * ux + y * uy + z * uz).toFloat()

    fun px(sx: Float): Float = centerX + radiusPx * sx

    fun py(sy: Float): Float = centerY - radiusPx * sy

    fun project(x: Double, y: Double, z: Double): ScreenPoint = ScreenPoint(px(screenX(x, y, z)), py(screenY(x, y, z)))

    /** 屏幕点反投影到球面；点击在球体之外时返回 null。 */
    fun unproject(px: Float, py: Float): LonLat? {
        val v = unprojectVector(px, py) ?: return null
        val lat = kotlin.math.asin(v[1].coerceIn(-1.0, 1.0)) / DEG
        val lon = atan2(v[2], v[0]) / DEG
        return LonLat(lat, lon)
    }

    /** 屏幕点反投影成单位球面向量（用于球面绕数点选，避免经纬度平面投影的跨 180° 问题）。 */
    fun unprojectVector(px: Float, py: Float): DoubleArray? {
        val sx = ((px - centerX) / radiusPx).toDouble()
        val sy = ((centerY - py) / radiusPx).toDouble()
        val r2 = sx * sx + sy * sy
        if (r2 > 1.0) return null
        val depth = sqrt((1.0 - r2).coerceAtLeast(0.0))
        return doubleArrayOf(
            sx * rx + sy * ux + depth * fx,
            sx * ry + sy * uy + depth * fy,
            sx * rz + sy * uz + depth * fz,
        )
    }
}

/**
 * 一帧内投影后的多边形顶点缓存。渲染时复用同一实例，避免每帧分配对象。
 */
class ProjectedRing(initialCapacity: Int = 16) {
    var count = 0
        internal set

    var x = FloatArray(initialCapacity)
    var y = FloatArray(initialCapacity)
    var onHorizon = BooleanArray(initialCapacity)

    internal var depth = FloatArray(initialCapacity)
    internal var vx = FloatArray(initialCapacity)
    internal var vy = FloatArray(initialCapacity)

    fun ensureCapacity(capacity: Int) {
        if (x.size >= capacity) return
        var cap = x.size
        while (cap < capacity) cap = cap * 2
        x = FloatArray(cap)
        y = FloatArray(cap)
        onHorizon = BooleanArray(cap)
        depth = FloatArray(cap)
        vx = FloatArray(cap)
        vy = FloatArray(cap)
    }
}

/**
 * 球面多边形投影 + 地平线裁剪（Sutherland–Hodgman 半空间裁剪，裁剪面为 depth=0）。
 * 完全可见：直接投影；部分可见：把交点精确压到地平线上（depth 恒为 0，归一化不改变符号）；
 * 完全不可见：返回 false，并检测“环是否围住视线轴”。
 */
object SphereProjector {

    fun project(ring: Ring, cam: GlobeCamera, out: ProjectedRing): Boolean {
        val n = ring.size
        out.ensureCapacity(n * 2 + 4)
        out.count = 0

        var visible = 0
        for (i in 0 until n) {
            val x = ring.ux[i].toDouble()
            val y = ring.uy[i].toDouble()
            val z = ring.uz[i].toDouble()
            val d = cam.depth(x, y, z)
            out.depth[i] = d.toFloat()
            out.vx[i] = cam.screenX(x, y, z)
            out.vy[i] = cam.screenY(x, y, z)
            if (d >= 0.0) visible++
        }

        if (visible == 0) {
            // 全部顶点都在背面：背面半球是测地凸集，环不可能围住视线轴，直接剔除
            return false
        }

        if (visible == n) {
            for (i in 0 until n) {
                out.x[i] = cam.px(out.vx[i])
                out.y[i] = cam.py(out.vy[i])
                out.onHorizon[i] = false
            }
            out.count = n
            return true
        }

        var m = 0
        for (i in 0 until n) {
            val j = if (i + 1 == n) 0 else i + 1
            val di = out.depth[i].toDouble()
            val dj = out.depth[j].toDouble()
            if (di >= 0.0) {
                out.x[m] = cam.px(out.vx[i])
                out.y[m] = cam.py(out.vy[i])
                out.onHorizon[m] = false
                m++
            }
            if ((di >= 0.0) != (dj >= 0.0)) {
                val t = di / (di - dj)
                var bx = ring.ux[i].toDouble() + (ring.ux[j].toDouble() - ring.ux[i].toDouble()) * t
                var by = ring.uy[i].toDouble() + (ring.uy[j].toDouble() - ring.uy[i].toDouble()) * t
                var bz = ring.uz[i].toDouble() + (ring.uz[j].toDouble() - ring.uz[i].toDouble()) * t
                val len = sqrt(bx * bx + by * by + bz * bz)
                if (len > 1e-9) {
                    bx /= len; by /= len; bz /= len
                } else {
                    bx = 0.0; by = 0.0; bz = 0.0
                }
                out.x[m] = cam.px(cam.screenX(bx, by, bz))
                out.y[m] = cam.py(cam.screenY(bx, by, bz))
                out.onHorizon[m] = true
                m++
            }
        }

        // 去掉重合点：顶点恰好落在地平线（depth==0）上时，交点会与顶点重合，
        // 直接输出会得到长度为 0 的线段（描边时表现为碎点）。
        var k = 0
        for (i in 0 until m) {
            if (k > 0 && abs(out.x[i] - out.x[k - 1]) < EPS_PX && abs(out.y[i] - out.y[k - 1]) < EPS_PX) {
                out.onHorizon[k - 1] = out.onHorizon[k - 1] || out.onHorizon[i]
                continue
            }
            out.x[k] = out.x[i]
            out.y[k] = out.y[i]
            out.onHorizon[k] = out.onHorizon[i]
            k++
        }
        // 首尾重合（闭合边）同样去掉
        if (k > 1 && abs(out.x[0] - out.x[k - 1]) < EPS_PX && abs(out.y[0] - out.y[k - 1]) < EPS_PX) {
            out.onHorizon[0] = out.onHorizon[0] || out.onHorizon[k - 1]
            k--
        }
        out.count = k
        return k >= 3
    }
}

/** 点击命中：屏幕点反投影成球面点，再用包围球 + 球面绕数法判定；重叠时取面积更小的国家。 */
object GlobeHitTest {

    fun hit(countries: List<Country>, cam: GlobeCamera, px: Float, py: Float): Country? {
        val q = cam.unprojectVector(px, py) ?: return null
        var best: Country? = null
        var bestSpan = Double.MAX_VALUE
        for (c in countries) {
            val d = c.centerX * cam.fx + c.centerY * cam.fy + c.centerZ * cam.fz
            if (d < c.capThreshold) continue
            if (!c.containsVector(q[0], q[1], q[2])) continue
            if (best == null || c.latSpan < bestSpan) {
                bestSpan = c.latSpan
                best = c
            }
        }
        return best
    }
}
