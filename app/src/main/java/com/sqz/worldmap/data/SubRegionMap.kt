package com.sqz.worldmap.data

/**
 * 附属区域归属表：选中某个国家时，连带高亮其附属区域；附属区域本身仍可单独选中。
 *
 * 数据驱动（不写死在渲染代码里），以后要加香港、澳门等只需在此追加一条映射 + 卡片短名。
 * 目前唯一一条：中国（CHN）→ 台湾（TWN）。
 */
object SubRegionMap {

    private val includesByCountry = mapOf(
        "CHN" to listOf("TWN"),
    )

    /** 附属区域在国家卡片里使用的短名（台湾作为独立 feature 的短名是「台湾」，卡片上写「中国台湾」）。 */
    private val cardNames = mapOf(
        "TWN" to "中国台湾",
    )

    /** 选中 [iso] 时需要连带高亮的附属区域 ISO 列表。 */
    fun includesOf(iso: String): List<String> = includesByCountry[iso].orEmpty()

    /** 该区域属于哪个国家；独立国家返回 null。 */
    fun parentOf(iso: String): String? =
        includesByCountry.entries.firstOrNull { iso in it.value }?.key

    fun isSubRegion(iso: String): Boolean = cardNames.containsKey(iso)

    /** 信息卡标题：主国家用自身短名，附属区域用带归属的短名（如「中国台湾」）。 */
    fun cardNameZh(country: Country): String = cardNames[country.iso] ?: country.nameZh

    /** 选中某国时要一起高亮的所有国家（本体在前，附属区域在后）。 */
    fun highlightSetOf(country: Country, all: List<Country>): List<Country> {
        val out = ArrayList<Country>(4)
        out.add(country)
        for (iso in includesOf(country.iso)) {
            all.firstOrNull { it.iso == iso }?.let { out.add(it) }
        }
        return out
    }
}
