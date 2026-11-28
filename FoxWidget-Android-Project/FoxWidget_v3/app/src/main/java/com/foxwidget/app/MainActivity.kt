package com.foxwidget.app

import android.Manifest
import com.foxwidget.app.util.I18n
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.res.ColorStateList
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.foxwidget.app.data.PrefsManager
import com.foxwidget.app.databinding.ActivityMainBinding
import com.foxwidget.app.service.FoxMonitorService
import com.foxwidget.app.util.DeviceInfoHelper
import com.foxwidget.app.util.DeviceTelemetryHelper
import com.foxwidget.app.util.IslandOverlayManager
import com.foxwidget.app.util.LiquidGlassRenderer
import com.foxwidget.app.util.OemOptimizationHelper
import com.foxwidget.app.util.VietLunarCalendar
import com.foxwidget.app.widget.FoxWidgetProvider
import com.foxwidget.app.widget.WidgetCommon
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PrefsManager

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshPermissionStatuses()
        lifecycleScope.launch {
            DeviceTelemetryHelper.refreshWeatherAndLocation(this@MainActivity)
            updateLivePreview()
            FoxWidgetProvider.updateAllWidgets(this@MainActivity)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        I18n.applyTo(binding.root)

        applyEdgeToEdgeInsets()

        prefs = PrefsManager(this)
        FoxMonitorService.start(this)

        setupBottomTabs()
        setupPermissionsTab()
        setupCustomizeTab()
        setupOtherTabs()
        setupInfoTab()
        setupIslandTab()

        selectTab(3)

        lifecycleScope.launch {
            DeviceTelemetryHelper.refreshWeatherAndLocation(this@MainActivity)
            updateLivePreview()
            FoxWidgetProvider.updateAllWidgets(this@MainActivity)
        }
    }

    private fun applyEdgeToEdgeInsets() {
        val density = resources.displayMetrics.density
        val basePad = (12 * density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val sysBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            binding.headerContainer.updatePadding(top = sysBars.top + basePad)
            binding.bottomNavBar.updatePadding(bottom = sysBars.bottom)
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStatuses()
        updateLivePreview()
        refreshIslandPage()
        IslandOverlayManager.sync(this)
    }

    private fun setupBottomTabs() {
        val tabs = listOf(
            binding.navTabPermissions,
            binding.navTabLunar,
            binding.navTabWeather,
            binding.navTabCustomize,
            binding.navTabWidget,
            binding.navTabVersion
        )
        tabs.forEachIndexed { index, view ->
            view.setOnClickListener { selectTab(index) }
        }
    }

    private fun selectTab(index: Int) {
        val pages = listOf(
            binding.pagePermissions,
            binding.pageLunar,
            binding.pageWeather,
            binding.pageCustomize,
            binding.pageWidgetIsland,
            binding.pageVersion
        )
        val navViews = listOf(
            binding.navTabPermissions,
            binding.navTabLunar,
            binding.navTabWeather,
            binding.navTabCustomize,
            binding.navTabWidget,
            binding.navTabVersion
        )

        pages.forEachIndexed { i, page ->
            page.visibility = if (i == index) View.VISIBLE else View.GONE
        }
        val colorOn = ContextCompat.getColor(this, R.color.fox_primary)
        val colorOff = ContextCompat.getColor(this, R.color.fox_text_sub)
        navViews.forEachIndexed { i, nav ->
            val selected = i == index
            nav.setBackgroundResource(if (selected) R.drawable.bg_nav_selected else 0)
            val color = if (selected) colorOn else colorOff
            (nav.getChildAt(0) as? ImageView)?.setColorFilter(color)
            (nav.getChildAt(1) as? TextView)?.setTextColor(color)
        }

        if (index == 1) refreshLunarPage()
        if (index == 2) refreshWeatherPage()
        if (index == 4) refreshIslandPage()
        if (index == 5) refreshInfoPage()
    }

    // ==================== TAB 1: BẢN QUYỀN & TỐI ƯU OEM ====================
    private fun setupPermissionsTab() {
        val oem = OemOptimizationHelper.detectOemProfile()
        binding.tvOemDetectedBanner.text = I18n.tf("Thiết bị: {0} · {1}", oem.brandTitle, oem.osEstimate)

        binding.btnPermLocation.setOnClickListener {
            val perms = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            permissionLauncher.launch(perms.toTypedArray())
        }

        binding.btnPermBackground.setOnClickListener {
            OemOptimizationHelper.openOemBackgroundSettings(this)
        }

        binding.btnPermBattery.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }

        binding.btnPermNotification.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            } else {
                refreshPermissionStatuses()
            }
        }

        binding.btnGuideOptimize.setOnClickListener {
            val profile = OemOptimizationHelper.detectOemProfile()
            AlertDialog.Builder(this)
                .setTitle(I18n.tf("Tối ưu cho {0}", profile.brandTitle))
                .setMessage(
                    I18n.tf("Hệ điều hành: {0}\n\n", profile.osEstimate) +
                    I18n.tf("1. Tự khởi động & Chạy ngầm:\n{0}\n\n", profile.autostartGuide) +
                    I18n.tf("2. Chống đóng băng pin:\n{0}\n\n", profile.batteryGuide) +
                    I18n.tr("3. Vị trí GPS:\nChọn 'Chỉ khi dùng ứng dụng'. Vị trí được làm mới mỗi lần bạn mở FoxWidget; khi chạy ngầm widget dùng vị trí đã lưu gần nhất, thời tiết vẫn cập nhật mỗi 15 phút.")
                )
                .setPositiveButton(I18n.tr("Mở cài đặt hãng")) { _, _ ->
                    OemOptimizationHelper.openOemBackgroundSettings(this)
                }
                .setNegativeButton(I18n.tr("Đóng"), null)
                .show()
        }
    }

    private fun refreshPermissionStatuses() {
        val hasLoc = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isIgnoringBattery = pm.isIgnoringBatteryOptimizations(packageName)

        val hasNotif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        val hasBg = true

        var enabledCount = 0
        if (hasLoc) enabledCount++
        if (hasBg) enabledCount++
        if (isIgnoringBattery) enabledCount++
        if (hasNotif) enabledCount++

        binding.tvPermCounter.text = I18n.tf("Đã bật {0}/4 quyền", enabledCount)
        styleStatusButton(binding.btnPermLocation, hasLoc)
        binding.btnPermBackground.text = I18n.tr("Mở")
        styleStatusButton(binding.btnPermBattery, isIgnoringBattery)
        styleStatusButton(binding.btnPermNotification, hasNotif)

        val ready = enabledCount == 4
        binding.btnAllPermsReady.text = if (ready) {
            I18n.tr("Tất cả quyền đã sẵn sàng")
        } else {
            I18n.tf("Còn thiếu {0} quyền cần cấp", 4 - enabledCount)
        }
        binding.btnAllPermsReady.setIconResource(if (ready) R.drawable.ic_check else R.drawable.ic_info)
        binding.btnAllPermsReady.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, if (ready) R.color.fox_success else R.color.fox_warn)
        )
    }

    /** Nút trạng thái bo tròn: đã bật = xanh lá nhạt, chưa bật = tím đặc. */
    private fun styleStatusButton(btn: MaterialButton, granted: Boolean) {
        btn.text = if (granted) I18n.tr("Đã bật") else I18n.tr("Bật ngay")
        if (granted) {
            btn.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.fox_success_bg))
            btn.setTextColor(ContextCompat.getColor(this, R.color.fox_success))
        } else {
            btn.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.fox_primary))
            btn.setTextColor(Color.WHITE)
        }
    }

    // ==================== TAB 4: TÙY CHỈNH KÍNH LỎNG ====================
    private fun setupCustomizeTab() {
        binding.btnStyleLight.setOnClickListener {
            prefs.widgetStyle = PrefsManager.STYLE_LIGHT
            applyAndBroadcast()
        }
        binding.btnStyleDark.setOnClickListener {
            prefs.widgetStyle = PrefsManager.STYLE_DARK
            applyAndBroadcast()
        }
        binding.btnStyleGlass.setOnClickListener {
            prefs.widgetStyle = PrefsManager.STYLE_LIQUID_GLASS
            applyAndBroadcast()
        }

        setupAccordion(binding.headerAccDate, binding.bodyAccDate, binding.arrowAccDate)
        setupAccordion(binding.headerAccWeather, binding.bodyAccWeather, binding.arrowAccWeather)
        setupAccordion(binding.headerAccSize, binding.bodyAccSize, binding.arrowAccSize)
        setupAccordion(binding.headerAccGlass, binding.bodyAccGlass, binding.arrowAccGlass)
        setupAccordion(binding.headerAccBg, binding.bodyAccBg, binding.arrowAccBg)

        // Mở sẵn phần Kính Liquid Glass & Chất liệu nền để FoxST chỉnh trực tiếp
        binding.bodyAccGlass.visibility = View.VISIBLE
        binding.arrowAccGlass.rotation = 180f
        binding.bodyAccBg.visibility = View.VISIBLE
        binding.arrowAccBg.rotation = 180f

        binding.swShowLunar.isChecked = prefs.showLunarDate
        binding.swShowWeek.isChecked = prefs.showWeekStrip
        binding.swShowWeather.isChecked = prefs.showWeather
        binding.swShowBattery.isChecked = prefs.showBatteryInfo
        binding.swShowDevice.isChecked = prefs.showDeviceName

        binding.swShowLunar.setOnCheckedChangeListener { _, isChecked ->
            prefs.showLunarDate = isChecked
            applyAndBroadcast()
        }
        binding.swShowWeek.setOnCheckedChangeListener { _, isChecked ->
            prefs.showWeekStrip = isChecked
            applyAndBroadcast()
        }
        binding.swShowWeather.setOnCheckedChangeListener { _, isChecked ->
            prefs.showWeather = isChecked
            applyAndBroadcast()
        }
        binding.swShowBattery.setOnCheckedChangeListener { _, isChecked ->
            prefs.showBatteryInfo = isChecked
            applyAndBroadcast()
        }
        binding.swShowDevice.setOnCheckedChangeListener { _, isChecked ->
            prefs.showDeviceName = isChecked
            applyAndBroadcast()
        }

        bindStepperSlider(
            seekBar = binding.seekDateSize,
            btnMinus = binding.btnDateMinus,
            btnPlus = binding.btnDatePlus,
            minVal = 75,
            maxVal = 135,
            step = 5,
            initial = prefs.dateFontScale
        ) { newVal ->
            prefs.dateFontScale = newVal
            applyAndBroadcast()
        }

        bindStepperSlider(
            seekBar = binding.seekClockSize,
            btnMinus = binding.btnClockMinus,
            btnPlus = binding.btnClockPlus,
            minVal = 75,
            maxVal = 135,
            step = 5,
            initial = prefs.clockFontScale
        ) { newVal ->
            prefs.clockFontScale = newVal
            applyAndBroadcast()
        }

        bindStepperSlider(
            seekBar = binding.seekBorderWidth,
            btnMinus = binding.btnBorderMinus,
            btnPlus = binding.btnBorderPlus,
            minVal = 0,
            maxVal = 6,
            step = 1,
            initial = prefs.borderWidthDp
        ) { newVal ->
            prefs.borderWidthDp = newVal
            applyAndBroadcast()
        }

        bindStepperSlider(
            seekBar = binding.seekCornerRadius,
            btnMinus = binding.btnCornerMinus,
            btnPlus = binding.btnCornerPlus,
            minVal = 0,
            maxVal = 40,
            step = 2,
            initial = prefs.cornerRadiusDp
        ) { newVal ->
            prefs.cornerRadiusDp = newVal
            applyAndBroadcast()
        }

        bindStepperSlider(
            seekBar = binding.seekGlassOpacity,
            btnMinus = binding.btnGlassMinus,
            btnPlus = binding.btnGlassPlus,
            minVal = 25,
            maxVal = 100,
            step = 5,
            initial = prefs.glassOpacity
        ) { newVal ->
            prefs.glassOpacity = newVal
            applyAndBroadcast()
        }

        bindStepperSlider(
            seekBar = binding.seekGlassRefraction,
            btnMinus = binding.btnRefractionMinus,
            btnPlus = binding.btnRefractionPlus,
            minVal = 0,
            maxVal = 100,
            step = 5,
            initial = prefs.glassRefraction
        ) { newVal ->
            prefs.glassRefraction = newVal
            applyAndBroadcast()
        }

        bindStepperSlider(
            seekBar = binding.seekGlassGloss,
            btnMinus = binding.btnGlossMinus,
            btnPlus = binding.btnGlossPlus,
            minVal = 0,
            maxVal = 100,
            step = 5,
            initial = prefs.glassGloss
        ) { newVal ->
            prefs.glassGloss = newVal
            applyAndBroadcast()
        }

        binding.btnBgObsidian.setOnClickListener {
            prefs.widgetStyle = PrefsManager.STYLE_LIQUID_GLASS
            prefs.widgetBgPreset = PrefsManager.BG_OBSIDIAN_TITAN
            applyAndBroadcast()
        }
        binding.btnBgPurple.setOnClickListener {
            prefs.widgetStyle = PrefsManager.STYLE_LIQUID_GLASS
            prefs.widgetBgPreset = PrefsManager.BG_PURPLE_DUSK
            applyAndBroadcast()
        }
        binding.btnBgOcean.setOnClickListener {
            prefs.widgetStyle = PrefsManager.STYLE_LIQUID_GLASS
            prefs.widgetBgPreset = PrefsManager.BG_OCEAN_BLUE
            applyAndBroadcast()
        }
        binding.btnBgEmerald.setOnClickListener {
            prefs.widgetStyle = PrefsManager.STYLE_LIQUID_GLASS
            prefs.widgetBgPreset = PrefsManager.BG_EMERALD_MIST
            applyAndBroadcast()
        }
        binding.btnBgPrismatic.setOnClickListener {
            prefs.widgetStyle = PrefsManager.STYLE_LIQUID_GLASS
            prefs.widgetBgPreset = PrefsManager.BG_PRISMATIC_FROST
            applyAndBroadcast()
        }

        binding.btnResetDefaults.setOnClickListener {
            prefs.resetDefaults()
            binding.seekDateSize.progress = prefs.dateFontScale - 75
            binding.seekClockSize.progress = prefs.clockFontScale - 75
            binding.seekBorderWidth.progress = prefs.borderWidthDp
            binding.seekCornerRadius.progress = prefs.cornerRadiusDp
            binding.seekGlassOpacity.progress = prefs.glassOpacity - 25
            binding.seekGlassRefraction.progress = prefs.glassRefraction
            binding.seekGlassGloss.progress = prefs.glassGloss
            binding.swShowLunar.isChecked = true
            binding.swShowWeek.isChecked = true
            binding.swShowWeather.isChecked = true
            binding.swShowBattery.isChecked = true
            binding.swShowDevice.isChecked = true
            applyAndBroadcast()
            Toast.makeText(this, I18n.tr("Đã khôi phục mặc định Kính Lỏng Quang Học!"), Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupAccordion(header: View, body: View, arrow: View) {
        header.setOnClickListener {
            val expanding = body.visibility != View.VISIBLE
            body.visibility = if (expanding) View.VISIBLE else View.GONE
            arrow.animate().rotation(if (expanding) 180f else 0f).setDuration(180).start()
        }
    }

    private fun bindStepperSlider(
        seekBar: SeekBar,
        btnMinus: View,
        btnPlus: View,
        minVal: Int,
        maxVal: Int,
        step: Int,
        initial: Int,
        onChanged: (Int) -> Unit
    ) {
        seekBar.max = maxVal - minVal
        seekBar.progress = (initial - minVal).coerceIn(0, seekBar.max)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                onChanged(minVal + progress)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        btnMinus.setOnClickListener {
            seekBar.progress = (seekBar.progress - step).coerceAtLeast(0)
        }
        btnPlus.setOnClickListener {
            seekBar.progress = (seekBar.progress + step).coerceAtMost(seekBar.max)
        }
    }

    private var islandUiUpdating = false

    private fun applyAndBroadcast() {
        updateLivePreview()
        FoxWidgetProvider.updateAllWidgets(this)
    }

    private fun updateLivePreview() {
        val activeBg = R.drawable.bg_segment_active
        binding.btnStyleLight.setBackgroundResource(
            if (prefs.widgetStyle == PrefsManager.STYLE_LIGHT) activeBg else 0
        )
        binding.btnStyleDark.setBackgroundResource(
            if (prefs.widgetStyle == PrefsManager.STYLE_DARK) activeBg else 0
        )
        binding.btnStyleGlass.setBackgroundResource(
            if (prefs.widgetStyle == PrefsManager.STYLE_LIQUID_GLASS) activeBg else 0
        )

        binding.btnStyleLight.setTextColor(
            if (prefs.widgetStyle == PrefsManager.STYLE_LIGHT) Color.WHITE else Color.parseColor("#6F6A8A")
        )
        binding.btnStyleDark.setTextColor(
            if (prefs.widgetStyle == PrefsManager.STYLE_DARK) Color.WHITE else Color.parseColor("#6F6A8A")
        )
        binding.btnStyleGlass.setTextColor(
            if (prefs.widgetStyle == PrefsManager.STYLE_LIQUID_GLASS) Color.WHITE else Color.parseColor("#6F6A8A")
        )

        binding.tvLabelDateSize.text = if (prefs.dateFontScale == 100) {
            I18n.tr("Cỡ chữ ngày dương & âm lịch: 100% (mặc định)")
        } else {
            I18n.tf("Cỡ chữ ngày dương & âm lịch: {0}%", prefs.dateFontScale)
        }

        binding.tvLabelClockSize.text = I18n.tf("Cỡ đồng hồ: {0}%", prefs.clockFontScale)
        binding.tvLabelBorderWidth.text = if (prefs.borderWidthDp == 1) {
            I18n.tr("Độ rộng viền: tự động")
        } else {
            I18n.tf("Độ rộng viền: {0}dp", prefs.borderWidthDp)
        }
        binding.tvLabelCornerRadius.text = if (prefs.cornerRadiusDp == 22) {
            I18n.tr("Bo góc widget: mặc định (đồng bộ OS)")
        } else {
            I18n.tf("Bo góc widget: {0}dp", prefs.cornerRadiusDp)
        }
        binding.tvLabelGlassOpacity.text = I18n.tf("Độ đặc khối kính: {0}%", prefs.glassOpacity)
        binding.tvLabelGlassRefraction.text = I18n.tf("Tán sắc cầu vồng & khúc xạ viền: {0}%", prefs.glassRefraction)
        binding.tvLabelGlassGloss.text = I18n.tf("Độ bóng vòm giọt nước: {0}%", prefs.glassGloss)

        // Render 1:1 Optical Liquid Glass Bitmap cho Live Preview
        val previewBitmap = LiquidGlassRenderer.renderWidgetSurface(this, prefs, 340, 175)
        binding.ivPreviewGlassBg.setImageBitmap(previewBitmap)

        val now = Calendar.getInstance()
        val isLight = prefs.widgetStyle == PrefsManager.STYLE_LIGHT
        val primaryColor = if (isLight) Color.parseColor("#1E2433") else Color.WHITE
        val secondaryColor = if (isLight) Color.parseColor("#4E596F") else Color.parseColor("#E8ECF8")

        binding.tvPreviewClock.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now.time)
        binding.tvPreviewClock.setTextColor(primaryColor)
        binding.tvPreviewClock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 42f * (prefs.clockFontScale / 100f))

        val dateSp = 12.5f * (prefs.dateFontScale / 100f)
        binding.tvPreviewSolar.text = VietLunarCalendar.formatSolarDateFull(now)
        binding.tvPreviewSolar.setTextColor(secondaryColor)
        binding.tvPreviewSolar.setTextSize(TypedValue.COMPLEX_UNIT_SP, dateSp)

        binding.tvPreviewLunar.text = VietLunarCalendar.formatLunarDateFull(now)
        binding.tvPreviewLunar.setTextColor(secondaryColor)
        binding.tvPreviewLunar.setTextSize(TypedValue.COMPLEX_UNIT_SP, dateSp)
        binding.tvPreviewLunar.visibility = if (prefs.showLunarDate) View.VISIBLE else View.GONE

        val battery = DeviceTelemetryHelper.getBatteryStats(this)
        binding.tvPreviewDevice.text = "📱 ${DeviceTelemetryHelper.getDeviceShortName(this)}"
        binding.tvPreviewDevice.visibility = if (prefs.showDeviceName) View.VISIBLE else View.GONE

        binding.tvPreviewTemp.text = "${prefs.cachedTempC}°"
        binding.tvPreviewTemp.setTextColor(primaryColor)
        binding.tvPreviewLocation.text = "📍 ${prefs.cachedLocationName}"
        binding.tvPreviewLocation.setTextColor(secondaryColor)
        val wVis = if (prefs.showWeather) View.VISIBLE else View.GONE
        binding.tvPreviewTemp.visibility = wVis
        binding.tvPreviewLocation.visibility = wVis

        binding.tvPreviewBatPct.text = "🔋 ${battery.levelPercent}%"
        binding.tvPreviewBatPct.setTextColor(primaryColor)
        binding.tvPreviewBatTemp.text = String.format(Locale.US, "🌡️ %.1f°C", battery.temperatureC)
        binding.tvPreviewBatTemp.setTextColor(secondaryColor)
        val bVis = if (prefs.showBatteryInfo) View.VISIBLE else View.GONE
        binding.tvPreviewBatPct.visibility = bVis
        binding.tvPreviewBatTemp.visibility = bVis

        binding.llPreviewWeekStrip.visibility = if (prefs.showWeekStrip) View.VISIBLE else View.GONE
        binding.llPreviewWeekStrip.removeAllViews()
        val weekItems = VietLunarCalendar.getCurrentWeekStrip(now)
        for (item in weekItems) {
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(4, 6, 4, 6)
                if (item.isToday) {
                    setBackgroundResource(R.drawable.bg_week_today_pill)
                }
            }
            val tvDow = TextView(this).apply {
                text = item.dayOfWeekLabel
                textSize = 10f
                gravity = android.view.Gravity.CENTER
                setTextColor(if (item.isToday) Color.WHITE else secondaryColor)
            }
            val tvSol = TextView(this).apply {
                text = item.solarDay.toString()
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = android.view.Gravity.CENTER
                setTextColor(if (item.isToday) Color.WHITE else primaryColor)
            }
            val tvLun = TextView(this).apply {
                text = item.lunarDay.toString()
                textSize = 9.5f
                gravity = android.view.Gravity.CENTER
                setTextColor(if (item.isToday) Color.parseColor("#E6ECFF") else secondaryColor)
            }
            col.addView(tvDow)
            col.addView(tvSol)
            col.addView(tvLun)
            binding.llPreviewWeekStrip.addView(col)
        }
    }

    private fun setupOtherTabs() {
        binding.btnRefreshWeatherNow.setOnClickListener {
            Toast.makeText(this, I18n.tr("Đang cập nhật thời tiết & vị trí..."), Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                DeviceTelemetryHelper.refreshWeatherAndLocation(this@MainActivity)
                refreshWeatherPage()
                updateLivePreview()
                FoxWidgetProvider.updateAllWidgets(this@MainActivity)
            }
        }

        binding.btnPinWidgetHome.setOnClickListener { requestPinWidget() }
        binding.btnWidgetManualGuide.setOnClickListener { showManualWidgetGuide(null) }

        binding.btnForceSyncWidget.setOnClickListener {
            lifecycleScope.launch {
                DeviceTelemetryHelper.refreshWeatherAndLocation(this@MainActivity)
                FoxWidgetProvider.updateAllWidgets(this@MainActivity)
                Toast.makeText(this@MainActivity, I18n.tr("Đã đồng bộ lại toàn bộ Widget trên màn hình!"), Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Android không cho ứng dụng tự đặt widget lên màn hình chính: ứng dụng chỉ có thể GỬI YÊU CẦU,
     * launcher mới là nơi hiện hộp thoại "Thêm". Nhiều launcher/hãng (Xiaomi, Oppo, Vivo, Huawei...)
     * không hỗ trợ hoặc chặn yêu cầu này nếu chưa bật quyền "Tạo lối tắt trên Màn hình chính".
     */
    private fun requestPinWidget() {
        val manager = AppWidgetManager.getInstance(this)
        if (!manager.isRequestPinAppWidgetSupported) {
            showManualWidgetGuide(I18n.tr("Launcher hiện tại không hỗ trợ thêm widget tự động."))
            return
        }
        val provider = ComponentName(this, FoxWidgetProvider::class.java)
        val callback = PendingIntent.getBroadcast(
            this,
            0,
            Intent(this, FoxWidgetProvider::class.java).setAction(FoxWidgetProvider.ACTION_REFRESH_WIDGET),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val accepted = try {
            manager.requestPinAppWidget(provider, null, callback)
        } catch (_: Exception) {
            false
        }
        if (accepted) {
            Toast.makeText(
                this,
                I18n.tr("Hãy bấm \"Thêm\" trên màn hình chính. Không thấy hộp thoại thì dùng hướng dẫn thủ công bên dưới."),
                Toast.LENGTH_LONG
            ).show()
        } else {
            showManualWidgetGuide(I18n.tr("Launcher đã từ chối yêu cầu thêm widget tự động."))
        }
    }

    private fun showManualWidgetGuide(reason: String?) {
        val mfg = (Build.MANUFACTURER ?: "").lowercase(Locale.ROOT)
        val brandTip = when {
            mfg.contains("xiaomi") || mfg.contains("redmi") || mfg.contains("poco") ->
                I18n.tr("Xiaomi / Redmi / POCO: vào Cài đặt > Ứng dụng > FoxWidget > Quyền khác, bật ") +
                    I18n.tr("\"Tạo lối tắt trên Màn hình chính\" rồi bấm lại nút Thêm.")
            mfg.contains("oppo") || mfg.contains("realme") || mfg.contains("oneplus") ->
                I18n.tr("OPPO / Realme / OnePlus: vào Thông tin ứng dụng FoxWidget > Quyền, bật quyền ") +
                    I18n.tr("\"Lối tắt trên màn hình chính\" (nếu có) rồi thử lại.")
            mfg.contains("vivo") || mfg.contains("iqoo") ->
                I18n.tr("vivo / iQOO: vào Thông tin ứng dụng FoxWidget > Quyền khác, bật ") +
                    I18n.tr("\"Tạo lối tắt trên màn hình chính\" rồi thử lại.")
            mfg.contains("huawei") || mfg.contains("honor") ->
                I18n.tr("Huawei / Honor: vào Thông tin ứng dụng FoxWidget > Quyền, bật quyền ") +
                    I18n.tr("\"Tạo lối tắt trên màn hình chính\" rồi thử lại.")
            else -> null
        }
        val message = buildString {
            if (reason != null) append(reason).append("\n\n")
            append(I18n.tr("Cách thêm thủ công (hoạt động trên mọi máy):\n"))
            append(I18n.tr("1. Nhấn giữ vào chỗ trống trên màn hình chính.\n"))
            append(I18n.tr("2. Chọn \"Tiện ích\" (Widgets).\n"))
            append(I18n.tr("3. Tìm \"FoxWidget\", nhấn giữ rồi kéo ra màn hình."))
            if (brandTip != null) append("\n\n").append(brandTip)
        }
        AlertDialog.Builder(this)
            .setTitle(I18n.tr("Thêm FoxWidget ra màn hình chính"))
            .setMessage(message)
            .setPositiveButton(I18n.tr("Đã hiểu"), null)
            .setNeutralButton(I18n.tr("Mở quyền ứng dụng")) { _, _ ->
                try {
                    startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                    )
                } catch (_: Exception) {
                }
            }
            .show()
    }

    private fun refreshLunarPage() {
        val now = Calendar.getInstance()
        val lunar = VietLunarCalendar.convertSolar2Lunar(
            now.get(Calendar.DAY_OF_MONTH),
            now.get(Calendar.MONTH) + 1,
            now.get(Calendar.YEAR)
        )
        val canChiYear = VietLunarCalendar.getYearCanChi(lunar.year)
        val canChiDay = VietLunarCalendar.getDayCanChi(now)
        binding.tvLunarBigSolar.text = VietLunarCalendar.formatSolarDateFull(now) + I18n.tf(" năm {0}", now.get(Calendar.YEAR))
        binding.tvLunarBigLunar.text = I18n.tf("{0} · Năm {1}", VietLunarCalendar.formatLunarDateFull(now), canChiYear)
        binding.tvLunarCanChiDetail.text = I18n.tf("Ngày {0} · Giờ Hoàng Đạo thuận lợi cho công việc & khởi tạo", canChiDay)
    }

    private fun refreshWeatherPage() {
        val battery = DeviceTelemetryHelper.getBatteryStats(this)
        binding.tvWeatherDetailLocation.text = I18n.tf("Khu vực: {0}", prefs.cachedLocationName)
        binding.tvWeatherDetailTemp.text = I18n.tf("Ngoài trời: {0}°C ({1})", prefs.cachedTempC, I18n.tr(prefs.cachedWeatherDesc))
        binding.tvWeatherDetailBattery.text = String.format(
            Locale.US,
            I18n.tr("Pin %d%% · Nhiệt độ pin %.1f°C"),
            battery.levelPercent,
            battery.temperatureC
        )
    }

    // ==================== TAB: DYNAMIC ISLAND ====================
    private fun setupIslandTab() {
        binding.swIslandEnabled.setOnCheckedChangeListener { _, checked ->
            if (islandUiUpdating) return@setOnCheckedChangeListener
            prefs.islandEnabled = checked
            if (checked) {
                FoxMonitorService.start(this)
                if (!IslandOverlayManager.canDrawOverlays(this)) {
                    Toast.makeText(
                        this,
                        I18n.tr("Hãy bật \"Hiển thị trên ứng dụng khác\" cho FoxWidget"),
                        Toast.LENGTH_LONG
                    ).show()
                    openOverlaySettings()
                }
            }
            IslandOverlayManager.sync(this)
            refreshIslandPage()
        }

        binding.btnIslandPermission.setOnClickListener { openOverlaySettings() }

        binding.swIslandCalendar.setOnCheckedChangeListener { _, checked ->
            if (islandUiUpdating) return@setOnCheckedChangeListener
            prefs.islandShowCalendar = checked
            IslandOverlayManager.sync(this)
        }
        binding.swIslandWeather.setOnCheckedChangeListener { _, checked ->
            if (islandUiUpdating) return@setOnCheckedChangeListener
            prefs.islandShowWeather = checked
            IslandOverlayManager.sync(this)
        }
        binding.swIslandLocation.setOnCheckedChangeListener { _, checked ->
            if (islandUiUpdating) return@setOnCheckedChangeListener
            prefs.islandShowLocation = checked
            IslandOverlayManager.sync(this)
        }
        binding.swIslandBattery.setOnCheckedChangeListener { _, checked ->
            if (islandUiUpdating) return@setOnCheckedChangeListener
            prefs.islandShowBattery = checked
            IslandOverlayManager.sync(this)
        }

        bindStepperSlider(
            seekBar = binding.seekIslandY,
            btnMinus = binding.btnIslandYMinus,
            btnPlus = binding.btnIslandYPlus,
            minVal = -16,
            maxVal = 60,
            step = 1,
            initial = prefs.islandDy
        ) { v ->
            prefs.islandDy = v
            updateIslandLabels()
            IslandOverlayManager.sync(this)
        }
        bindStepperSlider(
            seekBar = binding.seekIslandX,
            btnMinus = binding.btnIslandXMinus,
            btnPlus = binding.btnIslandXPlus,
            minVal = -150,
            maxVal = 150,
            step = 5,
            initial = prefs.islandDx
        ) { v ->
            prefs.islandDx = v
            updateIslandLabels()
            IslandOverlayManager.sync(this)
        }
        bindStepperSlider(
            seekBar = binding.seekIslandScale,
            btnMinus = binding.btnIslandScaleMinus,
            btnPlus = binding.btnIslandScalePlus,
            minVal = 50,
            maxVal = 130,
            step = 5,
            initial = prefs.islandScale
        ) { v ->
            prefs.islandScale = v
            updateIslandLabels()
            IslandOverlayManager.sync(this)
        }

        binding.swIslandAutoCutout.setOnCheckedChangeListener { _, checked ->
            if (islandUiUpdating) return@setOnCheckedChangeListener
            prefs.islandAutoCutout = checked
            IslandOverlayManager.sync(this)
            refreshIslandPage()
            binding.root.postDelayed({ refreshIslandPage() }, 500)
        }
        bindStepperSlider(
            seekBar = binding.seekIslandWidth,
            btnMinus = binding.btnIslandWidthMinus,
            btnPlus = binding.btnIslandWidthPlus,
            minVal = 60,
            maxVal = 320,
            step = 5,
            initial = prefs.islandWidthDp
        ) { v ->
            prefs.islandWidthDp = v
            updateIslandLabels()
            IslandOverlayManager.sync(this)
        }
        bindStepperSlider(
            seekBar = binding.seekIslandHeight,
            btnMinus = binding.btnIslandHeightMinus,
            btnPlus = binding.btnIslandHeightPlus,
            minVal = -12,
            maxVal = 24,
            step = 1,
            initial = prefs.islandDh
        ) { v ->
            prefs.islandDh = v
            updateIslandLabels()
            IslandOverlayManager.sync(this)
        }
        bindStepperSlider(
            seekBar = binding.seekIslandGap,
            btnMinus = binding.btnIslandGapMinus,
            btnPlus = binding.btnIslandGapPlus,
            minVal = 20,
            maxVal = 140,
            step = 2,
            initial = prefs.islandGapDp
        ) { v ->
            prefs.islandGapDp = v
            updateIslandLabels()
            IslandOverlayManager.sync(this)
        }
        binding.btnIslandIconLogo.setOnClickListener {
            prefs.islandLeftIconMode = 0
            refreshIslandPage()
            IslandOverlayManager.sync(this)
        }
        binding.btnIslandIconInfo.setOnClickListener {
            prefs.islandLeftIconMode = 1
            refreshIslandPage()
            IslandOverlayManager.sync(this)
        }

        binding.btnIslandModeSystem.setOnClickListener {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                Toast.makeText(this, I18n.tr("Máy này chưa có màu hệ thống động, hãy chọn màu Tùy chỉnh"), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            prefs.islandColorMode = PrefsManager.ISLAND_COLOR_SYSTEM
            refreshIslandPage()
            IslandOverlayManager.sync(this)
        }
        binding.btnIslandModeCustom.setOnClickListener {
            prefs.islandColorMode = PrefsManager.ISLAND_COLOR_CUSTOM
            refreshIslandPage()
            IslandOverlayManager.sync(this)
        }
        binding.btnIslandStyleSolid.setOnClickListener {
            prefs.islandStyle = PrefsManager.ISLAND_STYLE_SOLID
            refreshIslandPage()
            IslandOverlayManager.sync(this)
        }
        binding.btnIslandStyleGlass.setOnClickListener {
            prefs.islandStyle = PrefsManager.ISLAND_STYLE_GLASS
            refreshIslandPage()
            IslandOverlayManager.sync(this)
        }

        binding.btnIslandPreview.setOnClickListener {
            if (!IslandOverlayManager.canDrawOverlays(this)) {
                Toast.makeText(this, I18n.tr("Cần cấp quyền hiển thị trên ứng dụng khác trước"), Toast.LENGTH_LONG).show()
                openOverlaySettings()
                return@setOnClickListener
            }
            if (!prefs.islandEnabled) {
                prefs.islandEnabled = true
                FoxMonitorService.start(this)
                refreshIslandPage()
            }
            IslandOverlayManager.preview(this)
        }

        binding.btnIslandReset.setOnClickListener {
            prefs.resetIslandLayout()
            binding.seekIslandY.progress = prefs.islandDy + 16
            binding.seekIslandX.progress = prefs.islandDx + 150
            binding.seekIslandScale.progress = prefs.islandScale - 50
            binding.seekIslandWidth.progress = prefs.islandWidthDp - 60
            binding.seekIslandHeight.progress = prefs.islandDh + 12
            binding.seekIslandGap.progress = prefs.islandGapDp - 20
            updateIslandLabels()
            refreshIslandPage()
            IslandOverlayManager.sync(this)
            Toast.makeText(this, I18n.tr("Đã đặt lại vị trí & kích thước Dynamic Island"), Toast.LENGTH_SHORT).show()
        }

        binding.btnIslandOem.setOnClickListener {
            OemOptimizationHelper.openOemBackgroundSettings(this)
        }

        updateIslandLabels()
    }

    private fun openOverlaySettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        } catch (_: Exception) {
            try {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                )
            } catch (_: Exception) {
                Toast.makeText(this, I18n.tr("Hãy vào Cài đặt > Ứng dụng > FoxWidget để cấp quyền"), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun refreshIslandPage() {
        islandUiUpdating = true
        binding.swIslandEnabled.isChecked = prefs.islandEnabled
        binding.swIslandCalendar.isChecked = prefs.islandShowCalendar
        binding.swIslandWeather.isChecked = prefs.islandShowWeather
        binding.swIslandLocation.isChecked = prefs.islandShowLocation
        binding.swIslandBattery.isChecked = prefs.islandShowBattery
        binding.swIslandAutoCutout.isChecked = prefs.islandAutoCutout
        islandUiUpdating = false

        // Vị trí/chiều cao/khoảng trống thủ công: chỉ cần khi tắt tự căn hoặc máy không báo camera
        val detected = IslandOverlayManager.isCutoutDetected()
        val showManual = !prefs.islandAutoCutout || detected == false
        binding.llIslandManual.visibility = if (showManual) View.VISIBLE else View.GONE
        binding.tvIslandCutoutHint.text = when {
            !prefs.islandAutoCutout -> I18n.tr("Đang căn thủ công: chỉnh khoảng trống camera bên dưới. Vị trí, chiều cao, kích thước vẫn chỉnh được ở trên.")
            detected == true -> I18n.tr("Đã nhận diện camera của máy: viên thuốc tự căn khít vị trí camera, chừa trống phần giữa.")
            detected == false -> I18n.tr("Máy không báo vị trí camera, hãy chỉnh khoảng trống camera bên dưới.")
            else -> I18n.tr("Sẽ tự lấy vị trí camera của máy khi Dynamic Island hiển thị.")
        }
        setSegment(binding.btnIslandIconLogo, prefs.islandLeftIconMode == 0)
        setSegment(binding.btnIslandIconInfo, prefs.islandLeftIconMode == 1)

        val granted = IslandOverlayManager.canDrawOverlays(this)
        binding.tvIslandPermStatus.text = if (granted) {
            I18n.tr("Đã cấp quyền hiển thị trên ứng dụng khác")
        } else {
            I18n.tr("Chưa cấp quyền hiển thị trên ứng dụng khác")
        }
        styleStatusButton(binding.btnIslandPermission, granted)
        updateIslandLabels()
        refreshIslandColorUi()
    }

    private val islandSwatches = intArrayOf(
        0xFF000000.toInt(), 0xFF6C47FF.toInt(), 0xFF2563EB.toInt(), 0xFF0EA5A4.toInt(), 0xFF16A34A.toInt(),
        0xFFF59E0B.toInt(), 0xFFEA580C.toInt(), 0xFFDC2626.toInt(), 0xFFDB2777.toInt(), 0xFFF8F7FF.toInt()
    )

    private fun setSegment(view: TextView, active: Boolean) {
        view.setBackgroundResource(if (active) R.drawable.bg_segment_active else 0)
        view.setTextColor(
            if (active) Color.WHITE else ContextCompat.getColor(this, R.color.fox_text_sub)
        )
    }

    private fun refreshIslandColorUi() {
        val systemSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val systemMode = systemSupported && prefs.islandColorMode == PrefsManager.ISLAND_COLOR_SYSTEM

        setSegment(binding.btnIslandModeSystem, systemMode)
        setSegment(binding.btnIslandModeCustom, !systemMode)
        binding.btnIslandModeSystem.alpha = if (systemSupported) 1f else 0.4f
        setSegment(binding.btnIslandStyleSolid, prefs.islandStyle == PrefsManager.ISLAND_STYLE_SOLID)
        setSegment(binding.btnIslandStyleGlass, prefs.islandStyle == PrefsManager.ISLAND_STYLE_GLASS)

        binding.tvIslandColorHint.text = when {
            !systemSupported -> I18n.tr("Máy này (Android dưới 12) chưa có màu hệ thống động. Hãy chọn màu ở bên dưới.")
            systemMode -> I18n.tr("Đang dùng màu chủ đề của máy (Material You / màu hãng), tự đổi theo hình nền.")
            else -> I18n.tr("Đang dùng màu tùy chỉnh. Chọn \"Màu hệ thống\" để island tự theo màu của máy.")
        }
        binding.llIslandCustomColors.visibility = if (systemMode) View.GONE else View.VISIBLE
        if (!systemMode) buildIslandSwatches()
    }

    private fun buildIslandSwatches() {
        val host = binding.llIslandSwatches
        host.removeAllViews()
        val d = resources.displayMetrics.density
        val selectedColor = prefs.islandCustomColor or 0xFF000000.toInt()
        val primary = ContextCompat.getColor(this, R.color.fox_primary)

        islandSwatches.toList().chunked(5).forEach { chunk ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (8 * d).toInt() }
            }
            chunk.forEach { color ->
                val selected = color == selectedColor
                val cell = android.widget.FrameLayout(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, (44 * d).toInt(), 1f)
                }
                val dot = View(this).apply {
                    layoutParams = android.widget.FrameLayout.LayoutParams(
                        (38 * d).toInt(), (38 * d).toInt(), android.view.Gravity.CENTER
                    )
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        setColor(color)
                        if (selected) setStroke((3 * d).toInt(), primary)
                        else setStroke((1 * d).toInt().coerceAtLeast(1), 0x33000000)
                    }
                    contentDescription = I18n.tr("Màu island")
                    setOnClickListener {
                        prefs.islandColorMode = PrefsManager.ISLAND_COLOR_CUSTOM
                        prefs.islandCustomColor = color
                        buildIslandSwatches()
                        IslandOverlayManager.sync(this@MainActivity)
                    }
                }
                cell.addView(dot)
                row.addView(cell)
            }
            host.addView(row)
        }
    }

    private fun signedDp(v: Int): String = if (v > 0) "+$v" else "$v"

    private fun updateIslandLabels() {
        val y = prefs.islandDy
        binding.tvLabelIslandY.text =
            if (y == 0) I18n.tr("Lên/xuống: mặc định") else I18n.tf("Lên/xuống: {0}dp", signedDp(y))
        val x = prefs.islandDx
        binding.tvLabelIslandX.text = when {
            x == 0 -> I18n.tr("Trái/phải: căn giữa")
            x < 0 -> I18n.tf("Trái/phải: {0}dp sang trái", -x)
            else -> I18n.tf("Trái/phải: {0}dp sang phải", x)
        }
        val dh = prefs.islandDh
        binding.tvLabelIslandHeight.text =
            if (dh == 0) I18n.tr("Chiều cao: mặc định") else I18n.tf("Chiều cao: {0}dp", signedDp(dh))
        binding.tvLabelIslandScale.text = I18n.tf("Kích thước viên thuốc: {0}%", prefs.islandScale)
        binding.tvLabelIslandWidth.text = I18n.tf("Độ dài viên thuốc: {0}dp", prefs.islandWidthDp)
        binding.tvLabelIslandGap.text = I18n.tf("Khoảng trống giữa (camera): {0}dp", prefs.islandGapDp)
    }

    // ==================== TAB 6: THÔNG TIN ====================
    private fun setupInfoTab() {
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.6"
        } catch (_: Exception) {
            "1.6"
        }
        binding.tvHeaderVersion.text = "v$versionName"
        binding.tvInfoAppVersion.text = I18n.tf("Phiên bản {0}", versionName)

        binding.btnLangVi.setOnClickListener { changeLanguage(I18n.VI) }
        binding.btnLangEn.setOnClickListener { changeLanguage(I18n.EN) }
        refreshLanguageUi()

        binding.btnOpenAdvanced.setOnClickListener {
            startActivity(Intent(this, AdvancedSettingsActivity::class.java))
        }
        binding.btnContactFacebook.setOnClickListener { openLink(URL_FACEBOOK) }
        binding.btnContactTiktok.setOnClickListener { openLink(URL_TIKTOK) }
        binding.btnContactEmail.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$EMAIL_CONTACT")
                putExtra(Intent.EXTRA_SUBJECT, I18n.tr("Góp ý FoxWidget"))
            }
            try {
                startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, I18n.tf("Chưa có ứng dụng email. Gửi tới: {0}", EMAIL_CONTACT), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openLink(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, I18n.tf("Không mở được liên kết: {0}", url), Toast.LENGTH_LONG).show()
        }
    }

    /** Đổi ngôn ngữ: lưu lựa chọn, cập nhật widget/island/thông báo rồi dựng lại giao diện. */
    private fun changeLanguage(lang: String) {
        if (I18n.lang == lang) return
        I18n.setLanguage(this, lang)
        FoxWidgetProvider.updateAllWidgets(this)
        WidgetCommon.updateAll(this)
        IslandOverlayManager.sync(this)
        FoxMonitorService.start(this)
        recreate()
    }

    private fun refreshLanguageUi() {
        setSegment(binding.btnLangVi, I18n.lang == I18n.VI)
        setSegment(binding.btnLangEn, I18n.lang == I18n.EN)
    }

    private fun refreshInfoPage() {
        refreshLanguageUi()
        val oem = OemOptimizationHelper.detectOemProfile()
        val os = DeviceInfoHelper.getOsInfo()
        binding.tvInfoDeviceName.text = DeviceInfoHelper.getNameWithCode(this)
        binding.tvInfoOs.text = "${oem.brandTitle} · ${os.skinDisplay}"
        binding.tvInfoAndroid.text = buildString {
            append("Android ${os.androidVersion} (API ${os.apiLevel})")
            if (os.securityPatch.isNotBlank()) append(I18n.tf(" · Bản vá {0}", os.securityPatch))
        }
    }

    companion object {
        private const val URL_FACEBOOK = "https://www.facebook.com/nguyen.hai.225063"
        private const val URL_TIKTOK = "https://www.tiktok.com/@foxstudio257"
        private const val EMAIL_CONTACT = "haistrem6792@gmail.com"
    }
}
