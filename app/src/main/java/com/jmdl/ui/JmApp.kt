package com.jmdl.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jmdl.data.Task
import com.jmdl.data.TaskStatus
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JmApp(vm: TaskViewModel) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val saveUri by vm.saveUri.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var input by remember { mutableStateOf("") }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            vm.setSaveUri(uri)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("JM 下载") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(12.dp)
        ) {
            SaveFolderRow(saveUri = saveUri, onPick = { folderPicker.launch(null) })
            Spacer(Modifier.height(10.dp))
            InputRow(
                value = input,
                onValueChange = { input = it },
                onAdd = {
                    val err = vm.addTask(input)
                    if (err == null) input = "" else scope.launch { snackbar.showSnackbar(err) }
                },
            )
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            if (tasks.isEmpty()) {
                Text(
                    "还没有任务。输入车号点“添加”，会先记录到列表再逐个下载。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(tasks, key = { it.id }) { t ->
                        TaskRow(t, onRetry = { vm.retry(t.id) }, onRemove = { vm.remove(t.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SaveFolderRow(saveUri: String?, onPick: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("保存位置", style = MaterialTheme.typography.labelMedium)
                Text(
                    saveUri?.let { prettyUri(it) } ?: "未选择（下载前请先选择）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onPick) { Text(if (saveUri == null) "选择" else "更改") }
        }
    }
}

@Composable
private fun InputRow(value: String, onValueChange: (String) -> Unit, onAdd: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            leadingIcon = { Text("JM", style = MaterialTheme.typography.titleMedium) },
            placeholder = { Text("350234 或粘贴链接") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onAdd() }),
        )
        Spacer(Modifier.width(8.dp))
        Button(onClick = onAdd, enabled = value.isNotBlank()) { Text("添加") }
    }
}

@Composable
private fun TaskRow(t: Task, onRetry: () -> Unit, onRemove: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "JM${t.jmId}" + (t.title?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        statusText(t),
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor(t),
                    )
                }
                if (t.status == TaskStatus.FAILED) {
                    TextButton(onClick = onRetry) { Text("重试") }
                }
                TextButton(onClick = onRemove) { Text("删除") }
            }
            when {
                t.status == TaskStatus.RUNNING && t.total > 0 -> {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { t.done.toFloat() / t.total.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                t.status == TaskStatus.RUNNING || t.status == TaskStatus.ZIPPING -> {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
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
}

@Composable
private fun statusColor(t: Task): Color = when (t.status) {
    TaskStatus.FAILED -> MaterialTheme.colorScheme.error
    TaskStatus.DONE -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** Turn a SAF tree URI into something readable, e.g. "primary:Download/jm" → "Download/jm". */
private fun prettyUri(uri: String): String {
    val decoded = Uri.decode(uri)
    val afterColon = decoded.substringAfterLast(':', decoded)
    return afterColon.ifBlank { decoded }
}
