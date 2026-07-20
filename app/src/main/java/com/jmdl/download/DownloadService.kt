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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Foreground service that drains the task queue one album at a time (serial), mirroring the
 * Python bot's download_lock. Each task: fetch title → download+descramble (lib) → zip to the
 * SAF folder. Progress + status go to [TaskRepository] (drives UI) and the ongoing notification.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val drainMutex = Mutex()

    private val repo by lazy { TaskRepository.get(this) }
    private val settings by lazy { SettingsStore(this) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notifications.ensureChannel(this)
        startForegroundCompat("准备下载…", "", 0, 0, indeterminate = true)
        // Every start (re)launches a drain; the mutex serialises them, so a start that
        // arrives while draining simply runs another (usually empty) pass afterwards —
        // guaranteeing newly-queued tasks are picked up.
        scope.launch {
            drainMutex.withLock { drainLoop() }
            if (repo.nextQueued() == null) stopSelfSafely()
        }
        return START_NOT_STICKY
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

        repo.update(taskId) { it.copy(status = TaskStatus.RUNNING, done = 0, total = 0, message = null) }

        val treeUriStr = settings.saveTreeUri
        if (treeUriStr == null) {
            repo.update(taskId) { it.copy(status = TaskStatus.FAILED, message = "未选择保存目录") }
            return
        }

        try {
            val title = JmEngine.getTitle(jmId)
            repo.update(taskId) { it.copy(title = title) }
            updateNotif(title, "下载中…", 0, 0, indeterminate = true)

            var lastTick = 0L
            val outcome = JmEngine.download(jmId, workDir) { done, total ->
                val now = System.currentTimeMillis()
                if (now - lastTick > 300) {
                    lastTick = now
                    repo.update(taskId, persist = false) {
                        it.copy(status = TaskStatus.RUNNING, done = done, total = total)
                    }
                    updateNotif(title, "下载中 $done/$total", done, total, indeterminate = total == 0)
                }
            }
            repo.update(taskId) { it.copy(done = outcome.success, total = outcome.total) }

            if (outcome.success == 0) {
                repo.update(taskId) { it.copy(status = TaskStatus.FAILED, message = "下载失败（0 张成功）") }
                return
            }

            repo.update(taskId) { it.copy(status = TaskStatus.ZIPPING) }
            updateNotif(title, "打包中…", 0, 0, indeterminate = true)
            val zipName = FilenameSanitizer.zipName(jmId, title)
            val finalName = Zipper.zipToTree(this, workDir, Uri.parse(treeUriStr), zipName)

            val note = if (outcome.failed > 0) "完成（${outcome.failed} 张失败）" else "完成"
            repo.update(taskId) {
                it.copy(
                    status = TaskStatus.DONE,
                    zipName = finalName,
                    saveTreeUri = treeUriStr,
                    message = note,
                )
            }
        } catch (e: Exception) {
            repo.update(taskId) {
                it.copy(status = TaskStatus.FAILED, message = e.message ?: e.javaClass.simpleName)
            }
        } finally {
            runCatching { workDir.deleteRecursively() }
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
    }

    companion object {
        fun start(context: Context) {
            val i = Intent(context, DownloadService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
        }
    }
}
