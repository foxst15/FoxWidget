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
