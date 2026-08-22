package com.jmdl.data

import android.content.Context
import com.jmdl.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class TaskRepository private constructor(private val file: File) {

    private val lock = Any()
    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()

    init {
        load()
    }

    private fun load() {
        try {
            if (!file.exists()) {
                AppLogger.i("Task file not found, starting fresh: ${file.path}")
                return
            }

            val arr = JSONArray(file.readText())
            val list = ArrayList<Task>(arr.length())

            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(deserializeTask(o))
            }

            _tasks.value = list.map {
                if (it.status == TaskStatus.RUNNING || it.status == TaskStatus.ZIPPING) {
                    AppLogger.w("Task ${it.id} was in stale ${it.status} state, marking failed")
                    it.copy(status = TaskStatus.FAILED, message = "上次下载中断，已标记失败", finishedAt = System.currentTimeMillis())
                } else {
                    it
                }
            }

            AppLogger.i("Loaded ${_tasks.value.size} tasks from: ${file.path}")

        } catch (e: Exception) {
            AppLogger.e("Failed to load tasks from: ${file.path}", e)
        }
    }

    private fun deserializeTask(o: JSONObject): Task {
        return Task(
            id = o.getString("id"),
            jmId = o.getString("jmId"),
            title = o.optString("title").ifEmpty { null },
            status = runCatching { TaskStatus.valueOf(o.getString("status")) }
                .getOrDefault(TaskStatus.QUEUED),
            done = o.optInt("done"),
            total = o.optInt("total"),
            failed = o.optInt("failed"),
            message = o.optString("message").ifEmpty { null },
            zipName = o.optString("zipName").ifEmpty { null },
            saveTreeUri = o.optString("saveTreeUri").ifEmpty { null },
            fileUri = o.optString("fileUri").ifEmpty { null },
            createdAt = o.optLong("createdAt"),
            finishedAt = o.optLong("finishedAt").let { if (it > 0) it else null },
            currentPage = o.optInt("currentPage").let { if (it >= 0) it else null },
            pageCount = o.optInt("pageCount").let { if (it >= 0) it else null },
        )
    }

    private fun persist() {
        try {
            val arr = JSONArray()
            for (t in _tasks.value) {
                arr.put(serializeTask(t))
            }
            file.writeText(arr.toString())
            AppLogger.i("Persisted ${_tasks.value.size} tasks to: ${file.path}")
        } catch (e: Exception) {
            AppLogger.e("Failed to persist tasks to: ${file.path}", e)
        }
    }

    private fun serializeTask(t: Task): JSONObject {
        return JSONObject().apply {
            put("id", t.id)
            put("jmId", t.jmId)
            put("title", t.title ?: "")
            put("status", t.status.name)
            put("done", t.done)
            put("total", t.total)
            put("failed", t.failed)
            put("message", t.message ?: "")
            put("zipName", t.zipName ?: "")
            put("saveTreeUri", t.saveTreeUri ?: "")
            put("fileUri", t.fileUri ?: "")
            put("createdAt", t.createdAt)
            put("finishedAt", t.finishedAt ?: -1)
            put("currentPage", t.currentPage ?: -1)
            put("pageCount", t.pageCount ?: -1)
        }
    }

    fun add(task: Task) = synchronized(lock) {
        _tasks.value = _tasks.value + task
        AppLogger.i("Added task: ${task.id} jmId=${task.jmId}")
        persist()
    }

    fun update(id: String, persist: Boolean = true, transform: (Task) -> Task) = synchronized(lock) {
        val oldTask = _tasks.value.firstOrNull { it.id == id }
        _tasks.value = _tasks.value.map { if (it.id == id) transform(it) else it }
        val newTask = _tasks.value.firstOrNull { it.id == id }
        
        if (oldTask?.status != newTask?.status) {
            AppLogger.i("Task ${id} status changed: ${oldTask?.status} -> ${newTask?.status}")
        }
        
        if (persist) persist()
    }

    fun remove(id: String) = synchronized(lock) {
        _tasks.value = _tasks.value.filterNot { it.id == id }
        AppLogger.i("Removed task: $id")
        persist()
    }

    fun byId(id: String): Task? = _tasks.value.firstOrNull { it.id == id }

    fun nextQueued(): Task? = _tasks.value.filter { it.status == TaskStatus.QUEUED }.minByOrNull { it.createdAt }

    fun claimNextQueued(): Task? = synchronized(lock) {
        val task = _tasks.value.filter { it.status == TaskStatus.QUEUED }.minByOrNull { it.createdAt }
            ?: return null
        _tasks.value = _tasks.value.map {
            if (it.id == task.id) it.copy(status = TaskStatus.RUNNING, done = 0, total = 0, message = null)
            else it
        }
        AppLogger.i("Claimed task: ${task.id} jmId=${task.jmId}")
        persist()
        task.copy(status = TaskStatus.RUNNING, done = 0, total = 0, message = null)
    }

    fun failOtherActiveTasks(activeIds: Set<String>) = synchronized(lock) {
        var changed = false
        _tasks.value = _tasks.value.map { task ->
            if (task.id !in activeIds && (task.status == TaskStatus.RUNNING || task.status == TaskStatus.ZIPPING)) {
                changed = true
                AppLogger.w("Task ${task.id} is not in active set, marking failed")
                task.copy(status = TaskStatus.FAILED, message = "任务状态失效，已标记失败", finishedAt = System.currentTimeMillis())
            } else {
                task
            }
        }
        if (changed) persist()
    }

    fun hasPending(): Boolean = _tasks.value.any {
        it.status == TaskStatus.QUEUED || it.status == TaskStatus.RUNNING || it.status == TaskStatus.ZIPPING
    }

    fun isPaused(id: String): Boolean = _tasks.value.firstOrNull { it.id == id }?.status == TaskStatus.PAUSED

    fun pauseTask(id: String) = synchronized(lock) {
        _tasks.value = _tasks.value.map {
            if (it.id == id) it.copy(status = TaskStatus.PAUSED, message = "已暂停") else it
        }
        AppLogger.i("Paused task: $id")
        persist()
    }

    fun resumeTask(id: String) = synchronized(lock) {
        _tasks.value = _tasks.value.map {
            if (it.id == id) it.copy(status = TaskStatus.QUEUED, message = null, createdAt = System.currentTimeMillis()) else it
        }
        AppLogger.i("Resumed task: $id")
        persist()
    }

    fun pauseAll() = synchronized(lock) {
        _tasks.value = _tasks.value.map {
            if (it.status == TaskStatus.QUEUED || it.status == TaskStatus.RUNNING || it.status == TaskStatus.ZIPPING)
                it.copy(status = TaskStatus.PAUSED, message = "已暂停")
            else it
        }
        AppLogger.i("Paused all active tasks")
        persist()
    }

    fun resumeAll() = synchronized(lock) {
        _tasks.value = _tasks.value.map {
            if (it.status == TaskStatus.PAUSED) it.copy(status = TaskStatus.QUEUED, message = null, createdAt = System.currentTimeMillis()) else it
        }
        AppLogger.i("Resumed all paused tasks")
        persist()
    }

    companion object {
        @Volatile
        private var INSTANCE: TaskRepository? = null

        fun get(context: Context): TaskRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: TaskRepository(
                    File(context.applicationContext.filesDir, "tasks.json")
                ).also { INSTANCE = it }
            }
    }
}