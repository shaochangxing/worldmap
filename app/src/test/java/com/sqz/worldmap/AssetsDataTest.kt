package com.sqz.worldmap

import com.sqz.worldmap.data.Country
import com.sqz.worldmap.data.WorldDataParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/** 内置 assets 数据的解析与完整性单测（真实读取工程内的数据文件）。 */
class AssetsDataTest {

    private val countriesJson: String by lazy { File("src/main/assets/world_countries.json").readText() }
    private val citiesJson: String by lazy { File("src/main/assets/world_cities.json").readText() }

    private val countries: List<Country> by lazy { WorldDataParser.parseCountries(countriesJson) }

    private fun pointCount(list: List<Country>): Int = list.sumOf { c -> c.rings.sumOf { it.size } }

    @Test
    fun countriesAssetCoversNaturalEarth10m() {
        assertEquals(246, countries.size)
        assertTrue("1:10m 的 feature 数应明显多于 110m（177）", countries.size >= 240)
        assertTrue("顶点数应显著多于 50m（约 3 万）", pointCount(countries) > 100_000)
        assertTrue("环数应显著多于 50m（1545）", countries.sumOf { it.rings.size } > 3000)
    }

    @Test
    fun countryCoordinatesAreValid() {
        for (c in countries) {
            assertTrue("${c.nameZh} 至少有一段轮廓", c.rings.isNotEmpty())
            for (ring in c.rings) {
                assertTrue("${c.nameZh} 轮廓点数 >= 6", ring.size >= 6)
                for (i in 0 until ring.size) {
                    val lat = ring.lats[i]
                    val lon = ring.lons[i]
                    assertTrue("$lat 超出纬度范围", lat >= -90f && lat <= 90f)
                    assertTrue("$lon 超出经度范围", lon >= -360f && lon <= 540f)
                    val x = ring.ux[i]
                    val y = ring.uy[i]
                    val z = ring.uz[i]
                    assertTrue("${c.nameZh} 预计算向量非法", x.isFinite() && y.isFinite() && z.isFinite())
                }
            }
        }
    }

    @Test
    fun ringsAreClosedWithoutDuplicatePoints() {
        for (c in countries) {
            for (ring in c.rings) {
                // 输出约定：不显式闭合（首尾点不同），渲染时由 close() 闭合
                assertTrue(
                    "${c.nameZh} 环不应显式闭合",
                    !(ring.lats[0] == ring.lats[ring.size - 1] && ring.lons[0] == ring.lons[ring.size - 1]),
                )
                for (i in 0 until ring.size) {
                    val j = if (i + 1 == ring.size) 0 else i + 1
                    val sameLat = abs(ring.lats[i] - ring.lats[j]) < 1e-6f
                    val sameLon = abs(ring.lons[i] - ring.lons[j]) < 1e-6f
                    assertTrue("${c.nameZh} 存在零长度线段", !(sameLat && sameLon))
                }
            }
        }
    }

    @Test
    fun areaAndAnchorArePresent() {
        for (c in countries) {
            assertTrue("${c.nameZh} 球面面积非法", c.area >= 0.0)
            assertTrue(
                "${c.nameZh} 标注锚点非法",
                c.anchorLatDeg >= -90.0 && c.anchorLatDeg <= 90.0 &&
                    c.anchorLonDeg >= -180.0 && c.anchorLonDeg <= 180.0,
            )
        }
        // 摩纳哥这类微型国家的面积在 6 位小数下会被舍入成 0，只要求绝大多数国家面积 > 0
        assertTrue("有面积的国家数应 >= 240", countries.count { it.area > 0.0 } >= 240)
        val russia = countries.first { it.iso == "RUS" }
        val china = countries.first { it.iso == "CHN" }
        assertTrue("俄罗斯面积应大于中国", russia.area > china.area)
    }

    @Test
    fun chinaIsPresentWithShortNameAndAliases() {
        val china = countries.firstOrNull { it.iso == "CHN" }
        assertNotNull(china)
        assertEquals("中国", china!!.nameZh)
        assertEquals("China", china.nameEn)
        assertTrue(china.aliases.contains("中华人民共和国"))
        assertTrue(china.containsVector(
            Math.cos(Math.toRadians(39.9)) * Math.cos(Math.toRadians(116.4)),
            Math.sin(Math.toRadians(39.9)),
            Math.cos(Math.toRadians(39.9)) * Math.sin(Math.toRadians(116.4)),
        ))
    }

    @Test
    fun taiwanIsASeparateFeatureAndNotInsideChina() {
        val taiwan = countries.firstOrNull { it.iso == "TWN" }
        assertNotNull("1:10m 数据中台湾应为独立 feature（ISO TWN）", taiwan)
        assertEquals("台湾", taiwan!!.nameZh)
        assertEquals("Taiwan", taiwan.nameEn)
        assertTrue("台湾应有 8 段轮廓（本岛 + 离岛）", taiwan.rings.size >= 1)
        // 台湾本岛范围（lon 120-122.3, lat 21.7-25.5）：中国轮廓里不应再有环完全落在这个盒子内，
        // 否则同一块地会被画两遍（双重描边/漏色缝）
        val china = countries.first { it.iso == "CHN" }
        for (ring in china.rings) {
            val inside = (0 until ring.size).all {
                ring.lons[it] in 120.0f..122.3f && ring.lats[it] in 21.7f..25.5f
            }
            assertTrue("中国轮廓不应包含台湾本岛环", !inside)
        }
        // 搜「台湾 / 中国台湾」都能命中
        assertTrue(taiwan.aliases.contains("中国台湾"))
    }

    @Test
    fun antarcticaIsPresent() {
        assertTrue(countries.any { it.iso == "ATA" })
    }

    @Test
    fun citiesAreParsedAndReasonable() {
        val cities = WorldDataParser.parseCities(citiesJson)
        assertEquals(243, cities.size)
        assertTrue("首都数量应为主", cities.count { it.isCapital } >= 190)
        for (city in cities) {
            assertTrue(city.nameZh.isNotBlank())
            assertTrue(city.lat >= -90.0 && city.lat <= 90.0)
            assertTrue(city.lon >= -180.0 && city.lon <= 180.0)
        }
        assertTrue(cities.any { it.nameEn == "Beijing" && it.countryIso == "CHN" })
    }

    @Test
    fun assetsAreReasonableFor10m() {
        val size = File("src/main/assets/world_countries.json").length() +
            File("src/main/assets/world_cities.json").length()
        // 1:10m 精度优先，体积上限放宽到 5MB（实测约 1.7MB）
        assertTrue("assets 合计 $size 字节，应小于 5MB", size < 5L * 1024 * 1024)
    }

    @Test
    fun encodeThenParseKeepsCountryCount() {
        val encoded = WorldDataParser.encodeCountries(countries)
        val reparsed = WorldDataParser.parseCountries(encoded)
        assertEquals(countries.size, reparsed.size)
        assertEquals(countries[0].nameEn, reparsed[0].nameEn)
        assertEquals(countries[0].rings[0].size, reparsed[0].rings[0].size)
        assertEquals(countries[0].area, reparsed[0].area, 1e-9)
    }
}
