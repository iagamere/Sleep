package com.hares.focus

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/**
 * ساعة موثوقة لا تتأثر بتغيير وقت الهاتف.
 *
 * الفكرة: نحتفظ بنقطة أساس (الوقت الحقيقي + عداد elapsedRealtime) ثم نحسب الوقت من العداد
 * المونوتوني الذي لا يمكن تغييره من الإعدادات. بعد إعادة التشغيل نثق بساعة النظام فقط إذا كان
 * "الوقت التلقائي" مفعلاً ولم تتراجع الساعة إلى الوراء، وإلا نكمل من آخر وقت موثوق ونسجّل محاولة تلاعب.
 */
object Clock {
    private const val DRIFT_MS = 2 * 60_000L
    private var lastSaved = 0L
    @Volatile
    private var drifting = false

    fun autoTimeOn(c: Context): Boolean =
        Settings.Global.getInt(c.contentResolver, Settings.Global.AUTO_TIME, 1) == 1

    private fun bootCount(c: Context): Int =
        Settings.Global.getInt(c.contentResolver, "boot_count", -1)

    fun tz(c: Context): TimeZone {
        val s = Store.get(c)
        if (s.tzId.isEmpty()) s.tzId = TimeZone.getDefault().id
        return TimeZone.getTimeZone(s.tzId)
    }

    @Synchronized
    fun now(c: Context): Long {
        val s = Store.get(c)
        val el = SystemClock.elapsedRealtime()
        val sys = System.currentTimeMillis()
        val bc = bootCount(c)

        if (s.baseWall == 0L) {
            s.baseWall = sys
            s.baseElapsed = el
            s.bootCount = bc
            s.lastTrusted = sys
            return sys
        }

        if (bc != s.bootCount || el < s.baseElapsed) {
            // أُعيد تشغيل الهاتف: لا نعرف كم مرّ من الوقت، لذلك نحتاط.
            val last = s.lastTrusted
            val auto = autoTimeOn(c)
            val base = if (auto && sys >= last) sys else last
            if (!auto || sys < last - DRIFT_MS) addTamper(c, last)
            s.baseWall = base
            s.baseElapsed = el
            s.bootCount = bc
            s.lastTrusted = base
            return base
        }

        val t = s.baseWall + (el - s.baseElapsed)
        if (abs(sys - t) > DRIFT_MS) {
            if (!drifting) {
                drifting = true
                addTamper(c, t)
            }
        } else {
            drifting = false
        }
        if (t - lastSaved > 5000) {
            s.lastTrusted = t
            lastSaved = t
        }
        return t
    }

    private fun addTamper(c: Context, t: Long) {
        Store.get(c).addL("tam_" + dayKey(t, tz(c)), 1)
    }

    // ---------- أدوات الوقت المحلي ----------

    private fun cal(t: Long, tz: TimeZone): Calendar =
        Calendar.getInstance(tz).apply { timeInMillis = t }

    private fun key(cal: Calendar): String = String.format(
        Locale.US, "%04d%02d%02d",
        cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
    )

    fun dayKey(t: Long, tz: TimeZone): String = key(cal(t, tz))

    fun hour(t: Long, tz: TimeZone): Int = cal(t, tz).get(Calendar.HOUR_OF_DAY)

    fun hhmm(t: Long, tz: TimeZone): String {
        val c = cal(t, tz)
        return String.format(Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }

    /** معرّف "الليلة" الحالية، أو null إن لم نكن داخل نافذة الليل. */
    fun nightKey(t: Long, tz: TimeZone, cfg: Config): String? {
        val c = cal(t, tz)
        val h = c.get(Calendar.HOUR_OF_DAY)
        if (h !in cfg.nightHours()) return null
        if (cfg.nightStartHour > cfg.nightEndHour && h < cfg.nightEndHour) {
            c.add(Calendar.DAY_OF_MONTH, -1)
        }
        return key(c)
    }

    /** أقرب لحظة بعد [t] تكون فيها الساعة المحلية [hour]:00. */
    fun nextHour(t: Long, tz: TimeZone, hour: Int): Long {
        val c = cal(t, tz)
        c.set(Calendar.HOUR_OF_DAY, hour)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        if (c.timeInMillis <= t) c.add(Calendar.DAY_OF_MONTH, 1)
        return c.timeInMillis
    }

    fun fmtDuration(ms: Long): String {
        val s = maxOf(0L, ms / 1000)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
        else String.format(Locale.US, "%02d:%02d", m, sec)
    }
}
