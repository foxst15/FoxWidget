package com.foxwidget.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.foxwidget.app.MainActivity
import com.foxwidget.app.R
import com.foxwidget.app.data.PrefsManager
import com.foxwidget.app.service.FoxMonitorService
import com.foxwidget.app.util.DeviceTelemetryHelper
import com.foxwidget.app.util.IslandOverlayManager
import com.foxwidget.app.util.LiquidGlassRenderer
import com.foxwidget.app.util.VietLunarCalendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class FoxWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.foxwidget.app.ACTION_REFRESH_WIDGET"
        const val ACTION_MINUTE_TICK = "com.foxwidget.app.ACTION_MINUTE_TICK"

        fun updateAllWidgets(context: Context) {
            // Dynamic Island dùng chung nhịp cập nhật với widget (tự dựng lại nếu tiến trình bị hệ thống dọn)
            IslandOverlayManager.sync(context)
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, FoxWidgetProvider::class.java))
            for (id in ids) {
                val options = manager.getAppWidgetOptions(id)
                updateSingleWidget(context, manager, id, options)
            }
        }

        fun updateSingleWidget(
            context: Context,
            manager: AppWidgetManager,
            appWidgetId: Int,
            options: Bundle? = null
        ) {
            val prefs = PrefsManager(context)
            val views = RemoteViews(context.packageName, R.layout.widget_fox)
            val now = Calendar.getInstance()

            val opts = options ?: manager.getAppWidgetOptions(appWidgetId)
            val widthDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 320).coerceAtLeast(160)
            val heightDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 165).coerceAtLeast(80)
            val isCompactHeight = heightDp < 120

            // Dựng nền Kính Lỏng Quang Học (Optical Liquid Glass) theo đúng tỷ lệ Launcher
            val bgBitmap = LiquidGlassRenderer.renderWidgetSurface(context, prefs, widthDp, heightDp)
            views.setImageViewBitmap(R.id.iv_widget_bg, bgBitmap)

            val isLight = prefs.widgetStyle == PrefsManager.STYLE_LIGHT
            val primaryTextColor = if (isLight) Color.parseColor("#1E2433") else Color.WHITE
            val secondaryTextColor = if (isLight) Color.parseColor("#4E596F") else Color.parseColor("#E6EAF5")
            val mutedTextColor = if (isLight) Color.parseColor("#7A859E") else Color.parseColor("#B8C2DC")

            // Đồng hồ & Lịch Dương / Âm
            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now.time)
            views.setTextViewText(R.id.tv_widget_clock, timeStr)
            views.setTextColor(R.id.tv_widget_clock, primaryTextColor)
            val baseClockSp = if (isCompactHeight) 34f else 44f
            val clockSizeSp = baseClockSp * (prefs.clockFontScale / 100f)
            views.setTextViewTextSize(R.id.tv_widget_clock, TypedValue.COMPLEX_UNIT_SP, clockSizeSp)

            val solarStr = VietLunarCalendar.formatSolarDateFull(now)
            val lunarStr = VietLunarCalendar.formatLunarDateFull(now)
            val dateSizeSp = 12.5f * (prefs.dateFontScale / 100f)

            views.setTextViewText(R.id.tv_widget_solar, solarStr)
            views.setTextColor(R.id.tv_widget_solar, secondaryTextColor)
            views.setTextViewTextSize(R.id.tv_widget_solar, TypedValue.COMPLEX_UNIT_SP, dateSizeSp)

            views.setTextViewText(R.id.tv_widget_lunar, lunarStr)
            views.setTextColor(R.id.tv_widget_lunar, secondaryTextColor)
            views.setTextViewTextSize(R.id.tv_widget_lunar, TypedValue.COMPLEX_UNIT_SP, dateSizeSp)
            views.setViewVisibility(
                R.id.tv_widget_lunar,
                if (prefs.showLunarDate) View.VISIBLE else View.GONE
            )

            // Cột phải: Thiết bị, Thời tiết, Địa danh, % Pin, Nhiệt độ Pin
            val battery = DeviceTelemetryHelper.getBatteryStats(context)
            views.setTextViewText(R.id.tv_widget_device, DeviceTelemetryHelper.getDeviceShortName())
            views.setTextColor(R.id.tv_widget_device, mutedTextColor)
            views.setViewVisibility(
                R.id.tv_widget_device,
                if (prefs.showDeviceName && !isCompactHeight) View.VISIBLE else View.GONE
            )

            views.setTextViewText(R.id.tv_widget_temp, "${prefs.cachedTempC}°")
            views.setTextColor(R.id.tv_widget_temp, primaryTextColor)
            views.setTextViewText(R.id.tv_widget_location, "📍 ${prefs.cachedLocationName}")
            views.setTextColor(R.id.tv_widget_location, secondaryTextColor)

            val weatherVis = if (prefs.showWeather) View.VISIBLE else View.GONE
            views.setViewVisibility(R.id.tv_widget_temp, weatherVis)
            views.setViewVisibility(R.id.tv_widget_location, weatherVis)

            views.setTextViewText(R.id.tv_widget_battery_pct, "🔋 ${battery.levelPercent}%")
            views.setTextColor(R.id.tv_widget_battery_pct, primaryTextColor)
            views.setTextViewText(
                R.id.tv_widget_battery_temp,
                String.format(Locale.US, "🌡️ %.1f°C", battery.temperatureC)
            )
            views.setTextColor(R.id.tv_widget_battery_temp, secondaryTextColor)
            views.setTextColor(R.id.btn_widget_refresh_icon, secondaryTextColor)

            val batVis = if (prefs.showBatteryInfo) View.VISIBLE else View.GONE
            views.setViewVisibility(R.id.tv_widget_battery_pct, batVis)
            views.setViewVisibility(
                R.id.tv_widget_battery_temp,
                if (prefs.showBatteryInfo && !isCompactHeight) View.VISIBLE else View.GONE
            )

            // Dải lịch tuần T2 -> CN
            views.setViewVisibility(
                R.id.ll_week_strip,
                if (prefs.showWeekStrip && !isCompactHeight) View.VISIBLE else View.GONE
            )
            val weekItems = VietLunarCalendar.getCurrentWeekStrip(now)
            val dowIds = intArrayOf(
                R.id.tv_w_dow_0, R.id.tv_w_dow_1, R.id.tv_w_dow_2,
                R.id.tv_w_dow_3, R.id.tv_w_dow_4, R.id.tv_w_dow_5, R.id.tv_w_dow_6
            )
            val solarIds = intArrayOf(
                R.id.tv_w_sol_0, R.id.tv_w_sol_1, R.id.tv_w_sol_2,
                R.id.tv_w_sol_3, R.id.tv_w_sol_4, R.id.tv_w_sol_5, R.id.tv_w_sol_6
            )
            val lunarIds = intArrayOf(
                R.id.tv_w_lun_0, R.id.tv_w_lun_1, R.id.tv_w_lun_2,
                R.id.tv_w_lun_3, R.id.tv_w_lun_4, R.id.tv_w_lun_5, R.id.tv_w_lun_6
            )
            val colIds = intArrayOf(
                R.id.ll_w_col_0, R.id.ll_w_col_1, R.id.ll_w_col_2,
                R.id.ll_w_col_3, R.id.ll_w_col_4, R.id.ll_w_col_5, R.id.ll_w_col_6
            )

            for (i in weekItems.indices) {
                val item = weekItems[i]
                views.setTextViewText(dowIds[i], item.dayOfWeekLabel)
                views.setTextViewText(solarIds[i], item.solarDay.toString())
                views.setTextViewText(lunarIds[i], item.lunarDay.toString())

                views.setTextColor(dowIds[i], mutedTextColor)
                views.setTextColor(solarIds[i], primaryTextColor)
                views.setTextColor(lunarIds[i], mutedTextColor)

                if (item.isToday) {
                    views.setInt(colIds[i], "setBackgroundResource", R.drawable.bg_week_today_pill)
                    views.setTextColor(dowIds[i], Color.WHITE)
                    views.setTextColor(solarIds[i], Color.WHITE)
                    views.setTextColor(lunarIds[i], Color.parseColor("#E8EEFF"))
                } else {
                    views.setInt(colIds[i], "setBackgroundResource", 0)
                }
            }

            // Bấm vào widget mở trang tùy chỉnh FoxWidget
            val openAppIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openAppIntent)

            // Bấm vào nút làm mới nhanh trên widget để cập nhật thời tiết & pin ngay lập tức
            val refreshIntent = Intent(context, FoxWidgetProvider::class.java).apply {
                action = ACTION_REFRESH_WIDGET
            }
            val refreshPending = PendingIntent.getBroadcast(
                context,
                3003,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_widget_refresh_icon, refreshPending)

            manager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        FoxMonitorService.start(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            updateSingleWidget(context, appWidgetManager, id)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateSingleWidget(context, appWidgetManager, appWidgetId, newOptions)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_MINUTE_TICK -> {
                FoxMonitorService.scheduleNextMinuteTick(context)
                updateAllWidgets(context)
            }
            ACTION_REFRESH_WIDGET -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        DeviceTelemetryHelper.refreshWeatherAndLocation(context)
                        updateAllWidgets(context)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            else -> updateAllWidgets(context)
        }
    }
}
