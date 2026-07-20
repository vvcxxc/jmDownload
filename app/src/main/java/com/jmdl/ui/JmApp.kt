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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.layout.ContentScale
import androidx.documentfile.provider.DocumentFile
import coil.compose.AsyncImage
import com.jmdl.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JmApp(vm: TaskViewModel) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val saveUri by vm.saveUri.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var input by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<Task?>(null) }
    val ordered = remember(tasks) {
        tasks.sortedByDescending { it.createdAt } // newest added on top
    }
    var viewing by remember { mutableStateOf<Task?>(null) }

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
                    items(ordered, key = { it.id }) { t ->
                        TaskRow(
                            t,
                            onOpen = { if (t.status == TaskStatus.DONE && t.zipName != null) viewing = t },
                            onRetry = { vm.retry(t.id) },
                            onRemove = { pendingDelete = t },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { task ->
        DeleteDialog(
            task = task,
            canDeleteFile = task.zipName != null && saveUri != null,
            onDismiss = { pendingDelete = null },
            onTaskOnly = {
                vm.remove(task.id)
                pendingDelete = null
            },
            onWithFile = {
                vm.removeWithFile(task)
                pendingDelete = null
            },
        )
    }

    viewing?.let { task ->
        ViewerScreen(task = task, onClose = { viewing = null })
    }
}

@Composable
private fun DeleteDialog(
    task: Task,
    canDeleteFile: Boolean,
    onDismiss: () -> Unit,
    onTaskOnly: () -> Unit,
    onWithFile: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除「JM${task.jmId}」") },
        text = {
            Text(
                if (canDeleteFile)
                    "已保存：${task.zipName}\n\n“只删任务”仅从列表移除，保留 zip；\n“连文件一起删”会把 zip 从保存目录删掉（不可恢复）。"
                else
                    "从列表中删除该任务？（没有已保存的文件）"
            )
        },
        confirmButton = {
            if (canDeleteFile) {
                Row {
                    TextButton(onClick = onTaskOnly) { Text("只删任务") }
                    TextButton(
                        onClick = onWithFile,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                    ) { Text("连文件一起删") }
                }
            } else {
                TextButton(onClick = onTaskOnly) { Text("删除") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
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
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onAdd() }),
        )
        Spacer(Modifier.width(8.dp))
        Button(onClick = onAdd, enabled = value.isNotBlank()) { Text("添加") }
    }
}

@Composable
private fun TaskRow(t: Task, onOpen: () -> Unit, onRetry: () -> Unit, onRemove: () -> Unit) {
    val openable = t.status == TaskStatus.DONE && t.zipName != null
    Card(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = openable, onClick = onOpen)
    ) {
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

@Composable
private fun ViewerScreen(task: Task, onClose: () -> Unit) {
    val context = LocalContext.current
    var files by remember(task.id) { mutableStateOf<List<File>?>(null) }
    var error by remember(task.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(task.id) {
        val r = withContext(Dispatchers.IO) { runCatching { extractForView(context, task) } }
        r.onSuccess { files = it }.onFailure { error = it.message ?: "打开失败" }
    }
    DisposableEffect(task.id) {
        onDispose { runCatching { File(context.cacheDir, "view").deleteRecursively() } }
    }
    BackHandler { onClose() }

    Surface(Modifier.fillMaxSize(), color = Color.Black) {
        Box(Modifier.fillMaxSize()) {
            val list = files
            when {
                error != null -> Text(
                    error ?: "打开失败",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                list == null -> CircularProgressIndicator(
                    Modifier.align(Alignment.Center), color = Color.White,
                )
                list.isEmpty() -> Text(
                    "没有可显示的图片",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> {
                    val pager = rememberPagerState(pageCount = { list.size })
                    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                        AsyncImage(
                            model = list[page],
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    Text(
                        "${pager.currentPage + 1}/${list.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    )
                }
            }
            TextButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
            ) { Text("关闭", color = Color.White) }
        }
    }
}

private fun isImageName(name: String): Boolean {
    val n = name.lowercase()
    return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") ||
        n.endsWith(".webp") || n.endsWith(".gif") || n.endsWith(".bmp")
}

/** Extract image entries from the task's saved zip into cache, preserving zip order. */
private fun extractForView(context: Context, task: Task): List<File> {
    val treeUri = SettingsStore(context).saveTreeUri
        ?: throw IllegalStateException("未选择保存目录")
    val zipName = task.zipName ?: throw IllegalStateException("没有已保存的文件")
    val tree = DocumentFile.fromTreeUri(context, Uri.parse(treeUri))
        ?: throw IllegalStateException("保存目录不可用")
    val doc = tree.findFile(zipName) ?: throw IllegalStateException("找不到文件：$zipName")

    val outDir = File(context.cacheDir, "view/${task.id}")
    File(context.cacheDir, "view").deleteRecursively()
    outDir.mkdirs()

    val ins = context.contentResolver.openInputStream(doc.uri)
        ?: throw IllegalStateException("无法读取文件")
    var idx = 0
    ins.use { input ->
        ZipInputStream(input.buffered()).use { zis ->
            val buf = ByteArray(64 * 1024)
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && isImageName(entry.name)) {
                    val ext = entry.name.substringAfterLast('.', "jpg")
                    val f = File(outDir, "%05d.%s".format(idx, ext))
                    f.outputStream().use { os ->
                        while (true) {
                            val n = zis.read(buf)
                            if (n < 0) break
                            os.write(buf, 0, n)
                        }
                    }
                    idx++
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
    return outDir.listFiles()?.filter { it.isFile }?.sortedBy { it.name } ?: emptyList()
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
