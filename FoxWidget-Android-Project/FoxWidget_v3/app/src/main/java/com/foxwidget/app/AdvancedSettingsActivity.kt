package com.foxwidget.app

import android.Manifest
import com.foxwidget.app.util.I18n
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.foxwidget.app.data.PrefsManager
import com.foxwidget.app.util.CalendarEventsHelper
import com.foxwidget.app.util.DeviceInfoHelper
import com.foxwidget.app.util.IslandOverlayManager
import com.foxwidget.app.util.WeatherHelper
import com.foxwidget.app.widget.FoxCalendarWidgetProvider
import com.foxwidget.app.widget.FoxClockWidgetProvider
import com.foxwidget.app.widget.FoxWeatherWidgetProvider
import com.foxwidget.app.widget.FoxWidgetProvider
import kotlinx.coroutines.launch

/**
 * Tùy chỉnh nâng cao: kiểu Dynamic Island theo hãng, thời tiết (đơn vị, thành phố), lịch (ngày lễ, sự kiện)
 * và các lựa chọn của widget. Dựng bằng code để không đụng tới layout chính.
 */
class AdvancedSettingsActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager
    private lateinit var locationLabel: TextView
    private val dp by lazy { resources.displayMetrics.density }

    private var pendingCalendarSwitch: SwitchCompat? = null
    private var permissionForWidget = false

    private val calendarPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (permissionForWidget) {
            prefs.showNextEvent = granted
            pendingCalendarSwitch?.isChecked = granted
        } else if (!granted) {
            prefs.islandShowEvent = false
        }
        if (!granted) {
            Toast.makeText(this, I18n.tr("Cần quyền Lịch để hiện sự kiện sắp tới"), Toast.LENGTH_LONG).show()
        }
        changed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PrefsManager(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16), px(16), px(16), px(40))
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#F4F2FA"))
            addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        setContentView(scroll)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        root.addView(title(I18n.tr("Tùy chỉnh nâng cao"), 22f))
        root.addView(note(I18n.tr("Thay đổi được áp dụng ngay cho widget và Dynamic Island.")))

        buildIslandSection(root)
        buildWeatherSection(root)
        buildWidgetSection(root)
        buildAddWidgetSection(root)
    }

    // ------------------------------------------------------------------ Dynamic Island

    private fun buildIslandSection(root: LinearLayout) {
        val card = card(root, I18n.tr("DYNAMIC ISLAND · KIỂU THEO HÃNG"))
        val detected = DeviceInfoHelper.getBrandTitle()
        val options = listOf(
            PrefsManager.ISLAND_PRESET_AUTO to I18n.tf("Tự động theo máy ({0})", detected),
            PrefsManager.ISLAND_PRESET_CLASSIC to I18n.tr("Dynamic Island · iOS (nảy lò xo, nền đen tuyền)"),
            PrefsManager.ISLAND_PRESET_HYPER to I18n.tr("Hyper Island · Xiaomi HyperOS (mượt, vầng sáng nhẹ)"),
            PrefsManager.ISLAND_PRESET_FLUID to I18n.tr("Fluid Cloud · OPPO / OnePlus / realme (kính mờ, chảy)"),
            PrefsManager.ISLAND_PRESET_ATOMIC to I18n.tr("Atomic Island · vivo / iQOO (nhanh, gọn)"),
            PrefsManager.ISLAND_PRESET_CAPSULE to I18n.tr("Magic Capsule · HONOR (viên thuốc mềm)"),
            PrefsManager.ISLAND_PRESET_NOWBAR to I18n.tr("Now Bar · Samsung One UI (thẻ rộng)")
        )
        val group = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        val ids = HashMap<Int, Int>()
        for ((value, label) in options) {
            val rb = RadioButton(this).apply {
                id = View.generateViewId()
                text = label
                textSize = 13.5f
                setTextColor(Color.parseColor("#1E2433"))
            }
            ids[rb.id] = value
            group.addView(rb)
            if (prefs.islandPreset == value) rb.isChecked = true
        }
        group.setOnCheckedChangeListener { _, checkedId ->
            ids[checkedId]?.let {
                prefs.islandPreset = it
                changed()
                IslandOverlayManager.preview(this)
            }
        }
        card.addView(group)

        card.addView(switchRow(I18n.tr("Hiện ngày lễ trên island"), prefs.islandShowHoliday) { prefs.islandShowHoliday = it; changed() })
        card.addView(switchRow(I18n.tr("Hiện sự kiện sắp tới trên island"), prefs.islandShowEvent) {
            prefs.islandShowEvent = it
            if (it && !CalendarEventsHelper.hasPermission(this)) askCalendarPermission(forWidget = false)
            changed()
        })
        card.addView(switchRow(I18n.tr("Hiện chi tiết thời tiết (cảm giác, ẩm, gió, AQI)"), prefs.islandWeatherDetail) {
            prefs.islandWeatherDetail = it; changed()
        })
        card.addView(button(I18n.tr("Xem thử island")) {
            if (!prefs.islandEnabled || !IslandOverlayManager.canDrawOverlays(this)) {
                Toast.makeText(this, I18n.tr("Hãy bật Dynamic Island và cấp quyền hiển thị ở màn hình chính trước."), Toast.LENGTH_LONG).show()
            } else IslandOverlayManager.preview(this)
        })
    }

    // ------------------------------------------------------------------ Thời tiết

    private fun buildWeatherSection(root: LinearLayout) {
        val card = card(root, I18n.tr("THỜI TIẾT & VỊ TRÍ"))

        card.addView(label(I18n.tr("Đơn vị nhiệt độ")))
        val unit = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        val rc = RadioButton(this).apply { id = View.generateViewId(); text = "°C" }
        val rf = RadioButton(this).apply { id = View.generateViewId(); text = "°F" }
        unit.addView(rc); unit.addView(rf)
        if (prefs.tempUnit == PrefsManager.TEMP_F) rf.isChecked = true else rc.isChecked = true
        unit.setOnCheckedChangeListener { _, checked ->
            prefs.tempUnit = if (checked == rf.id) PrefsManager.TEMP_F else PrefsManager.TEMP_C
            changed()
        }
        card.addView(unit)

        card.addView(label(I18n.tr("Số ngày dự báo trên widget thời tiết")))
        val days = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        val dayIds = HashMap<Int, Int>()
        for (n in listOf(3, 5, 7)) {
            val rb = RadioButton(this).apply { id = View.generateViewId(); text = I18n.tf("{0} ngày", n) }
            dayIds[rb.id] = n
            days.addView(rb)
            if (prefs.forecastDays == n) rb.isChecked = true
        }
        days.setOnCheckedChangeListener { _, checked ->
            dayIds[checked]?.let { prefs.forecastDays = it; changed() }
        }
        card.addView(days)

        locationLabel = note("")
        card.addView(label(I18n.tr("Vị trí lấy thời tiết")))
        card.addView(locationLabel)
        updateLocationLabel()
        card.addView(button(I18n.tr("Chọn thành phố thủ công")) { showCitySearch() })
        card.addView(button(I18n.tr("Dùng vị trí tự động (GPS / mạng)")) {
            prefs.manualLocationEnabled = false
            updateLocationLabel()
            refreshWeather()
        })
        card.addView(button(I18n.tr("Làm mới thời tiết ngay")) { refreshWeather() })
        card.addView(note(I18n.tr("Nguồn: Open-Meteo (chính), MET Norway (dự phòng), chất lượng không khí Open-Meteo.")))
    }

    private fun updateLocationLabel() {
        locationLabel.text = if (prefs.manualLocationEnabled) {
            I18n.tf("Thủ công: {0}", prefs.manualName.ifBlank { I18n.tr("(chưa đặt tên)") })
        } else {
            I18n.tf("Tự động: {0}", prefs.cachedLocationName)
        }
    }

    private fun refreshWeather() {
        Toast.makeText(this, I18n.tr("Đang tải thời tiết…"), Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            WeatherHelper.refresh(this@AdvancedSettingsActivity)
            updateLocationLabel()
            changed()
            val snap = WeatherHelper.load(this@AdvancedSettingsActivity)
            Toast.makeText(
                this@AdvancedSettingsActivity,
                if (snap != null) I18n.tf("Đã cập nhật ({0})", snap.source) else I18n.tr("Không tải được, kiểm tra mạng"),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showCitySearch() {
        val input = EditText(this).apply {
            hint = I18n.tr("Ví dụ: Đà Nẵng, Hà Nội, Tokyo")
            inputType = InputType.TYPE_CLASS_TEXT
            setPadding(px(20), px(16), px(20), px(16))
        }
        AlertDialog.Builder(this)
            .setTitle(I18n.tr("Tìm thành phố"))
            .setView(input)
            .setPositiveButton(I18n.tr("Tìm")) { _, _ ->
                val q = input.text.toString().trim()
                if (q.isEmpty()) return@setPositiveButton
                lifecycleScope.launch {
                    val results = WeatherHelper.searchPlaces(q)
                    if (results.isEmpty()) {
                        Toast.makeText(this@AdvancedSettingsActivity, I18n.tf("Không tìm thấy \"{0}\"", q), Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    val labels = results.map { if (it.detail.isBlank()) it.name else "${it.name} · ${it.detail}" }.toTypedArray()
                    AlertDialog.Builder(this@AdvancedSettingsActivity)
                        .setTitle(I18n.tr("Chọn địa điểm"))
                        .setItems(labels) { _, which ->
                            val r = results[which]
                            prefs.manualLocationEnabled = true
                            prefs.manualLat = r.lat.toFloat()
                            prefs.manualLon = r.lon.toFloat()
                            prefs.manualName = r.name
                            prefs.cachedLocationName = r.name
                            updateLocationLabel()
                            refreshWeather()
                        }
                        .setNegativeButton(I18n.tr("Hủy"), null)
                        .show()
                }
            }
            .setNegativeButton(I18n.tr("Hủy"), null)
            .show()
    }

    // ------------------------------------------------------------------ Widget

    private fun buildWidgetSection(root: LinearLayout) {
        val card = card(root, I18n.tr("WIDGET · LỰA CHỌN HIỂN THỊ"))
        card.addView(switchRow(I18n.tr("Đồng hồ 24 giờ (tắt = 12 giờ AM/PM)"), prefs.clock24h) { prefs.clock24h = it; changed() })
        card.addView(switchRow(I18n.tr("Hiện Can Chi (ngày, năm)"), prefs.showCanChi) { prefs.showCanChi = it; changed() })
        card.addView(switchRow(I18n.tr("Hiện ngày lễ Việt Nam"), prefs.showHolidayLine) { prefs.showHolidayLine = it; changed() })
        val eventSwitch = switchRow(I18n.tr("Hiện sự kiện sắp tới từ Lịch của máy"), prefs.showNextEvent && CalendarEventsHelper.hasPermission(this)) {
            if (it) {
                if (CalendarEventsHelper.hasPermission(this)) {
                    prefs.showNextEvent = true; changed()
                } else askCalendarPermission(forWidget = true)
            } else {
                prefs.showNextEvent = false; changed()
            }
        }
        pendingCalendarSwitch = eventSwitch.getChildAt(1) as? SwitchCompat
        card.addView(eventSwitch)
        card.addView(switchRow(I18n.tr("Thời tiết: hiện cảm giác, độ ẩm, gió, UV, AQI"), prefs.showWeatherDetail) {
            prefs.showWeatherDetail = it; changed()
        })
        card.addView(switchRow(I18n.tr("Lịch tháng: tuần bắt đầu từ Thứ Hai"), prefs.calendarWeekStartMonday) {
            prefs.calendarWeekStartMonday = it; changed()
        })
        card.addView(switchRow(I18n.tr("Lịch tháng: hiện ngày âm lịch"), prefs.calendarShowLunar) {
            prefs.calendarShowLunar = it; changed()
        })
    }

    private fun askCalendarPermission(forWidget: Boolean) {
        permissionForWidget = forWidget
        calendarPermission.launch(Manifest.permission.READ_CALENDAR)
    }

    private fun buildAddWidgetSection(root: LinearLayout) {
        val card = card(root, I18n.tr("THÊM WIDGET RA MÀN HÌNH CHÍNH"))
        card.addView(note(I18n.tr("Có 4 loại: Chính (đồng hồ + lịch + thời tiết + pin), Đồng hồ, Thời tiết chi tiết, Lịch tháng.")))
        card.addView(button(I18n.tr("Thêm widget Đồng hồ")) { pin(FoxClockWidgetProvider::class.java) })
        card.addView(button(I18n.tr("Thêm widget Thời tiết")) { pin(FoxWeatherWidgetProvider::class.java) })
        card.addView(button(I18n.tr("Thêm widget Lịch tháng")) { pin(FoxCalendarWidgetProvider::class.java) })
        card.addView(button(I18n.tr("Thêm widget Chính")) { pin(FoxWidgetProvider::class.java) })
    }

    private fun pin(cls: Class<*>) {
        val m = AppWidgetManager.getInstance(this)
        if (m.isRequestPinAppWidgetSupported) {
            m.requestPinAppWidget(ComponentName(this, cls), null, null)
        } else {
            Toast.makeText(
                this,
                I18n.tr("Launcher không hỗ trợ thêm tự động. Nhấn giữ màn hình chính > Tiện ích > FoxWidget."),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ------------------------------------------------------------------ tiện ích dựng UI

    private fun changed() {
        FoxWidgetProvider.updateAllWidgets(applicationContext)
    }

    private fun px(v: Int) = (v * dp).toInt()

    private fun title(text: String, size: Float) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(Color.parseColor("#1E2433"))
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, px(4), 0, px(4))
    }

    private fun note(text: String) = TextView(this).apply {
        this.text = text
        textSize = 12f
        setTextColor(Color.parseColor("#6B7490"))
        setPadding(0, px(4), 0, px(4))
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(Color.parseColor("#1E2433"))
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, px(10), 0, px(2))
    }

    private fun card(root: LinearLayout, heading: String): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card_white)
            setPadding(px(16), px(14), px(16), px(16))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = px(14) }
        }
        c.addView(TextView(this).apply {
            text = heading
            textSize = 11.5f
            letterSpacing = 0.06f
            setTextColor(Color.parseColor("#6B7490"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        root.addView(c)
        return c
    }

    /** Hàng có chữ bên trái và công tắc bên phải. Công tắc là con thứ hai của hàng. */
    private fun switchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, px(8), 0, px(8))
        }
        row.addView(TextView(this).apply {
            this.text = text
            textSize = 13.5f
            setTextColor(Color.parseColor("#1E2433"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        val sw = SwitchCompat(this).apply {
            isChecked = checked
            setOnCheckedChangeListener { btn, value ->
                // Bỏ qua thay đổi do code đặt lại trạng thái
                if (btn.isPressed) onChange(value)
            }
        }
        row.addView(sw)
        return row
    }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = px(8) }
    }
}
