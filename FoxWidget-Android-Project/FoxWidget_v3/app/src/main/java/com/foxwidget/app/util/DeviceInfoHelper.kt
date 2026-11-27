package com.foxwidget.app.util

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.util.Locale

/**
 * Lấy thông tin thiết bị THẬT từ hệ thống (không đoán, không ghi cứng phiên bản):
 *  - Tên thương mại (marketing name): "Galaxy S26", "Redmi Note 14 Pro", "OPPO Find X9 Pro"...
 *  - Mã model (Build.MODEL): "SM-S942Z", "2509FPN0BG"...
 *  - Phiên bản skin của hãng: One UI 8.5 / HyperOS 3.0 / ColorOS 16 / OriginOS 6 / MagicOS 10... + phiên bản Android.
 *
 * Nguồn thuộc tính theo từng hãng (đối chiếu từ tài liệu nhà phát triển Xiaomi HyperOS, mã nguồn
 * SESL của Samsung, và các bộ thu thập thuộc tính ROM trên GitHub):
 *  - Xiaomi: ro.mi.os.version.name (HyperOS), ro.miui.ui.version.name (MIUI), ro.product.marketname
 *  - Samsung: ro.build.version.oneui / Build.VERSION.SEM_PLATFORM_INT, Settings.Global default_device_name
 *  - OPPO/realme/OnePlus: ro.build.version.oplusrom, ro.vendor.oplus.market.name
 *  - vivo/iQOO: ro.vivo.os.build.display.id, ro.vivo.market.name
 *  - HONOR: ro.build.version.magic, ro.config.marketing_name
 */
object DeviceInfoHelper {

    data class OsInfo(
        /** Tên skin: "One UI", "HyperOS", "ColorOS"... hoặc "Android" nếu là bản gốc. */
        val skinName: String,
        /** Phiên bản skin: "8.5", "3.0"... rỗng nếu không có. */
        val skinVersion: String,
        /** Phiên bản Android: "16", "17"... */
        val androidVersion: String,
        val apiLevel: Int,
        val securityPatch: String
    ) {
        val skinDisplay: String
            get() = if (skinVersion.isBlank()) skinName else "$skinName $skinVersion"

        /** "One UI 8.5 · Android 16" hoặc "Android 17" nếu máy dùng Android gốc. */
        val fullDisplay: String
            get() = if (skinName == "Android") "Android $androidVersion"
            else "$skinDisplay · Android $androidVersion"
    }

    @Volatile private var cachedName: String? = null
    @Volatile private var cachedOs: OsInfo? = null

    // ------------------------------------------------------------------ thuộc tính hệ thống

    private fun sysProp(key: String): String {
        // 1) Reflection vào android.os.SystemProperties (nhanh, không tạo tiến trình)
        try {
            val cls = Class.forName("android.os.SystemProperties")
            val get = cls.getMethod("get", String::class.java, String::class.java)
            val v = (get.invoke(null, key, "") as? String)?.trim().orEmpty()
            if (v.isNotEmpty()) return v
        } catch (_: Throwable) {
        }
        // 2) Dự phòng: lệnh getprop
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("getprop", key))
            val out = p.inputStream.bufferedReader().use { it.readLine() }?.trim().orEmpty()
            p.destroy()
            out
        } catch (_: Throwable) {
            ""
        }
    }

    private fun firstProp(vararg keys: String): String {
        for (k in keys) {
            val v = sysProp(k)
            if (v.isNotBlank() && !v.equals("unknown", true)) return v
        }
        return ""
    }

    private fun settingGlobal(context: Context, key: String): String = try {
        Settings.Global.getString(context.contentResolver, key)?.trim().orEmpty()
    } catch (_: Throwable) {
        ""
    }

    private fun brandKey(): String {
        val m = (Build.MANUFACTURER ?: "").lowercase(Locale.ROOT)
        val b = (Build.BRAND ?: "").lowercase(Locale.ROOT)
        return when {
            m.contains("samsung") -> "samsung"
            m.contains("xiaomi") || b.contains("xiaomi") || b.contains("redmi") || b.contains("poco") -> "xiaomi"
            m.contains("oneplus") || b.contains("oneplus") -> "oneplus"
            m.contains("realme") || b.contains("realme") -> "realme"
            m.contains("oppo") || b.contains("oppo") -> "oppo"
            m.contains("vivo") || b.contains("vivo") || b.contains("iqoo") -> "vivo"
            m.contains("honor") || b.contains("honor") -> "honor"
            m.contains("huawei") || b.contains("huawei") -> "huawei"
            m.contains("google") -> "google"
            m.contains("motorola") || b.contains("motorola") -> "motorola"
            m.contains("asus") -> "asus"
            m.contains("nothing") -> "nothing"
            else -> m
        }
    }

    // ------------------------------------------------------------------ tên & mã thiết bị

    /** Mã model đúng như hệ thống báo (SM-S942Z, 2509FPN0BG, CPH2655...). */
    fun getModelCode(): String = (Build.MODEL ?: "").trim().ifBlank { "Android" }

    /**
     * Tên thương mại của máy. Thử lần lượt khóa riêng của hãng, rồi các khóa chung,
     * rồi tên mặc định trong Settings, cuối cùng mới dùng mã model.
     */
    fun getMarketingName(context: Context): String {
        cachedName?.let { return it }
        val model = getModelCode()
        val brand = brandKey()

        val candidates = mutableListOf<String>()
        when (brand) {
            "samsung" -> {
                candidates += settingGlobal(context, "default_device_name")
                candidates += firstProp("ro.product.marketname", "ro.config.marketing_name")
            }
            "xiaomi" -> candidates += firstProp(
                "ro.product.marketname", "ro.product.vendor.marketname", "persist.sys.device_name"
            )
            "oppo", "realme", "oneplus" -> candidates += firstProp(
                "ro.vendor.oplus.market.enname", "ro.vendor.oplus.market.name",
                "ro.oppo.market.name", "ro.config.marketing_name", "ro.product.marketname"
            )
            "vivo" -> candidates += firstProp(
                "ro.vivo.market.name", "ro.vivo.market.enname", "ro.product.marketname"
            )
            "honor", "huawei" -> {
                candidates += firstProp("ro.config.marketing_name", "ro.product.marketname")
                candidates += settingGlobal(context, "unified_default_device_name")
            }
            else -> candidates += firstProp("ro.product.marketname", "ro.config.marketing_name")
        }
        candidates += firstProp(
            "ro.product.marketname", "ro.product.odm.marketname", "ro.product.vendor.marketname",
            "ro.product.system.marketname", "ro.config.marketing_name"
        )
        candidates += settingGlobal(context, "default_device_name")
        candidates += settingGlobal(context, "unified_default_device_name")

        val best = candidates.firstOrNull { it.isNotBlank() && !it.equals(model, true) }
        val name = (best ?: model).trim()
        cachedName = name
        return name
    }

    /** Tên hiển thị gọn trên widget: tên thương mại (cắt nếu quá dài). */
    fun getShortName(context: Context, max: Int = 22): String {
        val n = getMarketingName(context)
        return if (n.length > max) n.take(max - 1).trimEnd() + "…" else n
    }

    /** "Galaxy S26 · SM-S942Z" nếu có tên thương mại, ngược lại chỉ mã model. */
    fun getNameWithCode(context: Context): String {
        val name = getMarketingName(context)
        val code = getModelCode()
        return if (name.equals(code, true)) code else "$name · $code"
    }

    // ------------------------------------------------------------------ hệ điều hành

    private fun cleanVersion(raw: String): String {
        // "V16.0.0" -> "16.0", "OS3.0" -> "3.0", "EmotionUI_14.0.0" -> "14.0"
        val m = Regex("(\\d+)(?:\\.(\\d+))?").find(raw) ?: return ""
        val major = m.groupValues[1]
        val minor = m.groupValues.getOrNull(2).orEmpty()
        return if (minor.isNotEmpty()) "$major.$minor" else "$major.0"
    }

    private fun detectOneUi(): String {
        // ro.build.version.oneui: 80500 = 8.5, 60101 = 6.1.1
        val prop = sysProp("ro.build.version.oneui").toIntOrNull()
        if (prop != null && prop >= 10000) {
            return "${prop / 10000}.${(prop % 10000) / 100}"
        }
        // SEM_PLATFORM_INT: (giá trị - 90000) => X.Y
        try {
            val f = Build.VERSION::class.java.getDeclaredField("SEM_PLATFORM_INT")
            val v = f.getInt(null) - 90000
            if (v >= 10000) return "${v / 10000}.${(v % 10000) / 100}"
        } catch (_: Throwable) {
        }
        return ""
    }

    fun getOsInfo(): OsInfo {
        cachedOs?.let { return it }
        val release = (
            if (Build.VERSION.SDK_INT >= 33) Build.VERSION.RELEASE_OR_PREVIEW_DISPLAY else Build.VERSION.RELEASE
        ).orEmpty().ifBlank { "?" }
        val api = Build.VERSION.SDK_INT
        val patch = Build.VERSION.SECURITY_PATCH ?: ""

        var skin = "Android"
        var ver = ""

        val hyper = sysProp("ro.mi.os.version.name")
        val miui = sysProp("ro.miui.ui.version.name")
        val oplusRom = firstProp("ro.build.version.oplusrom", "ro.build.version.opporom")
        val vivoDisplay = sysProp("ro.vivo.os.build.display.id")
        val magic = sysProp("ro.build.version.magic")
        val harmony = sysProp("hw_sc.build.platform.version")
        val emui = sysProp("ro.build.version.emui")
        val oneUi = if (brandKey() == "samsung") detectOneUi() else ""

        when {
            oneUi.isNotEmpty() -> {
                skin = "One UI"; ver = oneUi
            }
            hyper.isNotBlank() -> {
                skin = "HyperOS"; ver = cleanVersion(hyper)
            }
            miui.isNotBlank() -> {
                skin = "MIUI"; ver = miui.removePrefix("V").removePrefix("v")
            }
            vivoDisplay.isNotBlank() -> {
                // "OriginOS 6" / "Funtouch OS_15"
                val m = Regex("^(OriginOS|Funtouch\\s*OS)[\\s_]*([\\d.]+)?", RegexOption.IGNORE_CASE).find(vivoDisplay)
                if (m != null) {
                    skin = if (m.groupValues[1].startsWith("Origin", true)) "OriginOS" else "Funtouch OS"
                    ver = m.groupValues[2]
                } else {
                    skin = "OriginOS"; ver = cleanVersion(vivoDisplay)
                }
            }
            sysProp("ro.vivo.os.version").isNotBlank() -> {
                skin = if (sysProp("ro.vivo.os.name").contains("origin", true)) "OriginOS" else "Funtouch OS"
                ver = sysProp("ro.vivo.os.version").removePrefix("V")
            }
            oplusRom.isNotBlank() -> {
                val display = (Build.DISPLAY ?: "") + " " + sysProp("ro.rom.version") + " " + sysProp("ro.build.display.id")
                skin = when {
                    brandKey() == "realme" && sysProp("ro.build.version.realmeui").isNotBlank() -> "realme UI"
                    brandKey() == "oneplus" && display.contains("oxygen", true) -> "OxygenOS"
                    // OnePlus dùng chung nền ColorOS, cùng số phiên bản; không có thuộc tính nào phân biệt chắc chắn
                    brandKey() == "oneplus" -> "OxygenOS/ColorOS"
                    else -> "ColorOS"
                }
                ver = if (skin == "realme UI") cleanVersion(sysProp("ro.build.version.realmeui"))
                else cleanVersion(oplusRom)
            }
            magic.isNotBlank() -> {
                val v = cleanVersion(magic)
                val major = v.substringBefore('.').toIntOrNull() ?: 0
                skin = if (major >= 7) "MagicOS" else "MagicUI"; ver = v
            }
            harmony.isNotBlank() -> {
                skin = "HarmonyOS"; ver = cleanVersion(harmony)
            }
            emui.isNotBlank() -> {
                skin = "EMUI"; ver = cleanVersion(emui)
            }
        }

        val info = OsInfo(skin, ver, release, api, patch)
        cachedOs = info
        return info
    }

    /** Tên hãng đẹp để hiển thị. */
    fun getBrandTitle(): String = when (brandKey()) {
        "samsung" -> "Samsung"
        "xiaomi" -> when {
            (Build.BRAND ?: "").equals("redmi", true) -> "Redmi"
            (Build.BRAND ?: "").equals("poco", true) -> "POCO"
            else -> "Xiaomi"
        }
        "oppo" -> "OPPO"
        "realme" -> "realme"
        "oneplus" -> "OnePlus"
        "vivo" -> if ((Build.BRAND ?: "").contains("iqoo", true)) "iQOO" else "vivo"
        "honor" -> "HONOR"
        "huawei" -> "HUAWEI"
        "google" -> "Google"
        else -> (Build.MANUFACTURER ?: "Android").replaceFirstChar { it.uppercase() }
    }

    /** Nhóm giao diện island của hãng, dùng để tự chọn preset. */
    fun getIslandFamily(): String = when (brandKey()) {
        "samsung" -> "samsung"
        "xiaomi" -> "xiaomi"
        "oppo", "oneplus", "realme" -> "oppo"
        "vivo" -> "vivo"
        "honor" -> "honor"
        else -> "apple"
    }
}
