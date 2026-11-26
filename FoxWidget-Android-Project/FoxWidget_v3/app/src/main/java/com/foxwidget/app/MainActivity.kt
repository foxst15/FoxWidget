package com.foxwidget.app

import android.Manifest
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
import com.foxwidget.app.util.DeviceTelemetryHelper
import com.foxwidget.app.util.LiquidGlassRenderer
import com.foxwidget.app.util.OemOptimizationHelper
import com.foxwidget.app.util.VietLunarCalendar
import com.foxwidget.app.widget.FoxWidgetProvider
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

        applyEdgeToEdgeInsets()

        prefs = PrefsManager(this)
        FoxMonitorService.start(this)

        setupBottomTabs()
        setupPermissionsTab()
        setupCustomizeTab()
        setupOtherTabs()
        setupInfoTab()

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
            binding.pageWidget,
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
        if (index == 5) refreshInfoPage()
    }

    // ==================== TAB 1: BẢN QUYỀN & TỐI ƯU OEM ====================
    private fun setupPermissionsTab() {
        val oem = OemOptimizationHelper.detectOemProfile()
        binding.tvOemDetectedBanner.text = "Thiết bị: ${oem.brandTitle} · ${oem.osEstimate}"

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
                .setTitle("Tối ưu cho ${profile.brandTitle}")
                .setMessage(
                    "Hệ điều hành: ${profile.osEstimate}\n\n" +
                    "1. Tự khởi động & Chạy ngầm:\n${profile.autostartGuide}\n\n" +
                    "2. Chống đóng băng pin:\n${profile.batteryGuide}\n\n" +
                    "3. Vị trí GPS:\nChọn 'Chỉ khi dùng ứng dụng'. Vị trí được làm mới mỗi lần bạn mở FoxWidget; khi chạy ngầm widget dùng vị trí đã lưu gần nhất, thời tiết vẫn cập nhật mỗi 15 phút."
                )
                .setPositiveButton("Mở cài đặt hãng") { _, _ ->
                    OemOptimizationHelper.openOemBackgroundSettings(this)
                }
                .setNegativeButton("Đóng", null)
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

        binding.tvPermCounter.text = "Đã bật $enabledCount/4 quyền"
        styleStatusButton(binding.btnPermLocation, hasLoc)
        binding.btnPermBackground.text = "Mở"
        styleStatusButton(binding.btnPermBattery, isIgnoringBattery)
        styleStatusButton(binding.btnPermNotification, hasNotif)

        val ready = enabledCount == 4
        binding.btnAllPermsReady.text = if (ready) {
            "Tất cả quyền đã sẵn sàng"
        } else {
            "Còn thiếu ${4 - enabledCount} quyền cần cấp"
        }
        binding.btnAllPermsReady.setIconResource(if (ready) R.drawable.ic_check else R.drawable.ic_info)
        binding.btnAllPermsReady.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, if (ready) R.color.fox_success else R.color.fox_warn)
        )
    }

    /** Nút trạng thái bo tròn: đã bật = xanh lá nhạt, chưa bật = tím đặc. */
    private fun styleStatusButton(btn: MaterialButton, granted: Boolean) {
        btn.text = if (granted) "Đã bật" else "Bật ngay"
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
            Toast.makeText(this, "Đã khôi phục mặc định Kính Lỏng Quang Học!", Toast.LENGTH_SHORT).show()
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
            "Cỡ chữ ngày dương & âm lịch: 100% (mặc định)"
        } else {
            "Cỡ chữ ngày dương & âm lịch: ${prefs.dateFontScale}%"
        }

        binding.tvLabelClockSize.text = "Cỡ đồng hồ: ${prefs.clockFontScale}%"
        binding.tvLabelBorderWidth.text = if (prefs.borderWidthDp == 1) {
            "Độ rộng viền: tự động"
        } else {
            "Độ rộng viền: ${prefs.borderWidthDp}dp"
        }
        binding.tvLabelCornerRadius.text = if (prefs.cornerRadiusDp == 22) {
            "Bo góc widget: mặc định (đồng bộ OS)"
        } else {
            "Bo góc widget: ${prefs.cornerRadiusDp}dp"
        }
        binding.tvLabelGlassOpacity.text = "Độ đặc khối kính: ${prefs.glassOpacity}%"
        binding.tvLabelGlassRefraction.text = "Tán sắc cầu vồng & khúc xạ viền: ${prefs.glassRefraction}%"
        binding.tvLabelGlassGloss.text = "Độ bóng vòm giọt nước: ${prefs.glassGloss}%"

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
        binding.tvPreviewDevice.text = "📱 ${DeviceTelemetryHelper.getDeviceShortName()}"
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
            Toast.makeText(this, "Đang cập nhật thời tiết & vị trí...", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                DeviceTelemetryHelper.refreshWeatherAndLocation(this@MainActivity)
                refreshWeatherPage()
                updateLivePreview()
                FoxWidgetProvider.updateAllWidgets(this@MainActivity)
            }
        }

        binding.btnPinWidgetHome.setOnClickListener {
            val appWidgetManager = AppWidgetManager.getInstance(this)
            val provider = ComponentName(this, FoxWidgetProvider::class.java)
            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                appWidgetManager.requestPinAppWidget(provider, null, null)
            } else {
                Toast.makeText(
                    this,
                    "Nhấn giữ màn hình chính > Chọn Tiện ích (Widgets) > Tìm FoxWidget!",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        binding.btnForceSyncWidget.setOnClickListener {
            lifecycleScope.launch {
                DeviceTelemetryHelper.refreshWeatherAndLocation(this@MainActivity)
                FoxWidgetProvider.updateAllWidgets(this@MainActivity)
                Toast.makeText(this@MainActivity, "Đã đồng bộ lại toàn bộ Widget trên màn hình!", Toast.LENGTH_SHORT).show()
            }
        }
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
        binding.tvLunarBigSolar.text = VietLunarCalendar.formatSolarDateFull(now) + " năm ${now.get(Calendar.YEAR)}"
        binding.tvLunarBigLunar.text = "${VietLunarCalendar.formatLunarDateFull(now)} · Năm $canChiYear"
        binding.tvLunarCanChiDetail.text = "Ngày $canChiDay · Giờ Hoàng Đạo thuận lợi cho công việc & khởi tạo"
    }

    private fun refreshWeatherPage() {
        val battery = DeviceTelemetryHelper.getBatteryStats(this)
        binding.tvWeatherDetailLocation.text = "Khu vực: ${prefs.cachedLocationName}"
        binding.tvWeatherDetailTemp.text = "Ngoài trời: ${prefs.cachedTempC}°C (${prefs.cachedWeatherDesc})"
        binding.tvWeatherDetailBattery.text = String.format(
            Locale.US,
            "Pin %d%% · Nhiệt độ pin %.1f°C",
            battery.levelPercent,
            battery.temperatureC
        )
    }

    // ==================== TAB 6: THÔNG TIN ====================
    private fun setupInfoTab() {
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "3.1"
        } catch (_: Exception) {
            "3.1"
        }
        binding.tvHeaderVersion.text = "v$versionName"
        binding.tvInfoAppVersion.text = "Phiên bản $versionName"

        binding.btnContactFacebook.setOnClickListener { openLink(URL_FACEBOOK) }
        binding.btnContactTiktok.setOnClickListener { openLink(URL_TIKTOK) }
        binding.btnContactEmail.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$EMAIL_CONTACT")
                putExtra(Intent.EXTRA_SUBJECT, "Góp ý FoxWidget")
            }
            try {
                startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, "Chưa có ứng dụng email. Gửi tới: $EMAIL_CONTACT", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openLink(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Không mở được liên kết: $url", Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshInfoPage() {
        val oem = OemOptimizationHelper.detectOemProfile()
        binding.tvInfoDeviceName.text = DeviceTelemetryHelper.getDeviceShortName()
        binding.tvInfoOs.text = "${oem.brandTitle} · ${oem.osEstimate}"
        binding.tvInfoAndroid.text = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    }

    companion object {
        private const val URL_FACEBOOK = "https://www.facebook.com/nguyen.hai.225063"
        private const val URL_TIKTOK = "https://www.tiktok.com/@foxstudio257"
        private const val EMAIL_CONTACT = "haistrem6792@gmail.com"
    }
}
