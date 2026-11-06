package com.sqz.worldmap.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

data class HistoryEntry(
    val nameZh: String,
    val nameEn: String,
    val timeMillis: Long,
)

/** 搜索历史编解码与去重逻辑（纯 JVM，可单测）。 */
object HistoryCodec {
    const val MAX_ENTRIES = 10

    fun encode(entries: List<HistoryEntry>): String {
        val arr = JSONArray()
        for (e in entries) {
            arr.put(
                JSONObject()
                    .put("zh", e.nameZh)
                    .put("en", e.nameEn)
                    .put("t", e.timeMillis)
            )
        }
        return arr.toString()
    }

    fun decode(raw: String?): List<HistoryEntry> {
        if (raw.isNullOrEmpty()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            val list = ArrayList<HistoryEntry>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    HistoryEntry(
                        nameZh = o.optString("zh"),
                        nameEn = o.optString("en"),
                        timeMillis = o.optLong("t"),
                    )
                )
            }
            list
        }.getOrDefault(emptyList())
    }

    /** 新记录置顶、同名去重、最多保留 MAX_ENTRIES 条。 */
    fun push(entries: List<HistoryEntry>, entry: HistoryEntry): List<HistoryEntry> {
        val fresh = entries.filterNot { it.nameEn.equals(entry.nameEn, ignoreCase = true) }
        return (listOf(entry) + fresh).take(MAX_ENTRIES)
    }
}

private val Context.historyStore by preferencesDataStore(name = "search_history")
private val KEY_HISTORY = stringPreferencesKey("history_json")

/** 搜索历史持久化（DataStore，重启 App 后仍在）。 */
class SearchHistoryRepository(private val context: Context) {

    val entries: Flow<List<HistoryEntry>> =
        context.historyStore.data.map { prefs -> HistoryCodec.decode(prefs[KEY_HISTORY]) }

    suspend fun add(entry: HistoryEntry) {
        context.historyStore.edit { prefs ->
            val current = HistoryCodec.decode(prefs[KEY_HISTORY])
            prefs[KEY_HISTORY] = HistoryCodec.encode(HistoryCodec.push(current, entry))
        }
    }

    suspend fun clear() {
        context.historyStore.edit { prefs -> prefs.remove(KEY_HISTORY) }
    }
}
