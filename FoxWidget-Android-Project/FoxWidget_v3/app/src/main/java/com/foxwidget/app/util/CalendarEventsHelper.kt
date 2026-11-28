package com.foxwidget.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Đọc sự kiện sắp tới từ lịch của máy (Google Calendar, lịch hãng...). Cần quyền READ_CALENDAR. */
object CalendarEventsHelper {

    data class Event(val title: String, val beginMs: Long, val allDay: Boolean)

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Sự kiện từ bây giờ đến hết [days] ngày tới, sắp theo thời gian. */
    fun upcoming(context: Context, days: Int = 7, limit: Int = 5): List<Event> {
        if (!hasPermission(context)) return emptyList()
        val now = System.currentTimeMillis()
        val end = now + days * 24L * 3600L * 1000L
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY
        )
        val out = mutableListOf<Event>()
        try {
            val cursor = CalendarContract.Instances.query(context.contentResolver, projection, now - 3600_000L, end)
            cursor?.use {
                while (it.moveToNext()) {
                    val title = it.getString(0)?.trim().orEmpty().ifBlank { I18n.tr("(Không tiêu đề)") }
                    val begin = it.getLong(1)
                    val endMs = it.getLong(2)
                    val allDay = it.getInt(3) == 1
                    // Bỏ sự kiện đã kết thúc
                    if (endMs < now && !allDay) continue
                    out += Event(title, begin, allDay)
                }
            }
        } catch (_: SecurityException) {
            return emptyList()
        } catch (_: Exception) {
            return emptyList()
        }
        return out.sortedBy { it.beginMs }.take(limit)
    }

    /** "14:30 Họp nhóm", "Hôm nay · Sinh nhật Lan", "Mai 09:00 Khám bệnh". */
    fun format(e: Event): String {
        val cal = Calendar.getInstance().apply { timeInMillis = e.beginMs }
        val today = Calendar.getInstance()
        val diffDays = dayNumber(cal) - dayNumber(today)
        val dayPart = when (diffDays) {
            0 -> ""
            1 -> I18n.tr("Mai ")
            else -> SimpleDateFormat("dd/MM ", Locale.getDefault()).format(cal.time)
        }
        return if (e.allDay) {
            (if (diffDays == 0) I18n.tr("Hôm nay") else dayPart.trim()) + " · " + e.title
        } else {
            dayPart + SimpleDateFormat("HH:mm", Locale.getDefault()).format(cal.time) + " " + e.title
        }
    }

    fun nextLine(context: Context): String {
        val e = upcoming(context, 7, 1).firstOrNull() ?: return ""
        return format(e)
    }

    private fun dayNumber(c: Calendar): Int = c.get(Calendar.YEAR) * 400 + c.get(Calendar.DAY_OF_YEAR)
}
