package com.foxwidget.app.data

import android.content.Context
import android.content.SharedPreferences

class PrefsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("fox_widget_prefs", Context.MODE_PRIVATE)

    companion object {
        const val STYLE_LIGHT = 0
        const val STYLE_DARK = 1
        const val STYLE_LIQUID_GLASS = 2

        const val BG_OBSIDIAN_TITAN = 0
        const val BG_PURPLE_DUSK = 1
        const val BG_OCEAN_BLUE = 2
        const val BG_EMERALD_MIST = 3
        const val BG_PRISMATIC_FROST = 4

        // Dynamic Island: nguồn màu & kiểu nền
        const val ISLAND_COLOR_SYSTEM = 0
        const val ISLAND_COLOR_CUSTOM = 1
        const val ISLAND_STYLE_SOLID = 0
        const val ISLAND_STYLE_GLASS = 1

        // Preset giao diện Dynamic Island theo từng hãng (0 = tự chọn theo hãng của máy)
        const val ISLAND_PRESET_AUTO = 0
        const val ISLAND_PRESET_CLASSIC = 1   // iOS Dynamic Island
        const val ISLAND_PRESET_HYPER = 2     // Xiaomi HyperOS - Hyper Island
        const val ISLAND_PRESET_FLUID = 3     // OPPO / OnePlus / realme - Fluid Cloud
        const val ISLAND_PRESET_ATOMIC = 4    // vivo / iQOO - Atomic Island
        const val ISLAND_PRESET_CAPSULE = 5   // HONOR - Magic Capsule
        const val ISLAND_PRESET_NOWBAR = 6    // Samsung One UI - Now Bar / Live Notifications

        const val TEMP_C = 0
        const val TEMP_F = 1
    }

    var widgetStyle: Int
        get() = prefs.getInt("widget_style", STYLE_LIQUID_GLASS)
        set(value) = prefs.edit().putInt("widget_style", value).apply()

    var widgetBgPreset: Int
        get() = prefs.getInt("widget_bg_preset", BG_OBSIDIAN_TITAN)
        set(value) = prefs.edit().putInt("widget_bg_preset", value).apply()

    var dateFontScale: Int
        get() = prefs.getInt("date_font_scale", 100)
        set(value) = prefs.edit().putInt("date_font_scale", value).apply()

    var clockFontScale: Int
        get() = prefs.getInt("clock_font_scale", 100)
        set(value) = prefs.edit().putInt("clock_font_scale", value).apply()

    var borderWidthDp: Int
        get() = prefs.getInt("border_width_dp", 1) // 1 = tự động
        set(value) = prefs.edit().putInt("border_width_dp", value).apply()

    var cornerRadiusDp: Int
        get() = prefs.getInt("corner_radius_dp", 22) // 22 = mặc định / đồng bộ hệ thống
        set(value) = prefs.edit().putInt("corner_radius_dp", value).apply()

    var glassOpacity: Int
        get() = prefs.getInt("glass_opacity", 74)
        set(value) = prefs.edit().putInt("glass_opacity", value).apply()

    // Độ khúc xạ & tán sắc cầu vồng viền kính (0 - 100%)
    var glassRefraction: Int
        get() = prefs.getInt("glass_refraction", 85)
        set(value) = prefs.edit().putInt("glass_refraction", value).apply()

    // Độ bóng phản quang bề mặt giọt nước (0 - 100%)
    var glassGloss: Int
        get() = prefs.getInt("glass_gloss", 80)
        set(value) = prefs.edit().putInt("glass_gloss", value).apply()

    var showLunarDate: Boolean
        get() = prefs.getBoolean("show_lunar_date", true)
        set(value) = prefs.edit().putBoolean("show_lunar_date", value).apply()

    var showWeekStrip: Boolean
        get() = prefs.getBoolean("show_week_strip", true)
        set(value) = prefs.edit().putBoolean("show_week_strip", value).apply()

    var showWeather: Boolean
        get() = prefs.getBoolean("show_weather", true)
        set(value) = prefs.edit().putBoolean("show_weather", value).apply()

    var showBatteryInfo: Boolean
        get() = prefs.getBoolean("show_battery_info", true)
        set(value) = prefs.edit().putBoolean("show_battery_info", value).apply()

    var showDeviceName: Boolean
        get() = prefs.getBoolean("show_device_name", true)
        set(value) = prefs.edit().putBoolean("show_device_name", value).apply()

    var cachedTempC: Int
        get() = prefs.getInt("cached_temp_c", 24)
        set(value) = prefs.edit().putInt("cached_temp_c", value).apply()

    var cachedLocationName: String
        get() = prefs.getString("cached_location_name", "Quảng Ngãi") ?: "Quảng Ngãi"
        set(value) = prefs.edit().putString("cached_location_name", value).apply()

    var cachedWeatherDesc: String
        get() = prefs.getString("cached_weather_desc", "Nắng nhẹ · Ít mây") ?: "Nắng nhẹ · Ít mây"
        set(value) = prefs.edit().putString("cached_weather_desc", value).apply()


    // ===== Dynamic Island (overlay) =====
    var islandEnabled: Boolean
        get() = prefs.getBoolean("island_enabled", false)
        set(value) = prefs.edit().putBoolean("island_enabled", value).apply()

    var islandShowCalendar: Boolean
        get() = prefs.getBoolean("island_show_calendar", true)
        set(value) = prefs.edit().putBoolean("island_show_calendar", value).apply()

    var islandShowWeather: Boolean
        get() = prefs.getBoolean("island_show_weather", true)
        set(value) = prefs.edit().putBoolean("island_show_weather", value).apply()

    var islandShowLocation: Boolean
        get() = prefs.getBoolean("island_show_location", true)
        set(value) = prefs.edit().putBoolean("island_show_location", value).apply()

    var islandShowBattery: Boolean
        get() = prefs.getBoolean("island_show_battery", true)
        set(value) = prefs.edit().putBoolean("island_show_battery", value).apply()

    // Khoảng cách từ mép trên màn hình (dp)
    var islandOffsetYDp: Int
        get() = prefs.getInt("island_offset_y_dp", 6)
        set(value) = prefs.edit().putInt("island_offset_y_dp", value).apply()

    // Lệch ngang so với giữa màn hình (dp, âm = sang trái)
    var islandOffsetXDp: Int
        get() = prefs.getInt("island_offset_x_dp", 0)
        set(value) = prefs.edit().putInt("island_offset_x_dp", value).apply()

    // Kích thước (%)
    var islandScale: Int
        get() = prefs.getInt("island_scale", 100)
        set(value) = prefs.edit().putInt("island_scale", value).apply()

    // 0 = theo màu hệ thống/hãng (Android 12+), 1 = tùy chỉnh. Máy cũ mặc định tùy chỉnh.
    var islandColorMode: Int
        get() = prefs.getInt("island_color_mode", ISLAND_COLOR_CUSTOM)
        set(value) = prefs.edit().putInt("island_color_mode", value).apply()

    var islandCustomColor: Int
        get() = prefs.getInt("island_custom_color", 0xFF000000.toInt())
        set(value) = prefs.edit().putInt("island_custom_color", value).apply()

    var islandStyle: Int
        get() = prefs.getInt("island_style", ISLAND_STYLE_SOLID)
        set(value) = prefs.edit().putInt("island_style", value).apply()

    // Độ dài viên thuốc (dp) - kéo dài tùy ý
    var islandWidthDp: Int
        get() = prefs.getInt("island_width_dp", 150)
        set(value) = prefs.edit().putInt("island_width_dp", value).apply()

    // Chiều cao viên thuốc (dp) - chỉ dùng khi tắt tự căn camera
    var islandHeightDp: Int
        get() = prefs.getInt("island_height_dp", 36)
        set(value) = prefs.edit().putInt("island_height_dp", value).apply()

    // Khoảng trống ở giữa dành cho camera (dp) - chỉ dùng khi tắt tự căn camera
    var islandGapDp: Int
        get() = prefs.getInt("island_gap_dp", 54)
        set(value) = prefs.edit().putInt("island_gap_dp", value).apply()

    // Tự lấy vị trí/kích thước camera đục lỗ từ hệ thống để căn viên thuốc
    var islandAutoCutout: Boolean
        get() = prefs.getBoolean("island_auto_cutout", true)
        set(value) = prefs.edit().putBoolean("island_auto_cutout", value).apply()

    // Icon bên trái: 0 = logo FoxWidget, 1 = biểu tượng của thông tin đang hiển thị
    var islandLeftIconMode: Int
        get() = prefs.getInt("island_left_icon_mode", 0)
        set(value) = prefs.edit().putInt("island_left_icon_mode", value).apply()

    // ===== Preset island theo hãng =====
    var islandPreset: Int
        get() = prefs.getInt("island_preset", ISLAND_PRESET_AUTO)
        set(value) = prefs.edit().putInt("island_preset", value).apply()

    var islandShowHoliday: Boolean
        get() = prefs.getBoolean("island_show_holiday", true)
        set(value) = prefs.edit().putBoolean("island_show_holiday", value).apply()

    var islandShowEvent: Boolean
        get() = prefs.getBoolean("island_show_event", true)
        set(value) = prefs.edit().putBoolean("island_show_event", value).apply()

    var islandWeatherDetail: Boolean
        get() = prefs.getBoolean("island_weather_detail", true)
        set(value) = prefs.edit().putBoolean("island_weather_detail", value).apply()

    // ===== Widget: tùy chọn bổ sung =====
    var tempUnit: Int
        get() = prefs.getInt("temp_unit", TEMP_C)
        set(value) = prefs.edit().putInt("temp_unit", value).apply()

    var clock24h: Boolean
        get() = prefs.getBoolean("clock_24h", true)
        set(value) = prefs.edit().putBoolean("clock_24h", value).apply()

    var showCanChi: Boolean
        get() = prefs.getBoolean("show_can_chi", true)
        set(value) = prefs.edit().putBoolean("show_can_chi", value).apply()

    var showHolidayLine: Boolean
        get() = prefs.getBoolean("show_holiday_line", true)
        set(value) = prefs.edit().putBoolean("show_holiday_line", value).apply()

    var showNextEvent: Boolean
        get() = prefs.getBoolean("show_next_event", false)
        set(value) = prefs.edit().putBoolean("show_next_event", value).apply()

    var showWeatherDetail: Boolean
        get() = prefs.getBoolean("show_weather_detail", true)
        set(value) = prefs.edit().putBoolean("show_weather_detail", value).apply()

    var forecastDays: Int
        get() = prefs.getInt("forecast_days", 5)
        set(value) = prefs.edit().putInt("forecast_days", value).apply()

    var calendarWeekStartMonday: Boolean
        get() = prefs.getBoolean("calendar_week_start_monday", true)
        set(value) = prefs.edit().putBoolean("calendar_week_start_monday", value).apply()

    var calendarShowLunar: Boolean
        get() = prefs.getBoolean("calendar_show_lunar", true)
        set(value) = prefs.edit().putBoolean("calendar_show_lunar", value).apply()

    // ===== Vị trí thời tiết =====
    var manualLocationEnabled: Boolean
        get() = prefs.getBoolean("manual_location_enabled", false)
        set(value) = prefs.edit().putBoolean("manual_location_enabled", value).apply()

    var manualLat: Float
        get() = prefs.getFloat("manual_lat", 15.1214f)
        set(value) = prefs.edit().putFloat("manual_lat", value).apply()

    var manualLon: Float
        get() = prefs.getFloat("manual_lon", 108.8044f)
        set(value) = prefs.edit().putFloat("manual_lon", value).apply()

    var manualName: String
        get() = prefs.getString("manual_name", "") ?: ""
        set(value) = prefs.edit().putString("manual_name", value).apply()

    var lastLat: Float
        get() = prefs.getFloat("last_lat", 15.1214f)
        set(value) = prefs.edit().putFloat("last_lat", value).apply()

    var lastLon: Float
        get() = prefs.getFloat("last_lon", 108.8044f)
        set(value) = prefs.edit().putFloat("last_lon", value).apply()

    // Dữ liệu thời tiết đầy đủ (JSON, xem WeatherHelper)
    var weatherJson: String
        get() = prefs.getString("weather_json", "") ?: ""
        set(value) = prefs.edit().putString("weather_json", value).apply()

    fun resetIslandLayout() {
        prefs.edit()
            .putInt("island_offset_y_dp", 6)
            .putInt("island_offset_x_dp", 0)
            .putInt("island_scale", 100)
            .putInt("island_width_dp", 150)
            .putInt("island_height_dp", 36)
            .putInt("island_gap_dp", 54)
            .putBoolean("island_auto_cutout", true)
            .apply()
    }

    fun resetDefaults() {
        prefs.edit()
            .putInt("widget_style", STYLE_LIQUID_GLASS)
            .putInt("widget_bg_preset", BG_OBSIDIAN_TITAN)
            .putInt("date_font_scale", 100)
            .putInt("clock_font_scale", 100)
            .putInt("border_width_dp", 1)
            .putInt("corner_radius_dp", 22)
            .putInt("glass_opacity", 74)
            .putInt("glass_refraction", 85)
            .putInt("glass_gloss", 80)
            .putBoolean("show_lunar_date", true)
            .putBoolean("show_week_strip", true)
            .putBoolean("show_weather", true)
            .putBoolean("show_battery_info", true)
            .putBoolean("show_device_name", true)
            .apply()
    }
}
