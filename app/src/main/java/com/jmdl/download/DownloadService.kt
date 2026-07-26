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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val drainMutex = Mutex()
    private var activeWorker: Future<*>? = null
    private var currentTaskId: String? = null

    private val repo by lazy { TaskRepository.get(this) }
    private val settings by lazy { SettingsStore(this) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            handleCancel(intent)
            return START_NOT_STICKY
        }

        AppLogger.i("Download service started")
        Notifications.ensureChannel(this)
        startForegroundCompat("准备下载…", "", 0, 0, indeterminate = true)
        
        scope.launch {
            drainMutex.withLock { drainLoop() }
            if (repo.nextQueued() == null) stopSelfSafely()
        }
        return START_NOT_STICKY
    }

    private fun handleCancel(intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID).orEmpty()
        if (taskId.isNotEmpty()) {
            repo.cancelTask(taskId)
            AppLogger.i("Download cancel requested taskId=$taskId")
            currentTaskId = null
            activeWorker?.cancel(true)
            activeWorker = null
        }
    }

    private suspend fun drainLoop() {
        while (true) {
            val task = repo.nextQueued() ?: break
            processTask(task.id)
        }
    }

    private fun processTask(taskId: String) {
        val task = repo.byId(taskId) ?: return
        val jmId = task.jmId
        val workDir = File(cacheDir, "work/$taskId")
        runCatching { workDir.deleteRecursively() }

        AppLogger.i("Download start taskId=$taskId jmId=$jmId")
        repo.update(taskId) { it.copy(status = TaskStatus.RUNNING, done = 0, total = 0, message = null) }

        try {
            val title = runWithTimeout(taskId) { JmEngine.getTitle(jmId) }
            repo.update(taskId) { it.copy(title = title) }
            updateNotif(title, "下载中…", 0, 0, indeterminate = true)

            val outcome = runWithTimeout(taskId) {
                var lastTick = 0L
                var lastProgressAt = System.currentTimeMillis()
                var lastReportedDone = 0
                var lastReportedTotal = 0

                JmEngine.download(jmId, workDir) { done, total ->
                    if (repo.isCancelled(taskId)) throw RuntimeException("下载已取消")
                    val now = System.currentTimeMillis()
                    val (safeDone, safeTotal) = JmEngine.calculateSafeProgress(done, total, lastReportedDone, lastReportedTotal)

                    if (safeTotal != lastReportedTotal && lastReportedTotal > 0) {
                        AppLogger.w("Download total changed taskId=$taskId: $lastReportedTotal -> $safeTotal")
                    }

                    if (now - lastTick > 300) {
                        lastTick = now
                        repo.update(taskId, persist = false) {
                            it.copy(status = TaskStatus.RUNNING, done = safeDone, total = safeTotal)
                        }
                        updateNotif(title, "下载中 $safeDone/$safeTotal", safeDone, safeTotal, indeterminate = safeTotal == 0)
                        AppLogger.i("Download progress taskId=$taskId: $safeDone/$safeTotal")
                    }

                    if (safeDone > lastReportedDone || safeTotal != lastReportedTotal) {
                        lastProgressAt = now
                        lastReportedDone = safeDone
                        lastReportedTotal = safeTotal
                    }

                    if (now - lastProgressAt > STALLED_PROGRESS_MS) {
                        throw TimeoutException("下载卡住")
                    }
                }
            }

            if (repo.isCancelled(taskId)) {
                AppLogger.i("Download cancelled before finalize taskId=$taskId")
                return
            }

            repo.update(taskId) { it.copy(done = outcome.success, total = outcome.total) }

            if (outcome.success == 0) {
                val errorMsg = "下载失败（0 张成功）"
                AppLogger.w(errorMsg)
                repo.update(taskId) { it.copy(status = TaskStatus.FAILED, message = errorMsg) }
                return
            }

            repo.update(taskId) { it.copy(status = TaskStatus.ZIPPING) }
            updateNotif(title, "打包中…", 0, 0, indeterminate = true)

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
                )
            }
            AppLogger.i("Download completed taskId=$taskId outcome=$outcome")

        } catch (e: TimeoutException) {
            handleTimeout(taskId)
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
                )
            }
        } catch (e: Exception) {
            handleError(taskId, e)
        } finally {
            cleanup(taskId, workDir)
        }
    }

    private fun handleTimeout(taskId: String) {
        AppLogger.w("Download timeout taskId=$taskId")
        repo.update(taskId) {
            if (repo.isCancelled(taskId)) {
                it.copy(status = TaskStatus.CANCELLED, message = "已取消")
            } else {
                it.copy(status = TaskStatus.FAILED, message = "下载超时（超过 ${DOWNLOAD_TIMEOUT_MINUTES} 分钟）")
            }
        }
    }

    private fun handleCancellation(taskId: String) {
        AppLogger.i("Download cancelled taskId=$taskId")
        repo.update(taskId) {
            it.copy(status = TaskStatus.CANCELLED, message = "已取消")
        }
    }

    private fun handleError(taskId: String, e: Exception) {
        AppLogger.e("Download error taskId=$taskId", e)
        repo.update(taskId) {
            if (repo.isCancelled(taskId)) {
                it.copy(status = TaskStatus.CANCELLED, message = "已取消")
            } else {
                it.copy(status = TaskStatus.FAILED, message = e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun cleanup(taskId: String, workDir: File) {
        currentTaskId = null
        activeWorker = null
        runCatching { workDir.deleteRecursively() }
            .onFailure { AppLogger.w("Failed to delete workDir: ${workDir.path}", it) }
    }

    private fun <T> runWithTimeout(taskId: String, action: () -> T): T {
        val executor = Executors.newSingleThreadExecutor()
        val future = executor.submit<T> {
            currentTaskId = taskId
            action()
        }
        activeWorker = future

        try {
            return future.get(DOWNLOAD_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            future.cancel(true)
            throw e
        } catch (e: InterruptedException) {
            future.cancel(true)
            throw e
        } catch (e: Exception) {
            throw e
        } finally {
            executor.shutdownNow()
            if (activeWorker === future) activeWorker = null
        }
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
        private const val EXTRA_TASK_ID = "task_id"
        private const val DOWNLOAD_TIMEOUT_MS = 5 * 60 * 1000L
        private const val DOWNLOAD_TIMEOUT_MINUTES = 5
        private const val STALLED_PROGRESS_MS = 90_000L

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
    }
}