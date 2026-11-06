package com.sqz.worldmap.data

import android.content.Context

/** 从 assets 读取内置离线数据（运行时零网络）。 */
object WorldAssets {
    /** 国家边界（Natural Earth 1:10m，容差 0.02°）。 */
    fun loadCountries(context: Context): List<Country> =
        context.assets.open("world_countries.json").bufferedReader().use {
            WorldDataParser.parseCountries(it.readText())
        }

    fun loadCities(context: Context): List<City> =
        context.assets.open("world_cities.json").bufferedReader().use {
            WorldDataParser.parseCities(it.readText())
        }
}
