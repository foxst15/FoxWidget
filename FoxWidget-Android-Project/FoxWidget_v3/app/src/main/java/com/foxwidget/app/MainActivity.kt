package com.foxwidget.app

import android.Manifest
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
import com.foxwidget.app.util.DeviceTelemetryHelper
import com.foxwidget.app.util.IslandOverlayManager
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

        binding.btnPinWidgetHome.setOnClickListener { requestPinWidget() }
        binding.btnWidgetManualGuide.setOnClickListener { showManualWidgetGuide(null) }

        binding.btnForceSyncWidget.setOnClickListener {
            lifecycleScope.launch {
                DeviceTelemetryHelper.refreshWeatherAndLocation(this@MainActivity)
                FoxWidgetProvider.updateAllWidgets(this@MainActivity)
                Toast.makeText(this@MainActivity, "Đã đồng bộ lại toàn bộ Widget trên màn hình!", Toast.LENGTH_SHORT).show()
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
            showManualWidgetGuide("Launcher hiện tại không hỗ trợ thêm widget tự động.")
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
                "Hãy bấm \"Thêm\" trên màn hình chính. Không thấy hộp thoại thì dùng hướng dẫn thủ công bên dưới.",
                Toast.LENGTH_LONG
            ).show()
        } else {
            showManualWidgetGuide("Launcher đã từ chối yêu cầu thêm widget tự động.")
        }
    }

    private fun showManualWidgetGuide(reason: String?) {
        val mfg = (Build.MANUFACTURER ?: "").lowercase(Locale.ROOT)
        val brandTip = when {
            mfg.contains("xiaomi") || mfg.contains("redmi") || mfg.contains("poco") ->
                "Xiaomi / Redmi / POCO: vào Cài đặt > Ứng dụng > FoxWidget > Quyền khác, bật " +
                    "\"Tạo lối tắt trên Màn hình chính\" rồi bấm lại nút Thêm."
            mfg.contains("oppo") || mfg.contains("realme") || mfg.contains("oneplus") ->
                "OPPO / Realme / OnePlus: vào Thông tin ứng dụng FoxWidget > Quyền, bật quyền " +
                    "\"Lối tắt trên màn hình chính\" (nếu có) rồi thử lại."
            mfg.contains("vivo") || mfg.contains("iqoo") ->
                "vivo / iQOO: vào Thông tin ứng dụng FoxWidget > Quyền khác, bật " +
                    "\"Tạo lối tắt trên màn hình chính\" rồi thử lại."
            mfg.contains("huawei") || mfg.contains("honor") ->
                "Huawei / Honor: vào Thông tin ứng dụng FoxWidget > Quyền, bật quyền " +
                    "\"Tạo lối tắt trên màn hình chính\" rồi thử lại."
            else -> null
        }
        val message = buildString {
            if (reason != null) append(reason).append("\n\n")
            append("Cách thêm thủ công (hoạt động trên mọi máy):\n")
            append("1. Nhấn giữ vào chỗ trống trên màn hình chính.\n")
            append("2. Chọn \"Tiện ích\" (Widgets).\n")
            append("3. Tìm \"FoxWidget\", nhấn giữ rồi kéo ra màn hình.")
            if (brandTip != null) append("\n\n").append(brandTip)
        }
        AlertDialog.Builder(this)
            .setTitle("Thêm FoxWidget ra màn hình chính")
            .setMessage(message)
            .setPositiveButton("Đã hiểu", null)
            .setNeutralButton("Mở quyền ứng dụng") { _, _ ->
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
                        "Hãy bật \"Hiển thị trên ứng dụng khác\" cho FoxWidget",
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
            minVal = 0,
            maxVal = 120,
            step = 2,
            initial = prefs.islandOffsetYDp
        ) { v ->
            prefs.islandOffsetYDp = v
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
            initial = prefs.islandOffsetXDp
        ) { v ->
            prefs.islandOffsetXDp = v
            updateIslandLabels()
            IslandOverlayManager.sync(this)
        }
        bindStepperSlider(
            seekBar = binding.seekIslandScale,
            btnMinus = binding.btnIslandScaleMinus,
            btnPlus = binding.btnIslandScalePlus,
            minVal = 70,
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
            minVal = 24,
            maxVal = 56,
            step = 1,
            initial = prefs.islandHeightDp
        ) { v ->
            prefs.islandHeightDp = v
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
                Toast.makeText(this, "Máy này chưa có màu hệ thống động, hãy chọn màu Tùy chỉnh", Toast.LENGTH_LONG).show()
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
                Toast.makeText(this, "Cần cấp quyền hiển thị trên ứng dụng khác trước", Toast.LENGTH_LONG).show()
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
            binding.seekIslandY.progress = prefs.islandOffsetYDp
            binding.seekIslandX.progress = prefs.islandOffsetXDp + 150
            binding.seekIslandScale.progress = prefs.islandScale - 70
            binding.seekIslandWidth.progress = prefs.islandWidthDp - 60
            binding.seekIslandHeight.progress = prefs.islandHeightDp - 24
            binding.seekIslandGap.progress = prefs.islandGapDp - 20
            updateIslandLabels()
            refreshIslandPage()
            IslandOverlayManager.sync(this)
            Toast.makeText(this, "Đã đặt lại vị trí & kích thước Dynamic Island", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "Hãy vào Cài đặt > Ứng dụng > FoxWidget để cấp quyền", Toast.LENGTH_LONG).show()
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
            !prefs.islandAutoCutout -> "Đang căn thủ công: tự chỉnh vị trí, chiều cao và khoảng trống camera bên dưới."
            detected == true -> "Đã nhận diện camera của máy: viên thuốc tự căn khít vị trí camera, chừa trống phần giữa."
            detected == false -> "Máy không báo vị trí camera, đang dùng thông số thủ công bên dưới."
            else -> "Sẽ tự lấy vị trí camera của máy khi Dynamic Island hiển thị."
        }
        setSegment(binding.btnIslandIconLogo, prefs.islandLeftIconMode == 0)
        setSegment(binding.btnIslandIconInfo, prefs.islandLeftIconMode == 1)

        val granted = IslandOverlayManager.canDrawOverlays(this)
        binding.tvIslandPermStatus.text = if (granted) {
            "Đã cấp quyền hiển thị trên ứng dụng khác"
        } else {
            "Chưa cấp quyền hiển thị trên ứng dụng khác"
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
            !systemSupported -> "Máy này (Android dưới 12) chưa có màu hệ thống động. Hãy chọn màu ở bên dưới."
            systemMode -> "Đang dùng màu chủ đề của máy (Material You / màu hãng), tự đổi theo hình nền."
            else -> "Đang dùng màu tùy chỉnh. Chọn \"Màu hệ thống\" để island tự theo màu của máy."
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
                    contentDescription = "Màu island"
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

    private fun updateIslandLabels() {
        binding.tvLabelIslandY.text = "Cách mép trên: ${prefs.islandOffsetYDp}dp"
        val x = prefs.islandOffsetXDp
        binding.tvLabelIslandX.text = when {
            x == 0 -> "Lệch ngang: giữa màn hình"
            x < 0 -> "Lệch ngang: ${-x}dp sang trái"
            else -> "Lệch ngang: ${x}dp sang phải"
        }
        binding.tvLabelIslandScale.text = "Kích thước chữ & icon: ${prefs.islandScale}%"
        binding.tvLabelIslandWidth.text = "Độ dài viên thuốc: ${prefs.islandWidthDp}dp"
        binding.tvLabelIslandHeight.text = "Chiều cao: ${prefs.islandHeightDp}dp"
        binding.tvLabelIslandGap.text = "Khoảng trống giữa (camera): ${prefs.islandGapDp}dp"
    }

    // ==================== TAB 6: THÔNG TIN ====================
    private fun setupInfoTab() {
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.4"
        } catch (_: Exception) {
            "1.4"
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
