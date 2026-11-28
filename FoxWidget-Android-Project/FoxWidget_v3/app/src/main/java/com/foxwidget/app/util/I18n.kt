package com.foxwidget.app.util

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.foxwidget.app.data.PrefsManager

/**
 * Đa ngôn ngữ nhẹ: Tiếng Việt là ngôn ngữ gốc (mặc định), Tiếng Anh là tùy chọn phụ.
 *
 *  - Chuỗi trong code/layout được viết bằng tiếng Việt, [tr] tra bảng [I18nEn] khi đang ở tiếng Anh.
 *  - Chuỗi có tham số dùng [tf] với {0}, {1}... (bản dịch có thể đổi thứ tự tham số).
 *  - Không có trong bảng dịch thì giữ nguyên tiếng Việt (không bao giờ làm app lỗi).
 *  - [applyTo] dịch toàn bộ chữ trong một cây View vừa inflate từ layout XML.
 *
 * Muốn thêm chuỗi mới: viết tiếng Việt như bình thường rồi thêm một dòng vào I18nEn.kt.
 */
object I18n {
    const val VI = "vi"
    const val EN = "en"

    @Volatile
    var lang: String = VI

    val isEnglish: Boolean get() = lang == EN

    /** Gọi một lần khi tiến trình khởi động (FoxApp). */
    fun init(context: Context) {
        lang = PrefsManager(context.applicationContext).language.let { if (it == EN) EN else VI }
    }

    fun setLanguage(context: Context, value: String) {
        val v = if (value == EN) EN else VI
        PrefsManager(context.applicationContext).language = v
        lang = v
    }

    fun tr(vi: String): String = if (lang == EN) (I18nEn.MAP[vi] ?: vi) else vi

    private val PLACEHOLDER = Regex("\\{(\\d+)\\}")

    fun tf(vi: String, vararg args: Any?): String {
        val tpl = tr(vi)
        return PLACEHOLDER.replace(tpl) { m ->
            val i = m.groupValues[1].toInt()
            if (i in args.indices) args[i].toString() else m.value
        }
    }

    /** Dịch chữ, gợi ý (hint) và mô tả của mọi View trong cây (dùng cho layout XML). */
    fun applyTo(root: View) {
        if (lang != EN) return
        if (root is TextView) {
            val t = root.text?.toString()
            if (!t.isNullOrEmpty()) {
                val en = I18nEn.MAP[t]
                if (en != null) root.text = en
            }
            val hint = root.hint?.toString()
            if (!hint.isNullOrEmpty()) {
                val en = I18nEn.MAP[hint]
                if (en != null) root.hint = en
            }
        }
        val cd = root.contentDescription?.toString()
        if (!cd.isNullOrEmpty()) {
            val en = I18nEn.MAP[cd]
            if (en != null) root.contentDescription = en
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) applyTo(root.getChildAt(i))
        }
    }
}
