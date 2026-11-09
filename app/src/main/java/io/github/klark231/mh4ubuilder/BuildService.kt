package io.github.klark231.mh4ubuilder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// keeps the process alive while a build runs, progress in the notification
class BuildService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watcher: Job? = null
    private val nm get() = getSystemService(NotificationManager::class.java)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        nm.createNotificationChannel(NotificationChannel(CH_RUN, getString(R.string.ch_progress), NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_DONE, getString(R.string.ch_done), NotificationManager.IMPORTANCE_DEFAULT))
        val n = running(Builder.state.value)
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID_RUN, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(ID_RUN, n)

        if (watcher == null) watcher = scope.launch {
            Builder.state.collect { s ->
                if (s.phase == Phase.RUNNING) {
                    nm.notify(ID_RUN, running(s))
                    delay(700)
                } else {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    if (s.phase != Phase.IDLE) nm.notify(ID_DONE, finished(s))
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun running(s: BuildState) = NotificationCompat.Builder(this, CH_RUN)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(getString(R.string.n_building))
        .setContentText(stepText(s))
        .setProgress(s.total, s.done, s.total == 0)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(openApp())
        .build()

    private fun finished(s: BuildState) = NotificationCompat.Builder(this, CH_DONE)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(
            when (s.phase) {
                Phase.DONE -> getString(R.string.n_built)
                Phase.CANCELLED -> getString(R.string.dlg_cancelled)
                else -> getString(R.string.dlg_failed)
            },
        )
        .setContentText(if (s.phase == Phase.DONE) getString(R.string.n_saved, s.message) else s.message)
        .setAutoCancel(true)
        .setContentIntent(openApp())
        .build()

    private fun stepText(s: BuildState): String {
        val step = if (s.step != 0) getString(s.step) else ""
        return if (s.total > 0) "$step ${s.done}/${s.total}" else step
    }

    companion object {
        private const val CH_RUN = "build"
        private const val CH_DONE = "build_done"
        private const val ID_RUN = 1
        private const val ID_DONE = 2
    }
}
