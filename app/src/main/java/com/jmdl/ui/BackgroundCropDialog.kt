package com.jmdl.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun loadBackgroundBitmap(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

        var sample = 1
        val maxDim = 2048
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxDim) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return@runCatching null

        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        orientBitmap(decoded, orientation)
    }.getOrNull()
}

private fun orientBitmap(bitmap: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.setRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.setRotate(-90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        else -> return bitmap
    }
    return runCatching {
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }.getOrDefault(bitmap)
}

internal fun saveBackgroundBitmap(context: Context, bitmap: Bitmap): Uri? = runCatching {
    val dir = File(context.filesDir, "background").apply { mkdirs() }
    dir.listFiles()?.filter { it.name.startsWith("bg_") }?.forEach { it.delete() }
    val dest = File(dir, "bg_${System.currentTimeMillis()}.jpg")
    FileOutputStream(dest).use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
    }
    Uri.fromFile(dest)
}.getOrNull()

@Composable
internal fun BackgroundCropDialog(
    uri: Uri,
    onDismiss: () -> Unit,
    onConfirm: (Bitmap) -> Unit,
) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var loadFailed by remember(uri) { mutableStateOf(false) }

    LaunchedEffect(uri) {
        bitmap = loadBackgroundBitmap(context, uri)
        loadFailed = bitmap == null
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            val image = bitmap
            if (image == null) {
                Text(
                    text = if (loadFailed) "图片加载失败" else "加载图片…",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                CropContent(
                    bitmap = image,
                    onCancel = onDismiss,
                    onConfirm = onConfirm,
                )
            }
        }
    }
}

@Composable
private fun CropContent(
    bitmap: Bitmap,
    onCancel: () -> Unit,
    onConfirm: (Bitmap) -> Unit,
) {
    val themeColor = LocalThemeColor.current
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember(bitmap) { mutableFloatStateOf(0f) }
    var offset by remember(bitmap) { mutableStateOf(Offset.Zero) }
    val minScale = remember(bitmap, viewport) {
        if (viewport.width <= 0 || viewport.height <= 0) 1f
        else max(
            viewport.width.toFloat() / bitmap.width,
            viewport.height.toFloat() / bitmap.height,
        )
    }

    LaunchedEffect(bitmap, viewport) {
        if (viewport.width > 0 && viewport.height > 0) {
            scale = minScale
            offset = Offset(
                (viewport.width - bitmap.width * minScale) / 2f,
                (viewport.height - bitmap.height * minScale) / 2f,
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewport = it }
            .pointerInput(bitmap, viewport) {
                if (viewport.width <= 0 || viewport.height <= 0) return@pointerInput
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val oldScale = scale
                    if (oldScale <= 0f) return@detectTransformGestures
                    val nextScale = (oldScale * zoom).coerceIn(minScale, minScale * 6f)
                    val factor = nextScale / oldScale
                    val next = Offset(
                        centroid.x - (centroid.x - offset.x) * factor + pan.x,
                        centroid.y - (centroid.y - offset.y) * factor + pan.y,
                    )
                    scale = nextScale
                    offset = clampCropOffset(next, nextScale, viewport, bitmap)
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawImage(
                image = bitmap.asImageBitmap(),
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(bitmap.width, bitmap.height),
                dstOffset = IntOffset(offset.x.roundToInt(), offset.y.roundToInt()),
                dstSize = IntSize(
                    (bitmap.width * scale).roundToInt(),
                    (bitmap.height * scale).roundToInt(),
                ),
                filterQuality = FilterQuality.High,
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .border(2.dp, Color.White.copy(alpha = 0.65f))
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.55f))
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "拖动图片选择背景区域，双指缩放",
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCancel) {
                Text("取消", color = Color.White)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.55f))
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = {
                    if (viewport.width <= 0 || viewport.height <= 0 || scale <= 0f) return@Button
                    val left = (-offset.x / scale).roundToInt().coerceIn(0, bitmap.width - 1)
                    val top = (-offset.y / scale).roundToInt().coerceIn(0, bitmap.height - 1)
                    val width = (viewport.width / scale).roundToInt()
                        .coerceIn(1, bitmap.width - left)
                    val height = (viewport.height / scale).roundToInt()
                        .coerceIn(1, bitmap.height - top)
                    onConfirm(Bitmap.createBitmap(bitmap, left, top, width, height))
                },
                colors = ButtonDefaults.buttonColors(containerColor = themeColor),
            ) {
                Text("使用这张背景")
            }
        }
    }
}

private fun clampCropOffset(
    offset: Offset,
    scale: Float,
    viewport: IntSize,
    bitmap: Bitmap,
): Offset {
    val imageW = bitmap.width * scale
    val imageH = bitmap.height * scale
    val minX = viewport.width - imageW
    val minY = viewport.height - imageH
    return Offset(
        x = if (minX > 0f) minX / 2f else offset.x.coerceIn(minX, 0f),
        y = if (minY > 0f) minY / 2f else offset.y.coerceIn(minY, 0f),
    )
}