package com.sqz.worldmap

import com.sqz.worldmap.data.Country
import com.sqz.worldmap.data.CountrySearch
import com.sqz.worldmap.data.HistoryCodec
import com.sqz.worldmap.data.HistoryEntry
import com.sqz.worldmap.data.WorldDataParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 搜索匹配 + 历史读写（编解码/去重）单测。 */
class SearchHistoryTest {

    private val countries: List<Country> by lazy {
        WorldDataParser.parseCountries(File("src/main/assets/world_countries.json").readText())
    }

    @Test
    fun searchByChineseName() {
        val results = CountrySearch.search(countries, "中国")
        assertEquals("中国", results.first().nameZh)
    }

    @Test
    fun searchByEnglishNameIgnoringCase() {
        val results = CountrySearch.search(countries, "china")
        assertTrue(results.any { it.iso == "CHN" })
        val upper = CountrySearch.search(countries, "CHINA")
        assertEquals(results.first().iso, upper.first().iso)
    }

    @Test
    fun searchByPrefixAndContains() {
        val prefix = CountrySearch.search(countries, "法")
        assertTrue(prefix.any { it.iso == "FRA" })
        val contains = CountrySearch.search(countries, "国")
        assertTrue(contains.size > 1)
    }

    @Test
    fun searchByIsoCode() {
        val results = CountrySearch.search(countries, "CHN")
        assertEquals("CHN", results.first().iso)
    }

    @Test
    fun searchByOfficialAlias() {
        val results = CountrySearch.search(countries, "中华人民共和国")
        assertTrue(results.any { it.iso == "CHN" })
        val englishFull = CountrySearch.search(countries, "People's Republic")
        assertTrue(englishFull.any { it.iso == "CHN" })
    }

    @Test
    fun emptyOrUnknownQueryReturnsNothing() {
        assertTrue(CountrySearch.search(countries, "   ").isEmpty())
        assertTrue(CountrySearch.search(countries, "zzzzz").isEmpty())
    }

    @Test
    fun historyRoundTrip() {
        val entries = listOf(
            HistoryEntry("中国", "China", 1000L),
            HistoryEntry("法国", "France", 2000L),
        )
        val decoded = HistoryCodec.decode(HistoryCodec.encode(entries))
        assertEquals(entries, decoded)
    }

    @Test
    fun historyPushDeduplicatesAndCapsTen() {
        var list = emptyList<HistoryEntry>()
        for (i in 1..15) {
            list = HistoryCodec.push(list, HistoryEntry("国家$i", "Country$i", i.toLong()))
        }
        assertEquals(HistoryCodec.MAX_ENTRIES, list.size)
        assertEquals("Country15", list.first().nameEn)
        // 同名记录再次搜索时置顶且不重复
        list = HistoryCodec.push(list, HistoryEntry("国家10", "Country10", 99L))
        assertEquals(10, list.size)
        assertEquals("Country10", list.first().nameEn)
        assertEquals(1, list.count { it.nameEn == "Country10" })
    }

    @Test
    fun historyDecodeToleratesGarbage() {
        assertTrue(HistoryCodec.decode(null).isEmpty())
        assertTrue(HistoryCodec.decode("").isEmpty())
        assertTrue(HistoryCodec.decode("not-json").isEmpty())
    }
}
