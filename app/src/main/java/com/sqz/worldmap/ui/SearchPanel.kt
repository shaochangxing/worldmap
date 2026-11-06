package com.sqz.worldmap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sqz.worldmap.data.Country
import com.sqz.worldmap.data.CountrySearch
import com.sqz.worldmap.data.HistoryEntry
import com.sqz.worldmap.data.SubRegionMap
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 搜索浮层：输入即筛选（中文名/英文名，前缀优先 + 包含匹配，忽略大小写），
 * 顶部展示最近 10 条搜索历史，可点选定位、可一键清空。
 */
@Composable
fun SearchPanel(
    countries: List<Country>,
    history: List<HistoryEntry>,
    onSelectCountry: (Country) -> Unit,
    onSelectHistory: (HistoryEntry) -> Unit,
    onClearHistory: () -> Unit,
    onClose: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val results = remember(query, countries) { CountrySearch.search(countries, query) }
    val focusRequester = remember { FocusRequester() }
    val timeFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose,
            ),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Surface(
                shape = RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 14.dp),
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("搜索国家（中文 / English）", fontSize = 14.sp) },
                        leadingIcon = {
                            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                        },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Filled.Close, contentDescription = "清空输入", modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = CircleShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    )

                    Spacer(Modifier.height(6.dp))

                    if (query.isBlank()) {
                        if (history.isEmpty()) {
                            HintText("输入国家名开始搜索，支持中文与英文")
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp),
                            ) {
                                Text(
                                    text = "最近搜索",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = onClearHistory, modifier = Modifier.height(32.dp)) {
                                    Text("清空", fontSize = 12.sp)
                                }
                            }
                            Column(Modifier.heightIn(max = 420.dp)) {
                                history.forEach { entry ->
                                    HistoryRow(
                                        entry = entry,
                                        timeText = timeFormat.format(Date(entry.timeMillis)),
                                        onClick = { onSelectHistory(entry) },
                                    )
                                }
                            }
                        }
                    } else {
                        if (results.isEmpty()) {
                            HintText("没有找到匹配「$query」的国家")
                        } else {
                            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                                items(results, key = { it.id }) { country ->
                                    ResultRow(country = country, onClick = { onSelectCountry(country) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HintText(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 18.dp),
    )
}

@Composable
private fun ResultRow(country: Country, onClick: () -> Unit) {
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 11.dp),
        ) {
            Text(country.nameZh, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(" · ", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = if (SubRegionMap.isSubRegion(country.iso)) {
                    "${SubRegionMap.cardNameZh(country)} · ${country.nameEn}"
                } else {
                    country.nameEn
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            if (country.iso.isNotBlank()) {
                Text(country.iso, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, timeText: String, onClick: () -> Unit) {
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 10.dp),
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(entry.nameZh, fontSize = 14.sp)
                Text(entry.nameEn, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(timeText, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    }
}
