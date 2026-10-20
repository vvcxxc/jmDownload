package com.jmdl.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jmdl.data.Task
import com.jmdl.data.TaskStatus
import com.jmdl.util.AppLogger
import com.jmdl.util.CoverCache

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TaskRow(
    t: Task,
    isLastViewed: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onToggleSelect: () -> Unit,
    onEnterSelect: () -> Unit,
    onCopyText: (String) -> Unit,
    onRequestAuthors: () -> Unit,
    onRetry: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit,
) {
    val themeColor = LocalThemeColor.current
    val openable = t.status == TaskStatus.DONE && t.zipName != null
    val unread = t.status == TaskStatus.DONE && t.currentPage == null
    val context = LocalContext.current
    val coverFile = remember(t.fileUri, t.jmId) { CoverCache.getCoverFile(context, t.jmId) }
    var showPreview by remember { mutableStateOf(false) }
    var coverReady by remember(t.fileUri, t.jmId) { mutableStateOf(coverFile.exists()) }
    LaunchedEffect(t.fileUri, t.jmId) {
        if (coverFile.exists()) {
            coverReady = true
            AppLogger.i("Cover preview: id=${t.jmId} cached")
        } else if (t.status == TaskStatus.DONE && t.fileUri != null) {
            val result = CoverCache.extractAndCache(context, t.fileUri, t.jmId)
            coverReady = result?.exists() == true
            AppLogger.i("Cover preview: id=${t.jmId} extract=${if (result != null) "ok" else "failed"}")
        }
    }
    LaunchedEffect(t.id, t.status, t.authors.isEmpty()) {
        if (t.status == TaskStatus.DONE && t.authors.isEmpty()) onRequestAuthors()
    }
    Box {
        Card(
        Modifier
            .fillMaxWidth()
            .border(
                width = if (isLastViewed) 2.dp else 0.dp,
                color = if (isLastViewed) themeColor else Color.Transparent,
                shape = CardShape
            )
            .combinedClickable(
                onClick = { if (selectionMode) onToggleSelect() else if (openable) onOpen() },
                onLongClick = { if (!selectionMode) onEnterSelect() },
            ),
        colors = appCardColors()
    ) {
        Row(Modifier.padding(12.dp)) {
            if (coverReady) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(coverFile).crossfade(true).build(),
                    contentDescription = "封面",
                    modifier = Modifier
                        .width(80.dp)
                        .height(110.dp)
                        .clip(SmallShape)
                        .clickable { showPreview = true },
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f).combinedClickable(
                onClick = { if (selectionMode) onToggleSelect() else if (openable) onOpen() },
                onLongClick = { if (!selectionMode) onEnterSelect() },
            )) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectionMode) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { onToggleSelect() },
                        colors = CheckboxDefaults.colors(checkedColor = themeColor),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Column(Modifier.weight(1f)) {
                    val displayName = if (t.jmId.all { it.isDigit() }) "JM${t.jmId}" else t.title ?: t.jmId
                    val copyTitle = t.title ?: "JM${t.jmId}"
                    Text(
                        displayName + (t.title?.let { if (t.jmId.all { it.isDigit() }) " · $it" else "" } ?: ""),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.pointerInput(copyTitle) {
                            detectTapGestures(onLongPress = { onCopyText(copyTitle) })
                        },
                    )
                    if (t.authors.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            t.authors.take(3).forEach { author ->
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = themeColor.copy(alpha = 0.1f),
                                            shape = RoundedCornerShape(4.dp),
                                        )
                                        .pointerInput(author) {
                                            detectTapGestures(onLongPress = { onCopyText(author) })
                                        }
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = author,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = themeColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            if (t.authors.size > 3) {
                                Text(
                                    text = "+${t.authors.size - 3}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Text(
                        statusText(t),
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor(t),
                    )
                    when {
                        t.status == TaskStatus.DONE && t.pageCount != null -> Text(
                            if (t.currentPage != null) "已读 ${t.currentPage}/${t.pageCount} 页" else "已完成，共 ${t.pageCount} 页",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        t.status == TaskStatus.DONE && t.total > 0 -> Text(
                            "已完成，共 ${t.total} 页",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        t.status == TaskStatus.DONE -> Text(
                            "已完成",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        t.pageCount != null -> Text(
                            "共 ${t.pageCount} 页",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        t.total > 0 -> Text(
                            "共 ${t.total} 页",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
                if (!selectionMode) {
                    if (t.status == TaskStatus.FAILED || (t.status == TaskStatus.DONE && t.failed > 0)) {
                        TextButton(onClick = onRetry) { Text("重试") }
                    }
                    if (t.status == TaskStatus.PAUSED) {
                        TextButton(onClick = onResume) { Text("下载") }
                    }
                    if (t.status == TaskStatus.QUEUED || t.status == TaskStatus.RUNNING || t.status == TaskStatus.ZIPPING) {
                        TextButton(onClick = onPause) { Text("暂停") }
                    }
                    TextButton(onClick = onRemove) { Text("删除") }
                }
            }
            when {
                t.status == TaskStatus.RUNNING && t.total > 0 -> {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { t.done.toFloat() / t.total.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                        color = themeColor,
                    )
                }
                t.status == TaskStatus.RUNNING || t.status == TaskStatus.ZIPPING -> {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = themeColor)
                }
            }
        }
    }
    }
    if (unread) {
        Surface(
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
            shape = TinyShape,
            color = themeColor,
            shadowElevation = 2.dp,
        ) {
            Text(
                "未读",
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
    if (showPreview && coverFile.exists()) {
        Dialog(
            onDismissRequest = { showPreview = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { showPreview = false },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(coverFile)
                        .crossfade(true)
                        .build(),
                    contentDescription = "封面预览",
                    modifier = Modifier.fillMaxSize(),
                )
                IconButton(
                    onClick = { showPreview = false },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                ) {
                    Icon(Icons.Default.Close, "关闭", tint = Color.White)
                }
            }
        }
    }
    }
}


private fun statusText(t: Task): String = when (t.status) {
    TaskStatus.QUEUED -> "排队中"
    TaskStatus.RUNNING -> if (t.total > 0) "下载中 ${t.done}/${t.total}" else "下载中…"
    TaskStatus.ZIPPING -> "打包中…"
    TaskStatus.DONE -> buildString {
        append(t.message ?: "完成")
        t.zipName?.let { append(" · ").append(it) }
    }
    TaskStatus.FAILED -> "失败：${t.message ?: "未知错误"}"
    TaskStatus.PAUSED -> "已暂停"
}

@Composable
private fun statusColor(t: Task): Color {
    val themeColor = LocalThemeColor.current
    return when (t.status) {
        TaskStatus.FAILED -> MaterialTheme.colorScheme.error
        TaskStatus.PAUSED -> MaterialTheme.colorScheme.tertiary
        TaskStatus.DONE -> themeColor
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}
