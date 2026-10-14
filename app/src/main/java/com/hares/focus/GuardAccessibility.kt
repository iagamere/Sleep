package com.hares.focus

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * الحارس:
 *  - يعرف التطبيق المفتوح الآن ويطرده إلى الشاشة الرئيسية أثناء القفل.
 *  - يحجب شاشات الإعدادات/التثبيت التي تحمل اسم التطبيق (حذف، إيقاف إجباري، تعطيل الخدمة...)
 *    وشاشة التاريخ والوقت، ما دامت الحماية مفعّلة ولم تُفتح "النافذة".
 */
class GuardAccessibility : AccessibilityService() {

    companion object {
        @Volatile
        var inst: GuardAccessibility? = null

        fun enforce(b: Block) {
            inst?.block(b)
        }

        private val DATE_WORDS = listOf(
            "التاريخ والوقت", "التاريخ و الوقت", "التاريخ والساعة",
            "date & time", "date and time", "date et heure", "datetime", "dateandtime"
        )
    }

    private var lastScan = 0L
    private var lastBlock = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        inst = this
        MonitorService.start(this)
        Overlay.removeNag()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        inst = null
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        val pkg = e.packageName?.toString() ?: return
        val type = e.eventType
        val s = Store.get(this)

        // إن كانت خدمة المراقبة قد توقفت أعد تشغيلها
        if (!MonitorService.running) MonitorService.start(this)

        // 1) حراسة الإعدادات والتثبيت
        if (s.guardOn && !Gate.windowOpen(this) && pkg in Allowed.GUARDED &&
            (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        ) {
            if (guardScreen(e, pkg)) return
        }

        // 2) تتبع التطبيق الأمامي وفرض القفل
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (Allowed.ignoredForForeground(this, pkg)) return
            Fg.pkg = pkg
            val b = Engine.blockFor(this, pkg)
            if (b != null) block(b)
        }
    }

    private fun guardScreen(e: AccessibilityEvent, pkg: String): Boolean {
        val t = SystemClock.uptimeMillis()
        if (t - lastScan < 300) return false
        lastScan = t

        // شاشة التاريخ والوقت (فقط عند عدم وجود تلاعب حالي كي يستطيع المستخدم الإصلاح)
        if (pkg.contains("settings") && Clock.autoTimeOn(this)) {
            val title = ((e.text?.joinToString(" ") ?: "") + " " + (e.className ?: "")).lowercase()
            if (DATE_WORDS.any { title.contains(it) }) {
                protect("تغيير الوقت والتاريخ مقفل أثناء تفعيل الحماية.")
                return true
            }
        }

        // أي شاشة تظهر فيها اسم التطبيق
        val root = rootInActiveWindow ?: return false
        try {
            val hits = root.findAccessibilityNodeInfosByText(getString(R.string.app_name))
            if (hits != null && hits.isNotEmpty()) {
                protect("هذه الشاشة محمية. لتعديل التطبيق استعمل كلمة سر الشخص الموثوق أو انتظر 24 ساعة من داخل التطبيق.")
                return true
            }
        } catch (ex: Exception) {
        }
        return false
    }

    private fun protect(msg: String) {
        performGlobalAction(GLOBAL_ACTION_HOME)
        Overlay.flash(this, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, "الحماية مفعّلة", msg)
        Store.get(this).addL("blk_" + Clock.dayKey(Clock.now(this), Clock.tz(this)), 1)
    }

    fun block(b: Block) {
        val t = SystemClock.uptimeMillis()
        if (t - lastBlock < 700) return
        lastBlock = t
        performGlobalAction(GLOBAL_ACTION_HOME)
        Overlay.flash(this, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, b.title, b.msg)
    }
}
