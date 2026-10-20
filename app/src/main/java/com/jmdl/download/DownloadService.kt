package com.jmdl.download

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import com.jmdl.core.FilenameSanitizer
import com.jmdl.data.SettingsStore
import com.jmdl.data.TaskRepository
import com.jmdl.data.TaskStatus
import com.jmdl.engine.JmEngine
import com.jmdl.util.AppLogger
import com.jmdl.util.CoverCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val drainMutex = Mutex()
    @Volatile private var activeTaskIds = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var activeWorkers = ConcurrentHashMap<String, Future<*>>()
    @Volatile private var drainRunning = false

    private val repo by lazy { TaskRepository.get(this) }
    private val settings by lazy { SettingsStore(this) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && (intent.action == ACTION_CANCEL || intent.action == ACTION_PAUSE)) {
            // These commands arrive through startForegroundService(), so the service
            // must be promoted even if its only job is to cancel/pause a worker.
            Notifications.ensureChannel(this)
            startForegroundCompat("准备下载…", "", 0, 0, indeterminate = true)
            if (intent.action == ACTION_CANCEL) handleCancel(intent) else handlePause(intent)
            if (!drainRunning && activeTaskIds.isEmpty()) stopSelfSafely()
            return START_NOT_STICKY
        }

        AppLogger.i("Download service started, drainRunning=$drainRunning")
        Notifications.ensureChannel(this)

        if (drainRunning) {
            AppLogger.w("Drain already running, restarting drain to pick up new tasks")
            return START_NOT_STICKY
        }
        drainRunning = true
        AppLogger.i("Drain loop starting")

        startForegroundCompat("准备下载…", "", 0, 0, indeterminate = true)
        scope.launch {
            drainMutex.withLock { drainLoop() }
            drainRunning = false
            AppLogger.i("Drain loop finished, drainRunning=false")
            if (repo.nextQueued() == null && activeTaskIds.isEmpty()) {
                val hasFailed = repo.tasks.value.any { it.status == TaskStatus.FAILED }
                if (repo.tasks.value.any { it.status == TaskStatus.DONE || it.status == TaskStatus.FAILED }) {
                    notifyDone(if (hasFailed) "有任务失败，点击查看详情" else "所有任务已完成")
                }
                // Always stop: keeping a dataSync foreground service alive just because
                // a task failed leaks the service (and its notification) indefinitely.
                stopSelfSafely()
            }
        }
        return START_NOT_STICKY
    }

    private fun handleCancel(intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID).orEmpty()
        if (taskId.isEmpty()) return
        val worker = activeWorkers.remove(taskId)
        AppLogger.i("Download cancel requested taskId=$taskId workerFound=${worker != null} active=${activeTaskIds.size}")
        worker?.cancel(true)
    }

    private fun handlePause(intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID).orEmpty()
        if (taskId.isEmpty()) {
            val ids = activeTaskIds.toList()
            ids.forEach { id ->
                repo.pauseTask(id)
                val worker = activeWorkers.remove(id)
                AppLogger.i("Pause requested taskId=$id workerFound=${worker != null}")
                worker?.cancel(true)
            }
            AppLogger.i("Pausing all active tasks, count=${ids.size}")
        } else {
            repo.pauseTask(taskId)
            val worker = activeWorkers.remove(taskId)
            AppLogger.i("Download pause requested taskId=$taskId workerFound=${worker != null} active=${activeTaskIds.size}")
            worker?.cancel(true)
        }
    }

    private suspend fun drainLoop() {
        AppLogger.i("DrainLoop starting")
        repo.failOtherActiveTasks(activeTaskIds.toSet())

        while (true) {
            val maxConcurrent = settings.maxConcurrent
            if (activeTaskIds.size >= maxConcurrent) {
                delay(500)
                continue
            }
            val task = repo.claimNextQueued()
            if (task == null) {
                if (activeTaskIds.isEmpty()) break
                delay(500)
                continue
            }
            activeTaskIds.add(task.id)
            AppLogger.i("Task claimed taskId=${task.id} jmId=${task.jmId} active=${activeTaskIds.size}/$maxConcurrent queued=${repo.tasks.value.count { it.status == TaskStatus.QUEUED }}")
            scope.launch {
                try {
                    AppLogger.i("Worker coroutine started taskId=${task.id} active=${activeTaskIds.size}")
                    processTask(task.id)
                } catch (e: Exception) {
                    AppLogger.e("processTask failed taskId=${task.id}", e)
                }
            }
        }
        AppLogger.i("DrainLoop all tasks completed")
    }

    private fun processTask(taskId: String) {
        val task = repo.byId(taskId) ?: run { activeTaskIds.remove(taskId); return }
        val jmId = task.jmId
        val workDir = File(cacheDir, "work/$taskId")
        runCatching { workDir.deleteRecursively() }

        AppLogger.i("Download start taskId=$taskId jmId=$jmId activeCount=${activeTaskIds.size}")
        updateAggregateNotif()

        try {
            requireRunning(taskId)
            AppLogger.i("Task phase=INFO taskId=$taskId jmId=$jmId")
            val info = runWithTimeout(taskId, TITLE_TIMEOUT_MS) { JmEngine.getAlbumInfo(jmId) }
            val title = info.title
            repo.update(taskId) { it.copy(title = title, authors = info.authors) }

            requireRunning(taskId)
            AppLogger.i("Task phase=DOWNLOAD taskId=$taskId jmId=$jmId title=$title authors=${info.authors}")
            val outcome = runWithTimeout(taskId, DOWNLOAD_TIMEOUT_MS) {
                var lastTick = 0L
                var lastProgressAt = System.currentTimeMillis()
                var lastReportedDone = 0
                var lastReportedTotal = 0

                JmEngine.download(jmId, workDir) { done, total ->
                    if (taskId !in activeTaskIds) {
                        AppLogger.w("Download stopped, taskId=$taskId is no longer active")
                        throw CancellationException("Download cancelled for taskId=$taskId")
                    }
                    val now = System.currentTimeMillis()
                    val (safeDone, safeTotal) = JmEngine.calculateSafeProgress(done, total, lastReportedDone, lastReportedTotal)

                    if (safeTotal != lastReportedTotal && lastReportedTotal > 0) {
                        AppLogger.w("Download total changed taskId=$taskId: $lastReportedTotal -> $safeTotal")
                    }

                    if (now - lastTick > 300) {
                        lastTick = now
                        repo.update(taskId, persist = false) {
                            if (it.status != TaskStatus.RUNNING) it
                            else it.copy(done = safeDone, total = safeTotal)
                        }
                        if (now - lastNotifUpdate > NOTIF_UPDATE_INTERVAL) {
                            lastNotifUpdate = now
                            updateAggregateNotif()
                        }
                    }

                    if (safeDone > lastReportedDone || safeTotal != lastReportedTotal) {
                        lastProgressAt = now
                        lastReportedDone = safeDone
                        lastReportedTotal = safeTotal
                        // Log per completed page instead of every 300ms tick.
                        AppLogger.i("Download progress taskId=$taskId: $safeDone/$safeTotal")
                    }

                    if (now - lastProgressAt > STALLED_PROGRESS_MS) {
                        AppLogger.e("Download stalled taskId=$taskId, lastProgress=${safeDone}/${safeTotal}, idle=${now - lastProgressAt}ms")
                        throw TimeoutException("下载卡住，${STALLED_PROGRESS_MS / 1000}秒无进度")
                    }
                }
            }

            AppLogger.i("Task phase=DOWNLOAD_DONE taskId=$taskId outcome=$outcome")
            repo.update(taskId) { it.copy(done = outcome.success, total = outcome.total) }

            if (outcome.success == 0) {
                val errorMsg = "下载失败（0 张成功）"
                AppLogger.w(errorMsg)
                repo.update(taskId) { it.copy(status = TaskStatus.FAILED, message = errorMsg, finishedAt = System.currentTimeMillis()) }
                return
            }

            repo.update(taskId) { it.copy(status = TaskStatus.ZIPPING) }
            updateAggregateNotif()

            val zipName = FilenameSanitizer.zipName(jmId, title)
            val treeUriStr = settings.saveTreeUri
            val saved = Storage.saveZip(this, workDir, zipName, treeUriStr)

            val note = if (outcome.failed > 0) "完成（${outcome.failed} 张失败）" else "完成"
            repo.update(taskId) {
                it.copy(
                    status = TaskStatus.DONE,
                    zipName = saved.name,
                    saveTreeUri = treeUriStr,
                    fileUri = saved.fileUri,
                    failed = outcome.failed,
                    message = note,
                    finishedAt = System.currentTimeMillis(),
                )
            }
            AppLogger.i("Download completed taskId=$taskId outcome=$outcome")
            scope.launch { CoverCache.extractAndCache(this@DownloadService, saved.fileUri, jmId) }

        } catch (e: TimeoutException) {
            handleTimeout(taskId, e)
        } catch (e: CancellationException) {
            handleCancellation(taskId)
        } catch (e: Storage.DuplicateFileException) {
            AppLogger.i("Download skipped, file exists taskId=$taskId name=${e.fileName}")
            val existingUri = Storage.findExistingFile(this, e.fileName, settings.saveTreeUri)
            repo.update(taskId) {
                it.copy(
                    status = TaskStatus.DONE,
                    zipName = e.fileName,
                    saveTreeUri = settings.saveTreeUri,
                    fileUri = existingUri,
                    message = "文件已存在，跳过下载",
                    finishedAt = System.currentTimeMillis(),
                )
            }
            scope.launch {
                existingUri?.let { uri -> CoverCache.extractAndCache(this@DownloadService, uri, jmId) }
            }
        } catch (e: Exception) {
            handleError(taskId, e)
        } finally {
            AppLogger.i("Download finished taskId=$taskId status=${repo.byId(taskId)?.status}")
            cleanup(taskId, workDir)
        }
    }

    private fun handleTimeout(taskId: String, e: TimeoutException) {
        AppLogger.e("Download timeout taskId=$taskId, setting FAILED", e)
        try {
            repo.update(taskId) {
                if (repo.isPaused(it.id)) {
                    it
                } else {
                    it.copy(status = TaskStatus.FAILED, message = e.message ?: "任务超时（长时间无响应）", finishedAt = System.currentTimeMillis())
                }
            }
        } catch (ex: Exception) {
            AppLogger.e("handleTimeout failed taskId=$taskId", ex)
        }
    }

    private fun handleCancellation(taskId: String) {
        AppLogger.e("Download cancelled taskId=$taskId, setting FAILED")
        try {
            repo.update(taskId) {
                if (repo.isPaused(it.id)) {
                    it
                } else {
                    it.copy(status = TaskStatus.FAILED, message = "已取消", finishedAt = System.currentTimeMillis())
                }
            }
        } catch (ex: Exception) {
            AppLogger.e("handleCancellation failed taskId=$taskId", ex)
        }
    }

    private fun handleError(taskId: String, e: Exception) {
        AppLogger.e("Download error taskId=$taskId, setting FAILED", e)
        try {
            repo.update(taskId) {
                if (repo.isPaused(it.id)) {
                    it
                } else {
                    it.copy(status = TaskStatus.FAILED, message = e.message ?: e.javaClass.simpleName, finishedAt = System.currentTimeMillis())
                }
            }
        } catch (ex: Exception) {
            AppLogger.e("handleError failed taskId=$taskId", ex)
        }
    }

    private fun requireRunning(taskId: String) {
        val task = repo.byId(taskId) ?: throw CancellationException("任务不存在")
        if (task.status != TaskStatus.RUNNING) {
            AppLogger.w("Task $taskId is not RUNNING (${task.status}), aborting")
            throw CancellationException("任务已暂停或取消")
        }
    }

    private fun cleanup(taskId: String, workDir: File) {
        AppLogger.i("Worker cleanup begin taskId=$taskId activeBefore=${activeTaskIds.size} workerPresent=${activeWorkers.containsKey(taskId)}")
        activeTaskIds.remove(taskId)
        activeWorkers.remove(taskId)
        AppLogger.i("Worker cleanup end taskId=$taskId activeAfter=${activeTaskIds.size} queued=${repo.tasks.value.count { it.status == TaskStatus.QUEUED }}")
        updateAggregateNotif()
        runCatching { workDir.deleteRecursively() }
            .onFailure { AppLogger.w("Failed to delete workDir: ${workDir.path}", it) }
    }

    private fun <T> runWithTimeout(taskId: String, timeoutMs: Long, action: () -> T): T {
        val executor = Executors.newSingleThreadExecutor()
        val future = executor.submit<T> {
            AppLogger.i("Worker thread entered taskId=$taskId")
            action()
        }
        activeWorkers[taskId] = future

        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            future.cancel(true)
            throw e
        } catch (e: InterruptedException) {
            future.cancel(true)
            throw e
        } catch (e: ExecutionException) {
            val cause = e.cause ?: e
            throw if (cause is Exception) cause else e
        } catch (e: Exception) {
            throw e
        } finally {
            executor.shutdownNow()
            activeWorkers.remove(taskId, future)
            AppLogger.i("Worker executor shutdown requested taskId=$taskId terminated=${executor.isTerminated}")
        }
    }

    private fun updateAggregateNotif() {
        val count = activeTaskIds.size
        if (count == 0) return
        var aggregateDone = 0
        var aggregateTotal = 0
        val titles = mutableListOf<String>()
        for (id in activeTaskIds) {
            val task = repo.byId(id) ?: continue
            if (task.status == TaskStatus.RUNNING) {
                aggregateDone += task.done
                aggregateTotal += task.total
                if (!task.title.isNullOrEmpty()) titles.add(task.title)
            }
        }
        val title = if (count == 1) {
            titles.firstOrNull() ?: "下载中"
        } else {
            "下载中 ($count 个任务)"
        }
        val text = if (aggregateTotal > 0) "$aggregateDone/$aggregateTotal" else ""
        val indeterminate = aggregateTotal == 0
        updateNotif(title, text, aggregateDone, aggregateTotal, indeterminate)
    }

    private fun startForegroundCompat(
        title: String, text: String, done: Int, total: Int, indeterminate: Boolean,
    ) {
        val notif = Notifications.build(this, title, text, done, total, indeterminate)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(Notifications.NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(Notifications.NOTIF_ID, notif)
        }
    }

    private fun updateNotif(title: String, text: String, done: Int, total: Int, indeterminate: Boolean) {
        val mgr = getSystemService(NotificationManager::class.java) ?: return
        mgr.notify(Notifications.NOTIF_ID, Notifications.build(this, title, text, done, total, indeterminate))
    }

    private fun notifyDone(text: String) {
        val mgr = getSystemService(NotificationManager::class.java) ?: return
        mgr.notify(Notifications.DONE_NOTIF_ID, Notifications.buildDone(this, "下载完成", text))
    }

    private fun stopSelfSafely() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        AppLogger.i("Download service destroyed")
    }

    companion object {
        private const val ACTION_CANCEL = "com.jmdl.CANCEL_TASK"
        private const val ACTION_PAUSE = "com.jmdl.PAUSE_TASK"
        private const val EXTRA_TASK_ID = "task_id"
        // A whole album can legitimately take much longer than 5 minutes on mobile
        // data. The 60s no-progress watchdog still catches real stalls; this outer
        // timeout is only a last-resort guard for a hung client.
        private const val DOWNLOAD_TIMEOUT_MS = 30 * 60 * 1000L
        private const val TITLE_TIMEOUT_MS = 2 * 60 * 1000L
        private const val STALLED_PROGRESS_MS = 60_000L
        @Volatile private var lastNotifUpdate = 0L
        private const val NOTIF_UPDATE_INTERVAL = 1000L

        fun start(context: Context) {
            val i = Intent(context, DownloadService::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun cancelTask(context: Context, taskId: String) {
            val i = Intent(context, DownloadService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_TASK_ID, taskId)
            }
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun pauseTask(context: Context, taskId: String) {
            val i = Intent(context, DownloadService::class.java).apply {
                action = ACTION_PAUSE
                putExtra(EXTRA_TASK_ID, taskId)
            }
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }
    }
}