package com.hares.focus

import android.content.Context

/** التطبيق الذي يظهر على الشاشة الآن (تحدّثه خدمة إمكانية الوصول). */
object Fg {
    @Volatile
    var pkg: String? = null
}

data class Block(val kind: String, val title: String, val msg: String, val untilMs: Long)

data class Status(
    val now: Long,
    val sessionUsedSec: Int,
    val sessionLimitSec: Int,
    val inNight: Boolean,
    val nightUsedSec: Int,
    val nightBudgetSec: Int,
    val lockUntil: Long,
    val lockKind: String,
    val autoTimeOk: Boolean,
    val pendingAt: Long
)

object Engine {
    const val DAY_MS = 24 * 3600_000L

    const val K_SESSION = "SESSION"
    const val K_NIGHT = "NIGHT"
    const val K_FOCUS = "FOCUS"
    const val K_APP = "APP"
    const val K_TAMPER = "TAMPER"

    /** تُستدعى كل ثانية من MonitorService. */
    @Synchronized
    fun tick(c: Context, dtSec: Int, screenUsable: Boolean, fg: String?) {
        val s = Store.get(c)
        val now = Clock.now(c)
        val tz = Clock.tz(c)

        applyPendingIfDue(c, now)
        val cfg = s.cfg
        val day = Clock.dayKey(now, tz)

        // تغيّر الليلة => تصفير عداد الليل
        val nk = Clock.nightKey(now, tz, cfg)
        if (nk != null && s.nightId != nk) {
            s.nightId = nk
            s.nightUsedSec = 0
            s.nightWarn = false
        }

        // الشاشة مطفأة أو مقفلة: نسجل وقت الابتعاد فقط
        if (!screenUsable) {
            if (s.offSince == 0L) s.offSince = now
            return
        }
        if (s.offSince != 0L) {
            if (now - s.offSince >= cfg.breakMin * 60_000L) {
                s.sessionUsedSec = 0
                s.sessionWarn = 0
            }
            s.offSince = 0L
        }

        // انتهاء قفل سابق
        if (s.lockUntil != 0L && s.lockUntil <= now) {
            s.lockUntil = 0L
            s.lockKind = ""
        }
        // أثناء القفل لا نحسب شيئاً
        if (s.lockUntil > now) return

        val counts = fg == null || !Allowed.isAllowed(c, fg)

        // ميزانية الليل مستنفدة؟ نقفل حتى نهاية الليل
        if (nk != null && s.nightUsedSec >= cfg.nightBudgetMin * 60) {
            s.lockUntil = Clock.nextHour(now, tz, cfg.nightEndHour)
            s.lockKind = K_NIGHT
            s.addL("lck_$day", 1)
            return
        }

        if (!counts) return

        // ---- العد ----
        s.sessionUsedSec += dtSec
        s.addL("dt_$day", dtSec.toLong())
        if (fg != null && cfg.appLimits.containsKey(fg)) s.addL("au_${day}_$fg", dtSec.toLong())
        if (nk != null) {
            s.nightUsedSec += dtSec
            s.addL("dn_$nk", dtSec.toLong())
            val nRem = cfg.nightBudgetMin * 60 - s.nightUsedSec
            if (nRem <= 600 && !s.nightWarn && cfg.nightBudgetMin > 10) {
                s.nightWarn = true
                Notifier.warn(c, "بقي ${maxOf(0, nRem / 60)} دقائق فقط من وقتك الليلي. الأفضل أن تنام الآن.")
            }
            if (s.nightUsedSec >= cfg.nightBudgetMin * 60) {
                s.lockUntil = Clock.nextHour(now, tz, cfg.nightEndHour)
                s.lockKind = K_NIGHT
                s.addL("lck_$day", 1)
                return
            }
        }

        // ---- حد الجلسة ----
        val rem = cfg.sessionMin * 60 - s.sessionUsedSec
        if (rem <= 300 && s.sessionWarn < 1 && cfg.sessionMin > 10) {
            s.sessionWarn = 1
            Notifier.warn(c, "بقيت 5 دقائق على نهاية الجلسة. أنهِ ما تفعله.")
        }
        if (rem <= 60 && s.sessionWarn < 2) {
            s.sessionWarn = 2
            Notifier.warn(c, "دقيقة واحدة وسيُقفل الهاتف للاستراحة.")
        }
        if (rem <= 0) {
            s.lockUntil = now + cfg.breakMin * 60_000L
            s.lockKind = K_SESSION
            s.sessionUsedSec = 0
            s.sessionWarn = 0
            s.addL("ses_$day", 1)
        }
    }

    /** وضع التركيز: قفل لا يمكن إلغاؤه لمدة محددة. */
    @Synchronized
    fun startFocus(c: Context, minutes: Int) {
        val s = Store.get(c)
        val now = Clock.now(c)
        val until = now + minutes * 60_000L
        if (until > s.lockUntil) {
            s.lockUntil = until
            s.lockKind = K_FOCUS
        }
    }

    /** هل يجب منع هذا التطبيق الآن؟ */
    fun blockFor(c: Context, pkg: String?): Block? {
        if (pkg == null || pkg == c.packageName) return null
        val s = Store.get(c)
        val now = Clock.now(c)

        // تلاعب بالوقت: نمنع كل شيء عدا الإعدادات (ليصلح المستخدم الوقت التلقائي)
        if (s.guardOn && !Clock.autoTimeOn(c)) {
            if (pkg == "com.android.settings") return null
            if (!Allowed.isAllowed(c, pkg)) {
                return Block(
                    K_TAMPER, "الوقت التلقائي معطّل",
                    "فعّل \"الوقت التلقائي\" من إعدادات الهاتف لتستعيد الوصول.", 0L
                )
            }
        }

        if (Allowed.isAllowed(c, pkg)) return null

        if (s.lockUntil > now) {
            val left = s.lockUntil - now
            val (t, m) = when (s.lockKind) {
                K_NIGHT -> "وقت النوم" to "الهاتف مقفل حتى الصباح (${Clock.hhmm(s.lockUntil, Clock.tz(c))}). نم جيداً."
                K_FOCUS -> "وضع التركيز" to "بقي ${Clock.fmtDuration(left)}. أنت من اختار هذا."
                else -> "استراحة" to "ابتعد عن الشاشة. بقي ${Clock.fmtDuration(left)}."
            }
            return Block(s.lockKind, t, m, s.lockUntil)
        }

        val cfg = s.cfg
        val lim = cfg.appLimits[pkg]
        if (lim != null) {
            val day = Clock.dayKey(now, Clock.tz(c))
            if (s.getL("au_${day}_$pkg") >= lim * 60L) {
                return Block(K_APP, "انتهى حد التطبيق", "استنفدت حدّك اليومي ($lim د) لهذا التطبيق. يتجدد غداً.", 0L)
            }
        }
        return null
    }

    fun status(c: Context): Status {
        val s = Store.get(c)
        val now = Clock.now(c)
        val cfg = s.cfg
        val nk = Clock.nightKey(now, Clock.tz(c), cfg)
        return Status(
            now = now,
            sessionUsedSec = s.sessionUsedSec,
            sessionLimitSec = cfg.sessionMin * 60,
            inNight = nk != null,
            nightUsedSec = if (nk != null && s.nightId == nk) s.nightUsedSec else 0,
            nightBudgetSec = cfg.nightBudgetMin * 60,
            lockUntil = if (s.lockUntil > now) s.lockUntil else 0L,
            lockKind = s.lockKind,
            autoTimeOk = Clock.autoTimeOn(c),
            pendingAt = if (s.pending != null) s.pendingAt else 0L
        )
    }

    fun statusLine(c: Context): String {
        val st = status(c)
        if (st.lockUntil != 0L) return "مقفل: بقي ${Clock.fmtDuration(st.lockUntil - st.now)}"
        val sess = "الجلسة ${st.sessionUsedSec / 60}/${st.sessionLimitSec / 60} د"
        return if (st.inNight) "$sess · الليل ${st.nightUsedSec / 60}/${st.nightBudgetSec / 60} د" else sess
    }

    // ---------- تغيير الإعدادات عبر البوابة ----------

    enum class Proposal { UNCHANGED, APPLIED, PENDING }

    /** يُطبّق ما هو أشد فوراً، وما فيه تخفيف يؤجَّل 24 ساعة. */
    @Synchronized
    fun propose(c: Context, n: Config): Proposal {
        val s = Store.get(c)
        val o = s.cfg
        val merged = Config.tighten(o, n)
        s.cfg = merged
        if (merged == n) {
            s.pending = null
            s.pendingAt = 0L
            return if (merged == o) Proposal.UNCHANGED else Proposal.APPLIED
        }
        s.pending = n
        s.pendingAt = Clock.now(c) + DAY_MS
        return Proposal.PENDING
    }

    @Synchronized
    fun applyPendingNow(c: Context) {
        val s = Store.get(c)
        val p = s.pending ?: return
        s.cfg = p
        s.pending = null
        s.pendingAt = 0L
    }

    private fun applyPendingIfDue(c: Context, now: Long) {
        val s = Store.get(c)
        if (s.pendingAt != 0L && now >= s.pendingAt) applyPendingNow(c)
    }
}
