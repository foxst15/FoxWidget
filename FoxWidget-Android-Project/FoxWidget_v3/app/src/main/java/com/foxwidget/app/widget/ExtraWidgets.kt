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
import com.foxwidget.app.util.CalendarEventsHelper
import com.foxwidget.app.util.LiquidGlassRenderer
import com.foxwidget.app.util.VietHolidays
import com.foxwidget.app.util.VietLunarCalendar
import com.foxwidget.app.util.WeatherHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

/** Phần dùng chung của các widget bổ sung (đồng hồ, thời tiết, lịch tháng). */
object WidgetCommon {

    class Palette(val primary: Int, val secondary: Int, val muted: Int)

    fun palette(prefs: PrefsManager): Palette {
        val light = prefs.widgetStyle == PrefsManager.STYLE_LIGHT
        return if (light) {
            Palette(Color.parseColor("#1E2433"), Color.parseColor("#4E596F"), Color.parseColor("#7A859E"))
        } else {
            Palette(Color.WHITE, Color.parseColor("#E6EAF5"), Color.parseColor("#B8C2DC"))
        }
    }

    fun sizeDp(manager: AppWidgetManager, id: Int, options: Bundle?, defW: Int, defH: Int): Pair<Int, Int> {
        val o = options ?: manager.getAppWidgetOptions(id)
        val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, defW).coerceAtLeast(100)
        val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, defH).coerceAtLeast(60)
        return Pair(w, h)
    }

    fun applyBackground(context: Context, prefs: PrefsManager, views: RemoteViews, wDp: Int, hDp: Int) {
        val bmp = LiquidGlassRenderer.renderWidgetSurface(context, prefs, wDp, hDp)
        views.setImageViewBitmap(R.id.iv_widget_bg, bmp)
    }

    fun openApp(context: Context, views: RemoteViews) {
        val pi = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pi)
    }

    fun refreshPending(context: Context): PendingIntent {
        val i = Intent(context, FoxWidgetProvider::class.java).apply { action = FoxWidgetProvider.ACTION_REFRESH_WIDGET }
        return PendingIntent.getBroadcast(context, 3003, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun id(context: Context, name: String): Int = context.resources.getIdentifier(name, "id", context.packageName)

    fun setColors(views: RemoteViews, ids: IntArray, color: Int) {
        for (i in ids) views.setTextColor(i, color)
    }

    /** Dòng phụ: ngày lễ hôm nay + sự kiện sắp tới; không có thì Can Chi hoặc ngày lễ tiếp theo. */
    fun extraLine(context: Context, prefs: PrefsManager, now: Calendar): String {
        val holiday = if (prefs.showHolidayLine) VietHolidays.todayLine(now) else ""
        val event = if (prefs.showNextEvent) CalendarEventsHelper.nextLine(context) else ""
        val joined = listOf(holiday, event).filter { it.isNotEmpty() }.joinToString(" · ")
        if (joined.isNotEmpty()) return joined
        if (prefs.showCanChi) {
            val l = VietLunarCalendar.convertSolar2Lunar(
                now.get(Calendar.DAY_OF_MONTH), now.get(Calendar.MONTH) + 1, now.get(Calendar.YEAR)
            )
            return "Ngày ${VietLunarCalendar.getDayCanChi(now)} · Năm ${VietLunarCalendar.getYearCanChi(l.year)}"
        }
        if (prefs.showHolidayLine) return VietHolidays.nextMajor(now)
        return ""
    }

    fun tempNum(c: Double, unit: Int): Int =
        (if (unit == PrefsManager.TEMP_F) c * 9.0 / 5.0 + 32.0 else c).roundToInt()

    /** Cập nhật mọi widget bổ sung (gọi cùng nhịp với widget chính). */
    fun updateAll(context: Context) {
        FoxClockWidgetProvider.updateAll(context)
        FoxWeatherWidgetProvider.updateAll(context)
        FoxCalendarWidgetProvider.updateAll(context)
    }
}

/** Khung chung: dựng lại khi cập nhật / đổi kích thước, và khởi động dịch vụ nền khi có widget đầu tiên. */
abstract class BaseExtraWidgetProvider : AppWidgetProvider() {

    abstract fun render(context: Context, manager: AppWidgetManager, id: Int, options: Bundle?)

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        FoxMonitorService.start(context)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) render(context, manager, id, null)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        super.onAppWidgetOptionsChanged(context, manager, id, newOptions)
        render(context, manager, id, newOptions)
    }
}

// ============================================================================================ ĐỒNG HỒ

class FoxClockWidgetProvider : BaseExtraWidgetProvider() {

    companion object {
        fun updateAll(context: Context) {
            val m = AppWidgetManager.getInstance(context)
            val ids = m.getAppWidgetIds(ComponentName(context, FoxClockWidgetProvider::class.java))
            val p = FoxClockWidgetProvider()
            for (id in ids) p.render(context, m, id, null)
        }
    }

    override fun render(context: Context, manager: AppWidgetManager, id: Int, options: Bundle?) {
        val prefs = PrefsManager(context)
        val views = RemoteViews(context.packageName, R.layout.widget_clock)
        val (w, h) = WidgetCommon.sizeDp(manager, id, options, 150, 150)
        val pal = WidgetCommon.palette(prefs)
        val now = Calendar.getInstance()

        WidgetCommon.applyBackground(context, prefs, views, w, h)

        val pattern = if (prefs.clock24h) "HH:mm" else "h:mm a"
        views.setTextViewText(R.id.tv_clock, SimpleDateFormat(pattern, Locale.getDefault()).format(now.time))
        val baseSp = when {
            h < 100 -> 32f
            w < 170 -> 40f
            else -> 50f
        }
        views.setTextViewTextSize(R.id.tv_clock, TypedValue.COMPLEX_UNIT_SP, baseSp * prefs.clockFontScale / 100f)
        val dateSp = 13f * prefs.dateFontScale / 100f

        views.setTextViewText(R.id.tv_date, VietLunarCalendar.formatSolarDateFull(now))
        views.setTextViewTextSize(R.id.tv_date, TypedValue.COMPLEX_UNIT_SP, dateSp)
        views.setTextViewText(R.id.tv_lunar, VietLunarCalendar.formatLunarDateFull(now))
        views.setTextViewTextSize(R.id.tv_lunar, TypedValue.COMPLEX_UNIT_SP, dateSp - 1f)
        views.setViewVisibility(R.id.tv_lunar, if (prefs.showLunarDate) View.VISIBLE else View.GONE)

        val extra = if (h >= 90) WidgetCommon.extraLine(context, prefs, now) else ""
        views.setTextViewText(R.id.tv_extra, extra)
        views.setViewVisibility(R.id.tv_extra, if (extra.isEmpty()) View.GONE else View.VISIBLE)

        views.setTextColor(R.id.tv_clock, pal.primary)
        views.setTextColor(R.id.tv_date, pal.secondary)
        views.setTextColor(R.id.tv_lunar, pal.secondary)
        views.setTextColor(R.id.tv_extra, pal.muted)

        WidgetCommon.openApp(context, views)
        manager.updateAppWidget(id, views)
    }
}

// ============================================================================================ THỜI TIẾT

class FoxWeatherWidgetProvider : BaseExtraWidgetProvider() {

    companion object {
        fun updateAll(context: Context) {
            val m = AppWidgetManager.getInstance(context)
            val ids = m.getAppWidgetIds(ComponentName(context, FoxWeatherWidgetProvider::class.java))
            val p = FoxWeatherWidgetProvider()
            for (id in ids) p.render(context, m, id, null)
        }
    }

    override fun render(context: Context, manager: AppWidgetManager, id: Int, options: Bundle?) {
        val prefs = PrefsManager(context)
        val views = RemoteViews(context.packageName, R.layout.widget_weather)
        val (w, h) = WidgetCommon.sizeDp(manager, id, options, 300, 190)
        val pal = WidgetCommon.palette(prefs)
        val unit = prefs.tempUnit
        val snap = WeatherHelper.load(context)

        WidgetCommon.applyBackground(context, prefs, views, w, h)

        if (snap == null) {
            views.setTextViewText(R.id.tv_w_icon, "🌡️")
            views.setTextViewText(R.id.tv_w_temp, "${prefs.cachedTempC}°")
            views.setTextViewText(R.id.tv_w_desc, prefs.cachedWeatherDesc)
            views.setTextViewText(R.id.tv_w_hilo, "Chạm biểu tượng để tải thời tiết")
        } else {
            views.setTextViewText(R.id.tv_w_icon, WeatherHelper.emoji(snap.code, snap.isDay))
            views.setTextViewText(R.id.tv_w_temp, WeatherHelper.formatTemp(snap.tempC, unit))
            views.setTextViewText(R.id.tv_w_desc, WeatherHelper.describe(snap.code, snap.isDay))
            views.setTextViewText(
                R.id.tv_w_hilo,
                "Cao ${WeatherHelper.formatTemp(snap.hiC, unit)} · Thấp ${WeatherHelper.formatTemp(snap.loC, unit)}"
            )
        }
        views.setTextViewText(R.id.tv_w_loc, "📍 ${prefs.cachedLocationName}")

        // Chi tiết
        val detail = if (snap != null && prefs.showWeatherDetail && h >= 100) {
            listOfNotNull(
                "Cảm giác ${WeatherHelper.formatTemp(snap.feelsC, unit)}",
                if (snap.humidity >= 0) "Ẩm ${snap.humidity}%" else null,
                "Gió ${snap.windKmh.roundToInt()} km/h",
                if (snap.rainProb >= 0) "Mưa ${snap.rainProb}%" else null,
                if (snap.uv >= 0) "UV ${snap.uv.roundToInt()} (${WeatherHelper.uvLabel(snap.uv)})" else null,
                if (snap.aqi >= 0) "AQI ${snap.aqi} (${WeatherHelper.aqiLabel(snap.aqi)})" else null
            ).joinToString(" · ")
        } else ""
        views.setTextViewText(R.id.tv_w_detail, detail)
        views.setViewVisibility(R.id.tv_w_detail, if (detail.isEmpty()) View.GONE else View.VISIBLE)

        // Theo giờ: 6 mốc cách nhau 2 giờ
        val showHourly = snap != null && snap.hourly.size >= 2 && h >= 170
        views.setViewVisibility(R.id.ll_hourly, if (showHourly) View.VISIBLE else View.GONE)
        if (showHourly && snap != null) {
            for (i in 0 until 6) {
                val hour = snap.hourly.getOrNull(i * 2)
                val vis = if (hour != null) View.VISIBLE else View.INVISIBLE
                views.setViewVisibility(WidgetCommon.id(context, "ll_h_$i"), vis)
                if (hour != null) {
                    views.setTextViewText(WidgetCommon.id(context, "tv_h_t_$i"), if (i == 0) "Giờ này" else hour.label)
                    views.setTextViewText(WidgetCommon.id(context, "tv_h_i_$i"), WeatherHelper.emoji(hour.code, true))
                    views.setTextViewText(
                        WidgetCommon.id(context, "tv_h_v_$i"), "${WidgetCommon.tempNum(hour.tempC, unit)}°"
                    )
                }
            }
        }

        // Nhiều ngày: 3 / 5 / 7
        val showDaily = snap != null && snap.daily.isNotEmpty() && h >= 135
        views.setViewVisibility(R.id.ll_daily, if (showDaily) View.VISIBLE else View.GONE)
        if (showDaily && snap != null) {
            val n = prefs.forecastDays.coerceIn(3, 7)
            for (i in 0 until 7) {
                val day = if (i < n) snap.daily.getOrNull(i) else null
                // GONE để các cột còn lại tự giãn đều
                views.setViewVisibility(WidgetCommon.id(context, "ll_d_$i"), if (day != null) View.VISIBLE else View.GONE)
                if (day != null) {
                    views.setTextViewText(WidgetCommon.id(context, "tv_d_t_$i"), if (i == 0) "Nay" else day.label)
                    views.setTextViewText(WidgetCommon.id(context, "tv_d_i_$i"), WeatherHelper.emoji(day.code, true))
                    views.setTextViewText(
                        WidgetCommon.id(context, "tv_d_v_$i"),
                        "${WidgetCommon.tempNum(day.hiC, unit)}/${WidgetCommon.tempNum(day.loC, unit)}"
                    )
                }
            }
        }

        // Chân: nguồn + giờ cập nhật + bình minh/hoàng hôn
        val footer = if (snap != null && h >= 150) {
            val upd = SimpleDateFormat("HH:mm", Locale.getDefault()).format(snap.updatedAt)
            listOfNotNull(
                if (snap.sunrise.isNotBlank()) "🌅 ${snap.sunrise}" else null,
                if (snap.sunset.isNotBlank()) "🌇 ${snap.sunset}" else null,
                "Cập nhật $upd · ${snap.source}"
            ).joinToString("  ")
        } else ""
        views.setTextViewText(R.id.tv_w_footer, footer)
        views.setViewVisibility(R.id.tv_w_footer, if (footer.isEmpty()) View.GONE else View.VISIBLE)

        // Màu chữ theo kiểu nền
        views.setTextColor(R.id.tv_w_temp, pal.primary)
        views.setTextColor(R.id.tv_w_desc, pal.primary)
        views.setTextColor(R.id.tv_w_loc, pal.secondary)
        views.setTextColor(R.id.tv_w_hilo, pal.muted)
        views.setTextColor(R.id.tv_w_detail, pal.secondary)
        views.setTextColor(R.id.tv_w_footer, pal.muted)
        for (i in 0 until 6) {
            views.setTextColor(WidgetCommon.id(context, "tv_h_t_$i"), pal.muted)
            views.setTextColor(WidgetCommon.id(context, "tv_h_v_$i"), pal.primary)
        }
        for (i in 0 until 7) {
            views.setTextColor(WidgetCommon.id(context, "tv_d_t_$i"), pal.muted)
            views.setTextColor(WidgetCommon.id(context, "tv_d_v_$i"), pal.primary)
        }

        WidgetCommon.openApp(context, views)
        // Chạm biểu tượng / nhiệt độ để tải lại thời tiết ngay
        val refresh = WidgetCommon.refreshPending(context)
        views.setOnClickPendingIntent(R.id.tv_w_icon, refresh)
        views.setOnClickPendingIntent(R.id.tv_w_temp, refresh)
        manager.updateAppWidget(id, views)
    }
}

// ============================================================================================ LỊCH THÁNG

class FoxCalendarWidgetProvider : BaseExtraWidgetProvider() {

    companion object {
        private const val ACTION_PREV = "com.foxwidget.app.ACTION_CAL_PREV"
        private const val ACTION_NEXT = "com.foxwidget.app.ACTION_CAL_NEXT"
        private const val ACTION_TODAY = "com.foxwidget.app.ACTION_CAL_TODAY"
        private const val STATE = "fox_calendar_state"

        private fun offset(context: Context): Int =
            context.getSharedPreferences(STATE, Context.MODE_PRIVATE).getInt("offset", 0)

        private fun setOffset(context: Context, v: Int) {
            context.getSharedPreferences(STATE, Context.MODE_PRIVATE).edit().putInt("offset", v.coerceIn(-240, 240)).apply()
        }

        fun updateAll(context: Context) {
            val m = AppWidgetManager.getInstance(context)
            val ids = m.getAppWidgetIds(ComponentName(context, FoxCalendarWidgetProvider::class.java))
            val p = FoxCalendarWidgetProvider()
            for (id in ids) p.render(context, m, id, null)
        }

        private fun pending(context: Context, action: String, code: Int): PendingIntent {
            val i = Intent(context, FoxCalendarWidgetProvider::class.java).apply { this.action = action }
            return PendingIntent.getBroadcast(context, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PREV -> { setOffset(context, offset(context) - 1); updateAll(context) }
            ACTION_NEXT -> { setOffset(context, offset(context) + 1); updateAll(context) }
            ACTION_TODAY -> { setOffset(context, 0); updateAll(context) }
            else -> super.onReceive(context, intent)
        }
    }

    override fun render(context: Context, manager: AppWidgetManager, id: Int, options: Bundle?) {
        val prefs = PrefsManager(context)
        val views = RemoteViews(context.packageName, R.layout.widget_calendar)
        val (w, h) = WidgetCommon.sizeDp(manager, id, options, 300, 300)
        val pal = WidgetCommon.palette(prefs)
        val off = offset(context)
        val today = Calendar.getInstance()

        WidgetCommon.applyBackground(context, prefs, views, w, h)

        val first = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, off)
        }
        val month = first.get(Calendar.MONTH) + 1
        val year = first.get(Calendar.YEAR)
        val dim = first.getActualMaximum(Calendar.DAY_OF_MONTH)
        val dow = first.get(Calendar.DAY_OF_WEEK)
        val monday = prefs.calendarWeekStartMonday
        val startIdx = if (monday) (dow + 5) % 7 else dow - 1
        val showLunar = prefs.calendarShowLunar && h >= 220

        views.setTextViewText(R.id.tv_cal_title, "Tháng $month · $year")
        views.setTextColor(R.id.tv_cal_title, pal.primary)
        for (c in intArrayOf(R.id.tv_cal_prev, R.id.tv_cal_next, R.id.tv_cal_today)) views.setTextColor(c, pal.primary)

        // Dòng phụ: Can Chi của năm âm lịch + ngày âm hôm nay (khi đang xem tháng hiện tại)
        val mid = VietLunarCalendar.convertSolar2Lunar(15, month, year)
        views.setTextViewText(
            R.id.tv_cal_sub,
            "Năm ${VietLunarCalendar.getYearCanChi(mid.year)}" +
                if (off == 0) " · ${VietLunarCalendar.formatLunarDateFull(today)}" else ""
        )
        views.setTextColor(R.id.tv_cal_sub, pal.muted)

        // Tên thứ
        val labels = if (monday) arrayOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
        else arrayOf("CN", "T2", "T3", "T4", "T5", "T6", "T7")
        for (i in 0 until 7) {
            val vid = WidgetCommon.id(context, "tv_cal_dow_$i")
            views.setTextViewText(vid, labels[i])
            views.setTextColor(vid, pal.muted)
        }

        // Lưới ngày
        val rows = if (startIdx + dim > 35) 6 else 5
        views.setViewVisibility(R.id.ll_cal_row_5, if (rows == 6) View.VISIBLE else View.GONE)
        val sundayCol = if (monday) 6 else 0
        val holidayColor = Color.parseColor("#FF8A80")
        for (n in 0 until 42) {
            val sId = WidgetCommon.id(context, "tv_cell_s_$n")
            val lId = WidgetCommon.id(context, "tv_cell_l_$n")
            val cId = WidgetCommon.id(context, "ll_cell_$n")
            val day = n - startIdx + 1
            if (day !in 1..dim) {
                views.setTextViewText(sId, "")
                views.setTextViewText(lId, "")
                views.setInt(cId, "setBackgroundResource", 0)
                continue
            }
            val date = Calendar.getInstance().apply { set(year, month - 1, day) }
            val lunar = VietLunarCalendar.convertSolar2Lunar(day, month, year)
            val isToday = off == 0 && day == today.get(Calendar.DAY_OF_MONTH)
            val isOff = VietHolidays.holidaysOn(date).any { it.isDayOff }
            val isSunday = n % 7 == sundayCol

            views.setTextViewText(sId, day.toString())
            views.setTextViewText(
                lId,
                if (!showLunar) "" else if (lunar.day == 1) "${lunar.day}/${lunar.month}" else lunar.day.toString()
            )
            if (isToday) {
                views.setInt(cId, "setBackgroundResource", R.drawable.bg_week_today_pill)
                views.setTextColor(sId, Color.WHITE)
                views.setTextColor(lId, Color.parseColor("#E8EEFF"))
            } else {
                views.setInt(cId, "setBackgroundResource", 0)
                views.setTextColor(sId, if (isOff || isSunday) holidayColor else pal.primary)
                views.setTextColor(lId, if (lunar.day == 1) pal.secondary else pal.muted)
            }
        }

        // Chân: hôm nay / ngày lễ trong tháng
        val footer = if (h < 200) "" else if (off == 0) {
            val t = VietHolidays.todayLine(today)
            if (t.isNotEmpty()) "🎉 Hôm nay: $t" else VietHolidays.nextMajor(today).let { if (it.isEmpty()) "" else "🎉 $it" }
        } else {
            val list = mutableListOf<String>()
            for (d in 1..dim) {
                val date = Calendar.getInstance().apply { set(year, month - 1, d) }
                VietHolidays.holidaysOn(date).filter { it.isDayOff }.forEach { list += "$d/$month ${it.name}" }
            }
            if (list.isEmpty()) "" else "🎉 " + list.take(3).joinToString(" · ")
        }
        views.setTextViewText(R.id.tv_cal_footer, footer)
        views.setTextColor(R.id.tv_cal_footer, pal.secondary)
        views.setViewVisibility(R.id.tv_cal_footer, if (footer.isEmpty()) View.GONE else View.VISIBLE)

        WidgetCommon.openApp(context, views)
        views.setOnClickPendingIntent(R.id.tv_cal_prev, pending(context, ACTION_PREV, 5101))
        views.setOnClickPendingIntent(R.id.tv_cal_next, pending(context, ACTION_NEXT, 5102))
        views.setOnClickPendingIntent(R.id.tv_cal_today, pending(context, ACTION_TODAY, 5103))
        manager.updateAppWidget(id, views)
    }
}
