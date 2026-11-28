package com.foxwidget.app.util

import java.util.Calendar

/**
 * Ngày lễ & ngày đặc biệt của Việt Nam (dương lịch + âm lịch), kèm Can Chi tháng và đếm ngược sự kiện tới.
 */
object VietHolidays {

    data class Holiday(val name: String, val isDayOff: Boolean = false)

    private val SOLAR = mapOf(
        (1 to 1) to Holiday("Tết Dương lịch", true),
        (14 to 2) to Holiday("Valentine"),
        (8 to 3) to Holiday("Quốc tế Phụ nữ"),
        (30 to 4) to Holiday("Giải phóng miền Nam", true),
        (1 to 5) to Holiday("Quốc tế Lao động", true),
        (19 to 5) to Holiday("Sinh nhật Bác Hồ"),
        (1 to 6) to Holiday("Quốc tế Thiếu nhi"),
        (27 to 7) to Holiday("Thương binh Liệt sĩ"),
        (2 to 9) to Holiday("Quốc khánh", true),
        (3 to 9) to Holiday("Nghỉ Quốc khánh", true),
        (20 to 10) to Holiday("Phụ nữ Việt Nam"),
        (20 to 11) to Holiday("Nhà giáo Việt Nam"),
        (22 to 12) to Holiday("Quân đội Nhân dân VN"),
        (24 to 12) to Holiday("Đêm Giáng sinh"),
        (25 to 12) to Holiday("Giáng sinh")
    )

    // (ngày âm, tháng âm)
    private val LUNAR = mapOf(
        (1 to 1) to Holiday("Tết Nguyên Đán", true),
        (2 to 1) to Holiday("Mùng 2 Tết", true),
        (3 to 1) to Holiday("Mùng 3 Tết", true),
        (15 to 1) to Holiday("Rằm tháng Giêng"),
        (3 to 3) to Holiday("Tết Hàn Thực"),
        (10 to 3) to Holiday("Giỗ Tổ Hùng Vương", true),
        (15 to 4) to Holiday("Lễ Phật Đản"),
        (5 to 5) to Holiday("Tết Đoan Ngọ"),
        (15 to 7) to Holiday("Lễ Vu Lan"),
        (15 to 8) to Holiday("Tết Trung Thu"),
        (9 to 9) to Holiday("Tết Trùng Cửu"),
        (23 to 12) to Holiday("Ông Táo chầu trời")
    )

    /** Danh sách ngày lễ rơi vào đúng ngày [cal]. */
    fun holidaysOn(cal: Calendar): List<Holiday> {
        val d = cal.get(Calendar.DAY_OF_MONTH)
        val m = cal.get(Calendar.MONTH) + 1
        val y = cal.get(Calendar.YEAR)
        val out = mutableListOf<Holiday>()
        SOLAR[d to m]?.let { out += it }

        val lunar = VietLunarCalendar.convertSolar2Lunar(d, m, y)
        if (!lunar.isLeap) {
            LUNAR[lunar.day to lunar.month]?.let { out += it }
            if (lunar.day == 1 && lunar.month != 1) out += Holiday("Mùng 1 âm lịch")
            if (lunar.day == 15 && out.none { it.name.startsWith("Rằm") || it.name.startsWith("Tết Trung") || it.name.startsWith("Lễ Vu") || it.name.startsWith("Lễ Phật") }) {
                out += Holiday("Rằm")
            }
        }
        // Giao thừa: ngày mai là mùng 1 tháng Giêng
        val tomorrow = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
        val tl = VietLunarCalendar.convertSolar2Lunar(
            tomorrow.get(Calendar.DAY_OF_MONTH), tomorrow.get(Calendar.MONTH) + 1, tomorrow.get(Calendar.YEAR)
        )
        if (tl.day == 1 && tl.month == 1 && !tl.isLeap) out += Holiday("Giao thừa", true)
        return out
    }

    /** Một dòng gọn cho hôm nay, ví dụ "Rằm · Lễ Vu Lan". Rỗng nếu không có gì đặc biệt. */
    fun todayLine(cal: Calendar = Calendar.getInstance()): String =
        holidaysOn(cal).joinToString(" · ") { I18n.tr(it.name) }

    /** Ngày lễ lớn tiếp theo (không tính Rằm/Mùng 1) trong 120 ngày tới: "Giỗ Tổ Hùng Vương · còn 23 ngày". */
    fun nextMajor(cal: Calendar = Calendar.getInstance()): String {
        val c = cal.clone() as Calendar
        for (i in 1..120) {
            c.add(Calendar.DAY_OF_MONTH, 1)
            val h = holidaysOn(c).firstOrNull { it.isDayOff || it.name != "Rằm" && it.name != "Mùng 1 âm lịch" }
            if (h != null) {
                return if (i == 1) I18n.tf("{0} · ngày mai", I18n.tr(h.name)) else I18n.tf("{0} · còn {1} ngày", I18n.tr(h.name), i)
            }
        }
        return ""
    }

    /** Can Chi tháng âm. Công thức: Can = (năm*12 + tháng + 3) mod 10, Chi = (tháng + 1) mod 12 (Tý = 0). */
    fun monthCanChi(lunarYear: Int, lunarMonth: Int): String {
        val can = arrayOf("Giáp", "Ất", "Bính", "Đinh", "Mậu", "Kỷ", "Canh", "Tân", "Nhâm", "Quý")
        val chi = arrayOf("Tý", "Sửu", "Dần", "Mão", "Thìn", "Tỵ", "Ngọ", "Mùi", "Thân", "Dậu", "Tuất", "Hợi")
        return "${can[(lunarYear * 12 + lunarMonth + 3) % 10]} ${chi[(lunarMonth + 1) % 12]}"
    }

    /** "Ngày Giáp Tý · Tháng Bính Dần · Năm Giáp Thìn". */
    fun canChiLine(cal: Calendar = Calendar.getInstance()): String {
        val l = VietLunarCalendar.convertSolar2Lunar(
            cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR)
        )
        return I18n.tf("Ngày {0} · Tháng {1} · Năm {2}", VietLunarCalendar.getDayCanChi(cal), monthCanChi(l.year, l.month), VietLunarCalendar.getYearCanChi(l.year))
    }
}
