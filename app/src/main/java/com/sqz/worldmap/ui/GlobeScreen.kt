package com.sqz.worldmap.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sqz.worldmap.data.City
import com.sqz.worldmap.data.Country
import com.sqz.worldmap.data.HistoryEntry
import com.sqz.worldmap.data.SearchHistoryRepository
import com.sqz.worldmap.data.SubRegionMap
import com.sqz.worldmap.globe.CountryLabelLayout
import com.sqz.worldmap.globe.GlobeCamera
import com.sqz.worldmap.globe.GlobeHitTest
import com.sqz.worldmap.globe.GlobePathBuilder
import com.sqz.worldmap.globe.GlobeScale
import com.sqz.worldmap.globe.landColorIndex
import com.sqz.worldmap.ui.theme.globePalette
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

private const val MAX_LAT = 89.0

/**
 * 拖动灵敏度：1x 时横滑一屏（屏幕宽度）转动的角度（度）。
 * 约 220° ≈ 0.61 圈，转满 360° 需要约 1.64 屏宽；
 * 高倍按 1/zoom 衰减（5x 时一屏约 44°），保持放大后可控。
 */
private const val DRAG_DEG_PER_SCREEN_AT_1X = 220.0

/** 国家名锚点的最小可见深度：靠近地平线的点投影被极度压扁，标签会糊在一起。 */
private const val LABEL_MIN_DEPTH = 0.30

private data class NavRequest(val lat: Double, val lon: Double, val zoom: Float)

private fun globeRadius(width: Float, height: Float, zoom: Float): Float =
    min(width, height) / 2f * 0.94f * zoom

private fun normalizeLon(lon: Double): Double {
    var v = lon
    while (v > 180.0) v -= 360.0
    while (v < -180.0) v += 360.0
    return v
}

private fun shortestDelta(from: Double, to: Double): Double =
    (to - from + 540.0) % 360.0 - 180.0

private fun cameraFor(width: Float, height: Float, lat: Double, lon: Double, zoom: Float): GlobeCamera =
    GlobeCamera(lat, lon, width / 2f, height / 2f, globeRadius(width, height, zoom))

@Composable
fun GlobeScreen(
    countries: List<Country>,
    cities: List<City>,
    historyRepository: SearchHistoryRepository,
) {
    var lat by remember { mutableStateOf(12.0) }
    var lon by remember { mutableStateOf(105.0) }
    var zoom by remember { mutableStateOf(1f) }
    var selected by remember { mutableStateOf<Country?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    var nav by remember { mutableStateOf<NavRequest?>(null) }
    // 搜索选中后的飞行动画期间忽略地图点击，避免浮层收起时残余的点击事件立刻改选中别的国家
    var navStartedAt by remember { mutableStateOf(0L) }

    val scope = rememberCoroutineScope()
    val history by historyRepository.entries.collectAsState(initial = emptyList())
    val palette = globePalette()
    val builder = remember { GlobePathBuilder() }
    val measurer = rememberTextMeasurer(cacheSize = 512)
    val cityLabelStyle = remember { TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Normal) }
    val cityLabelLayouts = remember(cities, cityLabelStyle) {
        cities.map { city -> measurer.measure(AnnotatedString(city.nameZh), cityLabelStyle, maxLines = 1, softWrap = false) }
    }

    val countriesByArea = remember(countries) { countries.sortedByDescending { it.area } }

    // 每国一个 Path，对象池复用（每帧只 rewind + 重填，不再分配）
    val pathPool = remember(countries) { Array(countries.size) { Path() } }
    val highlightPaths = remember { Array(6) { Path() } }
    val visibleIdx = remember { ArrayList<Int>(256) }
    val placedRects = remember { ArrayList<Rect>(200) }

    // 选中高亮：本体 + 附属区域（中国选中时台湾一起高亮；台湾单独选中时只高亮台湾）
    val highlight = remember(selected, countries) {
        selected?.let { SubRegionMap.highlightSetOf(it, countries) } ?: emptyList()
    }

    fun rotateGlobe(dx: Float, dy: Float, viewWidthPx: Float) {
        val degPerPx = DRAG_DEG_PER_SCREEN_AT_1X /
            (viewWidthPx.coerceAtLeast(1f) * zoom.coerceAtLeast(1f))
        lon = normalizeLon(lon - dx * degPerPx)
        lat = (lat + dy * degPerPx).coerceIn(-MAX_LAT, MAX_LAT)
    }

    val navigateTo: (Country) -> Unit = { country ->
        selected = country
        searchOpen = false
        // 以主体环（顶点最多的轮廓）为准：含海外属地的国家不能被属地撑大 bbox
        val main = country.mainRing
        val centerLat = asin(main.centerY.coerceIn(-1.0, 1.0)) * 180.0 / PI
        val centerLon = atan2(main.centerZ, main.centerX) * 180.0 / PI
        val span = max((main.maxLat - main.minLat).toDouble(), (main.maxLon - main.minLon).toDouble())
        // 视野按国土跨度自动选择：小国放大到 4~5.5 倍，保证轮廓完整；中国等大国仍能看到全境（含台湾）
        nav = NavRequest(centerLat, centerLon, GlobeScale.zoomForSpan(span))
        navStartedAt = System.currentTimeMillis()
        scope.launch {
            historyRepository.add(HistoryEntry(country.nameZh, country.nameEn, System.currentTimeMillis()))
        }
    }

    LaunchedEffect(nav) {
        val target = nav ?: return@LaunchedEffect
        val startLat = lat
        val startLon = lon
        val startZoom = zoom
        val deltaLon = shortestDelta(startLon, target.lon)
        animate(0f, 1f, animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing)) { value, _ ->
            lat = startLat + (target.lat - startLat) * value
            lon = normalizeLon(startLon + deltaLon * value)
            zoom = startZoom + (target.zoom - startZoom) * value
        }
        nav = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(countries) {
                    awaitEachGesture {
                        // 搜索浮层打开时，画布不再接收点选/拖动（否则浮层上的点击会同时命中地图，
                        // 造成“点了搜索结果却选中了地图上另一个国家”）
                        if (searchOpen) return@awaitEachGesture
                        val down = awaitFirstDown(requireUnconsumed = true)
                        var dragging = false
                        var multiTouch = false
                        var accumulated = Offset.Zero
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            if (pressed.size >= 2) {
                                multiTouch = true
                                val p0 = pressed[0]
                                val p1 = pressed[1]
                                val curDist = (p1.position - p0.position).getDistance()
                                val prevDist = (p1.previousPosition - p0.previousPosition).getDistance()
                                if (prevDist > 1f && curDist > 1f) {
                                    zoom = GlobeScale.clampZoom(zoom * (curDist / prevDist))
                                }
                                val centroid = (p0.position + p1.position) / 2f
                                val prevCentroid = (p0.previousPosition + p1.previousPosition) / 2f
                                rotateGlobe(centroid.x - prevCentroid.x, centroid.y - prevCentroid.y, size.width.toFloat())
                                pressed.forEach { it.consume() }
                                nav = null
                            } else {
                                val change = pressed[0]
                                val delta = change.position - change.previousPosition
                                accumulated += delta
                                if (!dragging && accumulated.getDistance() > viewConfiguration.touchSlop) dragging = true
                                if (dragging) {
                                    rotateGlobe(delta.x, delta.y, size.width.toFloat())
                                    change.consume()
                                    nav = null
                                }
                            }
                        }
                        if (!dragging && !multiTouch && nav == null &&
                            System.currentTimeMillis() - navStartedAt > 1600L
                        ) {
                            // 只处理落在球体内部的点击：球外的空白区域不改变选中状态，
                            // 也避免浮层收起后残余事件误清空刚选中的国家
                            val radius = globeRadius(size.width.toFloat(), size.height.toFloat(), zoom).coerceAtLeast(1f)
                            val dx = down.position.x - size.width / 2f
                            val dy = down.position.y - size.height / 2f
                            if (dx * dx + dy * dy <= radius * radius) {
                                val camera = cameraFor(size.width.toFloat(), size.height.toFloat(), lat, lon, zoom)
                                // 命中检测：面积更小的国家优先，台湾在点上会优先返回台湾自身
                                selected = GlobeHitTest.hit(countries, camera, down.position.x, down.position.y)
                            }
                        }
                    }
                },
        ) {
            val width = size.width
            val height = size.height
            val radius = globeRadius(width, height, zoom)
            val cam = GlobeCamera(lat, lon, width / 2f, height / 2f, radius)
            val center = Offset(width / 2f, height / 2f)

            // 海洋
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(palette.oceanCenter, palette.oceanEdge),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )

            // 1) 每个国家独立一个 Path（包围球剔除背面国家），对象池复用
            visibleIdx.clear()
            for (i in countries.indices) {
                val country = countries[i]
                val dot = country.centerX * cam.fx + country.centerY * cam.fy + country.centerZ * cam.fz
                if (dot < country.capThreshold) continue
                if (!builder.buildInto(country, cam, pathPool[i])) continue
                visibleIdx.add(i)
            }

            // 2) 先画所有国家的填充：按 ISO 哈希取色，相邻国家颜色不同
            for (idx in visibleIdx) {
                drawPath(pathPool[idx], palette.land[landColorIndex(countries[idx].iso, palette.land.size)])
            }

            // 3) 再统一画国界描边：后画的描边不会被子邻国填充盖住；
            //    宽度按屏幕近似恒定（1x≈1.25dp，10x≈1.10dp），放大不会变成粗线
            val borderPx = GlobeScale.borderWidthDp(zoom).dp.toPx()
            for (idx in visibleIdx) {
                drawPath(pathPool[idx], palette.border, style = Stroke(width = borderPx))
            }

            // 选中高亮：先填充本体与附属区域，再统一描边，视觉上像一个整体
            if (highlight.isNotEmpty()) {
                var used = 0
                for (country in highlight) {
                    if (used >= highlightPaths.size) break
                    builder.buildInto(country, cam, highlightPaths[used])
                    used++
                }
                for (i in 0 until used) drawPath(highlightPaths[i], palette.selectedFill)
                val hlStroke = 2.2f.dp.toPx()
                for (i in 0 until used) {
                    drawPath(highlightPaths[i], palette.selectedStroke, style = Stroke(width = hlStroke))
                }
            }

            // 城市点与城市名共用一套可见性：先算出“本帧能显示名字”的城市集合，
            // 名字被视口/重叠剔除的城市不再画点。绘制顺序仍是 点 → 国名 → 城市名。
            placedRects.clear()
            val acceptedCity = BooleanArray(cities.size)
            val textPlacements = ArrayList<TextPlacement>(320)
            if (zoom >= 1.55f) {
                // 国名先占位（面积优先），城市名在剩余空间里按 首都→非首都 的顺序占位
                placeCountryLabels(
                    countriesByArea = countriesByArea,
                    cam = cam,
                    measurer = measurer,
                    palette = palette,
                    width = width,
                    height = height,
                    zoom = zoom,
                    placed = placedRects,
                    out = textPlacements,
                )
                val cityLabelCap = when {
                    zoom >= 5f -> 600
                    zoom >= 2f -> 180
                    else -> 80
                }
                val capitalsDrawn = placeCityLabels(
                    cities = cities,
                    cam = cam,
                    layouts = cityLabelLayouts,
                    width = width,
                    height = height,
                    placed = placedRects,
                    minDepth = 0.35,
                    labelColor = palette.capitalLabel,
                    zoom = zoom,
                    capitalsOnly = true,
                    maxCount = cityLabelCap,
                    accepted = acceptedCity,
                    out = textPlacements,
                )
                placeCityLabels(
                    cities = cities,
                    cam = cam,
                    layouts = cityLabelLayouts,
                    width = width,
                    height = height,
                    placed = placedRects,
                    minDepth = 0.35,
                    labelColor = palette.label,
                    zoom = zoom,
                    capitalsOnly = false,
                    maxCount = (cityLabelCap - capitalsDrawn).coerceAtLeast(0),
                    accepted = acceptedCity,
                    out = textPlacements,
                )
            }

            // 没有名字就不画点：所有档位只画本帧通过标签判定的城市。
            // 1x 城市名配额为 0，因此 1x 也不会出现任何无名点。
            for (i in cities.indices) {
                if (!acceptedCity[i]) continue
                drawCityDot(cities[i], cam, palette)
            }

            // 文字统一最后画：先国名、后城市名，保证都压在城市点之上
            for (p in textPlacements) {
                drawText(p.layout, color = p.color, topLeft = p.topLeft)
            }

            // 球体轮廓
            drawCircle(
                color = palette.rim,
                radius = radius,
                center = center,
                style = Stroke(width = 1.2f),
            )
        }

        // 顶部搜索栏
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            shadowElevation = 2.dp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .fillMaxWidth()
                .height(48.dp)
                .clickable { searchOpen = true },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "搜索国家",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(10.dp))
                Text("搜索国家", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            }
        }

        // 缩放按钮（乘法步进，1x..10x）
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = if (selected != null) 132.dp else 28.dp),
        ) {
            ZoomButton(label = "+", description = "放大") {
                nav = null
                zoom = GlobeScale.clampZoom(zoom * 1.35f)
            }
            ZoomButton(label = "−", description = "缩小") {
                nav = null
                zoom = GlobeScale.clampZoom(zoom / 1.35f)
            }
        }

        // 底部国家信息（不跳转页面）
        AnimatedVisibility(
            visible = selected != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val country = selected
            if (country != null) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 18.dp)
                        .fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = SubRegionMap.cardNameZh(country),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = country.iso,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = country.nameEn,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (searchOpen) {
            SearchPanel(
                countries = countries,
                history = history,
                onSelectCountry = navigateTo,
                onSelectHistory = { entry ->
                    val match = countries.firstOrNull { it.nameEn.equals(entry.nameEn, ignoreCase = true) }
                    if (match != null) navigateTo(match)
                },
                onClearHistory = { scope.launch { historyRepository.clear() } },
                onClose = { searchOpen = false },
            )
        }
    }
}

/** 一次布局阶段产出的文字放置；统一在城市点之后绘制，保证文字永远压在点之上。 */
private class TextPlacement(val layout: TextLayoutResult, val color: Color, val topLeft: Offset)

/**
 * 计算国家名标签的放置：按面积从大到小尝试，被压缩的低优先级标签直接跳过。
 * 只做布局、不绘制，交由调用方在城市点之后统一绘制，保证文字压在城市点之上。
 */
private fun DrawScope.placeCountryLabels(
    countriesByArea: List<Country>,
    cam: GlobeCamera,
    measurer: androidx.compose.ui.text.TextMeasurer,
    palette: com.sqz.worldmap.ui.theme.GlobePalette,
    width: Float,
    height: Float,
    zoom: Float,
    placed: MutableList<Rect>,
    out: MutableList<TextPlacement>,
) {
    val style = TextStyle(
        fontSize = CountryLabelLayout.fontSizeSp(zoom).sp,
        fontWeight = FontWeight.Medium,
        color = palette.label,
        shadow = Shadow(color = palette.labelHalo, offset = Offset(0f, 1f), blurRadius = 3f),
    )
    val limit = CountryLabelLayout.maxLabels(zoom)
    val minArea = CountryLabelLayout.minAreaSr(zoom)
    val blocked = arrayListOf(
        Rect(0f, 0f, width, 100.dp.toPx()),
        Rect(0f, height - 24.dp.toPx(), width, height),
    )
    val viewport = Rect(2f, 2f, width - 2f, height - 2f)
    val boxes = ArrayList<Rect>(limit)
    val layouts = ArrayList<TextLayoutResult>(limit)
    val probe = limit * 3
    for (c in countriesByArea) {
        if (boxes.size >= probe) break
        if (c.area < minArea) continue
        val depth = c.anchorX * cam.fx + c.anchorY * cam.fy + c.anchorZ * cam.fz
        if (depth < LABEL_MIN_DEPTH) continue
        val point = cam.project(c.anchorX, c.anchorY, c.anchorZ)
        if (point.x < 0f || point.x > width || point.y < 0f || point.y > height) continue
        val layout = measurer.measure(c.nameZh, style, maxLines = 1, softWrap = false)
        boxes.add(CountryLabelLayout.boxFor(point.x, point.y, layout.size.width, layout.size.height))
        layouts.add(layout)
    }
    for (i in CountryLabelLayout.accept(boxes, blocked, viewport, limit)) {
        val layout = layouts[i]
        val box = boxes[i]
        out.add(TextPlacement(layout, palette.label, Offset(box.left, box.top)))
        placed.add(box)
    }
}

/** 计算一批城市标签的放置（不绘制），并把通过判定的城市索引写入 [accepted]。 */
private fun DrawScope.placeCityLabels(
    cities: List<City>,
    cam: GlobeCamera,
    layouts: List<TextLayoutResult>,
    width: Float,
    height: Float,
    placed: MutableList<Rect>,
    minDepth: Double,
    labelColor: Color,
    zoom: Float,
    capitalsOnly: Boolean,
    maxCount: Int,
    accepted: BooleanArray,
    out: MutableList<TextPlacement>,
): Int {
    var count = 0
    for (i in cities.indices) {
        if (count >= maxCount) break
        val city = cities[i]
        if (city.isCapital != capitalsOnly) continue
        if (!city.isCapital && zoom < 2f) continue
        val depth = city.ux * cam.fx + city.uy * cam.fy + city.uz * cam.fz
        if (depth < minDepth) continue
        val point = cam.project(city.ux, city.uy, city.uz)
        val layout = layouts[i]
        val lx = point.x + 6f
        val ly = point.y - layout.size.height / 2f
        if (lx < 0f || lx + layout.size.width > width || ly < 0f || ly + layout.size.height > height) continue
        val rect = Rect(lx, ly, lx + layout.size.width, ly + layout.size.height)
        var overlaps = false
        for (r in placed) {
            if (r.overlaps(rect)) {
                overlaps = true
                break
            }
        }
        if (overlaps) continue
        placed.add(rect)
        accepted[i] = true
        count++
        out.add(TextPlacement(layout, labelColor, Offset(lx, ly)))
    }
    return count
}

/** 画一个城市坐标点（仅用于已通过标签判定的城市）。 */
private fun DrawScope.drawCityDot(city: City, cam: GlobeCamera, palette: com.sqz.worldmap.ui.theme.GlobePalette) {
    val depth = city.ux * cam.fx + city.uy * cam.fy + city.uz * cam.fz
    if (depth < 0.05) return
    val point = cam.project(city.ux, city.uy, city.uz)
    if (point.x < -10f || point.x > size.width + 10f || point.y < -10f || point.y > size.height + 10f) return
    drawCircle(
        color = if (city.isCapital) palette.capitalDot else palette.cityDot,
        radius = if (city.isCapital) 3f else 2f,
        center = Offset(point.x, point.y),
    )
}

@Composable
private fun ZoomButton(label: String, description: String, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
        modifier = Modifier
            .size(46.dp)
            .clickable(onClickLabel = description, onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        }
    }
}
