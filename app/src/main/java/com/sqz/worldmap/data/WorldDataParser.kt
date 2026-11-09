package com.sqz.worldmap.data

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.cos
import kotlin.math.sin

/**
 * assets 内置数据的解析器。只依赖 org.json（Android 自带，JVM 单测下用 testImplementation 引入同名实现），
 * 不依赖任何 Android API，因此可以在普通单元测试里直接跑。
 *
 * 数据文件格式（由 Natural Earth 1:10m 数据预处理而来）：
 * world_countries.json: {"countries":[{"zh":..,"en":..,"iso":..,"cont":..,"area":..,"ac":[lon,lat],"rings":[[lon,lat,lon,lat,...],...]}]}
 * world_cities.json:    {"cities":[{"zh":..,"en":..,"cc":..,"c":..,"lat":..,"lon":..,"cap":0|1}]}
 * world_provinces.json: {"arcs":[[lon,lat,lon,lat,...],...]}（中国省际共享边界的开放折线）
 */
object WorldDataParser {

    private const val DEG_TO_RAD = Math.PI / 180.0

    /** 一条扁平坐标数组 [lon,lat,...] 转成预计算单位向量的 [Ring]（顶点数不足 [minVerts] 返回 null）。 */
    private fun readRing(flat: JSONArray, minVerts: Int = 3): Ring? {
        val n = flat.length() / 2
        if (n < minVerts) return null
        val lats = FloatArray(n)
        val lons = FloatArray(n)
        val ux = FloatArray(n)
        val uy = FloatArray(n)
        val uz = FloatArray(n)
        for (p in 0 until n) {
            val lon = flat.getDouble(p * 2)
            val lat = flat.getDouble(p * 2 + 1)
            lats[p] = lat.toFloat()
            lons[p] = lon.toFloat()
            val la = lat * DEG_TO_RAD
            val lo = lon * DEG_TO_RAD
            ux[p] = (cos(la) * cos(lo)).toFloat()
            uy[p] = sin(la).toFloat()
            uz[p] = (cos(la) * sin(lo)).toFloat()
        }
        return Ring(lats, lons, ux, uy, uz)
    }

    fun parseCountries(json: String): List<Country> {
        val root = JSONObject(json)
        val array = root.getJSONArray("countries")
        val result = ArrayList<Country>(array.length())
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            val ringsJson = o.getJSONArray("rings")
            val rings = ArrayList<Ring>(ringsJson.length())
            for (r in 0 until ringsJson.length()) {
                // 每个环是扁平数组 [lon,lat,lon,lat,...]（比嵌套数组解析快得多）
                val ring = readRing(ringsJson.getJSONArray(r)) ?: continue
                rings.add(ring)
            }
            if (rings.isEmpty()) continue
            val aliasesJson = o.optJSONArray("alt")
            val aliases = ArrayList<String>(aliasesJson?.length() ?: 0)
            if (aliasesJson != null) {
                for (a in 0 until aliasesJson.length()) {
                    val value = aliasesJson.optString(a)
                    if (value.isNotBlank()) aliases.add(value)
                }
            }
            val ac = o.optJSONArray("ac")
            val anchorLon = if (ac != null && ac.length() >= 2) ac.getDouble(0) else Double.NaN
            val anchorLat = if (ac != null && ac.length() >= 2) ac.getDouble(1) else Double.NaN
            result.add(
                Country(
                    id = result.size,
                    nameZh = o.optString("zh"),
                    nameEn = o.optString("en"),
                    iso = o.optString("iso"),
                    continent = o.optString("cont"),
                    aliases = aliases,
                    rings = rings,
                    area = o.optDouble("area", 0.0),
                    anchorLat = anchorLat,
                    anchorLon = anchorLon,
                )
            )
        }
        return result
    }

    fun parseCities(json: String): List<City> {
        val root = JSONObject(json)
        val array = root.getJSONArray("cities")
        val result = ArrayList<City>(array.length())
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            val zh = o.optString("zh")
            val en = o.optString("en")
            if (zh.isBlank() && en.isBlank()) continue
            result.add(
                City(
                    id = result.size,
                    nameZh = if (zh.isBlank()) en else zh,
                    nameEn = if (en.isBlank()) zh else en,
                    countryIso = o.optString("cc"),
                    countryZh = o.optString("c"),
                    lat = o.getDouble("lat"),
                    lon = o.getDouble("lon"),
                    isCapital = o.optInt("cap", 0) == 1,
                )
            )
        }
        return result
    }

    /**
     * 中国省级界线：每条弧是两省（或三省交界之间）共用的开放折线。
     * 复用 [Ring] 的单向量存储与包围球，只是渲染端不闭合、不填充。
     */
    fun parseProvinceArcs(json: String): List<Ring> {
        val root = JSONObject(json)
        val array = root.getJSONArray("arcs")
        val result = ArrayList<Ring>(array.length())
        for (i in 0 until array.length()) {
            val arc = readRing(array.getJSONArray(i), minVerts = 2) ?: continue
            result.add(arc)
        }
        return result
    }

    /** 便于测试/调试：把国家列表再序列化回 JSON。 */
    fun encodeCountries(countries: List<Country>): String {
        val root = JSONObject()
        val arr = JSONArray()
        for (c in countries) {
            val o = JSONObject()
            o.put("zh", c.nameZh)
            o.put("en", c.nameEn)
            o.put("iso", c.iso)
            o.put("cont", c.continent)
            o.put("area", c.area)
            o.put("ac", JSONArray().put(c.anchorLonDeg).put(c.anchorLatDeg))
            val alt = JSONArray()
            for (a in c.aliases) alt.put(a)
            o.put("alt", alt)
            val rings = JSONArray()
            for (r in c.rings) {
                val pts = JSONArray()
                for (i in 0 until r.size) {
                    pts.put(r.lons[i].toDouble())
                    pts.put(r.lats[i].toDouble())
                }
                rings.put(pts)
            }
            o.put("rings", rings)
            arr.put(o)
        }
        root.put("countries", arr)
        return root.toString()
    }
}
