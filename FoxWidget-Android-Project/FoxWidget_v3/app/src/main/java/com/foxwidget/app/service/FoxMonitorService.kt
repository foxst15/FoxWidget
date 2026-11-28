package com.foxwidget.app.service

import android.app.AlarmManager
import com.foxwidget.app.util.I18n
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.foxwidget.app.MainActivity
import com.foxwidget.app.R
import com.foxwidget.app.util.DeviceTelemetryHelper
import com.foxwidget.app.util.IslandOverlayManager
import com.foxwidget.app.widget.FoxWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class FoxMonitorService : Service() {

    companion object {
        const val CHANNEL_ID = "fox_widget_realtime_channel"
        const val NOTIF_ID = 1001

        fun start(context: Context) {
            try {
                val intent = Intent(context, FoxMonitorService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
            }
            scheduleNextMinuteTick(context)
            schedulePeriodicWeatherWorker(context)
        }

        fun scheduleNextMinuteTick(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, FoxWidgetProvider::class.java).apply {
                action = FoxWidgetProvider.ACTION_MINUTE_TICK
            }
            val pi = PendingIntent.getBroadcast(
                context,
                2002,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val cal = Calendar.getInstance().apply {
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.MINUTE, 1)
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
                } else {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
                }
            } catch (_: Exception) {
                am.set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            }
        }

        fun schedulePeriodicWeatherWorker(context: Context) {
            val req = PeriodicWorkRequestBuilder<WeatherSyncWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "fox_widget_weather_sync",
                ExistingPeriodicWorkPolicy.KEEP,
                req
            )
        }
    }

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var lastBatteryLevel = -1
    private var lastBatteryUpdateMs = 0L

    private val tickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                // Pin báo thay đổi rất dày (nhiệt độ, điện áp...) -> chỉ vẽ lại khi % đổi hoặc đã qua 60 giây
                val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                val now = System.currentTimeMillis()
                if (level == lastBatteryLevel && now - lastBatteryUpdateMs < 60_000L) return
                lastBatteryLevel = level
                lastBatteryUpdateMs = now
                updateNotificationAndWidget()
            } else {
                // TIME_TICK / DATE_CHANGED
                lastBatteryUpdateMs = System.currentTimeMillis()
                updateNotificationAndWidget()
                scheduleNextMinuteTick(context)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildStatusNotification())

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(tickReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(tickReceiver, filter)
        }

        serviceScope.launch {
            while (isActive) {
                DeviceTelemetryHelper.refreshWeatherAndLocation(applicationContext)
                FoxWidgetProvider.updateAllWidgets(applicationContext)
                delay(15 * 60 * 1000L)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        updateNotificationAndWidget()
        return START_STICKY
    }

    private fun updateNotificationAndWidget() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildStatusNotification())
        FoxWidgetProvider.updateAllWidgets(applicationContext)
    }

    private fun buildStatusNotification(): Notification {
        val stats = DeviceTelemetryHelper.getBatteryStats(this)
        val tempFormatted = String.format(Locale.US, "%.1f°C", stats.temperatureC)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_fox)
            .setContentTitle("FoxWidget Liquid Glass · Pin ${stats.levelPercent}% · $tempFormatted")
            .setContentText(I18n.tr("Kính lỏng quang học, Lịch Việt & Đồng hồ realtime đang hoạt động"))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FoxWidget Realtime Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = I18n.tr("Hiển thị % pin, nhiệt độ pin và giữ widget cập nhật đúng giờ")
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(tickReceiver)
        } catch (_: Exception) {
        }
        IslandOverlayManager.remove()
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    class WeatherSyncWorker(
        appContext: Context,
        workerParams: WorkerParameters
    ) : CoroutineWorker(appContext, workerParams) {
        override suspend fun doWork(): Result {
            DeviceTelemetryHelper.refreshWeatherAndLocation(applicationContext)
            FoxWidgetProvider.updateAllWidgets(applicationContext)
            return Result.success()
        }
    }
}
