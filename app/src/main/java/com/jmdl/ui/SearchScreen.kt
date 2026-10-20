package com.jmdl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jmdl.engine.JmEngine
import com.jmdl.data.Task
import com.jmdl.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SearchMode { JM_ID, KEYWORD }

data class SearchResultItem(
    val id: String,
    val title: String?,
    val authors: List<String>?,
    val image: String?,
    val pageCount: Int = 0
)

@Composable
fun SearchScreen(
    vm: TaskViewModel,
    snackbar: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val themeColor = LocalThemeColor.current

    var searchInput by remember { mutableStateOf("") }
    var searchMode by remember { mutableStateOf(SearchMode.KEYWORD) }
    var isSearching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<SearchResultItem>>(emptyList()) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var hasSearched by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var currentPage by remember { mutableIntStateOf(1) }
    var totalPages by remember { mutableIntStateOf(1) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val downloadedIds = remember(tasks) { tasks.map { it.jmId }.toSet() }

    fun performSearch(page: Int = 1) {
        val query = searchInput.trim()
        if (query.isBlank()) return
        if (searchMode == SearchMode.JM_ID && !query.all { it.isDigit() }) {
            searchError = "JM号必须为纯数字"
            hasSearched = true
            results = emptyList()
            return
        }
        focus.clearFocus()
        isSearching = true
        searchError = null
        if (page == 1) {
            results = emptyList()
            hasSearched = true
        }
        searchJob?.cancel()
        searchJob = scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    when (searchMode) {
                        SearchMode.JM_ID -> {
                            val album = JmEngine.searchByAlbum(query)
                            var imageUrl = album.image
                            if (imageUrl.isNullOrBlank()) {
                                try {
                                    val searchPage = JmEngine.searchByKeyword(query, 1)
                                    imageUrl = searchPage.content.firstOrNull { it.id == query }?.image
                                    AppLogger.i("Search JM_ID: cover fallback from keyword search: $imageUrl")
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    AppLogger.w("Search JM_ID: cover fallback failed: ${e.message}")
                                }
                            }
                            if (imageUrl.isNullOrBlank()) {
                                imageUrl = album.images.firstOrNull()?.url
                                AppLogger.i("Search JM_ID: cover fallback from first page image: $imageUrl")
                            }
                            val item = SearchResultItem(
                                id = query,
                                title = album.title,
                                authors = album.authors,
                                image = imageUrl,
                                pageCount = album.pageCount
                            )
                            AppLogger.i("Search JM_ID: id=$query title=${album.title} cover=$imageUrl pages=${album.pageCount}")
                            withContext(Dispatchers.Main) {
                                results = listOf(item)
                                currentPage = 1
                                totalPages = 1
                            }
                        }
                        SearchMode.KEYWORD -> {
                            val pageResult = JmEngine.searchByKeyword(query, page)
                            val items = pageResult.content.map { meta ->
                                SearchResultItem(
                                    id = meta.id,
                                    title = meta.title,
                                    authors = meta.authors,
                                    image = meta.image
                                )
                            }
                            AppLogger.i("Search KEYWORD: '$query' page=$page -> ${items.size} results, totalPages=${pageResult.totalPages}")
                            items.forEach { meta ->
                                AppLogger.i("  item: id=${meta.id} title=${meta.title} cover=${meta.image}")
                            }
                            withContext(Dispatchers.Main) {
                                results = if (pageResult.currentPage == 1) items else results + items
                                currentPage = pageResult.currentPage
                                totalPages = pageResult.totalPages
                            }
                        }
                    }
                }
                isSearching = false
            } catch (e: CancellationException) {
                // A newer search or leaving the screen cancelled this one; the new
                // request owns isSearching now.
                throw e
            } catch (e: Exception) {
                searchError = e.message ?: "搜索失败"
                if (searchMode == SearchMode.JM_ID) {
                    results = emptyList()
                }
                isSearching = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
    ) {
        Spacer(Modifier.height(12.dp))

        Card(
            Modifier.fillMaxWidth(),
            colors = appCardColors()
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = searchMode == SearchMode.KEYWORD,
                        onClick = {
                            searchMode = SearchMode.KEYWORD
                            results = emptyList()
                            searchError = null
                            hasSearched = false
                            selectionMode = false
                            selectedIds = emptySet()
                        },
                        label = { Text("标题·作者") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = themeColor.copy(alpha = 0.2f),
                            selectedLabelColor = themeColor
                        )
                    )
                    FilterChip(
                        selected = searchMode == SearchMode.JM_ID,
                        onClick = {
                            searchMode = SearchMode.JM_ID
                            results = emptyList()
                            searchError = null
                            hasSearched = false
                            selectionMode = false
                            selectedIds = emptySet()
                        },
                        label = { Text("JM号") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = themeColor.copy(alpha = 0.2f),
                            selectedLabelColor = themeColor
                        )
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = searchInput,
                        onValueChange = { searchInput = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                if (searchMode == SearchMode.JM_ID) "输入JM号" else "输入标题或作者关键词",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { performSearch(1) }),
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { performSearch(1) },
                        enabled = searchInput.isNotBlank() && !isSearching
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "搜索",
                            tint = if (searchInput.isNotBlank()) themeColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (selectionMode && results.isNotEmpty()) {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = themeColor.copy(alpha = 0.15f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            selectionMode = false
                            selectedIds = emptySet()
                        }) {
                            Text("取消")
                        }
                        Text(
                            "已选 ${selectedIds.size}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = themeColor
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            selectedIds = if (selectedIds.size == results.filter { it.title != null }.size)
                                emptySet()
                            else
                                results.filter { it.title != null }.map { it.id }.toSet()
                        }) {
                            Text(if (selectedIds.size == results.filter { it.title != null }.size) "取消全选" else "全选")
                        }
                        Button(
                            onClick = {
                                val toAdd = results.filter { it.id in selectedIds && it.title != null }
                                AppLogger.i("Batch download: ${toAdd.size} items selected, ids=${toAdd.map { it.id }}")
                                var added = 0
                                var skipped = 0
                                toAdd.forEach { item ->
                                    val err = vm.addTask(item.id)
                                    if (err == null) { added++; AppLogger.i("  added: id=${item.id}") }
                                    else { skipped++; AppLogger.w("  skip: id=${item.id} err=$err") }
                                }
                                val msg = buildString {
                                    if (added > 0) append("已加入 $added 个到下载列表")
                                    if (skipped > 0) {
                                        if (added > 0) append("，")
                                        append("$skipped 个已在列表中")
                                    }
                                }
                                if (msg.isNotEmpty()) snackbar(msg)
                                selectionMode = false
                                selectedIds = emptySet()
                            },
                            enabled = selectedIds.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                            Text("加入下载")
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (isSearching && results.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = themeColor)
            }
        } else if (searchError != null && results.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        searchError!!,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        } else if (hasSearched && results.isEmpty() && !isSearching) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "未找到结果",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (results.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(results.filter { it.title != null }, key = { it.id }) { item ->
                    SearchResultRow(
                        item = item,
                        selectionMode = selectionMode,
                        isSelected = item.id in selectedIds,
                        isDownloaded = item.id in downloadedIds,
                        themeColor = themeColor,
                        onToggleSelect = {
                            selectedIds = if (item.id in selectedIds)
                                selectedIds - item.id
                            else
                                selectedIds + item.id
                        },
                        onEnterSelect = {
                            selectionMode = true
                            selectedIds = selectedIds + item.id
                        },
                        onDownload = {
                            AppLogger.i("Single download from search: id=${item.id}")
                            val err: String? = vm.addTask(item.id)
                            if (err == null) {
                                snackbar("JM${item.id} 已加入下载列表")
                            } else {
                                AppLogger.w("Single download failed: id=${item.id} err=$err")
                                snackbar(err)
                            }
                        }
                    )
                }

                if (searchMode == SearchMode.KEYWORD && currentPage < totalPages) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSearching) {
                                CircularProgressIndicator(
                                    color = themeColor,
                                    modifier = Modifier.padding(16.dp)
                                )
                            } else {
                                TextButton(onClick = { performSearch(currentPage + 1) }) {
                                    Text("加载更多 (${currentPage}/${totalPages})")
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
private fun SearchResultRow(
    item: SearchResultItem,
    selectionMode: Boolean,
    isSelected: Boolean,
    isDownloaded: Boolean,
    themeColor: Color,
    onToggleSelect: () -> Unit,
    onEnterSelect: () -> Unit,
    onDownload: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (selectionMode) onToggleSelect()
            },
        colors = appCardColors()
    ) {
        Box {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelect() },
                        colors = CheckboxDefaults.colors(checkedColor = themeColor),
                    )
                    Spacer(Modifier.width(4.dp))
                }

                Column(modifier = Modifier.weight(1f).padding(bottom = if (!selectionMode) 32.dp else 0.dp)) {
                    val displayTitle = when {
                        item.title.isNullOrBlank() -> "JM${item.id}"
                        item.id.all { it.isDigit() } -> "JM${item.id} ${item.title}"
                        else -> "${item.title} · ${item.id}"
                    }
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (!item.authors.isNullOrEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            item.authors.take(3).forEach { author ->
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = themeColor.copy(alpha = 0.1f),
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = author,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = themeColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (item.authors.size > 3) {
                                Text(
                                    text = "+${item.authors.size - 3}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (item.pageCount > 0) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "${item.pageCount}页",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (!selectionMode) {
                Button(
                    onClick = {
                        if (!isDownloaded) {
                            AppLogger.i("Download from search: id=${item.id} title=${item.title}")
                            onDownload()
                        }
                    },
                    enabled = !isDownloaded,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDownloaded) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f) else themeColor,
                        disabledContainerColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 8.dp),
                ) {
                    if (!isDownloaded) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(2.dp))
                    }
                    Text(if (isDownloaded) "已加入" else "下载", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}