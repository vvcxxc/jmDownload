package com.jmdl.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.jmdl.core.PageOrdering
import com.jmdl.data.SettingsStore
import com.jmdl.data.Task
import com.jmdl.download.Storage
import com.jmdl.util.AppLogger
import java.io.File
import java.util.zip.ZipInputStream
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ViewerScreen(task: Task, vm: TaskViewModel, onClose: () -> Unit, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { SettingsStore(context) }
    var pages by remember(task.id) { mutableStateOf<List<ViewerPage>?>(null) }
    var error by remember(task.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(task.id) {
        val r = withContext(Dispatchers.IO) { runCatching { extractForView(context, task) } }
        r.onSuccess { pages = it }.onFailure { error = it.message ?: "打开失败" }
    }
    DisposableEffect(task.id) {
        onDispose {
            // Keep the extracted pages so rotating or immediately reopening the same
            // book does not unzip it again. The cache is fingerprint-checked and pruned
            // when another book is opened.
            vm.flushPage()
        }
    }
    BackHandler { onClose() }

    Surface(Modifier.fillMaxSize(), color = Color.Black) {
        Box(Modifier.fillMaxSize()) {
            val list = pages
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
                    // Restore the saved page directly through the state initializer. Animating
                    // there from 0 in a LaunchedEffect races with the mode-sync effect below.
                    val colState = rememberLazyListState(initialFirstVisibleItemIndex = initialPage)
                    var controls by remember(task.id) { mutableStateOf(false) }
                    var webtoon by remember { mutableStateOf(settings.webtoonMode) }

                    // Only sync the two modes when the user actually switches mode. Running
                    // this on first composition would scroll the freshly restored page back
                    // to page 0.
                    var previousWebtoon by remember(task.id) { mutableStateOf(webtoon) }
                    LaunchedEffect(webtoon) {
                        if (previousWebtoon == webtoon) return@LaunchedEffect
                        val target = if (webtoon) pager.currentPage else colState.firstVisibleItemIndex
                        previousWebtoon = webtoon
                        if (webtoon) {
                            colState.scrollToItem(target.coerceIn(0, list.lastIndex))
                        } else {
                            pager.scrollToPage(target.coerceIn(0, list.lastIndex))
                        }
                    }

                    // Shared jump used by the thumbnail strip and the slider. In webtoon
                    // mode LazyColumn can settle on another index because item heights are
                    // being measured while scrolling, so verify and snap once more.
                    suspend fun goToPage(index: Int) {
                        val target = index.coerceIn(0, list.lastIndex)
                        if (webtoon) {
                            val current = colState.firstVisibleItemIndex
                            // Every page has its own measured height. For far jumps,
                            // animateScrollToItem would travel across unmeasured items
                            // and can drift; an index-anchor jump is exact. Only use
                            // the animation for nearby pages.
                            if (abs(target - current) > 20) {
                                colState.scrollToItem(target)
                            } else {
                                colState.animateScrollToItem(target)
                            }
                            if (colState.firstVisibleItemIndex != target ||
                                colState.firstVisibleItemScrollOffset != 0
                            ) {
                                colState.scrollToItem(target)
                            }
                        } else {
                            pager.animateScrollToPage(target)
                        }
                    }

                    var showImageMenu by remember { mutableStateOf(false) }
                    var imageToSave by remember { mutableStateOf<String?>(null) }

                    val doSaveImage: (String) -> Unit = { filePath ->
                        scope.launch {
                            try {
                                saveImageToGallery(context, File(filePath))
                                snackbar.showSnackbar("图片已保存到相册")
                            } catch (e: Exception) {
                                AppLogger.e("Save image failed", e)
                                snackbar.showSnackbar("保存失败: ${e.message}")
                            }
                        }
                    }
                    var pendingImageSave by remember { mutableStateOf<String?>(null) }
                    val savePermissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { granted ->
                        val path = pendingImageSave
                        pendingImageSave = null
                        if (granted && path != null) {
                            doSaveImage(path)
                        } else {
                            scope.launch { snackbar.showSnackbar("没有存储权限，无法保存图片") }
                        }
                    }

                    val saveImage = { filePath: String ->
                        val file = File(filePath)
                        if (file.exists()) {
                            val needsLegacyPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                                ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.WRITE_EXTERNAL_STORAGE
                                ) != PackageManager.PERMISSION_GRANTED
                            if (needsLegacyPermission) {
                                pendingImageSave = filePath
                                savePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            } else {
                                doSaveImage(filePath)
                            }
                        }
                        showImageMenu = false
                    }

                    if (webtoon) {
                        BoxWithConstraints(Modifier.fillMaxSize()) {
                            // Guarantee that the last page can scroll all the way to the top.
                            // Otherwise the list is clamped near the end, firstVisibleItemIndex
                            // reports an earlier page, and a thumbnail jump looks like it
                            // landed on the wrong page.
                            val lastAspect = list.last().aspectRatio
                            val bottomPad = (maxHeight - maxWidth / lastAspect).coerceAtLeast(0.dp)
                            LazyColumn(
                                state = colState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = bottomPad),
                            ) {
                                items(list, key = { it.file.path }) { page ->
                                    val imageModifier = Modifier.pointerInput(Unit) {
                                        detectTapGestures(
                                            onTap = { controls = !controls },
                                            onLongPress = {
                                                imageToSave = page.file.path
                                                showImageMenu = true
                                            }
                                        )
                                    }
                                    AsyncImage(
                                        model = page.file,
                                        contentDescription = null,
                                        // Knowing the aspect ratio up front keeps item
                                        // heights stable while images decode, which makes
                                        // scrollToItem/ animateScrollToItem land accurately.
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(page.aspectRatio)
                                            .then(imageModifier),
                                        contentScale = ContentScale.FillWidth,
                                    )
                                }
                            }
                        }
                    } else {
                        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                            val imageModifier = Modifier.pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { controls = !controls },
                                    onLongPress = {
                                        imageToSave = list[page].file.path
                                        showImageMenu = true
                                    }
                                )
                            }
                            AsyncImage(
                                model = list[page].file,
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
                                shape = CardShape,
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

                    val currentIndex = (if (webtoon) colState.firstVisibleItemIndex else pager.currentPage)
                        .coerceIn(0, list.lastIndex)

                    // Persist/save progress only after the reader settles. Updating the
                    // whole task list on every frame of a pager fling/continuous scroll
                    // made the reader janky and could write tasks.json dozens of times.
                    LaunchedEffect(webtoon, list.size) {
                        snapshotFlow {
                            (if (webtoon) colState.firstVisibleItemIndex else pager.settledPage)
                                .coerceIn(0, list.lastIndex)
                        }.collectLatest { index ->
                            delay(250)
                            vm.updatePage(task.id, index + 1, list.size)
                        }
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
                                .background(OverlayScrim)
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = onClose) { Text("关闭", color = Color.White) }
                            val title = task.title ?: "JM${task.jmId}"
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    title,
                                    color = Color.White,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onLongPress = {
                                                    clipboard.setPrimaryClip(ClipData.newPlainText("title", title))
                                                    scope.launch { snackbar.showSnackbar("已复制标题") }
                                                }
                                            )
                                        },
                                )
                            }
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
                            onGoTo = { i -> scope.launch { goToPage(i) } },
                            onGoHome = { scope.launch { goToPage(0) } },
                        )
                    }
                }
            }
            if (pages == null || error != null) {
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
    list: List<ViewerPage>,
    currentIndex: Int,
    webtoon: Boolean,
    onModeChange: (Boolean) -> Unit,
    onGoTo: (Int) -> Unit,
    onGoHome: () -> Unit,
) {
    val rowState = rememberLazyListState()
    val thumbPx = with(LocalDensity.current) { ViewerThumbWidth.toPx() }.toInt()
    var sliderDragging by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(currentIndex.toFloat()) }
    var showJump by remember { mutableStateOf(false) }
    LaunchedEffect(currentIndex) {
        if (!sliderDragging) sliderValue = currentIndex.toFloat()
        delay(80)
        val i = currentIndex.coerceAtLeast(0)
        val li = rowState.layoutInfo
        val viewport = li.viewportEndOffset - li.viewportStartOffset
        val pad = ((viewport - thumbPx) / 2).coerceAtLeast(0)
        runCatching { rowState.scrollToItem(i, -pad) }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(OverlayScrim)
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onModeChange(false) }) {
                Text("翻页", color = if (!webtoon) Color.White else TextMutedOnBlack)
            }
            TextButton(onClick = { onModeChange(true) }) {
                Text("条漫", color = if (webtoon) Color.White else TextMutedOnBlack)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onGoHome) {
                Text("首页", color = TextSecondaryOnBlack)
            }
            TextButton(onClick = { showJump = true }) {
                Text("跳转", color = TextSecondaryOnBlack)
            }
        }
        LazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
            itemsIndexed(list, key = { _, page -> page.file.path }) { index, page ->
                val selected = index == currentIndex
                AsyncImage(
                    model = page.file,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(ViewerThumbWidth, ViewerThumbHeight)
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) LocalThemeColor.current else BorderSubtleOnBlack,
                        )
                        .clickable { onGoTo(index) },
                )
            }
        }
        if (list.size > 1) {
            Slider(
                value = sliderValue.coerceIn(0f, (list.size - 1).toFloat()),
                onValueChange = { v ->
                    // Follow the finger locally; jumping on every pixel would launch a
                    // new pager animation per frame and make the page land unpredictably.
                    sliderDragging = true
                    sliderValue = v
                },
                onValueChangeFinished = {
                    sliderDragging = false
                    onGoTo(sliderValue.roundToInt())
                },
                valueRange = 0f..(list.size - 1).toFloat(),
            )
        }
    }

    if (showJump) {
        val maxPage = list.size
        var jumpText by remember { mutableStateOf((currentIndex + 1).toString()) }
        val targetPage = jumpText.toIntOrNull()
        val valid = targetPage != null && targetPage in 1..maxPage
        AlertDialog(
            onDismissRequest = { showJump = false },
            title = { Text("跳转页码") },
            text = {
                Column {
                    Text("当前 ${currentIndex + 1} / $maxPage")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = jumpText,
                        onValueChange = { value ->
                            jumpText = value.filter { it.isDigit() }.take(7)
                        },
                        singleLine = true,
                        label = { Text("页码") },
                        supportingText = { Text("可输入 1 - $maxPage") },
                        isError = jumpText.isNotEmpty() && !valid,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                targetPage?.let { page ->
                                    if (page in 1..maxPage) {
                                        onGoTo(page - 1)
                                        showJump = false
                                    }
                                }
                            },
                        ),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        targetPage?.let { onGoTo(it - 1) }
                        showJump = false
                    },
                    enabled = valid,
                ) { Text("跳转") }
            },
            dismissButton = {
                TextButton(onClick = { showJump = false }) { Text("取消") }
            },
        )
    }
}

private data class ViewerPage(val file: File, val width: Int, val height: Int) {
    /** width / height, used to reserve the exact space before the image is decoded. */
    val aspectRatio: Float
        get() = if (width > 0 && height > 0) width.toFloat() / height else DEFAULT_ASPECT_RATIO

    private companion object {
        const val DEFAULT_ASPECT_RATIO = 0.7f
    }
}

private const val VIEW_CACHE_META = ".view-meta"
private const val VIEW_CACHE_VERSION = "v2"

private fun viewCacheFingerprint(task: Task): String =
    "${task.fileUri.orEmpty()}|${task.saveTreeUri.orEmpty()}|${task.zipName.orEmpty()}"

/** Keep only the currently opened book's extracted pages. */
private fun pruneViewCache(root: File, keep: File) {
    root.listFiles()?.forEach { dir ->
        if (dir.isDirectory && dir.name != keep.name) {
            runCatching { dir.deleteRecursively() }
        }
    }
}

private fun readViewCache(outDir: File, fingerprint: String): List<ViewerPage>? {
    val meta = File(outDir, VIEW_CACHE_META)
    if (!meta.isFile) return null
    val lines = runCatching { meta.readLines() }.getOrNull() ?: return null
    if (lines.size < 2 || lines[0] != VIEW_CACHE_VERSION || lines[1] != fingerprint) return null
    val root = runCatching { outDir.canonicalFile }.getOrNull() ?: return null
    val pages = ArrayList<ViewerPage>(lines.size - 2)
    for (line in lines.drop(2)) {
        if (line.isBlank()) continue
        val parts = line.split('\t')
        if (parts.size != 3) return null
        val file = File(root, parts[0])
        if (file.parentFile != root || !file.isFile) return null
        pages += ViewerPage(file, parts[1].toIntOrNull() ?: -1, parts[2].toIntOrNull() ?: -1)
    }
    return pages.ifEmpty { null }
}

private fun writeViewCache(outDir: File, fingerprint: String, pages: List<ViewerPage>) {
    val text = buildString {
        append(VIEW_CACHE_VERSION).append('\n')
        append(fingerprint).append('\n')
        for (page in pages) {
            append(page.file.name).append('\t')
                .append(page.width).append('\t')
                .append(page.height).append('\n')
        }
    }
    runCatching { File(outDir, VIEW_CACHE_META).writeText(text) }
}

/**
 * Decode only the header to get the rendered size. JPEG EXIF rotation is applied
 * because Coil displays the rotated image, so the reserved height must match.
 */
private fun readPageSize(file: File): Pair<Int, Int> {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    runCatching { BitmapFactory.decodeFile(file.absolutePath, opts) }
    var width = opts.outWidth
    var height = opts.outHeight
    val ext = file.extension.lowercase()
    if (width > 0 && height > 0 && (ext == "jpg" || ext == "jpeg")) {
        val orientation = runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val rotated = orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
            orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
            orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
            orientation == ExifInterface.ORIENTATION_TRANSVERSE
        if (rotated) {
            val tmp = width
            width = height
            height = tmp
        }
    }
    return width to height
}

private fun extractForView(context: Context, task: Task): List<ViewerPage> {
    val viewRoot = File(context.cacheDir, "view")
    val outDir = File(viewRoot, task.id)
    pruneViewCache(viewRoot, outDir)

    val fingerprint = viewCacheFingerprint(task)
    // Reopening the same book (including after rotation) can reuse the extracted
    // pages instead of unzipping the whole archive again.
    readViewCache(outDir, fingerprint)?.let { return it }

    outDir.deleteRecursively()
    outDir.mkdirs()

    class Extracted(val entryName: String, val temp: File)

    val extracted = ArrayList<Extracted>()
    val ins = Storage.openInput(context, task)
    ins.use { input ->
        ZipInputStream(input.buffered()).use { zis ->
            val buf = ByteArray(64 * 1024)
            var rawIndex = 0
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && PageOrdering.isImageName(entry.name)) {
                    val ext = entry.name.substringAfterLast('.', "jpg")
                    val tmp = File(outDir, "tmp_%06d.%s".format(rawIndex, ext))
                    tmp.outputStream().use { os ->
                        while (true) {
                            val n = zis.read(buf)
                            if (n < 0) break
                            os.write(buf, 0, n)
                        }
                    }
                    extracted += Extracted(entry.name, tmp)
                    rawIndex++
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    // Imported zips do not guarantee entry order, and names like 1.jpg/10.jpg/2.jpg
    // sort incorrectly with a plain string compare. Rename into canonical page order.
    val pages = ArrayList<ViewerPage>(extracted.size)
    extracted
        .sortedWith { first, second -> PageOrdering.naturalCompare(first.entryName, second.entryName) }
        .forEachIndexed { index, item ->
            val ext = item.temp.extension
            val target = File(outDir, "%05d.%s".format(index, ext))
            if (!item.temp.renameTo(target)) {
                item.temp.copyTo(target, overwrite = true)
                item.temp.delete()
            }
            val (width, height) = readPageSize(target)
            pages += ViewerPage(target, width, height)
        }
    writeViewCache(outDir, fingerprint, pages)
    return pages
}

private suspend fun saveImageToGallery(context: Context, file: File) = withContext(Dispatchers.IO) {
    require(file.exists()) { "文件不存在" }
    val mime = when (file.extension.lowercase()) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        else -> "image/jpeg"
    }
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
        put(MediaStore.Images.Media.MIME_TYPE, mime)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: error("无法创建相册文件")
    try {
        resolver.openOutputStream(uri)?.use { out ->
            file.inputStream().use { it.copyTo(out) }
        } ?: error("无法写入相册")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
    } catch (e: Exception) {
        runCatching { resolver.delete(uri, null, null) }
        throw e
    }
}
