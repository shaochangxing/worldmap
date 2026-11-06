package com.sqz.worldmap

import com.sqz.worldmap.globe.GlobeScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 缩放范围、描边宽度换算、定位档位的边界值单测。 */
class GlobeScaleTest {

    @Test
    fun zoomIsClampedTo1Through10() {
        assertEquals(1f, GlobeScale.clampZoom(0.3f))
        assertEquals(10f, GlobeScale.clampZoom(12f))
        assertEquals(10f, GlobeScale.clampZoom(10f))
        assertEquals(3.5f, GlobeScale.clampZoom(3.5f))
        assertEquals(1f, GlobeScale.MIN_ZOOM)
        assertEquals(10f, GlobeScale.MAX_ZOOM)
    }

    @Test
    fun buttonStepsReachTenWithoutOvershoot() {
        var zoom = 1f
        repeat(20) { zoom = GlobeScale.clampZoom(zoom * 1.35f) }
        assertEquals(10f, zoom)
        var back = zoom
        repeat(20) { back = GlobeScale.clampZoom(back / 1.35f) }
        assertEquals(1f, back)
    }

    @Test
    fun borderStaysNearConstantWidthAcrossZoom() {
        val at1 = GlobeScale.borderWidthDp(1f)
        val at4 = GlobeScale.borderWidthDp(4f)
        val at10 = GlobeScale.borderWidthDp(10f)
        assertEquals(1.25f, at1, 1e-4f)
        assertEquals(1.10f, at10, 1e-4f)
        assertTrue("10 倍描边不应比 1 倍粗", at10 <= at1)
        assertTrue("描边变化应连续且很小", at1 - at10 < 0.2f)
        assertTrue(at10 > 1.0f)
        // 单调：放大过程中描边只会变细一点点，不会突变
        var prev = GlobeScale.borderWidthDp(1f)
        for (z in 1..100) {
            val w = GlobeScale.borderWidthDp(z / 10f)
            assertTrue("描边宽度应随缩放单调不增", w <= prev + 1e-5f)
            assertTrue("相邻档位之间不应跳变", prev - w < 0.05f)
            prev = w
        }
    }

    @Test
    fun borderWidthIsClampedOutsideRange() {
        assertEquals(GlobeScale.borderWidthDp(1f), GlobeScale.borderWidthDp(-5f), 1e-5f)
        assertEquals(GlobeScale.borderWidthDp(10f), GlobeScale.borderWidthDp(99f), 1e-5f)
    }

    @Test
    fun zoomForSpanGivesSmallCountriesMoreZoom() {
        val russia = GlobeScale.zoomForSpan(160.0)
        val china = GlobeScale.zoomForSpan(62.0)
        val taiwan = GlobeScale.zoomForSpan(3.6)
        val singapore = GlobeScale.zoomForSpan(0.45)
        assertEquals(1.9f, russia, 1e-5f)
        assertTrue(china in 1.9f..2.5f)
        assertTrue("台湾这类小区域应放大到 4 倍以上", taiwan >= 4.0f)
        assertEquals(5.5f, singapore, 1e-5f)
        assertTrue(russia < china && china < taiwan && taiwan < singapore)
    }

    @Test
    fun zoomForSpanBoundaries() {
        assertEquals(1.9f, GlobeScale.zoomForSpan(70.0), 1e-5f)
        assertEquals(2.1f, GlobeScale.zoomForSpan(69.99), 1e-5f)
        assertEquals(2.6f, GlobeScale.zoomForSpan(20.0), 1e-5f)
        assertEquals(3.2f, GlobeScale.zoomForSpan(8.0), 1e-5f)
        assertEquals(4.2f, GlobeScale.zoomForSpan(3.0), 1e-5f)
        assertEquals(5.0f, GlobeScale.zoomForSpan(1.0), 1e-5f)
        assertEquals(5.5f, GlobeScale.zoomForSpan(0.0), 1e-5f)
    }
}
