package com.foxwidget.app.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.foxwidget.app.data.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

object DeviceTelemetryHelper {

    data class BatteryStats(
        val levelPercent: Int,
        val temperatureC: Float,
        val isCharging: Boolean
    )

    fun getBatteryStats(context: Context): BatteryStats {
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent? = context.registerReceiver(null, intentFilter)
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 85
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
        val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else 85

        val tempTenths = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 350) ?: 350
        val tempC = tempTenths / 10.0f

        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        return BatteryStats(pct, tempC, isCharging)
    }

    /** Tên thương mại của máy (Galaxy S26, Redmi Note 14...), không chỉ mã model. */
    fun getDeviceShortName(context: Context): String = DeviceInfoHelper.getShortName(context)

    @Suppress("MissingPermission")
    fun getLastKnownLocation(context: Context): Location? {
        val hasFine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return null

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = lm.getProviders(true)
        var bestLocation: Location? = null
        for (provider in providers) {
            val l = lm.getLastKnownLocation(provider) ?: continue
            if (bestLocation == null || l.accuracy < bestLocation.accuracy) {
                bestLocation = l
            }
        }
        return bestLocation
    }

    /** Làm mới thời tiết + địa danh (đa nguồn, xem WeatherHelper). Giữ tên hàm cũ cho các nơi đang gọi. */
    suspend fun refreshWeatherAndLocation(context: Context) {
        WeatherHelper.refresh(context)
    }
}
