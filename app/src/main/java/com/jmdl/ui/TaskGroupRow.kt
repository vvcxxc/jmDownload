package com.jmdl.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jmdl.data.Task
import com.jmdl.data.TaskStatus

internal data class TaskGroup(
    val albumId: String,
    val albumTitle: String,
    val authors: List<String>,
    val tasks: List<Task>,
    val chapterCount: Int,
) {
    val isMultiChapter: Boolean get() = chapterCount > 1 || tasks.size > 1
}

internal fun buildTaskGroups(tasks: List<Task>): List<TaskGroup> {
    val grouped = LinkedHashMap<String, MutableList<Task>>()
    tasks.forEach { task ->
        grouped.getOrPut(task.jmId) { mutableListOf() }.add(task)
    }
    return grouped.map { (albumId, list) ->
        val sorted = list.sortedWith(
            compareBy<Task> { it.chapterIndex ?: Int.MAX_VALUE }
                .thenBy { it.createdAt }
        )
        val first = sorted.firstOrNull { it.chapterId == null } ?: sorted.first()
        TaskGroup(
            albumId = albumId,
            albumTitle = first.albumTitle ?: first.title ?: "JM$albumId",
            authors = first.authors,
            tasks = sorted,
            chapterCount = maxOf(
                sorted.size,
                sorted.maxOfOrNull { it.chapterCount ?: 0 } ?: 0,
            ),
        )
    }
}

@Composable
internal fun TaskGroupRow(
    group: TaskGroup,
    expanded: Boolean,
    lastViewedId: String?,
    onToggle: () -> Unit,
    onOpen: (Task) -> Unit,
    onToggleSelect: (Task) -> Unit,
    onEnterSelect: (Task) -> Unit,
    onCopyText: (String) -> Unit,
    onRequestAuthors: (Task) -> Unit,
    onRetry: (Task) -> Unit,
    onPause: (Task) -> Unit,
    onResume: (Task) -> Unit,
    onRemove: (Task) -> Unit,
) {
    val themeColor = LocalThemeColor.current
    val done = group.tasks.count { it.status == TaskStatus.DONE }
    val running = group.tasks.count {
        it.status == TaskStatus.RUNNING || it.status == TaskStatus.QUEUED || it.status == TaskStatus.ZIPPING
    }
    val paused = group.tasks.count { it.status == TaskStatus.PAUSED }
    val failed = group.tasks.count { it.status == TaskStatus.FAILED }
    val totalPages = group.tasks.sumOf { it.pageCount ?: it.total }
    val donePages = group.tasks.sumOf { it.done }
    val summary = buildString {
        append("$done/${group.tasks.size} 章完成")
        if (running > 0) append(" · $running 章下载中")
        if (paused > 0) append(" · $paused 章暂停")
        if (failed > 0) append(" · $failed 章失败")
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = appCardColors(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = group.albumTitle,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Text(
                            text = " · ${group.chapterCount}章",
                            style = MaterialTheme.typography.labelSmall,
                            color = themeColor,
                        )
                    }
                    if (group.authors.isNotEmpty()) {
                        Text(
                            text = group.authors.joinToString(" / "),
                            style = MaterialTheme.typography.labelSmall,
                            color = themeColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = if (totalPages > 0) "$summary · $donePages/$totalPages 页" else summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onToggle) {
                    Text(if (expanded) "收起" else "展开", color = themeColor)
                }
            }

            if (running > 0 && totalPages > 0) {
                LinearProgressIndicator(
                    progress = { donePages.toFloat() / totalPages.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    color = themeColor,
                )
            }

            if (expanded) {
                Spacer(Modifier.height(6.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 4.dp),
                ) {
                    items(group.tasks, key = { it.id }) { task ->
                        TaskRow(
                            t = task,
                            isLastViewed = task.id == lastViewedId,
                            selectionMode = false,
                            selected = false,
                            compact = true,
                            onOpen = { onOpen(task) },
                            onToggleSelect = { onToggleSelect(task) },
                            onEnterSelect = { onEnterSelect(task) },
                            onCopyText = onCopyText,
                            onRequestAuthors = { onRequestAuthors(task) },
                            onRetry = { onRetry(task) },
                            onPause = { onPause(task) },
                            onResume = { onResume(task) },
                            onRemove = { onRemove(task) },
                        )
                    }
                }
            }
        }
    }
}
