package com.jmdl.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jmdl.data.Task
import com.jmdl.data.TaskStatus
import com.jmdl.download.DownloadService
import com.jmdl.util.AppLogger
import kotlinx.coroutines.launch
import android.os.Build
import android.provider.MediaStore
import android.content.ContentValues
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Surface

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.alpha
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import kotlinx.coroutines.withContext

enum class TaskFilter {
    ALL, UNREAD, DONE, RUNNING, FAILED
}

internal val LocalThemeColor = staticCompositionLocalOf { Color(0xFF2979FF) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JmApp(vm: TaskViewModel) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val saveUri by vm.saveUri.collectAsStateWithLifecycle()
    val lastViewedId by vm.lastViewedTaskId.collectAsStateWithLifecycle()
    val importResult by vm.importResult.collectAsStateWithLifecycle()
    val themeColor by vm.themeColor.collectAsStateWithLifecycle()
    val maxConcurrent by vm.maxConcurrent.collectAsStateWithLifecycle()
    val cardSurface = appCardSurfaceColor()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val snackbar = remember { SnackbarHostState() }
    var input by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<Task?>(null) }
    var viewing by remember { mutableStateOf<Task?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var showBatchDelete by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showBackground by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }
    var showThemeColor by remember { mutableStateOf(false) }
    var showInputRules by remember { mutableStateOf(false) }
    var currentFilter by remember { mutableStateOf(TaskFilter.ALL) }
    var currentTab by remember { mutableIntStateOf(0) }
    var cropOutputUri by remember { mutableStateOf<Uri?>(null) }
    val listState = rememberLazyListState()
    val stickyUnreadIds = remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(currentFilter) {
        if (currentFilter == TaskFilter.UNREAD) {
            stickyUnreadIds.value = tasks.filter { it.status == TaskStatus.DONE && it.currentPage == null }.map { it.id }.toSet()
        } else {
            stickyUnreadIds.value = emptySet()
        }
    }
    
    LaunchedEffect(importResult) {
        importResult?.let { result ->
            val message = buildString {
                append("导入完成: ")
                if (result.success > 0) append("成功 ${result.success} 个")
                if (result.failed > 0) append(if (result.success > 0) ", " else "").append("失败 ${result.failed} 个")
                if (result.skipped > 0) append(if (result.success > 0 || result.failed > 0) ", " else "").append("重复跳过 ${result.skipped} 个")
            }
            snackbar.showSnackbar(message)
            vm.clearImportResult()
        }
    }

    val tabCounts = remember(tasks) {
        val all = tasks.count { it.status == TaskStatus.DONE || it.status == TaskStatus.FAILED }
        val unread = tasks.count { it.status == TaskStatus.DONE && it.currentPage == null }
        val done = tasks.count { it.status == TaskStatus.DONE }
        val running = tasks.count { it.status in setOf(TaskStatus.RUNNING, TaskStatus.ZIPPING, TaskStatus.QUEUED, TaskStatus.PAUSED) }
        val failed = tasks.count { it.status == TaskStatus.FAILED || (it.status == TaskStatus.DONE && it.failed > 0) }
        mapOf(TaskFilter.ALL to all, TaskFilter.UNREAD to unread, TaskFilter.DONE to done, TaskFilter.RUNNING to running, TaskFilter.FAILED to failed)
    }

    val filteredTasks = remember(tasks, currentFilter, stickyUnreadIds.value) {
        val list = tasks.filter { task ->
            when (currentFilter) {
                TaskFilter.ALL -> task.status == TaskStatus.DONE || task.status == TaskStatus.FAILED
                TaskFilter.UNREAD -> task.id in stickyUnreadIds.value
                TaskFilter.DONE -> task.status == TaskStatus.DONE
                TaskFilter.RUNNING -> task.status == TaskStatus.RUNNING || task.status == TaskStatus.ZIPPING || task.status == TaskStatus.QUEUED || task.status == TaskStatus.PAUSED
                TaskFilter.FAILED -> task.status == TaskStatus.FAILED || (task.status == TaskStatus.DONE && task.failed > 0)
            }
        }
        if (currentFilter == TaskFilter.RUNNING) {
            list.sortedBy { if (it.status == TaskStatus.PAUSED) Long.MAX_VALUE else it.createdAt }
        } else {
            list.sortedByDescending { it.finishedAt ?: it.createdAt }
        }
    }

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

    val cropImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = cropOutputUri ?: result.data?.data
        cropOutputUri = null
        if (result.resultCode == android.app.Activity.RESULT_OK && uri != null) {
            scope.launch(Dispatchers.IO) {
                val localUri = copyToAppStorage(context, uri)
                if (localUri != null) {
                    withContext(Dispatchers.Main) {
                        vm.setBgImage(localUri)
                    }
                }
                // 清理 MediaStore 中作为裁剪输出的临时文件，避免污染相册
                runCatching { context.contentResolver.delete(uri, null, null) }
            }
        }
    }

    val bgImagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "bg_crop_${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            }
            val outputUri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
            )
            cropOutputUri = outputUri

            val metrics = context.resources.displayMetrics
            val screenW = metrics.widthPixels
            val screenH = metrics.heightPixels
            val maxDim = 1200
            val ratio = screenW.toFloat() / screenH.toFloat()
            val outW: Int
            val outY: Int
            if (screenW >= screenH) {
                outW = maxDim
                outY = (maxDim / ratio).toInt()
            } else {
                outY = maxDim
                outW = (maxDim * ratio).toInt()
            }

            val cropIntent = Intent("com.android.camera.action.CROP")
            cropIntent.setDataAndType(uri, "image/*")
            cropIntent.putExtra("crop", "true")
            cropIntent.putExtra("aspectX", screenW)
            cropIntent.putExtra("aspectY", screenH)
            cropIntent.putExtra("outputX", outW)
            cropIntent.putExtra("outputY", outY)
            cropIntent.putExtra("return-data", false)
            cropIntent.putExtra("outputFormat", "JPEG")
            if (outputUri != null) {
                cropIntent.putExtra(MediaStore.EXTRA_OUTPUT, outputUri)
            }
            cropIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            cropImageLauncher.launch(cropIntent)
        }
    }

    val localImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri>? ->
        if (!uris.isNullOrEmpty()) {
            showSettings = false
            currentFilter = TaskFilter.RUNNING
            vm.importLocalZips(uris)
            scope.launch { snackbar.showSnackbar("正在导入 ${uris.size} 个文件…") }
        }
    }

    val bg = vm.background.collectAsStateWithLifecycle().value
    val bgImage = bg.imageUri
    val bgColor = bg.color

    LaunchedEffect(lastViewedId) {
        if (lastViewedId != null) {
            val idx = filteredTasks.indexOfFirst { it.id == lastViewedId }
            if (idx >= 0) {
                listState.animateScrollToItem(idx)
            }
        }
    }

    val prevTaskCount = remember { mutableStateOf(0) }
    val prevFilter = remember { mutableStateOf(currentFilter) }
    LaunchedEffect(filteredTasks.size, currentFilter) {
        val current = filteredTasks.size
        if (currentFilter != prevFilter.value) {
            listState.animateScrollToItem(0)
        } else if (current > prevTaskCount.value) {
            listState.animateScrollToItem(0)
        } else if (current < prevTaskCount.value && current > 0) {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            if (lastVisible < 0 || listState.firstVisibleItemIndex + listState.layoutInfo.visibleItemsInfo.size > current) {
                listState.animateScrollToItem(current - 1)
            }
        }
        prevTaskCount.value = current
        prevFilter.value = currentFilter
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (bgImage != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(Uri.parse(bgImage))
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else if (bgColor != null) {
            Box(Modifier.fillMaxSize().background(Color(bgColor)))
        }

        CompositionLocalProvider(LocalThemeColor provides themeColor) {
        Scaffold(
            containerColor = if (bgImage != null || bgColor != null) Color.Transparent else MaterialTheme.colorScheme.background,
            topBar = {
                if (selectionMode) {
                    TopAppBar(
                        title = { Text("已选 ${selected.size}") },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = cardSurface
                        ),
                        navigationIcon = {
                            TextButton(onClick = { selectionMode = false; selected = emptySet() }) {
                                Text("取消")
                            }
                        },
                        actions = {
                            TextButton(onClick = {
                                selected = if (selected.size == filteredTasks.size) emptySet()
                                else filteredTasks.map { it.id }.toSet()
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
                        title = { Text(if (currentTab == 0) "列表" else "搜索") },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = cardSurface
                        ),
                        actions = {
                            TextButton(onClick = { showSettings = true }) { Text("设置") }
                            if (currentTab == 0 && tasks.isNotEmpty()) {
                                TextButton(onClick = { selectionMode = true }) { Text("选择") }
                            }
                        },
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar(
                    containerColor = cardSurface,
                ) {
                    NavigationBarItem(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                        label = { Text("列表") }
                    )
                    NavigationBarItem(
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                        icon = { Icon(Icons.Default.Search, contentDescription = null) },
                        label = { Text("搜索") }
                    )
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
            Column(
                Modifier.fillMaxSize()
                    .padding(start = 12.dp, end = 12.dp, top = 12.dp)
                    .zIndex(if (currentTab == 0) 1f else 0f)
                    .alpha(if (currentTab == 0) 1f else 0f)
            ) {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = appCardColors()
                ) {
                    Column(Modifier.padding(8.dp)) {
                        InputRow(
                            value = input,
                            onValueChange = { input = it },
                            onAdd = {
                                val err = vm.addTask(input)
                                currentFilter = TaskFilter.RUNNING
                                if (err == null) {
                                    input = ""
                                    focus.clearFocus()
                                } else {
                                    input = ""
                                    focus.clearFocus()
                                    scope.launch { snackbar.showSnackbar(err) }
                                }
                            },
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                FilterTabs(
                    currentFilter = currentFilter,
                    tabCounts = tabCounts,
                    onFilterChange = { currentFilter = it }
                )

                Spacer(Modifier.height(12.dp))

                if (filteredTasks.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp)
                            .background(cardSurface, CardShape)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            if (tasks.isEmpty()) "还没有任务"
                            else "暂无数据",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (tasks.isEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "输入车号点“添加”开始下载",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 12.dp),
                        ) {
                            items(filteredTasks, key = { it.id }) { t ->
                                TaskRow(
                                    t = t,
                                    isLastViewed = t.id == lastViewedId,
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
                                    onCopyText = { text ->
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("text", text))
                                        scope.launch { snackbar.showSnackbar("已复制") }
                                    },
                                    onRequestAuthors = { vm.ensureAuthors(t.id) },
                                    onRetry = { vm.retry(t.id) },
                                    onPause = { vm.pause(t.id) },
                                    onResume = { vm.resume(t.id) },
                                    onRemove = { pendingDelete = t },
                                )
                            }
                        }

                        if (currentFilter == TaskFilter.RUNNING && filteredTasks.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Button(
                                    onClick = { vm.pauseAll() },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    ),
                                ) {
                                    Text("全部暂停")
                                }
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    onClick = { vm.resumeAll() },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                                ) {
                                    Text("全部继续")
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                        if (currentFilter == TaskFilter.FAILED && filteredTasks.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Button(
                                    onClick = {
                                        filteredTasks.forEach { vm.remove(it.id) }
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    ),
                                ) {
                                    Text("全部清除")
                                }
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        vm.retryAll(filteredTasks.map { it.id })
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                                ) {
                                    Text("全部重试")
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            }
            Box(
                Modifier.fillMaxSize()
                    .zIndex(if (currentTab == 1) 1f else 0f)
                    .alpha(if (currentTab == 1) 1f else 0f)
            ) {
                SearchScreen(vm = vm, snackbar = { scope.launch { snackbar.showSnackbar(it) } })
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
            themeColor = themeColor,
            maxConcurrent = maxConcurrent,
            onPick = {
                showSettings = false
                folderPicker.launch(null)
            },
            onReset = { vm.clearSaveUri() },
            onOpenBackground = {
                showSettings = false
                showBackground = true
            },
            onShowLog = { showLog = true },
            onImportLocal = {
                showSettings = false
                localImportLauncher.launch(arrayOf("application/zip"))
            },
            onThemeColor = { showThemeColor = true },
            onShowRules = { showInputRules = true },
            onMaxConcurrent = { vm.setMaxConcurrent(it) },
            onDismiss = { showSettings = false },
        )
    }

    if (showLog) {
        LogDialog(
            onDismiss = { showLog = false },
        )
    }

    if (showInputRules) {
        InputRulesDialog(
            onDismiss = { showInputRules = false },
        )
    }

    if (showBackground) {
        BackgroundDialog(
            themeColor = themeColor,
            onPickImage = { showBackground = false; bgImagePicker.launch(arrayOf("image/*")) },
            onColor = { vm.setBgColor(it) },
            onReset = { vm.clearBg() },
            onDismiss = { showBackground = false },
        )
    }

    if (showThemeColor) {
        ColorPickerDialog(
            title = "选择主题色",
            initialColor = themeColor,
            onColor = { vm.setThemeColor(it) },
            onDismiss = { showThemeColor = false },
        )
    }

    if (showBatchDelete) {
        val anyFile = filteredTasks.any { it.id in selected && it.zipName != null }
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
        ViewerScreen(task = task, vm = vm, onClose = {
            vm.setLastViewedTaskId(task.id)
            viewing = null
        }, snackbar = snackbar)
    }
}

@Composable
private fun FilterTabs(currentFilter: TaskFilter, tabCounts: Map<TaskFilter, Int>, onFilterChange: (TaskFilter) -> Unit) {
    val themeColor = LocalThemeColor.current
    val surfaceColor = appCardSurfaceColor()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawWithContent {
                drawRoundRect(
                    color = surfaceColor,
                    cornerRadius = CornerRadius(12.dp.toPx())
                )
                drawContent()
            }
    ) {
        val tabs = listOf(
            TaskFilter.ALL to "全部",
            TaskFilter.UNREAD to "未读",
            TaskFilter.DONE to "已下载",
            TaskFilter.RUNNING to "下载中",
            TaskFilter.FAILED to "下载失败"
        )
        tabs.forEach { (filter, label) ->
            val isSelected = currentFilter == filter
            val count = tabCounts[filter] ?: 0
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onFilterChange(filter) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (isSelected) themeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = if (isSelected) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
                    maxLines = 1,
                    softWrap = false,
                )
                if (count > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 10.dp, y = (-6).dp)
                            .background(
                                color = if (isSelected) themeColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                shape = SmallShape
                            )
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$count",
                            color = MaterialTheme.colorScheme.onError,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun InputRow(value: String, onValueChange: (String) -> Unit, onAdd: () -> Unit) {
    val themeColor = LocalThemeColor.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("JM号快捷下载", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
            singleLine = true,
            leadingIcon = { Text("JM", style = MaterialTheme.typography.titleMedium) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, keyboardType = KeyboardType.Number),
            keyboardActions = KeyboardActions(onDone = { onAdd() }),
        )
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = onAdd,
            enabled = value.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = themeColor),
        ) { Text("添加") }
    }
}


private fun copyToAppStorage(context: Context, sourceUri: Uri): Uri? {
    return runCatching {
        val dir = File(context.filesDir, "background")
        if (!dir.exists()) dir.mkdirs()
        dir.listFiles()?.filter { it.name.startsWith("bg_") && it.extension == "jpg" }?.forEach { it.delete() }
        val destFile = File(dir, "bg_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        Uri.fromFile(destFile)
    }.getOrNull()
}
