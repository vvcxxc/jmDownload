package com.jmdl.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Single source of truth for the task list. Backed by a small JSON file in the app's
 * private storage (org.json, no extra deps). Exposes a StateFlow that drives the UI and
 * is queried by the download service. Thread-safe.
 */
class TaskRepository private constructor(private val file: File) {

    private val lock = Any()
    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()

    init {
        load()
    }

    private fun load() {
        try {
            if (!file.exists()) return
            val arr = JSONArray(file.readText())
            val list = ArrayList<Task>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    Task(
                        id = o.getString("id"),
                        jmId = o.getString("jmId"),
                        title = o.optString("title").ifEmpty { null },
                        status = runCatching { TaskStatus.valueOf(o.getString("status")) }
                            .getOrDefault(TaskStatus.QUEUED),
                        done = o.optInt("done"),
                        total = o.optInt("total"),
                        message = o.optString("message").ifEmpty { null },
                        zipName = o.optString("zipName").ifEmpty { null },
                        saveTreeUri = o.optString("saveTreeUri").ifEmpty { null },
                        fileUri = o.optString("fileUri").ifEmpty { null },
                        createdAt = o.optLong("createdAt"),
                    )
                )
            }
            // A task left RUNNING/ZIPPING means the process died mid-work → re-queue it.
            _tasks.value = list.map {
                if (it.status == TaskStatus.RUNNING || it.status == TaskStatus.ZIPPING)
                    it.copy(status = TaskStatus.QUEUED, done = 0, message = null)
                else it
            }
        } catch (_: Exception) {
            // corrupt file → start fresh
        }
    }

    private fun persist() {
        try {
            val arr = JSONArray()
            for (t in _tasks.value) {
                arr.put(
                    JSONObject().apply {
                        put("id", t.id)
                        put("jmId", t.jmId)
                        put("title", t.title ?: "")
                        put("status", t.status.name)
                        put("done", t.done)
                        put("total", t.total)
                        put("message", t.message ?: "")
                        put("zipName", t.zipName ?: "")
                        put("saveTreeUri", t.saveTreeUri ?: "")
                        put("fileUri", t.fileUri ?: "")
                        put("createdAt", t.createdAt)
                    }
                )
            }
            file.writeText(arr.toString())
        } catch (_: Exception) {
            // best-effort persistence
        }
    }

    fun add(task: Task) = synchronized(lock) {
        _tasks.value = _tasks.value + task
        persist()
    }

    /** Update a task by id. [persist]=false for high-frequency progress ticks. */
    fun update(id: String, persist: Boolean = true, transform: (Task) -> Task) = synchronized(lock) {
        _tasks.value = _tasks.value.map { if (it.id == id) transform(it) else it }
        if (persist) persist()
    }

    fun remove(id: String) = synchronized(lock) {
        _tasks.value = _tasks.value.filterNot { it.id == id }
        persist()
    }

    fun byId(id: String): Task? = _tasks.value.firstOrNull { it.id == id }

    fun nextQueued(): Task? = _tasks.value.firstOrNull { it.status == TaskStatus.QUEUED }

    fun hasPending(): Boolean = _tasks.value.any {
        it.status == TaskStatus.QUEUED || it.status == TaskStatus.RUNNING || it.status == TaskStatus.ZIPPING
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
