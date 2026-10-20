package com.jmdl.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.jmdl.data.Task
import com.jmdl.util.AppLogger

@Composable
private fun CompactDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: (@Composable () -> Unit)?,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = DialogShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)) {
                Box(modifier = Modifier.padding(horizontal = 24.dp)) {
                    title()
                }
                if (text != null) {
                    Box(
                        modifier = Modifier
                            .padding(start = 24.dp, end = 14.dp, top = 8.dp, bottom = 4.dp)
                    ) {
                        text()
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}

@Composable
internal fun DeleteDialog(
    task: Task,
    canDeleteFile: Boolean,
    onDismiss: () -> Unit,
    onTaskOnly: () -> Unit,
    onWithFile: () -> Unit,
) {
    val scrollState = rememberScrollState()
    CompactDialog(
        onDismissRequest = onDismiss,
        title = { 
            val label = if (task.jmId.all { it.isDigit() }) "JM${task.jmId}" else (task.title ?: task.jmId)
            Text("删除「$label」") 
        },
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
internal fun BatchDeleteDialog(
    count: Int,
    canDeleteFile: Boolean,
    onDismiss: () -> Unit,
    onTaskOnly: () -> Unit,
    onWithFile: () -> Unit,
) {
    CompactDialog(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsDialog(
    saveUri: String?,
    themeColor: Color,
    maxConcurrent: Int,
    onPick: () -> Unit,
    onReset: () -> Unit,
    onOpenBackground: () -> Unit,
    onShowLog: () -> Unit,
    onImportLocal: () -> Unit,
    onThemeColor: () -> Unit,
    onShowRules: () -> Unit,
    onMaxConcurrent: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val scrollState = rememberScrollState()
    CompactDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置") },
        text = {
            Box(
                modifier = Modifier
                    .height(320.dp)
                    .padding(end = 10.dp)
                    .scrollHints(scrollState),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 10.dp)
                        .verticalScroll(scrollState),
                ) {
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
                Spacer(Modifier.height(10.dp))
                Text("本地导入", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onImportLocal,
                    colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                ) { Text("导入本地压缩包（ZIP）") }
                Spacer(Modifier.height(10.dp))
                Text("同时下载任务数", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                var dropdownExpanded by remember { mutableStateOf(false) }
                val options = (1..5).toList()
                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = it },
                ) {
                    OutlinedTextField(
                        value = "$maxConcurrent",
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .width(240.dp)
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                            focusedBorderColor = themeColor,
                        ),
                    )
                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                    ) {
                        options.forEach { option ->
                            DropdownMenuItem(
                                text = { Text("$option") },
                                onClick = {
                                    onMaxConcurrent(option)
                                    dropdownExpanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("主页背景", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onOpenBackground,
                    colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                ) { Text("自定义背景（颜色 / 图片）") }
                Spacer(Modifier.height(10.dp))
                Text("主题色", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(24.dp)
                            .background(themeColor, ChipShape)
                            .border(1.dp, BorderSubtleDark, ChipShape)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onThemeColor,
                        colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                    ) { Text("选择主题色") }
                }
                Spacer(Modifier.height(8.dp))
                Text("规则说明", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onShowRules,
                    colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                ) { Text("规则说明") }
                Spacer(Modifier.height(8.dp))
                Text("日志", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onShowLog,
                    colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                ) { Text("查看日志") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
internal fun InputRulesDialog(onDismiss: () -> Unit) {
    val scrollState = rememberScrollState()
    val rules = """
输入框默认弹出数字键盘，支持以下输入方式：

1. 单个编号
   直接输入数字，如：350234
   带 JM 前缀，如：JM350234（JM 不分大小写，jm350234 同样有效）
   JM 和数字间可有空格，如：JM 350234

2. 多个编号（分隔符）
   中英文逗号或分号分隔，如：
   350234, JM350235; JM 350236
   每个编号可以是纯数字或带 JM 前缀

3. 粘连编号（无分隔符）
   多个 JM 编号连在一起会自动拆分，如：
   JM428980JM433633JM435515
   → 识别为 428980、433633、435515

4. 文本提取
   输入带中文描述的文本，自动提取所有数字拼接为一个编号：
   "老师们东方绀珠传游玩12次撞残50次重开558次成功通关" → 1250558

5. 自动去重
   同一编号出现多次只添加一次

Tab 显示规则：

1. 全部
    显示已下载完成和下载失败的任务，不显示排队中或正在下载的任务

2. 未读
    显示已下载完成但尚未打开阅读的任务。进入未读 Tab 时会固定当前未读列表，避免阅读返回后任务位置变化

3. 已下载
    显示已下载完成的任务，包括部分图片下载失败但已经打包保存的任务

4. 下载中
    显示排队中、下载中、打包中和已暂停的任务

5. 下载失败
    显示下载失败的任务，以及已下载完成但有图片下载失败的任务
    """.trimIndent()

    CompactDialog(
        onDismissRequest = onDismiss,
        title = { Text("规则说明") },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .padding(end = 10.dp)
                    .scrollHints(scrollState),
            ) {
                Text(
                    rules,
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 10.dp)
                        .verticalScroll(scrollState),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } },
    )
}

@Composable
private fun Modifier.scrollHints(scrollState: ScrollState): Modifier {
    val density = LocalDensity.current
    val indicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    val fadeHeight = with(density) { 18.dp.toPx() }
    val thumbMinHeight = with(density) { 24.dp.toPx() }
    val thumbWidth = with(density) { 3.dp.toPx() }
    val thumbInset = with(density) { 8.dp.toPx() }
    return drawWithContent {
        drawContent()
        if (scrollState.maxValue > 0) {
            val contentHeight = size.height + scrollState.maxValue
            val thumbHeight = (size.height * size.height / contentHeight)
                .coerceAtLeast(thumbMinHeight)
                .coerceAtMost(size.height)
            val scrollFraction = scrollState.value.toFloat() / scrollState.maxValue
            val thumbTop = (size.height - thumbHeight) * scrollFraction
            val right = size.width - thumbWidth - thumbInset

            if (scrollState.value > 0) {
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.Transparent, indicatorColor.copy(alpha = 0.12f))
                    ),
                    topLeft = Offset.Zero,
                    size = Size(size.width, fadeHeight),
                )
            }
            if (scrollState.value < scrollState.maxValue) {
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(indicatorColor.copy(alpha = 0.12f), Color.Transparent)
                    ),
                    topLeft = Offset(0f, size.height - fadeHeight),
                    size = Size(size.width, fadeHeight),
                )
            }
            drawRoundRect(
                color = indicatorColor,
                topLeft = Offset(right, thumbTop),
                size = Size(thumbWidth, thumbHeight),
                cornerRadius = CornerRadius(thumbWidth, thumbWidth),
            )
        }
    }
}

@Composable
internal fun LogDialog(onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(AppLogger.snapshot().joinToString(separator = "\n")) }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        text = AppLogger.snapshot().joinToString(separator = "\n")
    }
    CompactDialog(
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
                .clip(SmallShape)
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
                .background(previewColor, TinyShape)
                .border(1.dp, BorderSubtleDark, TinyShape)
        )
    }
}

@Composable
internal fun ColorPickerDialog(
    title: String,
    initialColor: Color,
    onColor: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var color by remember { mutableStateOf(initialColor) }
    CompactDialog(
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

private fun prettyUri(uri: String): String {
    val decoded = Uri.decode(uri)
    val afterColon = decoded.substringAfterLast(':', decoded)
    return afterColon.ifBlank { decoded }
}

@Composable
internal fun BackgroundDialog(
    themeColor: Color,
    onPickImage: () -> Unit,
    onColor: (Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showColorPicker by remember { mutableStateOf(false) }
    CompactDialog(
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
