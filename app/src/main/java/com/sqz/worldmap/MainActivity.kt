package com.sqz.worldmap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.sqz.worldmap.data.Ring
import com.sqz.worldmap.data.SearchHistoryRepository
import com.sqz.worldmap.data.WorldAssets
import com.sqz.worldmap.ui.GlobeScreen
import com.sqz.worldmap.ui.theme.WorldMapTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 单 Activity，进入即地图。 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appContext = applicationContext
        val historyRepository = SearchHistoryRepository(appContext)
        setContent {
            WorldMapTheme {
                val countries = remember { WorldAssets.loadCountries(appContext) }
                val cities = remember { WorldAssets.loadCities(appContext) }
                // 省界是新增图层：放到后台线程解析，首帧不再多负担一次解析；
                // 解析完成前为空列表，省界图层自然不显示。
                val provinces by produceState<List<Ring>>(initialValue = emptyList(), appContext) {
                    value = withContext(Dispatchers.Default) { WorldAssets.loadProvinces(appContext) }
                }
                GlobeScreen(
                    countries = countries,
                    cities = cities,
                    provinceArcs = provinces,
                    historyRepository = historyRepository,
                )
            }
        }
    }
}
