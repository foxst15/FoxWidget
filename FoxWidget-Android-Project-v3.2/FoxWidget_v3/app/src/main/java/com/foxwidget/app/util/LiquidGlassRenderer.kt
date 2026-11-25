package com.foxwidget.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.os.Build
import com.foxwidget.app.data.PrefsManager

/**
 * Bộ dựng đồ họa Kính Lỏng Quang Học (Optical Liquid Glass Engine):
 * 1. Lớp nền thủy tinh sâu đa sắc (Deep Translucent Glass Body)
 * 2. Tụ quang lòng kính (Internal Caustic Refraction Glow)
 * 3. Phản xạ vòm giọt nước phía trên (Convex Liquid Specular Dome)
 * 4. Tán sắc cầu vồng viền kính 3D (Chromatic Rim Dispersion: Cyan -> Crystal -> Magenta -> Violet)
 * 5. Viền cắt kim cương siêu nét (Inner Diamond Bevel)
 */
object LiquidGlassRenderer {

    fun renderWidgetSurface(
        context: Context,
        prefs: PrefsManager,
        widthDp: Int,
        heightDp: Int
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val scaleFactor = if (widthDp > 420) 1.5f else 2.0f
        val widthPx = (widthDp * scaleFactor).toInt().coerceIn(320, 960)
        val heightPx = (heightDp * scaleFactor).toInt().coerceIn(140, 640)

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val effectiveCornerDp = if (prefs.cornerRadiusDp == 22 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val sysRadPx = context.resources.getDimension(android.R.dimen.system_app_widget_background_radius)
                (sysRadPx / density).coerceIn(14f, 32f)
            } catch (_: Exception) {
                22f
            }
        } else {
            prefs.cornerRadiusDp.toFloat()
        }

        val radiusPx = effectiveCornerDp * scaleFactor
        val strokeBasePx = (prefs.borderWidthDp.coerceAtLeast(1)) * scaleFactor
        val rect = RectF(strokeBasePx, strokeBasePx, widthPx - strokeBasePx, heightPx - strokeBasePx)

        val clipPath = Path().apply {
            addRoundRect(rect, radiusPx, radiusPx, Path.Direction.CW)
        }

        val alpha = ((prefs.glassOpacity / 100f) * 255).toInt().coerceIn(36, 252)
        val refractionFactor = (prefs.glassRefraction / 100f).coerceIn(0f, 1f)
        val glossFactor = (prefs.glassGloss / 100f).coerceIn(0f, 1f)

        // 1. Lớp thân kính chính (Main Glass Body)
        val (startColor, midColor, endColor) = when (prefs.widgetStyle) {
            PrefsManager.STYLE_LIGHT -> Triple(
                Color.argb(244, 248, 251, 255),
                Color.argb(238, 236, 242, 254),
                Color.argb(242, 224, 234, 252)
            )
            PrefsManager.STYLE_DARK -> Triple(
                Color.argb(242, 20, 23, 34),
                Color.argb(244, 14, 16, 25),
                Color.argb(246, 10, 12, 19)
            )
            else -> when (prefs.widgetBgPreset) {
                PrefsManager.BG_PURPLE_DUSK -> Triple(
                    Color.argb(alpha, 66, 52, 122),
                    Color.argb(alpha, 92, 74, 156),
                    Color.argb(alpha, 126, 92, 174)
                )
                PrefsManager.BG_OCEAN_BLUE -> Triple(
                    Color.argb(alpha, 18, 68, 138),
                    Color.argb(alpha, 32, 102, 178),
                    Color.argb(alpha, 54, 142, 212)
                )
                PrefsManager.BG_EMERALD_MIST -> Triple(
                    Color.argb(alpha, 16, 86, 78),
                    Color.argb(alpha, 28, 122, 108),
                    Color.argb(alpha, 52, 158, 136)
                )
                PrefsManager.BG_PRISMATIC_FROST -> Triple(
                    Color.argb((alpha * 0.78f).toInt(), 92, 118, 168),
                    Color.argb((alpha * 0.72f).toInt(), 128, 132, 186),
                    Color.argb((alpha * 0.80f).toInt(), 108, 148, 192)
                )
                else -> Triple( // BG_OBSIDIAN_TITAN (Hắc Diện Kính Bá Khí)
                    Color.argb(alpha, 18, 22, 34),
                    Color.argb(alpha, 28, 34, 52),
                    Color.argb(alpha, 42, 48, 72)
                )
            }
        }

        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, widthPx.toFloat(), heightPx.toFloat(),
                intArrayOf(startColor, midColor, endColor),
                floatArrayOf(0f, 0.52f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(rect, radiusPx, radiusPx, bodyPaint)

        // Các hiệu ứng quang học bên trong khối kính (chỉ áp dụng khi ở chế độ Liquid Glass)
        if (prefs.widgetStyle == PrefsManager.STYLE_LIQUID_GLASS) {
            canvas.save()
            canvas.clipPath(clipPath)

            // 2. Tụ quang khúc xạ góc trên trái & dưới phải (Caustic Internal Refraction Orbs)
            if (refractionFactor > 0.05f) {
                val cyanAlpha = (72 * refractionFactor).toInt().coerceIn(0, 120)
                val violetAlpha = (68 * refractionFactor).toInt().coerceIn(0, 115)

                val topLeftCaustic = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(
                        widthPx * 0.14f,
                        heightPx * 0.12f,
                        widthPx * 0.52f,
                        intArrayOf(
                            Color.argb(cyanAlpha, 110, 235, 255),
                            Color.argb(cyanAlpha / 3, 140, 210, 255),
                            Color.TRANSPARENT
                        ),
                        floatArrayOf(0f, 0.55f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(rect, topLeftCaustic)

                val bottomRightCaustic = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(
                        widthPx * 0.88f,
                        heightPx * 0.90f,
                        widthPx * 0.50f,
                        intArrayOf(
                            Color.argb(violetAlpha, 215, 130, 255),
                            Color.argb(violetAlpha / 3, 165, 115, 255),
                            Color.TRANSPARENT
                        ),
                        floatArrayOf(0f, 0.55f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(rect, bottomRightCaustic)
            }

            // 3. Phản xạ vòm giọt nước phía trên (Convex Liquid Specular Dome)
            if (glossFactor > 0.05f) {
                val domeTopAlpha = (78 * glossFactor).toInt().coerceIn(0, 125)
                val domeMidAlpha = (22 * glossFactor).toInt().coerceIn(0, 45)
                val domePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        0f, 0f, widthPx * 0.15f, heightPx * 0.52f,
                        intArrayOf(
                            Color.argb(domeTopAlpha, 255, 255, 255),
                            Color.argb(domeMidAlpha, 235, 245, 255),
                            Color.TRANSPARENT
                        ),
                        floatArrayOf(0f, 0.45f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(rect, domePaint)

                // Vệt phản quang đáy kính (Bottom Rim Bounce Light)
                val bounceAlpha = (36 * glossFactor).toInt().coerceIn(0, 65)
                val bouncePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        0f, heightPx * 0.78f, 0f, heightPx.toFloat(),
                        Color.TRANSPARENT,
                        Color.argb(bounceAlpha, 210, 235, 255),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(rect, bouncePaint)
            }

            canvas.restore()
        }

        // 4. Viền kính tán sắc quang học (Chromatic Dispersion Rim)
        if (prefs.borderWidthDp > 0) {
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = strokeBasePx * 1.15f
                if (prefs.widgetStyle == PrefsManager.STYLE_LIQUID_GLASS && refractionFactor > 0.1f) {
                    val rimAlpha = (165 + 75 * refractionFactor).toInt().coerceIn(120, 245)
                    val tintAlpha = (145 * refractionFactor).toInt().coerceIn(40, 210)
                    shader = SweepGradient(
                        widthPx / 2f,
                        heightPx / 2f,
                        intArrayOf(
                            Color.argb(rimAlpha, 255, 255, 255),
                            Color.argb(tintAlpha, 115, 238, 255), // Tán sắc Cyan
                            Color.argb(rimAlpha / 2, 255, 255, 255),
                            Color.argb(tintAlpha, 224, 138, 255), // Tán sắc Magenta
                            Color.argb(rimAlpha, 255, 255, 255)
                        ),
                        floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)
                    )
                } else {
                    color = if (prefs.widgetStyle == PrefsManager.STYLE_LIGHT) {
                        Color.argb(95, 90, 110, 180)
                    } else {
                        Color.argb(145, 255, 255, 255)
                    }
                }
            }
            canvas.drawRoundRect(rect, radiusPx, radiusPx, borderPaint)

            // 5. Viền cắt kim cương 1px bên trong (Inner Specular Bevel)
            if (prefs.widgetStyle == PrefsManager.STYLE_LIQUID_GLASS) {
                val inset = strokeBasePx * 1.35f
                val innerRect = RectF(
                    rect.left + inset,
                    rect.top + inset,
                    rect.right - inset,
                    rect.bottom - inset
                )
                val innerRadius = (radiusPx - inset).coerceAtLeast(4f)
                val innerBevelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 1.2f * scaleFactor
                    shader = LinearGradient(
                        0f, 0f, 0f, heightPx.toFloat(),
                        Color.argb((95 * glossFactor).toInt(), 255, 255, 255),
                        Color.argb((25 * glossFactor).toInt(), 255, 255, 255),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRoundRect(innerRect, innerRadius, innerRadius, innerBevelPaint)
            }
        }

        return bitmap
    }
}
