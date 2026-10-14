package com.hares.focus

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

class MainActivity : Activity() {
    private val h = Handler(Looper.getMainLooper())

    private lateinit var tvLock: TextView
    private lateinit var tvSession: TextView
    private lateinit var barSession: ProgressBar
    private lateinit var tvNight: TextView
    private lateinit var barNight: ProgressBar
    private lateinit var tvInfo: TextView
    private lateinit var setupCard: LinearLayout
    private lateinit var armBtn: Button
    private lateinit var relaxBtn: Button

    private class Row(val title: String, val ok: () -> Boolean, val act: () -> Unit) {
        lateinit var label: TextView
        lateinit var btn: Button
    }

    private lateinit var rows: List<Row>

    private val tick = object : Runnable {
        override fun run() {
            refresh()
            h.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        rows = listOf(
            Row("الإشعارات", { Perms.notifOn(this) }, { Perms.requestNotif(this) }),
            Row("خدمة الحماية (إمكانية الوصول)", { Perms.accessibilityOn(this) }, { Perms.openAccessibility(this) }),
            Row("الظهور فوق التطبيقات", { Perms.overlayOn(this) }, { Perms.openOverlay(this) }),
            Row("مدير الجهاز (يمنع الحذف)", { Perms.adminOn(this) }, { Perms.openAdmin(this) }),
            Row("تجاهل تحسين البطارية", { Perms.batteryOn(this) }, { Perms.openBattery(this) })
        )
        build()
    }

    private fun build() {
        val col = Ui.page(this)
        col.addView(Ui.title(this, "🛡️ حارس التركيز"))

        // ----- بطاقة الحالة -----
        val status = Ui.card(this)
        tvLock = Ui.text(this, "", 20f)
        status.addView(tvLock)
        tvSession = Ui.text(this, "", 14f, Ui.MUTED)
        tvSession.setPadding(0, Ui.dp(this, 14), 0, 0)
        status.addView(tvSession)
        barSession = Ui.bar(this)
        status.addView(barSession)
        tvNight = Ui.text(this, "", 14f, Ui.MUTED)
        status.addView(tvNight)
        barNight = Ui.bar(this)
        status.addView(barNight)
        tvInfo = Ui.text(this, "", 13f, Ui.MUTED)
        status.addView(tvInfo)
        col.addView(status)

        // ----- بطاقة الإعداد -----
        setupCard = Ui.card(this)
        setupCard.addView(Ui.text(this, "الإعداد (مرة واحدة)", 17f))
        for (r in rows) {
            val line = LinearLayout(this)
            line.orientation = LinearLayout.HORIZONTAL
            line.setPadding(0, Ui.dp(this, 8), 0, 0)
            r.label = Ui.text(this, r.title, 15f)
            line.addView(r.label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            r.btn = Button(this)
            r.btn.text = "تفعيل"
            r.btn.setOnClickListener { r.act() }
            line.addView(r.btn)
            setupCard.addView(line)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            val hint = Ui.text(
                this,
                "ملاحظة (أندرويد 13+): إن ظهر أن خيار إمكانية الوصول معطّل/رمادي، افتح معلومات التطبيق ثم القائمة ⋮ (أعلى اليمين) واختر \"السماح بالإعدادات المقيدة\".",
                12f, Ui.WARN
            )
            hint.setPadding(0, Ui.dp(this, 10), 0, 0)
            setupCard.addView(hint)
            setupCard.addView(Ui.button(this, "فتح معلومات التطبيق") { Perms.openAppInfo(this) })
        }
        armBtn = Ui.button(this, "🔒 تفعيل الحماية الكاملة") { arm() }
        setupCard.addView(armBtn)
        col.addView(setupCard)

        // ----- الإجراءات -----
        val actions = Ui.card(this)
        actions.addView(Ui.button(this, "🎯 وضع التركيز (قفل لا يُلغى)") { focusDialog() })
        actions.addView(Ui.button(this, "⚙️ الإعدادات (الجلسة، الليل)") {
            startActivity(Intent(this, SettingsActivity::class.java))
        })
        actions.addView(Ui.button(this, "📱 التطبيقات (حدود وأساسيات)") {
            startActivity(Intent(this, AppsActivity::class.java))
        })
        actions.addView(Ui.button(this, "📊 الإحصائيات") {
            startActivity(Intent(this, StatsActivity::class.java))
        })
        actions.addView(Ui.button(this, "🔑 كلمة سر الشخص الموثوق") { passwordFlow() })
        relaxBtn = Ui.button(this, "🔓 طلب فتح الإعدادات (بوابة)") {
            GateUi.ask(
                this,
                "سيرفع هذا الحراسة عن شاشات الإعدادات 15 دقيقة، ويطبّق أي تخفيف معلّق."
            ) { Ui.toast(this, "فُتحت النافذة 15 دقيقة.") }
        }
        actions.addView(relaxBtn)
        col.addView(actions)

        col.addView(
            Ui.text(
                this,
                "تذكّر: الهدف أن تنام وتعيش، لا أن تعاقب نفسك. إن غلبتك الرغبة فالقفل يساعدك، لا تحاربه.",
                13f, Ui.MUTED
            )
        )
    }

    override fun onResume() {
        super.onResume()
        MonitorService.start(this)
        h.post(tick)
    }

    override fun onPause() {
        h.removeCallbacks(tick)
        super.onPause()
    }

    private fun refresh() {
        val s = Store.get(this)
        val st = Engine.status(this)
        val tz = Clock.tz(this)

        if (st.lockUntil != 0L) {
            val name = when (st.lockKind) {
                Engine.K_NIGHT -> "🌙 وقت النوم"
                Engine.K_FOCUS -> "🎯 وضع التركيز"
                else -> "☕ استراحة"
            }
            tvLock.text = "$name · ${Clock.fmtDuration(st.lockUntil - st.now)}"
            tvLock.setTextColor(Color.parseColor(Ui.WARN))
        } else {
            tvLock.text = "✅ الهاتف متاح"
            tvLock.setTextColor(Color.parseColor(Ui.ACCENT))
        }

        tvSession.text = "الجلسة الحالية: ${st.sessionUsedSec / 60} / ${st.sessionLimitSec / 60} دقيقة"
        barSession.progress = ratio(st.sessionUsedSec, st.sessionLimitSec)

        val cfg = s.cfg
        if (st.inNight) {
            tvNight.text = "وقت الليل: ${st.nightUsedSec / 60} / ${st.nightBudgetSec / 60} دقيقة"
            barNight.progress = ratio(st.nightUsedSec, st.nightBudgetSec)
        } else {
            tvNight.text = "الليل من ${cfg.nightStartHour}:00 إلى ${cfg.nightEndHour}:00 — المسموح ${cfg.nightBudgetMin} دقيقة فقط"
            barNight.progress = 0
        }

        val info = StringBuilder()
        info.append(if (s.guardOn) "الحماية: مفعّلة 🔒" else "الحماية: غير مفعّلة بعد")
        if (Gate.windowOpen(this)) info.append("\nنافذة الفتح مفتوحة: ${Clock.fmtDuration(Gate.windowLeftMs(this))}")
        if (st.pendingAt != 0L) info.append("\nتعديل أقل صرامة سيُطبّق بعد ${Clock.fmtDuration(st.pendingAt - st.now)}")
        val ws = Gate.waitState(this)
        if (ws.state == Gate.Wait.WAITING) info.append("\nانتظار الفتح: متبقٍ ${Clock.fmtDuration(ws.remainingMs)}")
        if (ws.state == Gate.Wait.READY) info.append("\nانتهى الانتظار: يمكنك الآن استعمال زر البوابة")
        if (!st.autoTimeOk) info.append("\n⚠️ الوقت التلقائي معطّل، فعّله من الإعدادات")
        info.append("\nالساعة الموثوقة: ${Clock.hhmm(st.now, tz)}")
        tvInfo.text = info.toString()

        var all = true
        for (r in rows) {
            val ok = r.ok()
            if (!ok) all = false
            r.label.text = (if (ok) "✓ " else "✗ ") + r.title
            r.label.setTextColor(Color.parseColor(if (ok) Ui.ACCENT else Ui.TEXT))
            r.btn.visibility = if (ok) android.view.View.GONE else android.view.View.VISIBLE
        }
        setupCard.visibility = if (s.guardOn && all) android.view.View.GONE else android.view.View.VISIBLE
        armBtn.visibility = if (s.guardOn) android.view.View.GONE else android.view.View.VISIBLE
        armBtn.isEnabled = Perms.coreReady(this)
        armBtn.alpha = if (armBtn.isEnabled) 1f else 0.4f
        relaxBtn.visibility = if (s.guardOn) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun ratio(a: Int, b: Int): Int = if (b <= 0) 0 else minOf(1000, a * 1000 / b)

    private fun arm() {
        if (!Perms.coreReady(this)) {
            Ui.toast(this, "فعّل خدمة الحماية والظهور فوق التطبيقات ومدير الجهاز أولاً.")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("تفعيل الحماية الكاملة")
            .setMessage(
                "بعد التفعيل: لن تستطيع حذف التطبيق أو تعطيله أو تغيير ساعة الهاتف أو تخفيف القيود إلا عبر البوابة " +
                    "(كلمة سر شخص موثوق أو انتظار 24 ساعة).\n\n" +
                    "هل جرّبت التطبيق وضبطت الإعدادات؟ هل عيّنت كلمة سر الشخص الموثوق؟"
            )
            .setPositiveButton("نعم، فعّل") { _, _ ->
                val s = Store.get(this)
                s.guardOn = true
                s.setupDone = true
                Ui.toast(this, "الحماية مفعّلة. بالتوفيق 💪")
            }
            .setNegativeButton("ليس بعد", null)
            .show()
    }

    private fun focusDialog() {
        val labels = arrayOf("30 دقيقة", "ساعة", "ساعتان", "4 ساعات")
        val mins = intArrayOf(30, 60, 120, 240)
        AlertDialog.Builder(this)
            .setTitle("وضع التركيز: لا يمكن إلغاؤه")
            .setItems(labels) { _, i ->
                Engine.startFocus(this, mins[i])
                Ui.toast(this, "بدأ وضع التركيز: ${labels[i]}")
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun passwordFlow() {
        if (!Gate.hasPassword(this)) {
            newPassword()
            return
        }
        Ui.askText(this, "كلمة السر الحالية", "أدخل كلمة السر الحالية لتغييرها.", true) { cur ->
            when (Gate.check(this, cur)) {
                Gate.Check.OK -> newPassword()
                Gate.Check.WRONG -> Ui.toast(this, "كلمة السر خاطئة.")
                Gate.Check.LOCKED -> Ui.toast(this, "محاولات كثيرة. حاول لاحقاً.")
            }
        }
    }

    private fun newPassword() {
        Ui.askText(
            this, "كلمة سر الشخص الموثوق",
            "سلّم الهاتف لشخص تثق به (أخ، صديق، والد) ليكتب كلمة سر دون أن تراها، ولا تسأله عنها أبداً.",
            true
        ) { p1 ->
            if (p1.length < 4) {
                Ui.toast(this, "قصيرة جداً (4 أحرف على الأقل).")
                return@askText
            }
            Ui.askText(this, "تأكيد كلمة السر", "اكتبها مرة أخرى.", true) { p2 ->
                if (p1 == p2) {
                    Gate.setPassword(this, p1)
                    Ui.toast(this, "تم حفظ كلمة السر.")
                } else {
                    Ui.toast(this, "غير متطابقتين.")
                }
            }
        }
    }
}
