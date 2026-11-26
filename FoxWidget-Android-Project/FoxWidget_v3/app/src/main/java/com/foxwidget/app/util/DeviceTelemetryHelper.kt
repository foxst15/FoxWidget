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

    fun getDeviceShortName(): String {
        val model = Build.MODEL ?: "Android"
        return if (model.length > 15) model.take(15) else model
    }

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

    suspend fun refreshWeatherAndLocation(context: Context) = withContext(Dispatchers.IO) {
        val prefs = PrefsManager(context)
        val loc = getLastKnownLocation(context)
        val lat = loc?.latitude ?: 15.1214
        val lon = loc?.longitude ?: 108.8044

        if (loc != null) {
            try {
                @Suppress("DEPRECATION")
                val geocoder = Geocoder(context, Locale("vi", "VN"))
                val addresses = geocoder.getFromLocation(lat, lon, 1)
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    val place = addr.subLocality
                        ?: addr.locality
                        ?: addr.subAdminArea
                        ?: addr.adminArea
                    if (!place.isNullOrBlank()) {
                        prefs.cachedLocationName = place
                    }
                }
            } catch (_: Exception) {
            }
        }

        try {
            val url = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                requestMethod = "GET"
            }
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val current = json.getJSONObject("current_weather")
                val temp = current.getDouble("temperature").toInt()
                val code = current.getInt("weathercode")
                prefs.cachedTempC = temp
                prefs.cachedWeatherDesc = mapWeatherCodeVi(code)
            }
            conn.disconnect()
        } catch (_: Exception) {
        }
    }

    private fun mapWeatherCodeVi(code: Int): String {
        return when (code) {
            0 -> "Trời quang · Nắng đẹp"
            1, 2 -> "Nắng nhẹ · Ít mây"
            3 -> "Nhiều mây · Mát mẻ"
            45, 48 -> "Sương mù nhẹ"
            51, 53, 55, 61, 63, 65 -> "Có mưa rào"
            80, 81, 82 -> "Mưa rào rải rác"
            95, 96, 99 -> "Có dông"
            else -> "Thời tiết ổn định"
        }
    }
}
