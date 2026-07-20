package com.jmdl.ui

import android.app.Application
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jmdl.core.JmIdParser
import com.jmdl.data.SettingsStore
import com.jmdl.data.Task
import com.jmdl.data.TaskRepository
import com.jmdl.data.TaskStatus
import com.jmdl.download.DownloadService
import com.jmdl.download.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class TaskViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = TaskRepository.get(app)
    private val settings = SettingsStore(app)

    val tasks: StateFlow<List<Task>> = repo.tasks

    private val _saveUri = MutableStateFlow(settings.saveTreeUri)
    val saveUri: StateFlow<String?> = _saveUri.asStateFlow()

    data class Background(val color: Int?, val imageUri: String?)

    private val _background = MutableStateFlow(Background(settings.bgColor, settings.bgImageUri))
    val background: StateFlow<Background> = _background.asStateFlow()

    init {
        // Resume any tasks left queued from a previous run.
        if (repo.hasPending()) DownloadService.start(app)
    }

    fun setSaveUri(uri: Uri) {
        val s = uri.toString()
        settings.saveTreeUri = s
        _saveUri.value = s
    }

    /** Back to the picker-free default (system Downloads/JMComic). */
    fun clearSaveUri() {
        settings.saveTreeUri = null
        _saveUri.value = null
    }

    fun setBgColor(argb: Int) {
        settings.bgColor = argb
        settings.bgImageUri = null
        _background.value = Background(argb, null)
    }

    fun setBgImage(uri: Uri) {
        val s = uri.toString()
        settings.bgImageUri = s
        settings.bgColor = null
        _background.value = Background(null, s)
    }

    fun clearBg() {
        settings.bgColor = null
        settings.bgImageUri = null
        _background.value = Background(null, null)
    }

    /** Parses [input] and enqueues a task. Returns an error message on failure, else null. */
    fun addTask(input: String): String? {
        val id = JmIdParser.parse(input)
            ?: return "识别不到车号。用法：JM 350234（也可直接粘贴带链接的文本）"
        repo.add(
            Task(
                id = UUID.randomUUID().toString(),
                jmId = id,
                status = TaskStatus.QUEUED,
                createdAt = System.currentTimeMillis(),
            )
        )
        DownloadService.start(getApplication())
        return null
    }

    fun retry(taskId: String) {
        repo.update(taskId) { it.copy(status = TaskStatus.QUEUED, done = 0, total = 0, message = null) }
        DownloadService.start(getApplication())
    }

    /** Removes only the task record; the saved zip in the folder is kept. */
    fun remove(taskId: String) = repo.remove(taskId)

    /** Removes the task record AND deletes its saved zip. */
    fun removeWithFile(task: Task) {
        viewModelScope.launch(Dispatchers.IO) {
            Storage.deleteFile(getApplication(), task)
            repo.remove(task.id)
        }
    }

    /** Batch remove. If [deleteFiles], also delete each task's saved zip. */
    fun removeTasks(ids: Set<String>, deleteFiles: Boolean) {
        if (!deleteFiles) {
            ids.forEach { repo.remove(it) }
            return
        }
        val targets = repo.tasks.value.filter { it.id in ids }
        viewModelScope.launch(Dispatchers.IO) {
            for (t in targets) {
                Storage.deleteFile(getApplication(), t)
                repo.remove(t.id)
            }
        }
    }

    /** Move each selected task's zip into [dest], updating its saved-location + name. */
    fun moveTasks(ids: Set<String>, dest: Uri) {
        val targets = repo.tasks.value.filter { it.id in ids && it.zipName != null }
        val destStr = dest.toString()
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val destDir = DocumentFile.fromTreeUri(app, dest) ?: return@launch
            val cr = app.contentResolver
            for (t in targets) {
                val name = t.zipName ?: continue
                if (t.saveTreeUri == destStr) continue // already in destination
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
