package com.hares.focus

import android.content.Context
import android.content.SharedPreferences
import kotlin.reflect.KProperty

/** كل الحالة المحفوظة للتطبيق (تبقى بعد إعادة التشغيل). */
class Store private constructor(ctx: Context) {
    private val p: SharedPreferences =
        ctx.applicationContext.getSharedPreferences("hares", Context.MODE_PRIVATE)

    companion object {
        @Volatile
        private var inst: Store? = null

        fun get(c: Context): Store =
            inst ?: synchronized(this) { inst ?: Store(c).also { inst = it } }
    }

    private inner class LongP(val k: String) {
        operator fun getValue(t: Any?, pr: KProperty<*>): Long = p.getLong(k, 0L)
        operator fun setValue(t: Any?, pr: KProperty<*>, v: Long) {
            p.edit().putLong(k, v).apply()
        }
    }

    private inner class IntP(val k: String) {
        operator fun getValue(t: Any?, pr: KProperty<*>): Int = p.getInt(k, 0)
        operator fun setValue(t: Any?, pr: KProperty<*>, v: Int) {
            p.edit().putInt(k, v).apply()
        }
    }

    private inner class BoolP(val k: String) {
        operator fun getValue(t: Any?, pr: KProperty<*>): Boolean = p.getBoolean(k, false)
        operator fun setValue(t: Any?, pr: KProperty<*>, v: Boolean) {
            p.edit().putBoolean(k, v).apply()
        }
    }

    private inner class StrP(val k: String) {
        operator fun getValue(t: Any?, pr: KProperty<*>): String = p.getString(k, "") ?: ""
        operator fun setValue(t: Any?, pr: KProperty<*>, v: String) {
            p.edit().putString(k, v).apply()
        }
    }

    // ---- الساعة الموثوقة ----
    var baseWall by LongP("baseWall")
    var baseElapsed by LongP("baseElapsed")
    var bootCount by IntP("bootCount")
    var lastTrusted by LongP("lastTrusted")
    var tzId by StrP("tzId")

    // ---- الحالة الحالية ----
    var lockUntil by LongP("lockUntil")
    var lockKind by StrP("lockKind")
    var sessionUsedSec by IntP("sessionUsedSec")
    var offSince by LongP("offSince")
    var sessionWarn by IntP("sessionWarn")
    var nightId by StrP("nightId")
    var nightUsedSec by IntP("nightUsedSec")
    var nightWarn by BoolP("nightWarn")

    // ---- الحماية والبوابة ----
    var setupDone by BoolP("setupDone")
    var guardOn by BoolP("guardOn")
    var pwHash by StrP("pwHash")
    var pwSalt by StrP("pwSalt")
    var failCount by IntP("failCount")
    var failUntil by LongP("failUntil")
    var unlockRequestAt by LongP("unlockRequestAt")
    var unlockWindowUntil by LongP("unlockWindowUntil")

    // ---- الإعدادات ----
    @Volatile
    private var cfgCache: Config? = null

    var cfg: Config
        get() {
            val c = cfgCache
            if (c != null) return c
            val loaded = Config.fromJson(p.getString("cfg", null))
            cfgCache = loaded
            return loaded
        }
        set(v) {
            cfgCache = v
            p.edit().putString("cfg", v.toJson()).apply()
        }

    var pendingAt by LongP("pendingAt")

    var pending: Config?
        get() {
            val s = p.getString("pending", null) ?: return null
            return Config.fromJson(s)
        }
        set(v) {
            if (v == null) p.edit().remove("pending").apply()
            else p.edit().putString("pending", v.toJson()).apply()
        }

    // ---- عدادات عامة (إحصائيات، استخدام التطبيقات) ----
    fun getL(k: String): Long = p.getLong(k, 0L)

    fun addL(k: String, d: Long) {
        p.edit().putLong(k, p.getLong(k, 0L) + d).apply()
    }

    /** يحذف عدادات الأيام القديمة كي لا يكبر الملف. */
    fun purgeOlderThan(prefixes: List<String>, keepKeys: Set<String>) {
        val e = p.edit()
        for (k in p.all.keys) {
            if (prefixes.any { k.startsWith(it) } && k !in keepKeys) e.remove(k)
        }
        e.apply()
    }

    fun allKeys(): Set<String> = p.all.keys
}
