package com.sqz.worldmap

import com.sqz.worldmap.data.Country
import com.sqz.worldmap.data.CountrySearch
import com.sqz.worldmap.data.SubRegionMap
import com.sqz.worldmap.data.WorldDataParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 附属区域归属（中国 ↔ 台湾）与搜索别名的单测。 */
class SubRegionMapTest {

    private val countries: List<Country> by lazy {
        WorldDataParser.parseCountries(File("src/main/assets/world_countries.json").readText())
    }

    private fun find(iso: String) = countries.first { it.iso == iso }

    @Test
    fun chinaIncludesTaiwanWhenHighlighted() {
        val china = find("CHN")
        val set = SubRegionMap.highlightSetOf(china, countries)
        assertEquals(2, set.size)
        assertEquals("CHN", set[0].iso)
        assertEquals("TWN", set[1].iso)
        assertEquals(listOf("TWN"), SubRegionMap.includesOf("CHN"))
    }

    @Test
    fun taiwanHighlightsOnlyItself() {
        val taiwan = find("TWN")
        val set = SubRegionMap.highlightSetOf(taiwan, countries)
        assertEquals(1, set.size)
        assertEquals("TWN", set[0].iso)
        assertTrue(SubRegionMap.includesOf("TWN").isEmpty())
    }

    @Test
    fun cardNamesUseShortChineseName() {
        assertEquals("中国", SubRegionMap.cardNameZh(find("CHN")))
        assertEquals("中国台湾", SubRegionMap.cardNameZh(find("TWN")))
        assertEquals("日本", SubRegionMap.cardNameZh(find("JPN")))
    }

    @Test
    fun parentRelationshipIsOneWay() {
        assertEquals("CHN", SubRegionMap.parentOf("TWN"))
        assertNull(SubRegionMap.parentOf("CHN"))
        assertNull(SubRegionMap.parentOf("JPN"))
        assertTrue(SubRegionMap.isSubRegion("TWN"))
        assertTrue(!SubRegionMap.isSubRegion("CHN"))
    }

    @Test
    fun searchFindsTaiwanByChineseAndAliases() {
        for (q in listOf("台湾", "中國台灣", "中国台湾", "Taiwan", "TWN")) {
            val results = CountrySearch.search(countries, q)
            assertTrue("搜索「$q」应命中台湾", results.any { it.iso == "TWN" })
        }
    }

    @Test
    fun searchFindsChinaAndNotTaiwanFirst() {
        val results = CountrySearch.search(countries, "中国")
        assertTrue(results.isNotEmpty())
        assertEquals("CHN", results.first().iso)
    }

    @Test
    fun searchFindsSmallCountries() {
        for ((q, iso) in listOf("新加坡" to "SGP", "马尔代夫" to "MDV", "马耳他" to "MLT", "黎巴嫩" to "LBN")) {
            val results = CountrySearch.search(countries, q)
            assertTrue("搜索「$q」应命中 $iso", results.any { it.iso == iso })
        }
    }
}
