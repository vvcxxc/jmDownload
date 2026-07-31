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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.TopAppBarDefaults
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
import com.jmdl.util.AppLogger
import kotlinx.coroutines.launch
import android.os.Build
import android.provider.MediaStore
import android.content.ContentValues
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.documentfile.provider.DocumentFile
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jmdl.data.SettingsStore
import com.jmdl.download.Storage
import java.io.File
import kotlinx.coroutines.Dispatchers
import androidx.compose.animation.core.tween
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Slider
import kotlin.math.roundToInt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import kotlinx.coroutines.withContext
import java.util.zip.ZipInputStream

enum class TaskFilter {
    ALL, DONE, RUNNING, FAILED
}

private const val CARD_ALPHA = 0.8f

private val LocalThemeColor = staticCompositionLocalOf { Color(0xFF2979FF) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JmApp(vm: TaskViewModel) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val saveUri by vm.saveUri.collectAsStateWithLifecycle()
    val lastViewedId by vm.lastViewedTaskId.collectAsStateWithLifecycle()
    val importResult by vm.importResult.collectAsStateWithLifecycle()
    val themeColor by vm.themeColor.collectAsStateWithLifecycle()
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
    var currentFilter by remember { mutableStateOf(TaskFilter.ALL) }
    var cropOutputUri by remember { mutableStateOf<Uri?>(null) }
    val listState = rememberLazyListState()
    
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

    val filteredTasks = remember(tasks, currentFilter) {
        tasks.filter { task ->
            when (currentFilter) {
                TaskFilter.ALL -> true
                TaskFilter.DONE -> task.status == TaskStatus.DONE
                TaskFilter.RUNNING -> task.status == TaskStatus.RUNNING || task.status == TaskStatus.ZIPPING || task.status == TaskStatus.QUEUED
                TaskFilter.FAILED -> task.status == TaskStatus.FAILED
            }
        }.sortedByDescending { it.createdAt }
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
            currentFilter = TaskFilter.ALL
            vm.importLocalZips(uris)
            scope.launch { snackbar.showSnackbar("正在导入 ${uris.size} 个文件…") }
        }
    }

    val bg = vm.background.collectAsStateWithLifecycle().value
    val bgImage = bg.imageUri
    val bgColor = bg.color

    LaunchedEffect(filteredTasks.firstOrNull()?.id, filteredTasks.size) {
        if (filteredTasks.isNotEmpty()) listState.animateScrollToItem(0)
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
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = CARD_ALPHA)
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
                        title = { Text("JM 下载") },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = CARD_ALPHA)
                        ),
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
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = CARD_ALPHA)
                    )
                ) {
                    Column(Modifier.padding(8.dp)) {
                        InputRow(
                            value = input,
                            onValueChange = { input = it },
                            onAdd = {
                                val err = vm.addTask(input)
                                currentFilter = TaskFilter.ALL
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
                    onFilterChange = { currentFilter = it }
                )

                Spacer(Modifier.height(12.dp))

                if (filteredTasks.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = CARD_ALPHA),
                                RoundedCornerShape(12.dp)
                            )
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
                                    onRetry = { vm.retry(t.id) },
                                    onCancel = { vm.cancel(t.id) },
                                    onRemove = { pendingDelete = t },
                                )
                            }
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
                                        filteredTasks.forEach { vm.retry(it.id) }
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
            onDismiss = { showSettings = false },
        )
    }

    if (showLog) {
        LogDialog(
            onDismiss = { showLog = false },
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
private fun FilterTabs(currentFilter: TaskFilter, onFilterChange: (TaskFilter) -> Unit) {
    val themeColor = LocalThemeColor.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = CARD_ALPHA),
                RoundedCornerShape(12.dp)
            )
    ) {
        val tabs = listOf(TaskFilter.ALL to "全部", TaskFilter.DONE to "已下载", TaskFilter.RUNNING to "下载中", TaskFilter.FAILED to "下载失败")
        tabs.forEach { (filter, label) ->
            val isSelected = currentFilter == filter
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
                    style = if (isSelected) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                )
            }
        }
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
    themeColor: Color,
    onPick: () -> Unit,
    onReset: () -> Unit,
    onOpenBackground: () -> Unit,
    onShowLog: () -> Unit,
    onImportLocal: () -> Unit,
    onThemeColor: () -> Unit,
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
                    Button(
                        onClick = onPick,
                        colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                    ) {
                        Text(if (saveUri == null) "自定义目录" else "更改目录")
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("本地导入", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onImportLocal,
                    colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                ) { Text("导入本地压缩包（ZIP）") }
                Spacer(Modifier.height(16.dp))
                Text("主页背景", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onOpenBackground,
                    colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                ) { Text("自定义背景（颜色 / 图片）") }
                Spacer(Modifier.height(16.dp))
                Text("主题色", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(24.dp)
                            .background(themeColor, RoundedCornerShape(4.dp))
                            .border(1.dp, Color(0x33000000), RoundedCornerShape(4.dp))
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onThemeColor,
                        colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                    ) { Text("选择主题色") }
                }
                Spacer(Modifier.height(12.dp))
                Text("日志", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onShowLog,
                    colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                ) { Text("查看日志") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun LogDialog(onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(AppLogger.snapshot().joinToString(separator = "\n")) }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        text = AppLogger.snapshot().joinToString(separator = "\n")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("日志") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().height(320.dp),
                readOnly = true,
                singleLine = false,
            )
        },
        confirmButton = {
            Row {
                TextButton(onClick = { AppLogger.clear(); text = "" }) { Text("清空") }
                TextButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("log", text)
                    clipboard.setPrimaryClip(clip)
                }) { Text("复制") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun ColorPickerPanel(
    currentColor: Color,
    onColorChange: (Color) -> Unit,
) {
    val density = LocalDensity.current
    val hsv = remember { FloatArray(3) }
    android.graphics.Color.RGBToHSV(
        (currentColor.red * 255).toInt(),
        (currentColor.green * 255).toInt(),
        (currentColor.blue * 255).toInt(),
        hsv
    )
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var bri by remember { mutableFloatStateOf(hsv[2]) }

    var sbSize by remember { mutableStateOf(IntSize.Zero) }
    var hueSize by remember { mutableStateOf(IntSize.Zero) }

    fun updateColor(h: Float, s: Float, v: Float) {
        hue = h; sat = s; bri = v
        onColorChange(Color.hsv(h, s, v))
    }

    val sbIndicatorDp = 12.dp
    val sbIndicatorPx = with(density) { sbIndicatorDp.toPx() }.toInt()
    val hueIndicatorDp = 6.dp
    val hueIndicatorPx = with(density) { hueIndicatorDp.toPx() }.toInt()

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .onSizeChanged { sbSize = it }
                .clip(RoundedCornerShape(8.dp))
                .pointerInput(hue) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var pos = down.position
                        val s = (pos.x / size.width).coerceIn(0f, 1f)
                        val v = 1f - (pos.y / size.height).coerceIn(0f, 1f)
                        updateColor(hue, s, v)
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            pos = change.position
                            val s2 = (pos.x / size.width).coerceIn(0f, 1f)
                            val v2 = 1f - (pos.y / size.height).coerceIn(0f, 1f)
                            updateColor(hue, s2, v2)
                            change.consume()
                        } while (true)
                    }
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f))))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            }
            if (sbSize.width > 0 && sbSize.height > 0) {
                Box(
                    Modifier
                        .offset {
                            IntOffset(
                                x = (sat * (sbSize.width - sbIndicatorPx)).toInt(),
                                y = ((1f - bri) * (sbSize.height - sbIndicatorPx)).toInt(),
                            )
                        }
                        .size(sbIndicatorDp)
                        .background(Color.White, CircleShape)
                        .border(2.dp, Color.Black, CircleShape)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .height(22.dp)
                .onSizeChanged { hueSize = it }
                .clip(RoundedCornerShape(4.dp))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var pos = down.position
                        val h = (pos.x / size.width).coerceIn(0f, 1f) * 360f
                        updateColor(h, sat, bri)
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            pos = change.position
                            val h2 = (pos.x / size.width).coerceIn(0f, 1f) * 360f
                            updateColor(h2, sat, bri)
                            change.consume()
                        } while (true)
                    }
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val colors = (0..360 step 30).map { Color.hsv(it.toFloat(), 1f, 1f) }
                drawRect(Brush.horizontalGradient(colors))
            }
            if (hueSize.width > 0) {
                Box(
                    Modifier
                        .offset {
                            IntOffset(
                                x = ((hue / 360f) * (hueSize.width - hueIndicatorPx)).toInt(),
                                y = 0,
                            )
                        }
                        .width(hueIndicatorDp)
                        .height(22.dp)
                        .background(Color.White, RoundedCornerShape(3.dp))
                        .border(1.dp, Color.Black, RoundedCornerShape(3.dp))
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        val previewColor = Color.hsv(hue, sat, bri)
        Box(
            Modifier
                .size(36.dp)
                .background(previewColor, RoundedCornerShape(6.dp))
                .border(1.dp, Color(0x33000000), RoundedCornerShape(6.dp))
        )
    }
}

@Composable
private fun ColorPickerDialog(
    title: String,
    initialColor: Color,
    onColor: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var color by remember { mutableStateOf(initialColor) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            ColorPickerPanel(
                currentColor = color,
                onColorChange = { color = it },
            )
        },
        confirmButton = {
            TextButton(onClick = { onColor(color.toArgb()); onDismiss() }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun BackgroundDialog(
    themeColor: Color,
    onPickImage: () -> Unit,
    onColor: (Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showColorPicker by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("主页背景") },
        text = {
            Column {
                Text("背景颜色", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { showColorPicker = true },
                    colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                ) { Text("选择颜色") }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onPickImage,
                        colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                    ) { Text("选图片") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onReset) { Text("恢复默认") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
    if (showColorPicker) {
        ColorPickerDialog(
            title = "选择背景颜色",
            initialColor = Color.White,
            onColor = onColor,
            onDismiss = { showColorPicker = false },
        )
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
            singleLine = true,
            leadingIcon = { Text("JM", style = MaterialTheme.typography.titleMedium) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskRow(
    t: Task,
    isLastViewed: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onToggleSelect: () -> Unit,
    onEnterSelect: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
) {
    val themeColor = LocalThemeColor.current
    val openable = t.status == TaskStatus.DONE && t.zipName != null
    Card(
        Modifier
            .fillMaxWidth()
            .border(
                width = if (isLastViewed) 2.dp else 0.dp,
                color = if (isLastViewed) themeColor else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .combinedClickable(
                onClick = { if (selectionMode) onToggleSelect() else if (openable) onOpen() },
                onLongClick = { if (!selectionMode) onEnterSelect() },
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = CARD_ALPHA)
        )
    ) {
        Column(Modifier.padding(12.dp)) {
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
                    when {
                        t.pageCount != null -> Text(
                            if (t.currentPage != null) "已读 ${t.currentPage}/${t.pageCount} 页" else "共 ${t.pageCount} 页",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        t.status == TaskStatus.DONE -> Text(
                            "已完成",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
                if (!selectionMode) {
                    if (t.status == TaskStatus.FAILED || t.status == TaskStatus.CANCELLED || (t.status == TaskStatus.DONE && t.failed > 0)) {
                        TextButton(onClick = onRetry) { Text("重试") }
                    }
                    if (t.status == TaskStatus.QUEUED || t.status == TaskStatus.RUNNING || t.status == TaskStatus.ZIPPING) {
                        TextButton(onClick = onCancel) { Text("取消") }
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

@Composable
private fun ViewerScreen(task: Task, vm: TaskViewModel, onClose: () -> Unit, snackbar: SnackbarHostState) {
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
                    val initialPage = ((task.currentPage ?: 1) - 1).coerceAtLeast(0).coerceAtMost(list.size - 1)
                    val pager = rememberPagerState(initialPage = initialPage, pageCount = { list.size })
                    val colState = rememberLazyListState(initialFirstVisibleItemIndex = initialPage)
                    var controls by remember(task.id) { mutableStateOf(false) }
                    var webtoon by remember { mutableStateOf(settings.webtoonMode) }
                    val noRipple = remember { MutableInteractionSource() }
                    val toggle = Modifier.clickable(
                        interactionSource = noRipple, indication = null,
                    ) { controls = !controls }

                    LaunchedEffect(webtoon) {
                        if (webtoon) colState.scrollToItem(pager.currentPage)
                        else pager.scrollToPage(colState.firstVisibleItemIndex)
                    }

                    LaunchedEffect(initialPage) {
                        if (webtoon) colState.scrollToItem(initialPage) else pager.scrollToPage(initialPage)
                    }

                    var showImageMenu by remember { mutableStateOf(false) }
                    var imageToSave by remember { mutableStateOf<String?>(null) }

                    val saveImage = { filePath: String ->
                        val file = File(filePath)
                        if (file.exists()) {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val contentValues = ContentValues().apply {
                                        put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
                                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                            put(MediaStore.Images.Media.IS_PENDING, 1)
                                        }
                                    }
                                    
                                    val uri = context.contentResolver.insert(
                                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                        contentValues
                                    )
                                    
                                    uri?.let {
                                        context.contentResolver.openOutputStream(it)?.use { outputStream ->
                                            file.inputStream().use { inputStream ->
                                                inputStream.copyTo(outputStream)
                                            }
                                        }
                                        
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                                            context.contentResolver.update(uri, contentValues, null, null)
                                        }
                                    }
                                    
                                    scope.launch(Dispatchers.Main) {
                                        snackbar.showSnackbar("图片已保存到相册")
                                    }
                                } catch (e: Exception) {
                                    AppLogger.e("Save image failed", e)
                                    scope.launch(Dispatchers.Main) {
                                        snackbar.showSnackbar("保存失败: ${e.message}")
                                    }
                                }
                            }
                        }
                        showImageMenu = false
                    }

                    if (webtoon) {
                        LazyColumn(state = colState, modifier = Modifier.fillMaxSize()) {
                            items(list) { file ->
                                val imageModifier = Modifier.pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = { controls = !controls },
                                        onLongPress = {
                                            imageToSave = file.path
                                            showImageMenu = true
                                        }
                                    )
                                }
                                AsyncImage(
                                    model = file,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxWidth().then(imageModifier),
                                    contentScale = ContentScale.FillWidth,
                                )
                            }
                        }
                    } else {
                        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                            val imageModifier = Modifier.pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { controls = !controls },
                                    onLongPress = {
                                        imageToSave = list[page].path
                                        showImageMenu = true
                                    }
                                )
                            }
                            AsyncImage(
                                model = list[page],
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().then(imageModifier),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }

                    if (showImageMenu) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.6f))
                                .clickable { showImageMenu = false },
                            contentAlignment = Alignment.Center,
                        ) {
                            Card(
                                modifier = Modifier.padding(16.dp),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    TextButton(
                                        onClick = { imageToSave?.let { saveImage(it) } },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text("保存图片", style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                            }
                        }
                    }

                    val currentIndex =
                        if (webtoon) colState.firstVisibleItemIndex else pager.currentPage

                    LaunchedEffect(currentIndex, list.size) {
                        vm.updatePage(task.id, currentIndex + 1, list.size)
                    }

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
                                    if (webtoon) {
                                        colState.scrollToItem(i)
                                    } else {
                                        pager.scrollToPage(i)
                                    }
                                }
                            },
                        )
                    }
                }
            }
            if (files == null || error != null) {
                TextButton(
                    onClick = onClose,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                ) { Text("关闭", color = Color.White) }
            }
            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp)
            )
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
        val pad = ((viewport - thumbPx) / 2).coerceAtLeast(0)
        runCatching { rowState.scrollToItem(i, -pad) }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xCC000000))
            .pointerInput(Unit) { detectTapGestures { } }
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
                            color = if (selected) LocalThemeColor.current else Color(0x33FFFFFF),
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
}

private fun isImageName(name: String): Boolean {
    val n = name.lowercase()
    return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") ||
        n.endsWith(".webp") || n.endsWith(".gif") || n.endsWith(".bmp")
}

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
    TaskStatus.CANCELLED -> "已取消"
}

@Composable
private fun statusColor(t: Task): Color {
    val themeColor = LocalThemeColor.current
    return when (t.status) {
        TaskStatus.FAILED -> MaterialTheme.colorScheme.error
        TaskStatus.CANCELLED -> MaterialTheme.colorScheme.tertiary
        TaskStatus.DONE -> themeColor
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

private fun prettyUri(uri: String): String {
    val decoded = Uri.decode(uri)
    val afterColon = decoded.substringAfterLast(':', decoded)
    return afterColon.ifBlank { decoded }
}

private fun copyToAppStorage(context: Context, sourceUri: Uri): Uri? {
    return runCatching {
        val dir = File(context.filesDir, "background")
        if (!dir.exists()) dir.mkdirs()
        // 删除旧背景文件，避免堆积
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