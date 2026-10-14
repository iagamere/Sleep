package com.hares.focus

import android.content.Context
import java.security.MessageDigest
import java.util.UUID

/**
 * البوابة: الطريقة الوحيدة لتخفيف الحماية.
 *  1) كلمة سر يحتفظ بها شخص تثق به (لا تعرفها أنت)، أو
 *  2) انتظار 24 ساعة بعد طلب صريح (صالح حتى 48 ساعة من الطلب).
 * عند النجاح تُفتح "نافذة" لمدة 15 دقيقة تُرفع فيها الحراسة عن الإعدادات ويُطبَّق أي تخفيف معلّق.
 */
object Gate {
    const val WAIT_MS = 24 * 3600_000L
    const val VALID_MS = 48 * 3600_000L
    const val WINDOW_MS = 15 * 60_000L

    enum class Wait { NONE, WAITING, READY }
    data class WaitState(val state: Wait, val remainingMs: Long)

    enum class Check { OK, WRONG, LOCKED }

    private fun hash(salt: String, pw: String): String =
        MessageDigest.getInstance("SHA-256").digest((salt + pw).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun hasPassword(c: Context): Boolean = Store.get(c).pwHash.isNotEmpty()

    fun setPassword(c: Context, pw: String) {
        val s = Store.get(c)
        val salt = UUID.randomUUID().toString()
        s.pwSalt = salt
        s.pwHash = hash(salt, pw)
    }

    fun check(c: Context, pw: String): Check {
        val s = Store.get(c)
        val now = Clock.now(c)
        if (s.failUntil > now) return Check.LOCKED
        if (s.pwHash.isNotEmpty() && hash(s.pwSalt, pw) == s.pwHash) {
            s.failCount = 0
            return Check.OK
        }
        s.failCount = s.failCount + 1
        if (s.failCount >= 5) {
            s.failCount = 0
            s.failUntil = now + 30 * 60_000L
            return Check.LOCKED
        }
        return Check.WRONG
    }

    fun lockedForMs(c: Context): Long {
        val s = Store.get(c)
        return maxOf(0L, s.failUntil - Clock.now(c))
    }

    fun windowOpen(c: Context): Boolean = Store.get(c).unlockWindowUntil > Clock.now(c)

    fun windowLeftMs(c: Context): Long =
        maxOf(0L, Store.get(c).unlockWindowUntil - Clock.now(c))

    fun waitState(c: Context): WaitState {
        val s = Store.get(c)
        val at = s.unlockRequestAt
        if (at == 0L) return WaitState(Wait.NONE, 0L)
        val age = Clock.now(c) - at
        if (age > VALID_MS) {
            s.unlockRequestAt = 0L
            return WaitState(Wait.NONE, 0L)
        }
        if (age >= WAIT_MS) return WaitState(Wait.READY, 0L)
        return WaitState(Wait.WAITING, WAIT_MS - age)
    }

    fun requestDelay(c: Context) {
        val s = Store.get(c)
        if (waitState(c).state == Wait.NONE) s.unlockRequestAt = Clock.now(c)
    }

    /** يفتح النافذة ويطبّق أي إعدادات معلّقة. */
    fun authorize(c: Context) {
        val s = Store.get(c)
        s.unlockWindowUntil = Clock.now(c) + WINDOW_MS
        s.unlockRequestAt = 0L
        Engine.applyPendingNow(c)
    }
}
