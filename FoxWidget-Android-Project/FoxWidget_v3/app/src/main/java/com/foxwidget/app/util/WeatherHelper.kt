package com.foxwidget.app.util

import android.content.Context
import android.location.Geocoder
import com.foxwidget.app.data.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Thời tiết đa nguồn:
 *  1. Open-Meteo Forecast  : hiện tại + 12 giờ tới + 7 ngày + UV + bình minh/hoàng hôn (nguồn chính)
 *  2. Open-Meteo Air Quality: chỉ số AQI (US) + bụi mịn PM2.5
 *  3. MET Norway Locationforecast: nguồn dự phòng khi Open-Meteo lỗi
 *  Tên địa danh: Geocoder của hệ thống -> BigDataCloud (dự phòng).
 *  Tìm thành phố thủ công: Open-Meteo Geocoding.
 */
object WeatherHelper {

    data class Hour(val label: String, val tempC: Double, val code: Int, val rainProb: Int)
    data class Day(val label: String, val hiC: Double, val loC: Double, val code: Int, val rainProb: Int)

    data class Snapshot(
        val source: String,
        val updatedAt: Long,
        val tempC: Double,
        val feelsC: Double,
        val humidity: Int,
        val windKmh: Double,
        val code: Int,
        val isDay: Boolean,
        val uv: Double,
        val rainProb: Int,
        val hiC: Double,
        val loC: Double,
        val sunrise: String,
        val sunset: String,
        val aqi: Int,
        val pm25: Double,
        val hourly: List<Hour>,
        val daily: List<Day>
    )

    data class PlaceResult(val name: String, val detail: String, val lat: Double, val lon: Double)

    // ------------------------------------------------------------------ đọc / ghi cache

    fun load(context: Context): Snapshot? {
        val raw = PrefsManager(context).weatherJson
        if (raw.isBlank()) return null
        return try {
            val o = JSONObject(raw)
            val hs = o.optJSONArray("hourly") ?: JSONArray()
            val ds = o.optJSONArray("daily") ?: JSONArray()
            Snapshot(
                source = o.optString("src"),
                updatedAt = o.optLong("ts"),
                tempC = o.optDouble("temp", 0.0),
                feelsC = o.optDouble("feels", 0.0),
                humidity = o.optInt("hum", -1),
                windKmh = o.optDouble("wind", 0.0),
                code = o.optInt("code", 0),
                isDay = o.optBoolean("day", true),
                uv = o.optDouble("uv", -1.0),
                rainProb = o.optInt("rain", -1),
                hiC = o.optDouble("hi", 0.0),
                loC = o.optDouble("lo", 0.0),
                sunrise = o.optString("sunrise"),
                sunset = o.optString("sunset"),
                aqi = o.optInt("aqi", -1),
                pm25 = o.optDouble("pm25", -1.0),
                hourly = (0 until hs.length()).map {
                    val h = hs.getJSONObject(it)
                    Hour(h.optString("h"), h.optDouble("t"), h.optInt("c"), h.optInt("p", -1))
                },
                daily = (0 until ds.length()).map {
                    val d = ds.getJSONObject(it)
                    Day(d.optString("d"), d.optDouble("hi"), d.optDouble("lo"), d.optInt("c"), d.optInt("p", -1))
                }
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun save(context: Context, s: Snapshot) {
        val o = JSONObject()
        o.put("src", s.source); o.put("ts", s.updatedAt)
        o.put("temp", s.tempC); o.put("feels", s.feelsC); o.put("hum", s.humidity)
        o.put("wind", s.windKmh); o.put("code", s.code); o.put("day", s.isDay)
        o.put("uv", s.uv); o.put("rain", s.rainProb); o.put("hi", s.hiC); o.put("lo", s.loC)
        o.put("sunrise", s.sunrise); o.put("sunset", s.sunset); o.put("aqi", s.aqi); o.put("pm25", s.pm25)
        val hs = JSONArray()
        s.hourly.forEach { hs.put(JSONObject().put("h", it.label).put("t", it.tempC).put("c", it.code).put("p", it.rainProb)) }
        val ds = JSONArray()
        s.daily.forEach { ds.put(JSONObject().put("d", it.label).put("hi", it.hiC).put("lo", it.loC).put("c", it.code).put("p", it.rainProb)) }
        o.put("hourly", hs); o.put("daily", ds)

        val prefs = PrefsManager(context)
        prefs.weatherJson = o.toString()
        // Giữ tương thích với phần cũ (widget chính, island)
        prefs.cachedTempC = s.tempC.roundToInt()
        prefs.cachedWeatherDesc = describe(s.code, s.isDay)
    }

    // ------------------------------------------------------------------ làm mới

    suspend fun refresh(context: Context) = withContext(Dispatchers.IO) {
        val prefs = PrefsManager(context)

        // 1) Xác định tọa độ: thủ công > GPS/mạng > lần cuối > mặc định
        val lat: Double
        val lon: Double
        if (prefs.manualLocationEnabled) {
            lat = prefs.manualLat.toDouble()
            lon = prefs.manualLon.toDouble()
            if (prefs.manualName.isNotBlank()) prefs.cachedLocationName = prefs.manualName
        } else {
            val loc = DeviceTelemetryHelper.getLastKnownLocation(context)
            if (loc != null) {
                lat = loc.latitude
                lon = loc.longitude
                prefs.lastLat = lat.toFloat()
                prefs.lastLon = lon.toFloat()
                reverseGeocode(context, lat, lon)?.let { prefs.cachedLocationName = it }
            } else {
                lat = prefs.lastLat.toDouble()
                lon = prefs.lastLon.toDouble()
            }
        }

        // 2) Thời tiết: Open-Meteo, lỗi thì MET Norway
        var snap = fetchOpenMeteo(lat, lon)
        if (snap == null) snap = fetchMetNorway(lat, lon)
        if (snap == null) return@withContext

        // 3) Chất lượng không khí
        val aq = fetchAirQuality(lat, lon)
        if (aq != null) snap = snap.copy(aqi = aq.first, pm25 = aq.second)

        save(context, snap)
    }

    // ------------------------------------------------------------------ nguồn 1: Open-Meteo

    private fun fetchOpenMeteo(lat: Double, lon: Double): Snapshot? {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,is_day,weather_code,wind_speed_10m" +
            "&hourly=temperature_2m,weather_code,precipitation_probability" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset,uv_index_max" +
            "&timezone=auto&forecast_days=7"
        val body = httpGet(url) ?: return null
        return try {
            val root = JSONObject(body)
            val cur = root.getJSONObject("current")
            val hourly = root.getJSONObject("hourly")
            val daily = root.getJSONObject("daily")

            val nowStr = cur.optString("time") // "2026-11-27T14:15"
            val hTimes = hourly.getJSONArray("time")
            var startIdx = 0
            val nowHour = nowStr.take(13) // "2026-11-27T14"
            for (i in 0 until hTimes.length()) {
                if (hTimes.getString(i).startsWith(nowHour)) {
                    startIdx = i; break
                }
            }
            val hT = hourly.getJSONArray("temperature_2m")
            val hC = hourly.getJSONArray("weather_code")
            val hP = hourly.optJSONArray("precipitation_probability")
            val hours = mutableListOf<Hour>()
            var i = startIdx
            while (i < hTimes.length() && hours.size < 12) {
                hours += Hour(
                    hTimes.getString(i).substring(11, 13) + "h",
                    hT.optDouble(i), hC.optInt(i), hP?.optInt(i, -1) ?: -1
                )
                i++
            }

            val dTimes = daily.getJSONArray("time")
            val dC = daily.getJSONArray("weather_code")
            val dMax = daily.getJSONArray("temperature_2m_max")
            val dMin = daily.getJSONArray("temperature_2m_min")
            val dP = daily.optJSONArray("precipitation_probability_max")
            val days = (0 until dTimes.length()).map {
                Day(dayLabel(dTimes.getString(it)), dMax.optDouble(it), dMin.optDouble(it), dC.optInt(it), dP?.optInt(it, -1) ?: -1)
            }
            val uv = daily.optJSONArray("uv_index_max")?.optDouble(0, -1.0) ?: -1.0
            val sunrise = daily.optJSONArray("sunrise")?.optString(0).orEmpty().takeLast(5)
            val sunset = daily.optJSONArray("sunset")?.optString(0).orEmpty().takeLast(5)

            Snapshot(
                source = "Open-Meteo",
                updatedAt = System.currentTimeMillis(),
                tempC = cur.getDouble("temperature_2m"),
                feelsC = cur.optDouble("apparent_temperature", cur.getDouble("temperature_2m")),
                humidity = cur.optInt("relative_humidity_2m", -1),
                windKmh = cur.optDouble("wind_speed_10m", 0.0),
                code = cur.optInt("weather_code", 0),
                isDay = cur.optInt("is_day", 1) == 1,
                uv = uv,
                rainProb = days.firstOrNull()?.rainProb ?: -1,
                hiC = days.firstOrNull()?.hiC ?: cur.getDouble("temperature_2m"),
                loC = days.firstOrNull()?.loC ?: cur.getDouble("temperature_2m"),
                sunrise = sunrise,
                sunset = sunset,
                aqi = -1,
                pm25 = -1.0,
                hourly = hours,
                daily = days
            )
        } catch (_: Exception) {
            null
        }
    }

    // ------------------------------------------------------------------ nguồn 2: chất lượng không khí

    private fun fetchAirQuality(lat: Double, lon: Double): Pair<Int, Double>? {
        val url = "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=$lat&longitude=$lon&current=us_aqi,pm2_5"
        val body = httpGet(url) ?: return null
        return try {
            val cur = JSONObject(body).getJSONObject("current")
            val aqi = cur.optInt("us_aqi", -1)
            if (aqi < 0) null else Pair(aqi, cur.optDouble("pm2_5", -1.0))
        } catch (_: Exception) {
            null
        }
    }

    // ------------------------------------------------------------------ nguồn 3: MET Norway (dự phòng)

    private fun fetchMetNorway(lat: Double, lon: Double): Snapshot? {
        val url = String.format(
            Locale.US, "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=%.4f&lon=%.4f", lat, lon
        )
        // MET Norway bắt buộc User-Agent nhận diện ứng dụng
        val body = httpGet(url, mapOf("User-Agent" to "FoxWidget/1.5 (Android widget)")) ?: return null
        return try {
            val series = JSONObject(body).getJSONObject("properties").getJSONArray("timeseries")
            if (series.length() == 0) return null
            val utc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            val hourFmt = SimpleDateFormat("HH'h'", Locale.US)
            val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

            val first = series.getJSONObject(0)
            val d0 = first.getJSONObject("data").getJSONObject("instant").getJSONObject("details")
            val t0 = d0.getDouble("air_temperature")
            val hum = d0.optDouble("relative_humidity", -1.0).roundToInt()
            val windMs = d0.optDouble("wind_speed", 0.0)
            val sym0 = first.getJSONObject("data").optJSONObject("next_1_hours")
                ?.optJSONObject("summary")?.optString("symbol_code").orEmpty()
            val hourNow = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val isDay = !sym0.endsWith("_night") && (sym0.endsWith("_day") || hourNow in 6..17)

            val hours = mutableListOf<Hour>()
            val byDay = linkedMapOf<String, MutableList<Triple<Int, Double, Int>>>() // giờ, nhiệt độ, mã
            for (i in 0 until series.length()) {
                val e = series.getJSONObject(i)
                val time = utc.parse(e.getString("time")) ?: continue
                val data = e.getJSONObject("data")
                val temp = data.getJSONObject("instant").getJSONObject("details").optDouble("air_temperature", Double.NaN)
                if (temp.isNaN()) continue
                val sym = data.optJSONObject("next_1_hours")?.optJSONObject("summary")?.optString("symbol_code")
                    ?: data.optJSONObject("next_6_hours")?.optJSONObject("summary")?.optString("symbol_code")
                    ?: ""
                val code = metSymbolToWmo(sym)
                if (hours.size < 12 && data.has("next_1_hours")) {
                    hours += Hour(hourFmt.format(time), temp, code, -1)
                }
                val cal = Calendar.getInstance().apply { this.time = time }
                byDay.getOrPut(dayFmt.format(time)) { mutableListOf() }
                    .add(Triple(cal.get(Calendar.HOUR_OF_DAY), temp, code))
            }
            val days = byDay.entries.take(7).map { (date, list) ->
                val noon = list.minByOrNull { kotlin.math.abs(it.first - 12) }
                Day(dayLabel(date), list.maxOf { it.second }, list.minOf { it.second }, noon?.third ?: 0, -1)
            }

            Snapshot(
                source = "MET Norway",
                updatedAt = System.currentTimeMillis(),
                tempC = t0,
                feelsC = apparent(t0, if (hum >= 0) hum else 60, windMs),
                humidity = hum,
                windKmh = windMs * 3.6,
                code = metSymbolToWmo(sym0),
                isDay = isDay,
                uv = -1.0,
                rainProb = -1,
                hiC = days.firstOrNull()?.hiC ?: t0,
                loC = days.firstOrNull()?.loC ?: t0,
                sunrise = "",
                sunset = "",
                aqi = -1,
                pm25 = -1.0,
                hourly = hours,
                daily = days
            )
        } catch (_: Exception) {
            null
        }
    }

    /** Nhiệt độ cảm nhận (công thức Apparent Temperature của BOM). */
    private fun apparent(tC: Double, rh: Int, windMs: Double): Double {
        val e = rh / 100.0 * 6.105 * exp(17.27 * tC / (237.7 + tC))
        return tC + 0.33 * e - 0.70 * windMs - 4.0
    }

    private fun metSymbolToWmo(sym: String): Int {
        val s = sym.substringBefore("_day").substringBefore("_night").substringBefore("_polartwilight")
        return when {
            s.contains("thunder") -> 95
            s.contains("heavyrain") -> 65
            s.contains("lightrain") && s.contains("showers") -> 80
            s.contains("lightrain") -> 61
            s.contains("rainshowers") -> 81
            s.contains("rain") -> 63
            s.contains("sleet") -> 67
            s.contains("snow") -> 73
            s == "fog" -> 45
            s == "cloudy" -> 3
            s == "partlycloudy" -> 2
            s == "fair" -> 1
            s == "clearsky" -> 0
            else -> 3
        }
    }

    // ------------------------------------------------------------------ địa danh

    private fun reverseGeocode(context: Context, lat: Double, lon: Double): String? {
        try {
            @Suppress("DEPRECATION")
            val geocoder = Geocoder(context, Locale("vi", "VN"))
            val list = geocoder.getFromLocation(lat, lon, 1)
            if (!list.isNullOrEmpty()) {
                val a = list[0]
                val place = a.subLocality ?: a.locality ?: a.subAdminArea ?: a.adminArea
                if (!place.isNullOrBlank()) return place
            }
        } catch (_: Exception) {
        }
        // Dự phòng: BigDataCloud (không cần khóa)
        val body = httpGet(
            "https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=$lat&longitude=$lon&localityLanguage=vi"
        ) ?: return null
        return try {
            val o = JSONObject(body)
            listOf("locality", "city", "principalSubdivision")
                .map { o.optString(it) }
                .firstOrNull { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    /** Tìm thành phố theo tên (dùng cho chọn vị trí thủ công). */
    suspend fun searchPlaces(query: String): List<PlaceResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val body = httpGet("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=6&language=vi&format=json")
            ?: return@withContext emptyList()
        try {
            val arr = JSONObject(body).optJSONArray("results") ?: return@withContext emptyList()
            (0 until arr.length()).map {
                val r = arr.getJSONObject(it)
                val detail = listOf(r.optString("admin1"), r.optString("country"))
                    .filter { s -> s.isNotBlank() }.joinToString(", ")
                PlaceResult(r.optString("name"), detail, r.optDouble("latitude"), r.optDouble("longitude"))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ------------------------------------------------------------------ mô tả / định dạng

    fun describe(code: Int, isDay: Boolean = true): String = when (code) {
        0 -> if (isDay) "Trời quang" else "Trời quang, đêm trong"
        1 -> if (isDay) "Nắng nhẹ" else "Ít mây"
        2 -> "Có mây rải rác"
        3 -> "Nhiều mây"
        45, 48 -> "Sương mù"
        51, 53, 55 -> "Mưa phùn"
        56, 57 -> "Mưa phùn lạnh"
        61 -> "Mưa nhỏ"
        63 -> "Mưa vừa"
        65 -> "Mưa to"
        66, 67 -> "Mưa đá lạnh"
        71, 73, 75, 77 -> "Có tuyết"
        80, 81 -> "Mưa rào"
        82 -> "Mưa rào lớn"
        85, 86 -> "Tuyết rơi"
        95 -> "Có dông"
        96, 99 -> "Dông kèm mưa đá"
        else -> "Thời tiết ổn định"
    }

    fun emoji(code: Int, isDay: Boolean = true): String = when (code) {
        0 -> if (isDay) "☀️" else "🌙"
        1 -> if (isDay) "🌤️" else "🌙"
        2 -> if (isDay) "⛅" else "☁️"
        3 -> "☁️"
        45, 48 -> "🌫️"
        51, 53, 55, 56, 57 -> "🌦️"
        61, 63, 80, 81 -> "🌧️"
        65, 82 -> "🌧️"
        66, 67 -> "🌨️"
        71, 73, 75, 77, 85, 86 -> "❄️"
        95, 96, 99 -> "⛈️"
        else -> "🌡️"
    }

    fun aqiLabel(aqi: Int): String = when {
        aqi < 0 -> ""
        aqi <= 50 -> "Tốt"
        aqi <= 100 -> "Trung bình"
        aqi <= 150 -> "Kém"
        aqi <= 200 -> "Xấu"
        aqi <= 300 -> "Rất xấu"
        else -> "Nguy hại"
    }

    fun uvLabel(uv: Double): String = when {
        uv < 0 -> ""
        uv < 3 -> "Thấp"
        uv < 6 -> "Trung bình"
        uv < 8 -> "Cao"
        uv < 11 -> "Rất cao"
        else -> "Cực cao"
    }

    /** 28.4 -> "28°" hoặc "83°" nếu dùng °F. */
    fun formatTemp(tempC: Double, unit: Int): String {
        val v = if (unit == PrefsManager.TEMP_F) tempC * 9.0 / 5.0 + 32.0 else tempC
        return "${v.roundToInt()}°"
    }

    fun unitSuffix(unit: Int): String = if (unit == PrefsManager.TEMP_F) "F" else "C"

    private fun dayLabel(date: String): String {
        return try {
            val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date) ?: return date
            val cal = Calendar.getInstance().apply { time = d }
            when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> "T2"
                Calendar.TUESDAY -> "T3"
                Calendar.WEDNESDAY -> "T4"
                Calendar.THURSDAY -> "T5"
                Calendar.FRIDAY -> "T6"
                Calendar.SATURDAY -> "T7"
                else -> "CN"
            }
        } catch (_: Exception) {
            date
        }
    }

    // ------------------------------------------------------------------ HTTP

    private fun httpGet(url: String, headers: Map<String, String> = emptyMap()): String? {
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            try {
                if (conn.responseCode == 200) conn.inputStream.bufferedReader().use { it.readText() } else null
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }
}
