package com.sqz.worldmap

import com.sqz.worldmap.data.Country
import com.sqz.worldmap.data.WorldDataParser
import com.sqz.worldmap.globe.GlobeCamera
import com.sqz.worldmap.globe.GlobeHitTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 点击命中检测单测：投影 + 反投影 + 球面绕数。 */
class HitTestTest {

    private val countries: List<Country> by lazy {
        WorldDataParser.parseCountries(File("src/main/assets/world_countries.json").readText())
    }

    private val width = 1080f
    private val height = 1920f
    private val radius = 500f

    private fun camera(lat: Double, lon: Double) = GlobeCamera(lat, lon, width / 2f, height / 2f, radius)

    private fun screenOf(cam: GlobeCamera, lat: Double, lon: Double): Pair<Float, Float> {
        val la = lat * PI / 180.0
        val lo = lon * PI / 180.0
        val p = cam.project(cos(la) * cos(lo), sin(la), cos(la) * sin(lo))
        return p.x to p.y
    }

    @Test
    fun tapOnChinaHitsChina() {
        val cam = camera(35.0, 105.0)
        val (x, y) = screenOf(cam, 39.9, 116.4) // 北京
        val hit = GlobeHitTest.hit(countries, cam, x, y)
        assertEquals("CHN", hit?.iso)
    }

    @Test
    fun tapCenterOfFacingChinaHitsChina() {
        val cam = camera(35.0, 105.0)
        val hit = GlobeHitTest.hit(countries, cam, width / 2f, height / 2f)
        assertEquals("CHN", hit?.iso)
    }

    @Test
    fun tapOnAtlanticHitsNothing() {
        val cam = camera(0.0, -30.0)
        val hit = GlobeHitTest.hit(countries, cam, width / 2f, height / 2f)
        assertNull(hit)
    }

    @Test
    fun tapOutsideTheGlobeHitsNothing() {
        val cam = camera(0.0, 0.0)
        val hit = GlobeHitTest.hit(countries, cam, width / 2f + radius + 20f, height / 2f)
        assertNull(hit)
    }

    @Test
    fun tapOnSouthPoleHitsAntarctica() {
        val cam = camera(-89.0, 0.0)
        val hit = GlobeHitTest.hit(countries, cam, width / 2f, height / 2f)
        assertEquals("ATA", hit?.iso)
    }

    @Test
    fun tapOnTaiwanHitsTaiwanNotChina() {
        // 相机正对台湾，点在台湾岛内部（23.7N, 121.0E）：必须返回台湾自身
        val cam = camera(24.0, 121.0)
        val (x, y) = screenOf(cam, 23.7, 121.0)
        assertEquals("TWN", GlobeHitTest.hit(countries, cam, x, y)?.iso)
    }

    @Test
    fun tapOnTaiwanWhenChinaIsSelectedSceneStaysTaiwan() {
        // 与上一场景同一相机，但缩小到能同时看到大陆与台湾：台湾内部仍应命中台湾
        val cam = camera(30.0, 115.0)
        val (x, y) = screenOf(cam, 23.7, 121.0)
        assertEquals("TWN", GlobeHitTest.hit(countries, cam, x, y)?.iso)
        // 大陆上的点仍然命中中国
        val (cx, cy) = screenOf(cam, 35.0, 110.0)
        assertEquals("CHN", GlobeHitTest.hit(countries, cam, cx, cy)?.iso)
    }

    @Test
    fun backsideCountryIsNotHit() {
        // 相机对着欧洲；新西兰在地球背面（与视线夹角约 166°）。正交投影下背面点也会落在球盘内，
        // 但反投影得到的是该屏幕方向上的正面球面点，因此绝不能命中新西兰。
        val cam = camera(50.0, 10.0)
        val (x, y) = screenOf(cam, -41.3, 174.8) // 惠灵顿
        assertNotEquals("NZL", GlobeHitTest.hit(countries, cam, x, y)?.iso)
    }
}
