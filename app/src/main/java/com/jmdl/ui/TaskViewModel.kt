package com.jmdl.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.graphics.Color
import com.jmdl.core.JmIdParser
import com.jmdl.core.PageOrdering
import com.jmdl.data.SettingsStore
import com.jmdl.data.Task
import com.jmdl.data.TaskRepository
import com.jmdl.data.TaskStatus
import com.jmdl.download.DownloadService
import com.jmdl.download.Storage
import com.jmdl.engine.JmEngine
import com.jmdl.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

class TaskViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = TaskRepository.get(app)
    private val settings = SettingsStore(app)

    val tasks: StateFlow<List<Task>> = repo.tasks

    private val _saveUri = MutableStateFlow(settings.saveTreeUri)
    val saveUri: StateFlow<String?> = _saveUri.asStateFlow()

    data class Background(val color: Int?, val imageUri: String?)

    private val _background = MutableStateFlow(Background(settings.bgColor, settings.bgImageUri))
    val background: StateFlow<Background> = _background.asStateFlow()

    private val _themeColor = MutableStateFlow(Color(settings.themeColor))
    val themeColor: StateFlow<Color> = _themeColor.asStateFlow()

    private val _maxConcurrent = MutableStateFlow(settings.maxConcurrent)
    val maxConcurrent: StateFlow<Int> = _maxConcurrent.asStateFlow()

    private val _lastViewedTaskId = MutableStateFlow<String?>(null)
    val lastViewedTaskId: StateFlow<String?> = _lastViewedTaskId.asStateFlow()

    data class ImportResult(val success: Int, val failed: Int, val skipped: Int)
    private val _importResult = MutableStateFlow<ImportResult?>(null)
    val importResult: StateFlow<ImportResult?> = _importResult.asStateFlow()

    private val authorLookupAttempted = mutableSetOf<String>()
    private val authorLookupLock = Any()
    private val authorLookupSemaphore = Semaphore(1)

    fun clearImportResult() {
        _importResult.value = null
    }

    /** Backfills authors for tasks saved before author metadata was persisted. */
    fun ensureAuthors(taskId: String) {
        val task = repo.byId(taskId) ?: return
        if (task.status != TaskStatus.DONE || task.authors.isNotEmpty()) return
        if (!task.jmId.all { it.isDigit() }) return
        val shouldFetch = synchronized(authorLookupLock) {
            authorLookupAttempted.add(task.id)
        }
        if (!shouldFetch) return

        viewModelScope.launch(Dispatchers.IO) {
            authorLookupSemaphore.withPermit {
                runCatching { JmEngine.getAlbumInfo(task.jmId) }
                    .onSuccess { info ->
                        if (info.authors.isNotEmpty()) {
                            repo.update(task.id) {
                                if (it.authors.isEmpty()) it.copy(authors = info.authors) else it
                            }
                        }
                    }
                    .onFailure {
                        AppLogger.w("Author backfill failed jmId=${task.jmId}: ${it.message}")
                    }
            }
        }
    }

    init {
        if (repo.hasPending()) DownloadService.start(app)
    }

    fun setLastViewedTaskId(taskId: String?) {
        _lastViewedTaskId.value = taskId
    }

    fun setSaveUri(uri: Uri) {
        val s = uri.toString()
        settings.saveTreeUri = s
        _saveUri.value = s
        AppLogger.i("settings saveUri changed -> $s")
    }

    fun clearSaveUri() {
        settings.saveTreeUri = null
        _saveUri.value = null
        AppLogger.i("settings saveUri reset to default")
    }

    fun setBgColor(argb: Int) {
        settings.bgColor = argb
        settings.bgImageUri = null
        _background.value = Background(argb, null)
        AppLogger.i("settings background color changed")
    }

    fun setBgImage(uri: Uri) {
        val s = uri.toString()
        settings.bgImageUri = s
        settings.bgColor = null
        _background.value = Background(null, s)
        AppLogger.i("settings background image changed -> $s")
    }

    fun clearBg() {
        settings.bgColor = null
        settings.bgImageUri = null
        _background.value = Background(null, null)
        AppLogger.i("settings background reset")
    }

    fun setThemeColor(argb: Int) {
        settings.themeColor = argb
        _themeColor.value = Color(argb)
        AppLogger.i("settings theme color changed")
    }

    fun setMaxConcurrent(count: Int) {
        settings.maxConcurrent = count
        _maxConcurrent.value = count
        AppLogger.i("settings maxConcurrent changed -> $count")
    }

    fun addTask(input: String): String? {
        val ids = JmIdParser.parseAll(input)
        if (ids.isEmpty()) return "识别不到车号。用法：JM 350234（也可直接粘贴带链接的文本）"

        var added = 0
        val skipped = mutableListOf<String>()
        ids.forEach { id ->
            val err = addOneTask(id)
            if (err != null) skipped.add(err) else added++
        }
        if (ids.isNotEmpty()) {
            DownloadService.start(getApplication())
        }

        return when {
            added > 0 && skipped.isEmpty() -> null  // 全部成功，不提示
            added > 0 -> "添加 $added 个，${skipped.joinToString("，")}"  // 部分成功
            else -> skipped.joinToString("，")  // 全部失败
        }
    }

    private fun addOneTask(id: String): String? {
        val existing = repo.tasks.value.firstOrNull { it.jmId == id }
        if (existing != null) {
            when (existing.status) {
                TaskStatus.DONE -> return "JM$id 已下载完成"
                TaskStatus.RUNNING, TaskStatus.ZIPPING, TaskStatus.QUEUED, TaskStatus.PAUSED -> return "JM$id 已在列表中"
                TaskStatus.FAILED -> {
                    retryOneTask(existing)
                    return null
                }
            }
        }

        val taskId = UUID.randomUUID().toString()
        repo.add(
            Task(
                id = taskId,
                jmId = id,
                status = TaskStatus.QUEUED,
                createdAt = System.currentTimeMillis(),
            )
        )
        AppLogger.i("task created jmId=$id taskId=$taskId")
        return null
    }

    private fun retryOneTask(t: Task) {
        if (t.fileUri != null || t.zipName != null) {
            viewModelScope.launch(Dispatchers.IO) {
                Storage.deleteFile(getApplication(), t)
                AppLogger.i("deleted file for retry taskId=${t.id}")
            }
        }
        repo.update(t.id) {
            it.copy(
                status = TaskStatus.QUEUED,
                done = 0, total = 0, failed = 0,
                message = null, zipName = null, fileUri = null,
                createdAt = System.currentTimeMillis(),
            )
        }
        AppLogger.i("task retried taskId=${t.id}")
    }

    fun retry(taskId: String) {
        repo.byId(taskId)?.let { retryOneTask(it) }
        DownloadService.start(getApplication())
    }

    fun retryAll(taskIds: List<String>) {
        AppLogger.i("retryAll called, count=${taskIds.size}")
        taskIds.forEach { id -> repo.byId(id)?.let { retryOneTask(it) } }
        DownloadService.start(getApplication())
    }

    fun remove(taskId: String) {
        val task = repo.byId(taskId)
        if (task?.status == TaskStatus.RUNNING) {
            DownloadService.cancelTask(getApplication(), taskId)
        }
        repo.remove(taskId)
    }

    fun pause(taskId: String) {
        repo.pauseTask(taskId)
        AppLogger.i("task paused taskId=$taskId")
        DownloadService.pauseTask(getApplication(), taskId)
    }

    fun resume(taskId: String) {
        repo.resumeTask(taskId)
        AppLogger.i("task resumed taskId=$taskId")
        DownloadService.start(getApplication())
    }

    fun pauseAll() {
        repo.pauseAll()
        AppLogger.i("pauseAll called")
        DownloadService.pauseTask(getApplication(), "")
    }

    fun resumeAll() {
        repo.resumeAll()
        AppLogger.i("resumeAll called")
        DownloadService.start(getApplication())
    }

    private var pagePersistJob: Job? = null

    fun updatePage(taskId: String, page: Int, pageCount: Int) {
        // The reader calls this on every page change. Writing tasks.json synchronously
        // here would block the UI thread while flipping/swiping, so update the in-memory
        // snapshot immediately and persist after the user stops turning pages.
        repo.update(taskId, persist = false) { it.copy(currentPage = page, pageCount = pageCount) }
        pagePersistJob?.cancel()
        pagePersistJob = viewModelScope.launch(Dispatchers.IO) {
            delay(800)
            repo.persistNow()
        }
    }

    /** Writes the last page immediately when the viewer closes. */
    fun flushPage() {
        pagePersistJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) { repo.persistNow() }
    }

    fun removeWithFile(task: Task) {
        if (task.status == TaskStatus.RUNNING) {
            DownloadService.cancelTask(getApplication(), task.id)
        }
        viewModelScope.launch(Dispatchers.IO) {
            Storage.deleteFile(getApplication(), task)
            repo.remove(task.id)
        }
    }

    fun removeTasks(ids: Set<String>, deleteFiles: Boolean) {
        val targets = repo.tasks.value.filter { it.id in ids }
        targets.forEach { task ->
            if (task.status == TaskStatus.RUNNING) {
                DownloadService.cancelTask(getApplication(), task.id)
            }
        }
        
        if (!deleteFiles) {
            ids.forEach { repo.remove(it) }
            return
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            for (t in targets) {
                Storage.deleteFile(getApplication(), t)
                repo.remove(t.id)
            }
        }
    }

    fun moveTasks(ids: Set<String>, dest: Uri) {
        val targets = repo.tasks.value.filter { it.id in ids && it.zipName != null }
        val destStr = dest.toString()
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val destDir = DocumentFile.fromTreeUri(app, dest) ?: return@launch
            val cr = app.contentResolver
            for (t in targets) {
                val name = t.zipName ?: continue
                if (t.saveTreeUri == destStr) continue
                runCatching {
                    val finalName = uniqueDocName(destDir, name)
                    val newDoc = destDir.createFile("application/zip", finalName)
                        ?: return@runCatching
                    Storage.openInput(app, t).use { inp ->
                        cr.openOutputStream(newDoc.uri)?.use { out -> inp.copyTo(out) }
                    }
                    Storage.deleteFile(app, t)
                    repo.update(t.id) {
                        it.copy(saveTreeUri = destStr, fileUri = newDoc.uri.toString(), zipName = finalName)
                    }
                }
            }
        }
    }

    fun importLocalZips(uris: List<Uri>) {
        val app = getApplication<Application>()
        val cr = app.contentResolver
        
        viewModelScope.launch(Dispatchers.IO) {
            var successCount = 0
            var failedCount = 0
            var skippedCount = 0
            
            AppLogger.i("Starting import of ${uris.size} files")
            
            for (uri in uris) {
                val docFile = DocumentFile.fromSingleUri(app, uri)
                val fileName = docFile?.name ?: continue
                
                val jmId = extractJmIdFromFileName(fileName)
                    ?: fileName.substringBeforeLast('.')  // non-JM files: use filename as ID

                val existing = repo.tasks.value.firstOrNull {
                    it.jmId == jmId || it.zipName == fileName
                }
                if (existing != null) {
                    AppLogger.w("Import skipped: already exists (jmId=$jmId, file=$fileName)")
                    skippedCount++
                    continue
                }

                runCatching {
                    // If the URI is file-backed, reading the ZIP central directory is
                    // much cheaper than decompressing every entry with ZipInputStream.
                    val pageCount = Storage.localFileOf(app, uri)
                        ?.let { local -> countImagesInZip(local) }
                        ?: countImagesInZip(app, uri)

                    val saved = Storage.copyFileToSaveLocation(
                        app, uri, fileName, settings.saveTreeUri
                    )
                    
                    val taskId = UUID.randomUUID().toString()
                    val title = if (jmId.all { it.isDigit() }) {
                        fileName.substringBeforeLast('.').replace("JM$jmId-", "")
                    } else {
                        fileName.substringBeforeLast('.')
                    }
                    repo.add(
                        Task(
                            id = taskId,
                            jmId = jmId,
                            title = title,
                            status = TaskStatus.DONE,
                            total = pageCount,
                            pageCount = pageCount,
                            zipName = saved.name,
                            fileUri = saved.fileUri,
                            saveTreeUri = settings.saveTreeUri,
                            createdAt = System.currentTimeMillis(),
                            finishedAt = System.currentTimeMillis(),
                        )
                    )
                    AppLogger.i("Import successful: $jmId ($pageCount pages)")
                    successCount++
                }.onFailure { e ->
                    if (e is Storage.DuplicateFileException) {
                        AppLogger.i("Import skipped: file exists ${e.fileName}")
                        skippedCount++
                    } else {
                        AppLogger.e("Import failed: $fileName", e)
                        failedCount++
                    }
                }
            }
            
            AppLogger.i("Import completed: $successCount success, $failedCount failed, $skippedCount skipped")
            _importResult.value = ImportResult(successCount, failedCount, skippedCount)
        }
    }

    private fun extractJmIdFromFileName(fileName: String): String? {
        val regex = Regex("JM(\\d{3,})", RegexOption.IGNORE_CASE)
        return regex.find(fileName)?.groupValues?.get(1)
    }

    private fun countImagesInZip(file: File): Int? = runCatching {
        ZipFile(file).use { zip ->
            zip.entries().asSequence().count { entry ->
                !entry.isDirectory && PageOrdering.isImageName(entry.name)
            }
        }
    }.getOrNull()

    private fun countImagesInZip(context: Context, uri: Uri): Int {
        var count = 0
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory && PageOrdering.isImageName(entry.name)) {
                            count++
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
        }
        return count
    }

    private fun uniqueDocName(dir: DocumentFile, name: String): String {
        if (dir.findFile(name) == null) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot >= 0) name.substring(0, dot) else name
        val ext = if (dot >= 0) name.substring(dot) else ""
        var i = 1
        while (dir.findFile("$base ($i)$ext") != null) i++
        return "$base ($i)$ext"
    }
}