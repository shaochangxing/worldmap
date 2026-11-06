package com.sqz.worldmap

import com.sqz.worldmap.data.Ring
import com.sqz.worldmap.globe.GlobeCamera
import com.sqz.worldmap.globe.ProjectedRing
import com.sqz.worldmap.globe.SphereProjector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 投影 / 背面剔除 / 地平线裁剪 的数学单测。 */
class GeoMathTest {

    private val radiusPx = 300f
    private val cx = 500f
    private val cy = 400f

    private fun camera(lat: Double, lon: Double) =
        GlobeCamera(lat, lon, cx, cy, radiusPx)

    @Test
    fun cameraBasisIsOrthonormal() {
        val cam = camera(35.0, 116.0)
        // 三个基准向量互相垂直且都是单位长度
        assertEquals(1.0, sqrt(cam.fx * cam.fx + cam.fy * cam.fy + cam.fz * cam.fz), 1e-9)
        assertEquals(1.0, sqrt(cam.rx * cam.rx + cam.ry * cam.ry + cam.rz * cam.rz), 1e-9)
        assertEquals(1.0, sqrt(cam.ux * cam.ux + cam.uy * cam.uy + cam.uz * cam.uz), 1e-9)
        assertEquals(0.0, cam.fx * cam.rx + cam.fy * cam.ry + cam.fz * cam.rz, 1e-9)
        assertEquals(0.0, cam.fx * cam.ux + cam.fy * cam.uy + cam.fz * cam.uz, 1e-9)
        assertEquals(0.0, cam.rx * cam.ux + cam.ry * cam.uy + cam.rz * cam.uz, 1e-9)
    }

    @Test
    fun centerOfViewIsVisibleAndProjectsToCenter() {
        val cam = camera(0.0, 0.0)
        val p = cam.project(1.0, 0.0, 0.0)
        assertEquals(cx, p.x, 1e-3f)
        assertEquals(cy, p.y, 1e-3f)
        assertEquals(1.0, cam.depth(1.0, 0.0, 0.0), 1e-9)
    }

    @Test
    fun northIsUpOnScreen() {
        val cam = camera(0.0, 0.0)
        val north = 30.0 * Math.PI / 180.0
        val p = cam.project(cos(north), sin(north), 0.0)
        // 北侧点在屏幕上应该更靠上（y 更小）
        assertTrue(p.y < cy)
        assertEquals(cx, p.x, 1e-3f)
    }

    @Test
    fun antipodeIsBackside() {
        val cam = camera(0.0, 0.0)
        // 背面点（lon=180）深度为 -1，应被剔除
        assertTrue(cam.depth(-1.0, 0.0, 0.0) < -0.99)
    }

    @Test
    fun horizonPointProjectsExactlyOnLimb() {
        val cam = camera(0.0, 0.0)
        // 距中心 90° 的点：depth≈0，投影必须落在半径 R 的圆周上
        val p = cam.project(0.0, 0.0, 1.0)
        val dx = p.x - cx
        val dy = p.y - cy
        assertEquals(0.0, cam.depth(0.0, 0.0, 1.0), 1e-9)
        assertEquals(radiusPx.toDouble(), sqrt((dx * dx + dy * dy).toDouble()), 1e-3)
    }

    @Test
    fun unprojectRoundTrip() {
        val cam = camera(20.0, 100.0)
        val lat = 39.93
        val lon = 116.39
        val la = lat * Math.PI / 180.0
        val lo = lon * Math.PI / 180.0
        val p = cam.project(cos(la) * cos(lo), sin(la), cos(la) * sin(lo))
        val back = cam.unproject(p.x, p.y)
        assertNotNull(back)
        assertEquals(lat, back!!.lat, 1e-4)
        assertEquals(lon, back.lon, 1e-4)
    }

    @Test
    fun tapOutsideGlobeReturnsNull() {
        val cam = camera(0.0, 0.0)
        assertEquals(null, cam.unproject(cx + radiusPx + 10f, cy))
    }

    @Test
    fun fullyVisibleRingKeepsAllPoints() {
        val cam = camera(0.0, 0.0)
        val ring = TestFixtures.box(0.0, 0.0, 20.0, 20.0)
        val out = ProjectedRing()
        assertTrue(SphereProjector.project(ring, cam, out))
        assertEquals(ring.size, out.count)
        assertFalse(out.onHorizon.any { it })
    }

    @Test
    fun backsideRingIsCulled() {
        val cam = camera(0.0, 0.0)
        val ring = TestFixtures.box(180.0, 0.0, 20.0, 20.0)
        val out = ProjectedRing()
        assertFalse(SphereProjector.project(ring, cam, out))
        assertEquals(0, out.count)
    }

    @Test
    fun horizonCrossingRingIsClippedWithoutNaN() {
        val cam = camera(0.0, 0.0)
        // 横跨地平线（经度 60°~120°）：60° 在前半球，120° 在后半球
        val ring = TestFixtures.ring(
            listOf(
                60.0 to -10.0,
                120.0 to -10.0,
                120.0 to 10.0,
                60.0 to 10.0,
            )
        )
        val out = ProjectedRing()
        assertTrue(SphereProjector.project(ring, cam, out))
        assertTrue(out.count >= 4)
        for (i in 0 until out.count) {
            assertFalse(out.x[i].isNaN())
            assertFalse(out.y[i].isNaN())
            val dx = out.x[i] - cx
            val dy = out.y[i] - cy
            val dist = sqrt((dx * dx + dy * dy).toDouble())
            // 裁剪后的所有点都应该落在球面圆盘内
            assertTrue("point outside limb: $dist", dist <= radiusPx + 0.5)
            if (out.onHorizon[i]) {
                assertEquals(radiusPx.toDouble(), dist, 0.5)
            }
        }
        // 必须有地平线交点
        assertTrue(out.onHorizon.take(out.count).any { it })
    }

    @Test
    fun farSidePoleRingIsCulled() {
        // 环绕北极的一圈，相机对着南极：所有顶点都在背面，且环围的是北极而非视线轴，必须被剔除
        val ring = TestFixtures.ring(
            (0 until 24).map { i -> (i * 15.0) to 80.0 }
        )
        val cam = camera(-89.0, 0.0)
        val out = ProjectedRing()
        assertFalse(SphereProjector.project(ring, cam, out))
        assertEquals(0, out.count)
    }

    @Test
    fun denseHorizonCrossingRingHasNoZeroLengthSegments() {
        // 一个 360 点、纬度在 -20°..70° 剧烈变化的环：反复跨越地平线，
        // 用来验证裁剪 + 地平线补弧后不会出现重复点（零长度线段）或 NaN。
        val ring = TestFixtures.ring(
            (0 until 360).map { i ->
                val lon = i.toDouble()
                val lat = if (i % 2 == 0) 70.0 else -20.0
                lon to lat
            }
        )
        for (camLat in listOf(-80.0, -30.0, 0.0, 45.0, 80.0)) {
            val cam = camera(camLat, 90.0)
            val out = ProjectedRing()
            if (!SphereProjector.project(ring, cam, out)) continue
            for (i in 0 until out.count) {
                assertTrue("NaN at $i", out.x[i].isFinite() && out.y[i].isFinite())
                val j = if (i + 1 == out.count) 0 else i + 1
                val dx = out.x[i] - out.x[j]
                val dy = out.y[i] - out.y[j]
                assertTrue("camLat=$camLat 第 $i 段长度为 0", abs(dx) > 1e-4f || abs(dy) > 1e-4f)
            }
        }
    }
}

/** 测试用：由 (经度, 纬度) 序列构造 Ring（与解析器内部算法一致）。 */
private object TestFixtures {

    private const val DEG = Math.PI / 180.0

    fun ring(points: List<Pair<Double, Double>>): Ring {
        val n = points.size
        val lats = FloatArray(n)
        val lons = FloatArray(n)
        val ux = FloatArray(n)
        val uy = FloatArray(n)
        val uz = FloatArray(n)
        for (i in 0 until n) {
            val (lon, lat) = points[i]
            lats[i] = lat.toFloat()
            lons[i] = lon.toFloat()
            val la = lat * DEG
            val lo = lon * DEG
            ux[i] = (cos(la) * cos(lo)).toFloat()
            uy[i] = sin(la).toFloat()
            uz[i] = (cos(la) * sin(lo)).toFloat()
        }
        return Ring(lats, lons, ux, uy, uz)
    }

    /** 以 (lon, lat) 为中心、半径约 [dLon]/[dLat] 度的矩形环。 */
    fun box(lon: Double, lat: Double, dLon: Double, dLat: Double): Ring = ring(
        listOf(
            lon - dLon to lat - dLat,
            lon + dLon to lat - dLat,
            lon + dLon to lat + dLat,
            lon - dLon to lat + dLat,
        )
    )
}
