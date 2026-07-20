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

    init {
        // Resume any tasks left queued from a previous run.
        if (repo.hasPending()) DownloadService.start(app)
    }

    fun setSaveUri(uri: Uri) {
        val s = uri.toString()
        settings.saveTreeUri = s
        _saveUri.value = s
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

    /** Removes the task record AND deletes its saved zip from the save folder. */
    fun removeWithFile(task: Task) {
        val zip = task.zipName
        val treeUri = settings.saveTreeUri
        viewModelScope.launch(Dispatchers.IO) {
            if (zip != null && treeUri != null) {
                runCatching {
                    DocumentFile.fromTreeUri(getApplication(), Uri.parse(treeUri))
                        ?.findFile(zip)
                        ?.delete()
                }
            }
            repo.remove(task.id)
        }
    }
}
