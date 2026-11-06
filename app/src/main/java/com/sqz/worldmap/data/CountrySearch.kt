package com.sqz.worldmap.data

/**
 * 国家搜索：中文名 / 英文名 / 别名（官方全称等），前缀优先于包含匹配，忽略大小写。
 */
object CountrySearch {

    fun search(countries: List<Country>, rawQuery: String, limit: Int = 40): List<Country> {
        val q = rawQuery.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val scored = ArrayList<Pair<Country, Int>>()
        for (c in countries) {
            val zh = c.nameZh.lowercase()
            val en = c.nameEn.lowercase()
            var score = when {
                zh == q || en == q -> 0
                zh.startsWith(q) || en.startsWith(q) -> 1
                zh.contains(q) || en.contains(q) -> 2
                c.iso.isNotEmpty() && c.iso.lowercase() == q -> 3
                else -> -1
            }
            if (score < 0) {
                for (alias in c.aliases) {
                    val a = alias.lowercase()
                    if (a == q) {
                        score = 0
                        break
                    }
                    if (a.startsWith(q)) {
                        score = 1
                        break
                    }
                    if (a.contains(q)) score = 2
                }
            }
            if (score >= 0) scored.add(c to score)
        }
        scored.sortWith(compareBy({ it.second }, { it.first.nameZh.length }, { it.first.nameZh }))
        return scored.take(limit).map { it.first }
    }
}
