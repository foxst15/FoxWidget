package com.foxwidget.app.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.util.Locale

object OemOptimizationHelper {

    data class OemProfile(
        val brandTitle: String,
        val osEstimate: String,
        val autostartGuide: String,
        val batteryGuide: String
    )

    fun detectOemProfile(): OemProfile {
        val mfg = (Build.MANUFACTURER ?: "").lowercase(Locale.ROOT)
        val brand = (Build.BRAND ?: "").lowercase(Locale.ROOT)
        val realOs = DeviceInfoHelper.getOsInfo().fullDisplay

        return when {
            mfg.contains("xiaomi") || mfg.contains("redmi") || mfg.contains("poco") || brand.contains("xiaomi") -> {
                OemProfile(
                    brandTitle = "Xiaomi / Redmi / POCO",
                    osEstimate = realOs,
                    autostartGuide = "Cài đặt > Ứng dụng > Quyền > Tự khởi động nền (Background autostart) > Bật FoxWidget.",
                    batteryGuide = "Thông tin ứng dụng > Tiết kiệm pin > Chọn 'Không hạn chế' + Khóa app trong Đa nhiệm."
                )
            }
            mfg.contains("samsung") -> {
                OemProfile(
                    brandTitle = "Samsung Galaxy",
                    osEstimate = realOs,
                    autostartGuide = "One UI tự quản lý khởi động khi bạn loại bỏ app khỏi danh sách 'Ứng dụng nghỉ sâu'.",
                    batteryGuide = "Thông tin ứng dụng > Pin > Chọn 'Không bị hạn chế' (Unrestricted)."
                )
            }
            mfg.contains("oppo") || mfg.contains("realme") || mfg.contains("oneplus") -> {
                OemProfile(
                    brandTitle = "OPPO / OnePlus / Realme",
                    osEstimate = realOs,
                    autostartGuide = "Quản lý ứng dụng > FoxWidget > Mức sử dụng pin > Bật 'Cho phép tự khởi động' & 'Cho phép hoạt động nền'.",
                    batteryGuide = "Tắt 'Tối ưu hóa mức sử dụng pin' và khóa FoxWidget trên thanh đa nhiệm."
                )
            }
            mfg.contains("vivo") || mfg.contains("iqoo") -> {
                OemProfile(
                    brandTitle = "vivo / iQOO",
                    osEstimate = realOs,
                    autostartGuide = "iManager > Quản lý ứng dụng > Tự khởi động > Bật FoxWidget.",
                    batteryGuide = "Cài đặt > Pin > Mức tiêu thụ điện năng cao ở nền > Bật cho FoxWidget."
                )
            }
            mfg.contains("honor") -> {
                OemProfile(
                    brandTitle = "HONOR",
                    osEstimate = realOs,
                    autostartGuide = "Cài đặt > Ứng dụng > Khởi chạy ứng dụng > Tắt 'Quản lý tự động' cho FoxWidget và bật cả 3 mục thủ công.",
                    batteryGuide = "Thông tin ứng dụng > Pin > Chọn 'Không hạn chế'."
                )
            }
            else -> {
                OemProfile(
                    brandTitle = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
                    osEstimate = realOs,
                    autostartGuide = "Hệ điều hành chuẩn tự động duy trì AppWidget sau khi khởi động lại máy.",
                    batteryGuide = "Thông tin ứng dụng > Mức sử dụng pin của ứng dụng > Chọn 'Không hạn chế'."
                )
            }
        }
    }

    fun openOemBackgroundSettings(context: Context) {
        val candidateIntents = listOf(
            Intent().setComponent(
                ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            ),
            Intent().setComponent(
                ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                )
            ),
            Intent().setComponent(
                ComponentName(
                    "com.oplus.safecenter",
                    "com.oplus.safecenter.permission.startup.StartupAppListActivity"
                )
            ),
            Intent().setComponent(
                ComponentName(
                    "com.iqoo.secure",
                    "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"
                )
            ),
            Intent().setComponent(
                ComponentName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                )
            ),
            Intent().setComponent(
                ComponentName(
                    "com.hihonor.systemmanager",
                    "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
            )
        )

        for (intent in candidateIntents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (context.packageManager.resolveActivity(intent, 0) != null) {
                    context.startActivity(intent)
                    return
                }
            } catch (_: Exception) {
            }
        }

        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(fallback)
    }
}
