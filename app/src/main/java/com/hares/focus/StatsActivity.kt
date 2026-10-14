package com.hares.focus

import android.app.Activity
import android.os.Bundle
import java.util.Locale

class StatsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = Ui.page(this)
        col.addView(Ui.title(this, "📊 آخر 7 أيام"))

        val s = Store.get(this)
        val tz = Clock.tz(this)
        val now = Clock.now(this)
        val keep = HashSet<String>()

        for (i in 0..6) {
            val t = now - i * Engine.DAY_MS
            val day = Clock.dayKey(t, tz)
            val total = s.getL("dt_$day") / 60
            val night = s.getL("dn_$day") / 60
            val ses = s.getL("ses_$day")
            val lck = s.getL("lck_$day")
            val tam = s.getL("tam_$day")
            val blk = s.getL("blk_$day")

            val card = Ui.card(this)
            val label = when (i) {
                0 -> "اليوم"
                1 -> "أمس"
                else -> day.substring(4, 6) + "-" + day.substring(6, 8)
            }
            card.addView(Ui.text(this, label, 17f))
            val bars = "█".repeat(minOf(40, (total / 10).toInt()))
            card.addView(Ui.text(this, String.format(Locale.US, "%d د  %s", total, bars), 15f, Ui.ACCENT))
            val sb = StringBuilder()
            sb.append("جلسات انتهت بقفل: $ses · أقفال الليل: $lck")
            if (night > 0) sb.append("\nاستخدام الليل: $night د")
            if (blk > 0) sb.append("\nمحاولات الوصول لشاشات محمية: $blk")
            if (tam > 0) sb.append("\n⚠️ محاولات تلاعب بالوقت: $tam")
            card.addView(Ui.text(this, sb.toString(), 13f, Ui.MUTED))
            col.addView(card)

            keep.add("dt_$day"); keep.add("dn_$day"); keep.add("ses_$day"); keep.add("lck_$day")
            keep.add("tam_$day"); keep.add("blk_$day")
        }

        // حدود التطبيقات اليوم
        val cfg = s.cfg
        if (cfg.appLimits.isNotEmpty()) {
            val day = Clock.dayKey(now, tz)
            val pm = packageManager
            val card = Ui.card(this)
            card.addView(Ui.text(this, "حدود التطبيقات اليوم", 17f))
            for ((pkg, lim) in cfg.appLimits) {
                val name = try {
                    pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
                } catch (e: Exception) {
                    pkg
                }
                val used = s.getL("au_${day}_$pkg") / 60
                card.addView(Ui.text(this, "$name: $used / $lim د", 14f, Ui.MUTED))
                keep.add("au_${day}_$pkg")
            }
            col.addView(card)
        }

        // تنظيف القديم
        s.purgeOlderThan(listOf("dt_", "dn_", "ses_", "lck_", "tam_", "blk_", "au_"), keep)

        col.addView(Ui.button(this, "رجوع") { finish() })
    }
}
