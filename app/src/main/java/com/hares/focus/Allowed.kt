package com.hares.focus

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager

/** التطبيقات المسموحة دائماً (المكالمات، الساعة، الشاشة الرئيسية...) والشاشات المحمية. */
object Allowed {

    /** شاشات الإعدادات والتثبيت التي نحرسها أثناء تفعيل الحماية. */
    val GUARDED = setOf(
        "com.android.settings",
        "com.android.settings.intelligence",
        "com.samsung.android.settings",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.samsung.android.packageinstaller",
        "com.miui.packageinstaller",
        "com.miui.securitycenter",
        "com.android.vending"
    )

    private val BASE = setOf(
        "com.android.systemui",
        "android",
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.incallui",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.samsung.android.incallui",
        "com.android.emergency",
        "com.google.android.deskclock",
        "com.android.deskclock",
        "com.sec.android.app.clockpackage",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller"
    )

    private var cacheAt = 0L
    private var launchers: Set<String> = emptySet()
    private var imes: Set<String> = emptySet()
    private var dialer: String? = null

    @Synchronized
    private fun refresh(c: Context) {
        val now = SystemClock.elapsedRealtime()
        if (cacheAt != 0L && now - cacheAt < 60_000) return
        cacheAt = now
        try {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            launchers = c.packageManager.queryIntentActivities(home, 0)
                .map { it.activityInfo.packageName }.toSet()
        } catch (e: Exception) {
        }
        try {
            val imm = c.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imes = imm.enabledInputMethodList.map { it.packageName }.toSet()
        } catch (e: Exception) {
        }
        try {
            val tm = c.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            dialer = tm.defaultDialerPackage
        } catch (e: Exception) {
        }
    }

    fun isIme(c: Context, pkg: String): Boolean {
        refresh(c)
        return pkg in imes
    }

    fun isAllowed(c: Context, pkg: String): Boolean {
        if (pkg == c.packageName) return true
        if (pkg in BASE) return true
        if (pkg in Store.get(c).cfg.essentials) return true
        refresh(c)
        return pkg in launchers || pkg == dialer
    }

    /** نوافذ لا تعني أن المستخدم انتقل إلى تطبيق آخر (لوحة المفاتيح، شريط الإشعارات...). */
    fun ignoredForForeground(c: Context, pkg: String): Boolean {
        return pkg == "com.android.systemui" || pkg == "android" || isIme(c, pkg)
    }
}
