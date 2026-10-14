package com.hares.focus

import android.app.KeyguardManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock

/** خدمة تعمل دائماً في الخلفية: تعدّ وقت الاستخدام كل ثانية وتفرض القيود. */
class MonitorService : Service() {
    private val h = Handler(Looper.getMainLooper())
    private var last = 0L
    private var acc = 0L
    private var lastNotif = 0L

    private val loop = object : Runnable {
        override fun run() {
            try {
                step()
            } catch (e: Exception) {
            }
            h.postDelayed(this, 1000)
        }
    }

    companion object {
        @Volatile
        var running = false

        fun start(c: Context) {
            try {
                c.startForegroundService(Intent(c, MonitorService::class.java))
            } catch (e: Exception) {
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Notifier.channels(this)
        val n = Notifier.status(this, Engine.statusLine(this))
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        h.removeCallbacks(loop)
        last = SystemClock.elapsedRealtime()
        h.post(loop)
        return START_STICKY
    }

    override fun onDestroy() {
        h.removeCallbacks(loop)
        running = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun step() {
        val el = SystemClock.elapsedRealtime()
        // نقصّ القفزات الكبيرة (مثلاً بعد نوم المعالج) كي لا تُحسب كاستخدام
        acc += (el - last).coerceIn(0L, 5000L)
        last = el
        val sec = (acc / 1000).toInt()
        acc %= 1000

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val usable = pm.isInteractive && !km.isKeyguardLocked

        Engine.tick(this, sec, usable, Fg.pkg)

        val s = Store.get(this)
        if (usable) {
            // إن بدأ القفل وأنت داخل تطبيق: اخرج منه
            val b = Engine.blockFor(this, Fg.pkg)
            if (b != null) GuardAccessibility.enforce(b)

            // خدمة الحماية معطّلة؟ امنع الاستخدام حتى تُفعَّل من جديد
            if (s.guardOn && !Perms.accessibilityOn(this)) {
                Overlay.nag(this)
            } else {
                Overlay.removeNag()
            }
        }

        if (el - lastNotif > 15_000) {
            lastNotif = el
            Notifier.updateStatus(this, Engine.statusLine(this))
        }
    }
}
