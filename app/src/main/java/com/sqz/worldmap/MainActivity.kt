package com.sqz.worldmap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import com.sqz.worldmap.data.SearchHistoryRepository
import com.sqz.worldmap.data.WorldAssets
import com.sqz.worldmap.ui.GlobeScreen
import com.sqz.worldmap.ui.theme.WorldMapTheme

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
                GlobeScreen(countries = countries, cities = cities, historyRepository = historyRepository)
            }
        }
    }
}
