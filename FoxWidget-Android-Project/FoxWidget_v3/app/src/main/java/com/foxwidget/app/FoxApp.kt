package com.foxwidget.app

import android.app.Application
import com.foxwidget.app.util.I18n

/** Nạp ngôn ngữ đã chọn ngay khi tiến trình khởi động (Activity, Service, widget, receiver đều dùng chung). */
class FoxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        I18n.init(this)
    }
}
