package com.foxwidget.app.util

import android.animation.ValueAnimator
import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Display
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.foxwidget.app.R
import com.foxwidget.app.data.PrefsManager
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Dynamic Island cho Android, dựng theo kiểu "đảo" của hệ thống (HyperOS / One UI / iOS):
 *
 *  - Thu gọn: viên thuốc đen nằm đúng vị trí camera đục lỗ. Bên TRÁI là icon app, bên PHẢI là thông tin,
 *    khoảng GIỮA để trống cho camera. Độ dài viên thuốc kéo dài tùy ý.
 *  - Chạm: viên thuốc giãn thành thẻ bo góc (hiệu ứng nảy nhẹ, nội dung từ mờ sang rõ, vầng sáng màu ở đáy thẻ).
 *    Chạm lại / vuốt lên / chạm ra ngoài / sau 7 giây: co về viên thuốc.
 *
 * Tương thích hệ thống: tự lấy vị trí + kích thước camera (DisplayCutout) để căn viên thuốc, tự ẩn khi xoay ngang,
 * né thanh trạng thái khi mở rộng, và theo thang thời gian hoạt ảnh của hệ thống.
 * Mọi thao tác UI chạy trên main thread; có thể gọi từ thread bất kỳ.
 */
object IslandOverlayManager {

    private const val EXPAND_MS = 320L
    private const val COLLAPSE_MS = 280L
    private const val CYCLE_MS = 3500L
    private const val AUTO_COLLAPSE_MS = 7000L

    private const val COLOR_CAL = 0xFFB79CFF.toInt()
    private const val COLOR_WEATHER = 0xFF7DD3FC.toInt()
    private const val COLOR_LOC = 0xFFF9A8D4.toInt()
    private const val COLOR_BAT = 0xFF86EFAC.toInt()
    private const val COLOR_BAT_HOT = 0xFFFDBA74.toInt()
    private const val COLOR_GLOW_DEFAULT = 0xFF5B4BFF.toInt()

    private val main = Handler(Looper.getMainLooper())

    private class Row(val root: View, val icon: ImageView, val label: TextView, val value: TextView)

    private class Holder(
        val root: FrameLayout,
        val collapsed: FrameLayout,
        val leftIcon: ImageView,
        val text: TextView,
        val expanded: View,
        val expLogo: ImageView,
        val expTitle: TextView,
        val rowCal: Row,
        val rowWeather: Row,
        val rowLoc: Row,
        val rowBat: Row
    )

    private data class Item(val iconRes: Int, val tint: Int, val shortText: String, val tinyText: String)

    private class CutoutInfo(val centerX: Float, val top: Float, val bottom: Float, val width: Float)

    /** Toàn bộ kích thước/vị trí (px) của hai trạng thái, tính từ cài đặt + camera. */
    private class Geom(
        val cW: Int, val cH: Int, val cX: Int, val cY: Int,
        val eW: Int, val eH: Int, val eY: Int,
        val iconSize: Int, val leftPad: Int, val rightPad: Int, val textW: Int
    )

    private var holder: Holder? = null
    private var windowManager: WindowManager? = null
    private var background: IslandBackground? = null
    private var geom: Geom? = null
    private var builtScale = -1
    private var progress = 0f
    private var expanded = false
    private var animator: ValueAnimator? = null
    private var cycleIndex = 0
    private var currentItems: List<Item> = emptyList()
    private var appearanceKey = ""
    private var onColor = Color.WHITE
    private var pastelIcons = true
    private var leftIconMode = 0
    private var callbacksRegistered = false
    private var cutoutDetected: Boolean? = null
    private var needsSecondPass = false
    private var appCtx: Context? = null

    private val collapseRunnable = Runnable { setExpanded(false, animate = true) }

    private val cycleRunnable = object : Runnable {
        override fun run() {
            val h = holder ?: return
            if (expanded || currentItems.size < 2) return
            h.collapsed.animate().alpha(0f).setDuration(140).withEndAction {
                if (holder === h && currentItems.isNotEmpty() && !expanded) {
                    cycleIndex = (cycleIndex + 1) % currentItems.size
                    showCollapsedItem(h)
                    h.collapsed.animate().alpha(1f).setDuration(140).start()
                }
            }.start()
            main.postDelayed(this, CYCLE_MS)
        }
    }

    private val configCallbacks = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            // Xoay màn hình / đổi kích thước hiển thị -> dựng lại theo camera mới
            appCtx?.let { sync(it) }
        }

        override fun onLowMemory() {}
    }

    // ------------------------------------------------------------------ API công khai

    /** Đồng bộ island theo cài đặt: bật/tắt, vị trí, kích thước, màu và nội dung. */
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

    /** true/false: máy có báo vị trí camera hay không; null: chưa xác định (island chưa hiển thị). */
    fun isCutoutDetected(): Boolean? = cutoutDetected

    // ------------------------------------------------------------------ vòng đời

    private fun syncNow(ctx: Context) {
        appCtx = ctx
        if (!callbacksRegistered) {
            ctx.registerComponentCallbacks(configCallbacks)
            callbacksRegistered = true
        }
        val prefs = PrefsManager(ctx)
        val portrait = ctx.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        val allowed = prefs.islandEnabled && Settings.canDrawOverlays(ctx) && portrait
        if (!allowed) {
            removeNow()
            return
        }
        if (holder != null && builtScale != prefs.islandScale) removeNow()
        if (holder == null && !addNow(ctx, prefs)) return
        bind(ctx, prefs)
        if (needsSecondPass) {
            // Android 9-11: chỉ đọc được camera sau khi view đã gắn vào cửa sổ
            needsSecondPass = false
            main.post { syncNow(ctx) }
        }
    }

    private fun addNow(ctx: Context, prefs: PrefsManager): Boolean {
        return try {
            val themed = scaledThemeContext(ctx, prefs.islandScale)
            val view = LayoutInflater.from(themed).inflate(R.layout.island_overlay, null) as FrameLayout

            fun row(rowId: Int, iconId: Int, labelId: Int, valueId: Int) = Row(
                view.findViewById(rowId), view.findViewById(iconId),
                view.findViewById(labelId), view.findViewById(valueId)
            )

            val h = Holder(
                root = view,
                collapsed = view.findViewById(R.id.island_collapsed),
                leftIcon = view.findViewById(R.id.island_left_icon),
                text = view.findViewById(R.id.island_text),
                expanded = view.findViewById(R.id.island_expanded),
                expLogo = view.findViewById(R.id.island_exp_logo),
                expTitle = view.findViewById(R.id.island_exp_title),
                rowCal = row(R.id.island_row_cal, R.id.island_ic_cal, R.id.island_lbl_cal, R.id.island_val_cal),
                rowWeather = row(R.id.island_row_weather, R.id.island_ic_weather, R.id.island_lbl_weather, R.id.island_val_weather),
                rowLoc = row(R.id.island_row_loc, R.id.island_ic_loc, R.id.island_lbl_loc, R.id.island_val_loc),
                rowBat = row(R.id.island_row_bat, R.id.island_ic_bat, R.id.island_lbl_bat, R.id.island_val_bat)
            )
            h.leftIcon.clipToOutline = true
            h.expLogo.clipToOutline = true
            h.expLogo.outlineProvider = roundedOutline(0.28f)
            h.leftIcon.outlineProvider = roundedOutline(0.28f)

            val detector = GestureDetector(themed, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    setExpanded(!expanded, animate = true)
                    return true
                }

                override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                    if (vy < -600f && expanded) {
                        setExpanded(false, animate = true)
                        return true
                    }
                    if (vy > 600f && !expanded) {
                        setExpanded(true, animate = true)
                        return true
                    }
                    return false
                }
            })
            view.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    if (expanded) setExpanded(false, animate = true)
                    false
                } else {
                    detector.onTouchEvent(event)
                }
            }

            holder = h
            progress = 0f
            expanded = false
            cycleIndex = 0
            appearanceKey = ""
            builtScale = prefs.islandScale

            // Bố cục lần đầu (chưa gắn cửa sổ nên chưa đọc được camera trên Android 9-11)
            applyAppearance(ctx, prefs, h)
            populate(ctx, prefs, h)
            val g = computeGeometry(ctx, prefs, h)
            geom = g
            applySizes(h, g)

            val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val lp = buildParams(g)
            wm.addView(view, lp)
            windowManager = wm
            applyProgress(0f)

            // Hiệu ứng xuất hiện: nảy ra từ giữa viên thuốc
            view.alpha = 0f
            view.scaleX = 0.6f
            view.scaleY = 0.6f
            view.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260)
                .setInterpolator(OvershootInterpolator(1.2f)).start()

            if (cutoutDetected != true &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                Build.VERSION.SDK_INT < Build.VERSION_CODES.S
            ) {
                needsSecondPass = true
            }
            true
        } catch (_: Exception) {
            // Thiếu quyền, hệ thống từ chối cửa sổ overlay... -> bỏ qua, lần đồng bộ sau sẽ thử lại
            holder = null
            false
        }
    }

    private fun removeNow() {
        animator?.cancel()
        animator = null
        main.removeCallbacks(cycleRunnable)
        main.removeCallbacks(collapseRunnable)
        val h = holder ?: return
        try {
            windowManager?.removeViewImmediate(h.root)
        } catch (_: Exception) {
        }
        holder = null
        background = null
        geom = null
        expanded = false
        progress = 0f
        appearanceKey = ""
    }

    /** Context có mật độ điểm ảnh nhân theo % kích thước -> chữ và icon trong layout co giãn đồng đều. */
    private fun scaledThemeContext(base: Context, scalePercent: Int): Context {
        val cfg = Configuration(base.resources.configuration)
        cfg.densityDpi = (cfg.densityDpi * (scalePercent / 100f)).roundToInt().coerceAtLeast(80)
        val scaled = base.createConfigurationContext(cfg)
        return ContextThemeWrapper(scaled, R.style.Theme_FoxWidget)
    }

    private fun roundedOutline(ratio: Float) = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, min(view.width, view.height) * ratio)
        }
    }

    // ------------------------------------------------------------------ hình học (camera, kích thước)

    private fun statusBarHeight(ctx: Context): Int {
        val id = ctx.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) {
            ctx.resources.getDimensionPixelSize(id)
        } else {
            (24 * ctx.resources.displayMetrics.density).toInt()
        }
    }

    /** Lấy camera đục lỗ / tai thỏ ở mép trên từ hệ thống. */
    private fun findCutout(ctx: Context, root: View?): CutoutInfo? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        val screenW = ctx.resources.displayMetrics.widthPixels
        var rects: List<Rect> = emptyList()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                rects = dm.getDisplay(Display.DEFAULT_DISPLAY)?.cutout?.boundingRects ?: emptyList()
            } catch (_: Exception) {
            }
        }
        if (rects.isEmpty() && root != null && root.isAttachedToWindow) {
            try {
                rects = root.rootWindowInsets?.displayCutout?.boundingRects ?: emptyList()
            } catch (_: Exception) {
            }
        }
        val top = rects
            .filter { it.top <= 6 && it.width() > 0 && it.height() > 0 }
            .minByOrNull { abs(it.exactCenterX() - screenW / 2f) } ?: return null
        return CutoutInfo(top.exactCenterX(), top.top.toFloat(), top.bottom.toFloat(), top.width().toFloat())
    }

    private fun computeGeometry(ctx: Context, prefs: PrefsManager, h: Holder): Geom {
        val density = ctx.resources.displayMetrics.density
        val unit = density * prefs.islandScale / 100f
        val screenW = ctx.resources.displayMetrics.widthPixels

        val cut = findCutout(ctx, h.root)
        cutoutDetected = cut != null
        val pad = 5f * density

        val cH: Int
        val cY: Int
        val cX: Int
        val gap: Float
        if (prefs.islandAutoCutout && cut != null) {
            cH = max(cut.bottom - cut.top + 2 * pad, 28f * density).roundToInt()
            cY = max(0f, cut.top - pad).roundToInt()
            cX = (cut.centerX - screenW / 2f).roundToInt()
            gap = cut.width + 2f * pad * 1.2f
        } else {
            cH = (prefs.islandHeightDp * unit).roundToInt()
            cY = (prefs.islandOffsetYDp * density).roundToInt()
            cX = (prefs.islandOffsetXDp * density).roundToInt()
            gap = prefs.islandGapDp * unit
        }

        val iconSize = (cH * 0.58f).roundToInt()
        val leftPad = (cH * 0.30f).roundToInt()
        val rightPad = (cH * 0.38f).roundToInt()
        val minW = gap + 2f * (leftPad + iconSize + 8f * density)
        val maxW = max(minW, screenW - 16f * density)
        val cW = (prefs.islandWidthDp * unit).coerceIn(minW, maxW).roundToInt()
        val textW = max(0f, (cW - gap) / 2f - rightPad).roundToInt()

        val eW = min(360f * unit, screenW - 24f * density).roundToInt()
        h.expanded.measure(
            View.MeasureSpec.makeMeasureSpec(eW, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val eH = h.expanded.measuredHeight
        val eY = (max(statusBarHeight(ctx), cY + cH) + 4f * density).roundToInt()

        return Geom(cW, cH, cX, cY, eW, eH, eY, iconSize, leftPad, rightPad, textW)
    }

    private fun applySizes(h: Holder, g: Geom) {
        h.collapsed.layoutParams = FrameLayout.LayoutParams(g.cW, g.cH, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        h.expanded.layoutParams = FrameLayout.LayoutParams(g.eW, g.eH, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        h.leftIcon.layoutParams =
            FrameLayout.LayoutParams(g.iconSize, g.iconSize, Gravity.START or Gravity.CENTER_VERTICAL).apply {
                marginStart = g.leftPad
            }
        h.text.layoutParams =
            FrameLayout.LayoutParams(g.textW, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.CENTER_VERTICAL).apply {
                marginEnd = g.rightPad
            }
        h.text.setTextSize(TypedValue.COMPLEX_UNIT_PX, g.cH * 0.34f)
        h.root.pivotX = g.cW / 2f
        h.root.pivotY = g.cH / 2f
    }

    private fun buildParams(g: Geom): WindowManager.LayoutParams {
        val lp = WindowManager.LayoutParams(
            g.cW,
            g.cH,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        lp.x = g.cX
        lp.y = g.cY
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Cho phép vẽ vào vùng khuyết camera (tai thỏ / đục lỗ)
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        lp.title = "FoxWidget Island"
        return lp
    }

    // ------------------------------------------------------------------ hoạt ảnh

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    /** p = 0: viên thuốc, p = 1: thẻ mở rộng (có thể vượt nhẹ để tạo độ nảy). */
    private fun applyProgress(p: Float) {
        val g = geom ?: return
        val h = holder ?: return
        val wm = windowManager ?: return
        progress = p
        val t = p.coerceIn(-0.04f, 1.08f)

        val lp = h.root.layoutParams as? WindowManager.LayoutParams ?: return
        val w = lerp(g.cW.toFloat(), g.eW.toFloat(), t).roundToInt().coerceAtLeast(1)
        val hh = lerp(g.cH.toFloat(), g.eH.toFloat(), t).roundToInt().coerceAtLeast(1)
        val x = lerp(g.cX.toFloat(), 0f, t.coerceIn(0f, 1f)).roundToInt()
        val y = lerp(g.cY.toFloat(), g.eY.toFloat(), t).roundToInt().coerceAtLeast(0)
        if (lp.width != w || lp.height != hh || lp.x != x || lp.y != y) {
            lp.width = w
            lp.height = hh
            lp.x = x
            lp.y = y
            try {
                wm.updateViewLayout(h.root, lp)
            } catch (_: Exception) {
            }
        }

        val q = p.coerceIn(0f, 1f)
        setLayer(h.collapsed, (1f - q * 3.2f).coerceIn(0f, 1f))
        setLayer(h.expanded, ((q - 0.40f) / 0.60f).coerceIn(0f, 1f))
        background?.progress = q
    }

    /** Lớp nội dung: mờ dần + (Android 12+) làm nhòe khi chuyển trạng thái, như hiệu ứng của hệ thống. */
    private fun setLayer(view: View, alpha: Float) {
        view.alpha = alpha
        view.visibility = if (alpha <= 0.01f) View.INVISIBLE else View.VISIBLE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                if (alpha < 0.98f && alpha > 0.01f) {
                    val r = max(0.1f, (1f - alpha) * 16f)
                    view.setRenderEffect(RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP))
                } else {
                    view.setRenderEffect(null)
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun setExpanded(value: Boolean, animate: Boolean) {
        val h = holder ?: return
        expanded = value
        animator?.cancel()
        main.removeCallbacks(collapseRunnable)
        main.removeCallbacks(cycleRunnable)

        if (value) {
            // Đo lại để thẻ vừa khít nội dung hiện tại
            val ctx = appCtx
            if (ctx != null) {
                val g = computeGeometry(ctx, PrefsManager(ctx), h)
                geom = g
                applySizes(h, g)
            }
        }

        val target = if (value) 1f else 0f
        if (!animate) {
            applyProgress(target)
        } else {
            animator = ValueAnimator.ofFloat(progress, target).apply {
                duration = if (value) EXPAND_MS else COLLAPSE_MS
                interpolator = if (value) OvershootInterpolator(0.9f) else DecelerateInterpolator(1.6f)
                addUpdateListener { applyProgress(it.animatedValue as Float) }
                start()
            }
        }
        if (value) main.postDelayed(collapseRunnable, AUTO_COLLAPSE_MS)
        scheduleCycle()
    }

    // ------------------------------------------------------------------ nội dung

    private fun bind(ctx: Context, prefs: PrefsManager) {
        val h = holder ?: return
        applyAppearance(ctx, prefs, h)
        populate(ctx, prefs, h)
        val g = computeGeometry(ctx, prefs, h)
        geom = g
        applySizes(h, g)
        showCollapsedItem(h)
        applyProgress(progress)
        scheduleCycle()
    }

    /** Điền dữ liệu vào các dòng của thẻ mở rộng + danh sách luân phiên của viên thuốc. */
    private fun populate(ctx: Context, prefs: PrefsManager, h: Holder) {
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
                String.format(Locale.US, "%02d/%02d · ÂL %d/%d", d, m, lunar.day, lunar.month),
                String.format(Locale.US, "%02d/%02d", d, m)
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
                "${prefs.cachedTempC}°C · ${desc.substringBefore(" ·")}",
                "${prefs.cachedTempC}°C"
            )
        }

        // --- Vị trí
        h.rowLoc.root.visibility = if (prefs.islandShowLocation) View.VISIBLE else View.GONE
        if (prefs.islandShowLocation) {
            h.rowLoc.icon.setImageResource(R.drawable.ic_pin)
            h.rowLoc.icon.setColorFilter(tintFor(COLOR_LOC))
            h.rowLoc.value.text = prefs.cachedLocationName
            items += Item(R.drawable.ic_pin, COLOR_LOC, prefs.cachedLocationName, prefs.cachedLocationName)
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
            items += Item(icon, tint, "${b.levelPercent}% · $temp", "${b.levelPercent}%")
        }

        if (items.isEmpty()) {
            items += Item(R.drawable.ic_info, Color.WHITE, "Chưa chọn thông tin", "FoxWidget")
        }
        currentItems = items
        if (cycleIndex >= items.size) cycleIndex = 0
        leftIconMode = prefs.islandLeftIconMode
    }

    private fun tintFor(itemTint: Int): Int = if (pastelIcons) itemTint else onColor

    /** Viên thuốc: icon app (trái) + thông tin (phải), phần giữa để trống cho camera. */
    private fun showCollapsedItem(h: Holder) {
        val item = currentItems.getOrNull(cycleIndex) ?: return
        val g = geom
        if (leftIconMode == 0) {
            h.leftIcon.setImageResource(R.drawable.fox_logo)
            h.leftIcon.clearColorFilter()
        } else {
            h.leftIcon.setImageResource(item.iconRes)
            h.leftIcon.setColorFilter(tintFor(item.tint))
        }
        h.text.setTextColor(onColor)
        val fitsFull = g == null || h.text.paint.measureText(item.shortText) <= g.textW
        h.text.text = if (fitsFull) item.shortText else item.tinyText
    }

    private fun scheduleCycle() {
        main.removeCallbacks(cycleRunnable)
        if (!expanded && currentItems.size > 1) main.postDelayed(cycleRunnable, CYCLE_MS)
    }

    // ------------------------------------------------------------------ giao diện (màu, kính)

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

    /** Màu vầng sáng ở đáy thẻ khi mở rộng (như màu chủ đạo của ảnh bìa trong đảo media). */
    private fun resolveGlowColor(ctx: Context, prefs: PrefsManager, base: Int): Int {
        if (ColorUtils.calculateLuminance(base) > 0.55) return 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            prefs.islandColorMode == PrefsManager.ISLAND_COLOR_SYSTEM
        ) {
            try {
                return ctx.getColor(android.R.color.system_accent1_500)
            } catch (_: Exception) {
            }
        }
        return if (ColorUtils.calculateLuminance(base) < 0.05) COLOR_GLOW_DEFAULT else base
    }

    private fun applyAppearance(ctx: Context, prefs: PrefsManager, h: Holder) {
        val base = resolveBaseColor(ctx, prefs)
        val glass = prefs.islandStyle == PrefsManager.ISLAND_STYLE_GLASS
        val lum = ColorUtils.calculateLuminance(base)
        onColor = if (lum > 0.55) 0xFF1B1530.toInt() else Color.WHITE
        pastelIcons = lum < 0.08

        val labelColor = ColorUtils.setAlphaComponent(onColor, 0xB0)
        for (row in listOf(h.rowCal, h.rowWeather, h.rowLoc, h.rowBat)) {
            row.value.setTextColor(onColor)
            row.label.setTextColor(labelColor)
        }
        h.expTitle.setTextColor(onColor)
        h.text.setTextColor(onColor)

        val glow = resolveGlowColor(ctx, prefs, base)
        val key = "$base|$glass|$glow|$builtScale"
        if (key != appearanceKey) {
            appearanceKey = key
            val density = h.root.resources.displayMetrics.density
            val bg = IslandBackground(base, glow, glass, 22f * density, 1.2f * density)
            bg.progress = progress.coerceIn(0f, 1f)
            background = bg
            h.root.background = bg
        }
    }

    /**
     * Nền island. Thu gọn: viên thuốc đặc (hoặc kính). Khi mở rộng: sáng nhẹ lên và có vầng sáng màu ở đáy thẻ.
     * Bo góc tự lấy min(22dp, nửa chiều cao) nên viên thuốc luôn tròn đầu và thẻ bo góc mềm.
     */
    private class IslandBackground(
        private val base: Int,
        private val glow: Int,
        private val glass: Boolean,
        private val cornerPx: Float,
        private val strokePx: Float
    ) : Drawable() {
        var progress = 0f
            set(value) {
                if (field != value) {
                    field = value
                    invalidateSelf()
                }
            }

        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val gloss = Paint(Paint.ANTI_ALIAS_FLAG)
        private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val rect = RectF()
        private val clip = Path()
        private var lastW = -1
        private var lastH = -1

        private fun rebuild(w: Int, h: Int) {
            lastW = w
            lastH = h
            rect.set(strokePx / 2f, strokePx / 2f, w - strokePx / 2f, h - strokePx / 2f)
            val radius = min(cornerPx, min(rect.width(), rect.height()) / 2f)
            clip.reset()
            clip.addRoundRect(rect, radius, radius, Path.Direction.CW)

            edge.strokeWidth = strokePx
            if (glass) {
                val top = ColorUtils.setAlphaComponent(ColorUtils.blendARGB(base, Color.WHITE, 0.30f), 0xCC)
                val bottom = ColorUtils.setAlphaComponent(ColorUtils.blendARGB(base, Color.BLACK, 0.20f), 0xB8)
                fill.shader = LinearGradient(0f, rect.top, 0f, rect.bottom, top, bottom, Shader.TileMode.CLAMP)
                gloss.shader = LinearGradient(
                    0f, rect.top, 0f, rect.centerY(),
                    0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP
                )
                edge.shader = LinearGradient(
                    rect.left, rect.top, rect.right, rect.bottom,
                    intArrayOf(0xCCFFFFFF.toInt(), 0x33FFFFFF, 0x80FFFFFF.toInt()),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            if (glow != 0) {
                glowPaint.shader = LinearGradient(
                    0f, rect.top + rect.height() * 0.30f, 0f, rect.bottom,
                    ColorUtils.setAlphaComponent(glow, 0),
                    ColorUtils.setAlphaComponent(glow, 0xE0),
                    Shader.TileMode.CLAMP
                )
            }
        }

        override fun draw(canvas: Canvas) {
            val w = bounds.width()
            val h = bounds.height()
            if (w <= 0 || h <= 0) return
            if (w != lastW || h != lastH) rebuild(w, h)
            val p = progress.coerceIn(0f, 1f)
            val radius = min(cornerPx, min(rect.width(), rect.height()) / 2f)

            if (glass) {
                canvas.drawRoundRect(rect, radius, radius, fill)
            } else {
                val lifted = if (ColorUtils.calculateLuminance(base) < 0.05) 0xFF242427.toInt() else base
                fill.shader = null
                fill.color = ColorUtils.blendARGB(base, lifted, p)
                canvas.drawRoundRect(rect, radius, radius, fill)
            }

            val save = canvas.save()
            canvas.clipPath(clip)
            if (glow != 0 && p > 0.01f) {
                glowPaint.alpha = (255 * p * (if (glass) 0.7f else 1f)).roundToInt()
                canvas.drawRect(rect, glowPaint)
            }
            if (glass) canvas.drawRect(rect.left, rect.top, rect.right, rect.centerY(), gloss)
            canvas.restoreToCount(save)

            if (glass) {
                canvas.drawRoundRect(rect, radius, radius, edge)
            } else if (p > 0.01f) {
                edge.shader = null
                edge.color = ColorUtils.setAlphaComponent(Color.WHITE, (0x30 * p).roundToInt())
                canvas.drawRoundRect(rect, radius, radius, edge)
            }
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
}
