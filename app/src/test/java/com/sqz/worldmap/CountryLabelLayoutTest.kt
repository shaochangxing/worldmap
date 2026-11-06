package com.sqz.worldmap

import androidx.compose.ui.geometry.Rect
import com.sqz.worldmap.globe.CountryLabelLayout
import com.sqz.worldmap.globe.landColorIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 国家名标签的分级/重叠剔除与陆地配色的纯逻辑单测。 */
class CountryLabelLayoutTest {

    @Test
    fun zoomTiersAreMonotonic() {
        assertTrue(CountryLabelLayout.maxLabels(1f) < CountryLabelLayout.maxLabels(1.6f))
        assertTrue(CountryLabelLayout.maxLabels(1.6f) < CountryLabelLayout.maxLabels(3f))
        // 字号屏幕恒定：从全球视野到 10x 都不放大（sp 本身与缩放无关）
        assertEquals(CountryLabelLayout.fontSizeSp(1f), CountryLabelLayout.fontSizeSp(10f), 0.001f)
        assertEquals(CountryLabelLayout.LABEL_SP, CountryLabelLayout.fontSizeSp(4f), 0.001f)
    }

    @Test
    fun wideViewOnlyShowsTopCountries() {
        assertEquals(40, CountryLabelLayout.maxLabels(1f))
        assertEquals(90, CountryLabelLayout.maxLabels(1.6f))
        assertEquals(240, CountryLabelLayout.maxLabels(3f))
    }

    @Test
    fun areaThresholdLoosensWithZoom() {
        assertTrue(CountryLabelLayout.minAreaSr(1f) > CountryLabelLayout.minAreaSr(1.6f))
        assertTrue(CountryLabelLayout.minAreaSr(1.6f) > CountryLabelLayout.minAreaSr(3f))
        assertEquals(0.0, CountryLabelLayout.minAreaSr(3f), 0.0)
    }

    @Test
    fun overlappingLowerPriorityLabelIsDropped() {
        val viewport = Rect(0f, 0f, 1080f, 2280f)
        val first = CountryLabelLayout.boxFor(500f, 500f, 120, 40)
        val second = CountryLabelLayout.boxFor(510f, 505f, 120, 40)
        val accepted = CountryLabelLayout.accept(listOf(first, second), emptyList(), viewport, 10)
        assertEquals(listOf(0), accepted)
    }

    @Test
    fun labelOutsideViewportOrUnderSearchBarIsDropped() {
        val viewport = Rect(0f, 0f, 1080f, 2280f)
        val blocked = listOf(Rect(0f, 0f, 1080f, 300f))
        val boxes = listOf(
            CountryLabelLayout.boxFor(540f, 120f, 100, 30), // 被搜索栏压住
            CountryLabelLayout.boxFor(1200f, 500f, 100, 30), // 出屏
            CountryLabelLayout.boxFor(540f, 900f, 100, 30), // 正常
        )
        assertEquals(listOf(2), CountryLabelLayout.accept(boxes, blocked, viewport, 10))
    }

    @Test
    fun limitStopsFurtherPlacement() {
        val viewport = Rect(0f, 0f, 1080f, 2280f)
        val boxes = listOf(
            CountryLabelLayout.boxFor(300f, 400f, 80, 30),
            CountryLabelLayout.boxFor(700f, 700f, 80, 30),
            CountryLabelLayout.boxFor(300f, 1500f, 80, 30),
        )
        assertEquals(listOf(0, 1), CountryLabelLayout.accept(boxes, emptyList(), viewport, 2))
    }

    @Test
    fun landColorIndexIsStableAndInRange() {
        val size = 8
        for (iso in listOf("CHN", "USA", "FRA", "DEU", "BRA", "IND", "", "ZWE")) {
            val i = landColorIndex(iso, size)
            assertTrue("index $i 越界", i in 0 until size)
            assertEquals(i, landColorIndex(iso, size))
        }
        assertTrue(landColorIndex("CHN", size) != landColorIndex("USA", size) ||
            landColorIndex("CHN", size) != landColorIndex("FRA", size))
    }
}
