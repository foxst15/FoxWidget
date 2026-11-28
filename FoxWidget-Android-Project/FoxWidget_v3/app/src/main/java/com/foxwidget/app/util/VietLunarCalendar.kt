package com.foxwidget.app.util

import java.util.Calendar
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

object VietLunarCalendar {

    data class LunarDate(
        val day: Int,
        val month: Int,
        val year: Int,
        val isLeap: Boolean
    )

    data class WeekDayItem(
        val dayOfWeekLabel: String,
        val solarDay: Int,
        val lunarDay: Int,
        val isToday: Boolean
    )

    private val CAN = arrayOf("Giáp", "Ất", "Bính", "Đinh", "Mậu", "Kỷ", "Canh", "Tân", "Nhâm", "Quý")
    private val CHI = arrayOf("Tý", "Sửu", "Dần", "Mão", "Thìn", "Tỵ", "Ngọ", "Mùi", "Thân", "Dậu", "Tuất", "Hợi")

    private fun jdFromDate(dd: Int, mm: Int, yy: Int): Int {
        val a = (14 - mm) / 12
        val y = yy + 4800 - a
        val m = mm + 12 * a - 3
        var jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045
        if (jd < 2299161) {
            jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - 32083
        }
        return jd
    }

    private fun getNewMoonDay(k: Int, timeZone: Double): Int {
        val t = k / 1236.85
        val t2 = t * t
        val t3 = t2 * t
        val dr = PI / 180.0
        var jd1 = 2415020.75933 + 29.53058868 * k + 0.0001178 * t2 - 0.000000155 * t3
        jd1 += 0.00033 * sin((166.56 + 132.87 * t - 0.009173 * t2) * dr)
        val m = 359.2242 + 29.10535608 * k - 0.0000333 * t2 - 0.00000347 * t3
        val mpr = 306.0253 + 385.81691806 * k + 0.0107306 * t2 + 0.00001236 * t3
        val f = 21.2964 + 390.67050646 * k - 0.0016528 * t2 - 0.00000239 * t3
        var c1 = (0.1734 - 0.000393 * t) * sin(m * dr) + 0.0021 * sin(2 * dr * m)
        c1 = c1 - 0.4068 * sin(mpr * dr) + 0.0161 * sin(dr * 2 * mpr)
        c1 -= 0.0004 * sin(dr * 3 * mpr)
        c1 = c1 + 0.0104 * sin(dr * 2 * f) - 0.0051 * sin(dr * (m + mpr))
        c1 = c1 - 0.0074 * sin(dr * (m - mpr)) + 0.0004 * sin(dr * (2 * f + m))
        c1 = c1 - 0.0004 * sin(dr * (2 * f - m)) - 0.0006 * sin(dr * (2 * f + mpr))
        c1 = c1 + 0.0010 * sin(dr * (2 * f - mpr)) + 0.0005 * sin(dr * (2 * mpr + m))
        val deltat = if (t < -11) {
            0.001 + 0.000839 * t + 0.0002261 * t2 - 0.00000845 * t3 - 0.000000081 * t * t3
        } else {
            -0.000278 + 0.000265 * t + 0.000262 * t2
        }
        val jdNew = jd1 + c1 - deltat
        return floor(jdNew + 0.5 + timeZone / 24.0).toInt()
    }

    private fun getSunLongitude(jdn: Int, timeZone: Double): Int {
        val t = (jdn - 2451545.5 - timeZone / 24.0) / 36525.0
        val t2 = t * t
        val dr = PI / 180.0
        val m = 357.52910 + 35999.05030 * t - 0.0001559 * t2 - 0.00000048 * t * t2
        val l0 = 280.46645 + 36000.76983 * t + 0.0003032 * t2
        var dl = (1.914600 - 0.004817 * t - 0.000014 * t2) * sin(dr * m)
        dl += (0.019993 - 0.000101 * t) * sin(dr * 2 * m) + 0.000290 * sin(dr * 3 * m)
        var l = l0 + dl
        l *= dr
        l -= PI * 2 * floor(l / (PI * 2))
        return floor(l / PI * 6).toInt()
    }

    private fun getLunarMonth11(yy: Int, timeZone: Double): Int {
        val off = jdFromDate(31, 12, yy) - 2415021
        val k = floor(off / 29.530588853).toInt()
        var nm = getNewMoonDay(k, timeZone)
        val sunLong = getSunLongitude(nm, timeZone)
        if (sunLong >= 9) {
            nm = getNewMoonDay(k - 1, timeZone)
        }
        return nm
    }

    private fun getLeapMonthOffset(a11: Int, timeZone: Double): Int {
        val k = floor((a11 - 2415021.076998695) / 29.530588853 + 0.5).toInt()
        var last: Int
        var i = 1
        var arc = getSunLongitude(getNewMoonDay(k + i, timeZone), timeZone)
        do {
            last = arc
            i++
            arc = getSunLongitude(getNewMoonDay(k + i, timeZone), timeZone)
        } while (arc != last && i < 14)
        return i - 1
    }

    fun convertSolar2Lunar(dd: Int, mm: Int, yy: Int, timeZone: Double = 7.0): LunarDate {
        val dayNumber = jdFromDate(dd, mm, yy)
        val k = floor((dayNumber - 2415021.076998695) / 29.530588853).toInt()
        var monthStart = getNewMoonDay(k + 1, timeZone)
        if (monthStart > dayNumber) {
            monthStart = getNewMoonDay(k, timeZone)
        }
        var a11 = getLunarMonth11(yy, timeZone)
        var b11 = a11
        var lunarYear: Int
        if (a11 >= monthStart) {
            lunarYear = yy
            a11 = getLunarMonth11(yy - 1, timeZone)
        } else {
            lunarYear = yy + 1
            b11 = getLunarMonth11(yy + 1, timeZone)
        }
        val lunarDay = dayNumber - monthStart + 1
        val diff = floor((monthStart - a11) / 29.0).toInt()
        var lunarLeap = false
        var lunarMonth = diff + 11
        if (b11 - a11 > 365) {
            val leapMonthDiff = getLeapMonthOffset(a11, timeZone)
            if (diff >= leapMonthDiff) {
                lunarMonth = diff + 10
                if (diff == leapMonthDiff) {
                    lunarLeap = true
                }
            }
        }
        if (lunarMonth > 12) {
            lunarMonth -= 12
        }
        if (lunarMonth >= 11 && diff < 4) {
            lunarYear -= 1
        }
        return LunarDate(lunarDay, lunarMonth, lunarYear, lunarLeap)
    }

    fun getYearCanChi(lunarYear: Int): String {
        val can = CAN[(lunarYear + 6) % 10]
        val chi = CHI[(lunarYear + 8) % 12]
        return "$can $chi"
    }

    fun getDayCanChi(cal: Calendar): String {
        val jd = jdFromDate(
            cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.YEAR)
        )
        val can = CAN[(jd + 9) % 10]
        val chi = CHI[(jd + 1) % 12]
        return "$can $chi"
    }

    /** Thứ trong tuần theo ngôn ngữ đang chọn (tên hàm giữ nguyên để không đổi chỗ gọi). */
    fun getVietnameseDayOfWeek(cal: Calendar): String {
        return I18n.tr(
            when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> "Thứ Hai"
                Calendar.TUESDAY -> "Thứ Ba"
                Calendar.WEDNESDAY -> "Thứ Tư"
                Calendar.THURSDAY -> "Thứ Năm"
                Calendar.FRIDAY -> "Thứ Sáu"
                Calendar.SATURDAY -> "Thứ Bảy"
                else -> "Chủ Nhật"
            }
        )
    }

    val EN_MONTHS = arrayOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    fun formatSolarDateFull(cal: Calendar): String {
        val dow = getVietnameseDayOfWeek(cal)
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val month = cal.get(Calendar.MONTH) + 1
        return if (I18n.isEnglish) "$dow, ${EN_MONTHS[month - 1]} $day" else "$dow, $day tháng $month"
    }

    fun formatLunarDateFull(cal: Calendar): String {
        val lunar = convertSolar2Lunar(
            cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.YEAR)
        )
        val leapText = if (lunar.isLeap) I18n.tr(" (nhuận)") else ""
        return I18n.tf("Âm lịch: {0} tháng {1}{2}", lunar.day, lunar.month, leapText)
    }

    fun getCurrentWeekStrip(now: Calendar = Calendar.getInstance()): List<WeekDayItem> {
        val todayDay = now.get(Calendar.DAY_OF_MONTH)
        val todayMonth = now.get(Calendar.MONTH)
        val todayYear = now.get(Calendar.YEAR)

        val cursor = now.clone() as Calendar
        val dow = cursor.get(Calendar.DAY_OF_WEEK)
        val offsetToMonday = if (dow == Calendar.SUNDAY) -6 else (Calendar.MONDAY - dow)
        cursor.add(Calendar.DAY_OF_MONTH, offsetToMonday)

        val labels = arrayOf("T2", "T3", "T4", "T5", "T6", "T7", "CN").map { I18n.tr(it) }
        val list = mutableListOf<WeekDayItem>()
        for (i in 0 until 7) {
            val d = cursor.get(Calendar.DAY_OF_MONTH)
            val m = cursor.get(Calendar.MONTH) + 1
            val y = cursor.get(Calendar.YEAR)
            val lunar = convertSolar2Lunar(d, m, y)
            val isToday = (d == todayDay && cursor.get(Calendar.MONTH) == todayMonth && y == todayYear)
            list.add(
                WeekDayItem(
                    dayOfWeekLabel = labels[i],
                    solarDay = d,
                    lunarDay = lunar.day,
                    isToday = isToday
                )
            )
            cursor.add(Calendar.DAY_OF_MONTH, 1)
        }
        return list
    }
}
