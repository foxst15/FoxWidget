package com.foxwidget.app.util

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.foxwidget.app.R
import com.foxwidget.app.data.PrefsManager
import java.util.Calendar
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Dynamic Island cho Android: một cửa sổ overlay (TYPE_APPLICATION_OVERLAY) ở đỉnh màn hình.
 * Chạy trên mọi hãng Android (API 26+), chỉ cần quyền "Hiển thị trên ứng dụng khác".
 *
 *  - Thu gọn: viên thuốc đen, tự luân phiên các thông tin đã chọn (lịch, thời tiết, vị trí, pin).
 *  - Chạm: mở rộng hiện đủ thông tin. Chạm lần nữa / chạm ra ngoài / sau 7 giây: thu gọn.
 *
 * Mọi thao tác UI đều chạy trên main thread, có thể gọi từ bất kỳ thread nào.
 */
object IslandOverlayManager {

    private const val CYCLE_MS = 3500L
    private const val AUTO_COLLAPSE_MS = 7000L

    private const val COLOR_CAL = 0xFFB79CFF.toInt()
    private const val COLOR_WEATHER = 0xFF7DD3FC.toInt()
    private const val COLOR_LOC = 0xFFF9A8D4.toInt()
    private const val COLOR_BAT = 0xFF86EFAC.toInt()
    private const val COLOR_BAT_HOT = 0xFFFDBA74.toInt()

    private val main = Handler(Looper.getMainLooper())

    private class Row(val root: View, val icon: ImageView, val label: TextView, val value: TextView)

    private class Holder(
        val root: View,
        val container: ViewGroup,
        val collapsed: View,
        val expandedBox: View,
        val icon: ImageView,
        val text: TextView,
        val rowCal: Row,
        val rowWeather: Row,
        val rowLoc: Row,
        val rowBat: Row
    )

    private data class Item(val iconRes: Int, val tint: Int, val shortText: String)

    private var holder: Holder? = null
    private var windowManager: WindowManager? = null
    private var builtScale = -1
    private var expanded = false
    private var cycleIndex = 0
    private var currentItems: List<Item> = emptyList()
    private var appearanceKey = ""
    private var onColor = Color.WHITE
    private var pastelIcons = true

    private val collapseRunnable = Runnable { setExpanded(false, animate = true) }

    private val cycleRunnable = object : Runnable {
        override fun run() {
            val h = holder ?: return
            if (expanded || currentItems.size < 2) return
            h.collapsed.animate().alpha(0f).setDuration(140).withEndAction {
                if (holder === h && currentItems.isNotEmpty()) {
                    cycleIndex = (cycleIndex + 1) % currentItems.size
                    showCollapsedItem(h)
                    h.collapsed.animate().alpha(1f).setDuration(140).start()
                }
            }.start()
            main.postDelayed(this, CYCLE_MS)
        }
    }

    // ------------------------------------------------------------------ API công khai

    /** Đồng bộ island theo cài đặt: bật/tắt, vị trí, kích thước và nội dung. */
    fun sync(context: Context) {
        val app = context.applicationContext
        main.post { syncNow(app) }
    }

    /** Gỡ island khỏi màn hình (không đổi cài đặt). */
    fun remove() {
        main.post { removeNow() }
    }

    /** Mở rộng island một lần để xem thử. */
    fun preview(context: Context) {
        val app = context.applicationContext
        main.post {
            syncNow(app)
            if (holder != null) setExpanded(true, animate = true)
        }
    }

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    // ------------------------------------------------------------------ nội bộ

    private fun syncNow(ctx: Context) {
        val prefs = PrefsManager(ctx)
        val allowed = prefs.islandEnabled && Settings.canDrawOverlays(ctx)
        if (!allowed) {
            removeNow()
            return
        }
        if (holder != null && builtScale != prefs.islandScale) removeNow()
        if (holder == null && !addNow(ctx, prefs)) return
        applyPosition(ctx, prefs)
        bind(ctx, prefs)
    }

    private fun addNow(ctx: Context, prefs: PrefsManager): Boolean {
        return try {
            val themed = scaledThemeContext(ctx, prefs.islandScale)
            val view = LayoutInflater.from(themed).inflate(R.layout.island_overlay, null)

            fun row(rowId: Int, iconId: Int, labelId: Int, valueId: Int) = Row(
                view.findViewById(rowId),
                view.findViewById(iconId),
                view.findViewById(labelId),
                view.findViewById(valueId)
            )

            val h = Holder(
                root = view,
                container = view.findViewById(R.id.island_container),
                collapsed = view.findViewById(R.id.island_collapsed),
                expandedBox = view.findViewById(R.id.island_expanded),
                icon = view.findViewById(R.id.island_icon),
                text = view.findViewById(R.id.island_text),
                rowCal = row(R.id.island_row_cal, R.id.island_ic_cal, R.id.island_lbl_cal, R.id.island_val_cal),
                rowWeather = row(R.id.island_row_weather, R.id.island_ic_weather, R.id.island_lbl_weather, R.id.island_val_weather),
                rowLoc = row(R.id.island_row_loc, R.id.island_ic_loc, R.id.island_lbl_loc, R.id.island_val_loc),
                rowBat = row(R.id.island_row_bat, R.id.island_ic_bat, R.id.island_lbl_bat, R.id.island_val_bat)
            )

            view.setOnClickListener { setExpanded(!expanded, animate = true) }
            view.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE && expanded) {
                    setExpanded(false, animate = true)
                }
                false
            }

            val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.addView(view, buildParams(ctx, prefs))

            windowManager = wm
            holder = h
            builtScale = prefs.islandScale
            expanded = false
            cycleIndex = 0
            appearanceKey = ""
            true
        } catch (_: Exception) {
            // Thiếu quyền, hệ thống từ chối cửa sổ overlay... -> bỏ qua, lần đồng bộ sau sẽ thử lại
            holder = null
            false
        }
    }

    private fun removeNow() {
        main.removeCallbacks(cycleRunnable)
        main.removeCallbacks(collapseRunnable)
        val h = holder ?: return
        try {
            windowManager?.removeViewImmediate(h.root)
        } catch (_: Exception) {
        }
        holder = null
        expanded = false
        appearanceKey = ""
    }

    /** Context có mật độ điểm ảnh nhân theo % kích thước -> toàn bộ dp trong layout co giãn đồng đều. */
    private fun scaledThemeContext(base: Context, scalePercent: Int): Context {
        val cfg = Configuration(base.resources.configuration)
        cfg.densityDpi = (cfg.densityDpi * (scalePercent / 100f)).roundToInt().coerceAtLeast(80)
        val scaled = base.createConfigurationContext(cfg)
        return ContextThemeWrapper(scaled, R.style.Theme_FoxWidget)
    }

    private fun buildParams(ctx: Context, prefs: PrefsManager): WindowManager.LayoutParams {
        val density = ctx.resources.displayMetrics.density
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        lp.x = (prefs.islandOffsetXDp * density).roundToInt()
        lp.y = (prefs.islandOffsetYDp * density).roundToInt()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Cho phép vẽ vào vùng khuyết camera (tai thỏ / đục lỗ)
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        lp.title = "FoxWidget Island"
        return lp
    }

    private fun applyPosition(ctx: Context, prefs: PrefsManager) {
        val h = holder ?: return
        val lp = h.root.layoutParams as? WindowManager.LayoutParams ?: return
        val density = ctx.resources.displayMetrics.density
        val x = (prefs.islandOffsetXDp * density).roundToInt()
        val y = (prefs.islandOffsetYDp * density).roundToInt()
        if (lp.x != x || lp.y != y) {
            lp.x = x
            lp.y = y
            try {
                windowManager?.updateViewLayout(h.root, lp)
            } catch (_: Exception) {
            }
        }
    }

    private fun bind(ctx: Context, prefs: PrefsManager) {
        val h = holder ?: return
        applyAppearance(ctx, prefs, h)
        val now = Calendar.getInstance()
        val items = mutableListOf<Item>()

        // --- Lịch (dương + âm)
        h.rowCal.root.visibility = if (prefs.islandShowCalendar) View.VISIBLE else View.GONE
        if (prefs.islandShowCalendar) {
            val d = now.get(Calendar.DAY_OF_MONTH)
            val m = now.get(Calendar.MONTH) + 1
            val y = now.get(Calendar.YEAR)
            val lunar = VietLunarCalendar.convertSolar2Lunar(d, m, y)
            h.rowCal.icon.setImageResource(R.drawable.ic_calendar)
            h.rowCal.icon.setColorFilter(tintFor(COLOR_CAL))
            h.rowCal.value.text = VietLunarCalendar.formatSolarDateFull(now) + "\n" +
                VietLunarCalendar.formatLunarDateFull(now)
            items += Item(
                R.drawable.ic_calendar, COLOR_CAL,
                String.format(Locale.US, "%02d/%02d · ÂL %d/%d", d, m, lunar.day, lunar.month)
            )
        }

        // --- Thời tiết
        h.rowWeather.root.visibility = if (prefs.islandShowWeather) View.VISIBLE else View.GONE
        if (prefs.islandShowWeather) {
            val desc = prefs.cachedWeatherDesc
            h.rowWeather.icon.setImageResource(R.drawable.ic_sun_cloud)
            h.rowWeather.icon.setColorFilter(tintFor(COLOR_WEATHER))
            h.rowWeather.value.text = "${prefs.cachedTempC}°C · $desc"
            items += Item(
                R.drawable.ic_sun_cloud, COLOR_WEATHER,
                "${prefs.cachedTempC}°C · ${desc.substringBefore(" ·")}"
            )
        }

        // --- Vị trí
        h.rowLoc.root.visibility = if (prefs.islandShowLocation) View.VISIBLE else View.GONE
        if (prefs.islandShowLocation) {
            h.rowLoc.icon.setImageResource(R.drawable.ic_pin)
            h.rowLoc.icon.setColorFilter(tintFor(COLOR_LOC))
            h.rowLoc.value.text = prefs.cachedLocationName
            items += Item(R.drawable.ic_pin, COLOR_LOC, prefs.cachedLocationName)
        }

        // --- Pin & nhiệt độ
        h.rowBat.root.visibility = if (prefs.islandShowBattery) View.VISIBLE else View.GONE
        if (prefs.islandShowBattery) {
            val b = DeviceTelemetryHelper.getBatteryStats(ctx)
            val tint = if (b.temperatureC >= 40f) COLOR_BAT_HOT else COLOR_BAT
            val icon = if (b.isCharging) R.drawable.ic_bolt else R.drawable.ic_battery
            val temp = String.format(Locale.US, "%.1f°C", b.temperatureC)
            h.rowBat.icon.setImageResource(icon)
            h.rowBat.icon.setColorFilter(tintFor(tint))
            h.rowBat.value.text = "${b.levelPercent}% · $temp" + if (b.isCharging) " · đang sạc" else ""
            items += Item(icon, tint, "${b.levelPercent}% · $temp")
        }

        if (items.isEmpty()) {
            items += Item(R.drawable.ic_info, Color.WHITE, "Chưa chọn thông tin")
        }
        currentItems = items
        if (cycleIndex >= items.size) cycleIndex = 0
        showCollapsedItem(h)
        scheduleCycle()
    }

    private fun tintFor(itemTint: Int): Int = if (pastelIcons) itemTint else onColor

    /** Màu nền gốc: theo màu hệ thống (Android 12+) hoặc màu người dùng chọn. */
    private fun resolveBaseColor(ctx: Context, prefs: PrefsManager): Int {
        val useSystem = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            prefs.islandColorMode == PrefsManager.ISLAND_COLOR_SYSTEM
        if (useSystem) {
            try {
                val res = if (prefs.islandStyle == PrefsManager.ISLAND_STYLE_GLASS) {
                    android.R.color.system_accent1_700
                } else {
                    android.R.color.system_accent1_800
                }
                return ctx.getColor(res)
            } catch (_: Exception) {
                // rơi xuống màu tùy chỉnh
            }
        }
        return prefs.islandCustomColor or 0xFF000000.toInt()
    }

    private fun applyAppearance(ctx: Context, prefs: PrefsManager, h: Holder) {
        val base = resolveBaseColor(ctx, prefs)
        val glass = prefs.islandStyle == PrefsManager.ISLAND_STYLE_GLASS
        val lum = ColorUtils.calculateLuminance(base)
        val light = lum > 0.55
        onColor = if (light) 0xFF1B1530.toInt() else Color.WHITE
        pastelIcons = lum < 0.08

        val labelColor = ColorUtils.setAlphaComponent(onColor, 0xB0)
        for (row in listOf(h.rowCal, h.rowWeather, h.rowLoc, h.rowBat)) {
            row.value.setTextColor(onColor)
            row.label.setTextColor(labelColor)
        }

        val key = "$base|$glass|$builtScale"
        if (key != appearanceKey) {
            appearanceKey = key
            val density = h.root.resources.displayMetrics.density
            val corner = 28f * density
            h.container.background = if (glass) {
                GlassDrawable(base, corner, 1.2f * density)
            } else {
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = corner
                    setColor(base)
                    setStroke(density.roundToInt().coerceAtLeast(1), 0x26FFFFFF)
                }
            }
        }
    }

    /** Nền Liquid Glass: kính trong mờ pha màu, ánh bóng phía trên và viền sáng chéo. */
    private class GlassDrawable(
        private val base: Int,
        private val cornerPx: Float,
        private val strokePx: Float
    ) : Drawable() {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val gloss = Paint(Paint.ANTI_ALIAS_FLAG)
        private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val rect = RectF()
        private val clip = Path()
        private var radius = 0f

        override fun onBoundsChange(bounds: Rect) {
            super.onBoundsChange(bounds)
            if (bounds.isEmpty) return
            rect.set(
                bounds.left + strokePx / 2f,
                bounds.top + strokePx / 2f,
                bounds.right - strokePx / 2f,
                bounds.bottom - strokePx / 2f
            )
            radius = min(cornerPx, min(rect.width(), rect.height()) / 2f)

            val top = ColorUtils.setAlphaComponent(ColorUtils.blendARGB(base, Color.WHITE, 0.30f), 0xCC)
            val bottom = ColorUtils.setAlphaComponent(ColorUtils.blendARGB(base, Color.BLACK, 0.20f), 0xB8)
            fill.shader = LinearGradient(0f, rect.top, 0f, rect.bottom, top, bottom, Shader.TileMode.CLAMP)

            gloss.shader = LinearGradient(
                0f, rect.top, 0f, rect.centerY(),
                0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP
            )

            edge.strokeWidth = strokePx
            edge.shader = LinearGradient(
                rect.left, rect.top, rect.right, rect.bottom,
                intArrayOf(0xCCFFFFFF.toInt(), 0x33FFFFFF, 0x80FFFFFF.toInt()),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )

            clip.reset()
            clip.addRoundRect(rect, radius, radius, Path.Direction.CW)
        }

        override fun draw(canvas: Canvas) {
            if (rect.isEmpty) return
            canvas.drawRoundRect(rect, radius, radius, fill)
            val save = canvas.save()
            canvas.clipPath(clip)
            canvas.drawRect(rect.left, rect.top, rect.right, rect.centerY(), gloss)
            canvas.restoreToCount(save)
            canvas.drawRoundRect(rect, radius, radius, edge)
        }

        override fun setAlpha(alpha: Int) {
            fill.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            fill.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private fun showCollapsedItem(h: Holder) {
        val item = currentItems.getOrNull(cycleIndex) ?: return
        h.icon.setImageResource(item.iconRes)
        h.icon.setColorFilter(tintFor(item.tint))
        h.text.setTextColor(onColor)
        h.text.text = item.shortText
    }

    private fun scheduleCycle() {
        main.removeCallbacks(cycleRunnable)
        if (!expanded && currentItems.size > 1) main.postDelayed(cycleRunnable, CYCLE_MS)
    }

    private fun setExpanded(value: Boolean, animate: Boolean) {
        val h = holder ?: return
        expanded = value
        if (animate) {
            TransitionManager.beginDelayedTransition(h.container, AutoTransition().setDuration(220))
        }
        h.collapsed.visibility = if (value) View.GONE else View.VISIBLE
        h.expandedBox.visibility = if (value) View.VISIBLE else View.GONE
        h.collapsed.alpha = 1f
        main.removeCallbacks(collapseRunnable)
        if (value) main.postDelayed(collapseRunnable, AUTO_COLLAPSE_MS)
        scheduleCycle()
    }
}
