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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
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
import com.jmdl.download.Storage
import androidx.compose.animation.core.tween
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import kotlin.math.roundToInt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.Checkbox
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
    val focus = LocalFocusManager.current
    val snackbar = remember { SnackbarHostState() }
    var input by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<Task?>(null) }
    val ordered = remember(tasks) {
        tasks.sortedByDescending { it.createdAt } // newest added on top
    }
    var viewing by remember { mutableStateOf<Task?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var showBatchDelete by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showBackground by remember { mutableStateOf(false) }
    val bg by vm.background.collectAsStateWithLifecycle()

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            vm.setSaveUri(uri)
        }
    }
    val moveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            vm.moveTasks(selected, uri)
        }
        selectionMode = false
        selected = emptySet()
    }
    val bgImagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            vm.setBgImage(uri)
        }
    }

    val bgImage = bg.imageUri
    val bgColor = bg.color
    Box(Modifier.fillMaxSize()) {
        if (bgImage != null) {
            AsyncImage(
                model = Uri.parse(bgImage),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else if (bgColor != null) {
            Box(Modifier.fillMaxSize().background(Color(bgColor)))
        }
        Scaffold(
            containerColor = if (bgImage != null || bgColor != null)
                Color.Transparent
            else
                MaterialTheme.colorScheme.background,
            topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = { Text("已选 ${selected.size}") },
                    navigationIcon = {
                        TextButton(onClick = { selectionMode = false; selected = emptySet() }) {
                            Text("取消")
                        }
                    },
                    actions = {
                        TextButton(onClick = {
                            selected = if (selected.size == ordered.size) emptySet()
                            else ordered.map { it.id }.toSet()
                        }) { Text("全选") }
                        TextButton(onClick = { if (selected.isNotEmpty()) moveLauncher.launch(null) }) {
                            Text("移动")
                        }
                        TextButton(onClick = { if (selected.isNotEmpty()) showBatchDelete = true }) {
                            Text("删除")
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("JM 下载") },
                    actions = {
                        TextButton(onClick = { showSettings = true }) { Text("设置") }
                        if (tasks.isNotEmpty()) {
                            TextButton(onClick = { selectionMode = true }) { Text("选择") }
                        }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(8.dp)) {
                    InputRow(
                        value = input,
                        onValueChange = { input = it },
                        onAdd = {
                            val err = vm.addTask(input)
                            if (err == null) {
                                input = ""
                                focus.clearFocus()
                            } else {
                                scope.launch { snackbar.showSnackbar(err) }
                            }
                        },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (tasks.isEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        "还没有任务。输入车号点“添加”，会先记录到列表再逐个下载。",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(ordered, key = { it.id }) { t ->
                        TaskRow(
                            t = t,
                            selectionMode = selectionMode,
                            selected = t.id in selected,
                            onOpen = {
                                if (t.status == TaskStatus.DONE && t.zipName != null) {
                                    focus.clearFocus()
                                    viewing = t
                                }
                            },
                            onToggleSelect = {
                                selected = if (t.id in selected) selected - t.id else selected + t.id
                            },
                            onEnterSelect = {
                                selectionMode = true
                                selected = selected + t.id
                            },
                            onRetry = { vm.retry(t.id) },
                            onRemove = { pendingDelete = t },
                        )
                    }
                }
            }
        }
    }
    }

    pendingDelete?.let { task ->
        DeleteDialog(
            task = task,
            canDeleteFile = task.zipName != null,
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

    if (showSettings) {
        SettingsDialog(
            saveUri = saveUri,
            onPick = { folderPicker.launch(null) },
            onReset = { vm.clearSaveUri() },
            onOpenBackground = { showBackground = true },
            onDismiss = { showSettings = false },
        )
    }

    if (showBackground) {
        BackgroundDialog(
            onPickImage = { bgImagePicker.launch(arrayOf("image/*")) },
            onColor = { vm.setBgColor(it) },
            onReset = { vm.clearBg() },
            onDismiss = { showBackground = false },
        )
    }

    if (showBatchDelete) {
        val anyFile = tasks.any { it.id in selected && it.zipName != null }
        BatchDeleteDialog(
            count = selected.size,
            canDeleteFile = anyFile,
            onDismiss = { showBatchDelete = false },
            onTaskOnly = {
                vm.removeTasks(selected, false)
                showBatchDelete = false
                selectionMode = false
                selected = emptySet()
            },
            onWithFile = {
                vm.removeTasks(selected, true)
                showBatchDelete = false
                selectionMode = false
                selected = emptySet()
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
private fun BatchDeleteDialog(
    count: Int,
    canDeleteFile: Boolean,
    onDismiss: () -> Unit,
    onTaskOnly: () -> Unit,
    onWithFile: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除选中的 $count 项") },
        text = {
            Text(
                if (canDeleteFile)
                    "“只删任务”仅从列表移除，保留已保存的 zip；\n“连文件一起删”会把这些任务的 zip 一并删掉（不可恢复）。"
                else
                    "从列表中删除选中的 $count 项？"
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun SettingsDialog(
    saveUri: String?,
    onPick: () -> Unit,
    onReset: () -> Unit,
    onOpenBackground: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置") },
        text = {
            Column {
                Text("保存位置", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    saveUri?.let { prettyUri(it) } ?: "系统下载 / JMComic（默认）",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (saveUri != null) {
                        TextButton(onClick = onReset) { Text("恢复默认") }
                        Spacer(Modifier.width(4.dp))
                    }
                    Button(onClick = onPick) {
                        Text(if (saveUri == null) "自定义目录" else "更改目录")
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("主页背景", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Button(onClick = onOpenBackground) { Text("自定义背景（颜色 / 图片）") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BackgroundDialog(
    onPickImage: () -> Unit,
    onColor: (Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val swatches = listOf(
        Color.White, Color(0xFFF2F2F2), Color(0xFFDDDDDD), Color(0xFF9E9E9E),
        Color(0xFF555555), Color.Black,
        Color(0xFFFFCDD2), Color(0xFFF8BBD0), Color(0xFFE1BEE7), Color(0xFFD1C4E9),
        Color(0xFFBBDEFB), Color(0xFFB3E5FC), Color(0xFFB2DFDB), Color(0xFFC8E6C9),
        Color(0xFFFFF9C4), Color(0xFFFFE0B2),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("主页背景") },
        text = {
            Column {
                Text("背景颜色", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    swatches.forEach { c ->
                        Box(
                            Modifier
                                .size(40.dp)
                                .background(c, RoundedCornerShape(6.dp))
                                .border(1.dp, Color(0x33000000), RoundedCornerShape(6.dp))
                                .clickable { onColor(c.toArgb()) }
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onPickImage) { Text("选图片") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onReset) { Text("恢复默认") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskRow(
    t: Task,
    selectionMode: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onToggleSelect: () -> Unit,
    onEnterSelect: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
    val openable = t.status == TaskStatus.DONE && t.zipName != null
    Card(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (selectionMode) onToggleSelect() else if (openable) onOpen() },
                onLongClick = { if (!selectionMode) onEnterSelect() },
            )
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectionMode) {
                    Checkbox(checked = selected, onCheckedChange = { onToggleSelect() })
                    Spacer(Modifier.width(4.dp))
                }
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
                if (!selectionMode) {
                    if (t.status == TaskStatus.FAILED) {
                        TextButton(onClick = onRetry) { Text("重试") }
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
    val scope = rememberCoroutineScope()
    val settings = remember { SettingsStore(context) }
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
                list == null -> Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(10.dp))
                    Text("解压中…", color = Color.White)
                }
                list.isEmpty() -> Text(
                    "没有可显示的图片",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> {
                    val pager = rememberPagerState(pageCount = { list.size })
                    val colState = rememberLazyListState()
                    var controls by remember(task.id) { mutableStateOf(false) }
                    var webtoon by remember { mutableStateOf(settings.webtoonMode) }
                    val noRipple = remember { MutableInteractionSource() }
                    val toggle = Modifier.clickable(
                        interactionSource = noRipple, indication = null,
                    ) { controls = !controls }

                    // keep the two reading modes in sync on switch
                    LaunchedEffect(webtoon) {
                        if (webtoon) colState.scrollToItem(pager.currentPage)
                        else pager.scrollToPage(colState.firstVisibleItemIndex)
                    }

                    if (webtoon) {
                        LazyColumn(state = colState, modifier = Modifier.fillMaxSize()) {
                            items(list) { file ->
                                AsyncImage(
                                    model = file,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxWidth().then(toggle),
                                    contentScale = ContentScale.FillWidth,
                                )
                            }
                        }
                    } else {
                        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                            AsyncImage(
                                model = list[page],
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().then(toggle),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }

                    val currentIndex =
                        if (webtoon) colState.firstVisibleItemIndex else pager.currentPage

                    // Top bar: 关闭 + 页码
                    AnimatedVisibility(
                        visible = controls,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.align(Alignment.TopCenter),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(Color(0xCC000000))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = onClose) { Text("关闭", color = Color.White) }
                            Spacer(Modifier.weight(1f))
                            Text(
                                "${currentIndex + 1} / ${list.size}",
                                color = Color.White,
                                modifier = Modifier.padding(end = 12.dp),
                            )
                        }
                    }

                    // Bottom panel: 阅读方式 + 缩略图 + 滑动条
                    AnimatedVisibility(
                        visible = controls,
                        enter = slideInVertically(animationSpec = tween(160)) { it },
                        exit = slideOutVertically(animationSpec = tween(160)) { it },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    ) {
                        ViewerControls(
                            list = list,
                            currentIndex = currentIndex,
                            webtoon = webtoon,
                            onModeChange = { webtoon = it; settings.webtoonMode = it },
                            onGoTo = { i ->
                                scope.launch {
                                    if (webtoon) colState.scrollToItem(i) else pager.scrollToPage(i)
                                }
                            },
                        )
                    }
                }
            }
            // Close affordance for loading / error states (image view uses the top bar).
            if (files == null || error != null) {
                TextButton(
                    onClick = onClose,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                ) { Text("关闭", color = Color.White) }
            }
        }
    }
}

@Composable
private fun ViewerControls(
    list: List<File>,
    currentIndex: Int,
    webtoon: Boolean,
    onModeChange: (Boolean) -> Unit,
    onGoTo: (Int) -> Unit,
) {
    val rowState = rememberLazyListState()
    val thumbPx = with(LocalDensity.current) { 46.dp.toPx() }.toInt()
    LaunchedEffect(currentIndex) {
        val i = currentIndex.coerceAtLeast(0)
        val li = rowState.layoutInfo
        val viewport = li.viewportEndOffset - li.viewportStartOffset
        // Center the current thumbnail; head/tail clamp automatically.
        val pad = ((viewport - thumbPx) / 2).coerceAtLeast(0)
        runCatching { rowState.scrollToItem(i, -pad) }
    }
    var showJump by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xCC000000))
            .pointerInput(Unit) { detectTapGestures { } } // swallow taps on the panel
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onModeChange(false) }) {
                Text("翻页", color = if (!webtoon) Color.White else Color(0x88FFFFFF))
            }
            TextButton(onClick = { onModeChange(true) }) {
                Text("条漫", color = if (webtoon) Color.White else Color(0x88FFFFFF))
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showJump = true }) { Text("跳转", color = Color.White) }
        }
        LazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
            itemsIndexed(list) { index, file ->
                val selected = index == currentIndex
                AsyncImage(
                    model = file,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(46.dp, 62.dp)
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) Color(0xFF2979FF) else Color(0x33FFFFFF),
                        )
                        .clickable { onGoTo(index) },
                )
            }
        }
        if (list.size > 1) {
            Slider(
                value = currentIndex.toFloat(),
                onValueChange = { v -> onGoTo(v.roundToInt()) },
                valueRange = 0f..(list.size - 1).toFloat(),
            )
        }
    }

    if (showJump) {
        var jumpInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showJump = false },
            title = { Text("跳转到第几页") },
            text = {
                OutlinedTextField(
                    value = jumpInput,
                    onValueChange = { s -> jumpInput = s.filter { it.isDigit() }.take(6) },
                    singleLine = true,
                    label = { Text("1 - ${list.size}") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    jumpInput.toIntOrNull()?.let { onGoTo((it - 1).coerceIn(0, list.size - 1)) }
                    showJump = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showJump = false }) { Text("取消") } },
        )
    }
}

private fun isImageName(name: String): Boolean {
    val n = name.lowercase()
    return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") ||
        n.endsWith(".webp") || n.endsWith(".gif") || n.endsWith(".bmp")
}

/** Extract image entries from the task's saved zip into cache, preserving zip order. */
private fun extractForView(context: Context, task: Task): List<File> {
    val outDir = File(context.cacheDir, "view/${task.id}")
    File(context.cacheDir, "view").deleteRecursively()
    outDir.mkdirs()

    val ins = Storage.openInput(context, task)
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
