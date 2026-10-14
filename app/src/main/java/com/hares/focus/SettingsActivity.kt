package com.hares.focus

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class SettingsActivity : Activity() {

    private val getters = HashMap<String, () -> Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = Ui.page(this)
        col.addView(Ui.title(this, "⚙️ الإعدادات"))

        val cfg = Store.get(this).cfg

        val card = Ui.card(this)
        card.addView(
            Ui.text(
                this,
                "التشديد يُطبَّق فوراً. أما التخفيف (زيادة الوقت، تقليل الاستراحة...) فيُؤجَّل 24 ساعة، أو يُطبَّق فوراً بكلمة سر الشخص الموثوق.",
                13f, Ui.WARN
            )
        )
        addStepper(card, "s", "أقصى جلسة متواصلة", cfg.sessionMin, 5, 120, 5, " د", false)
        addStepper(card, "b", "مدة الاستراحة الإجبارية", cfg.breakMin, 1, 60, 1, " د", false)
        addStepper(card, "n", "المسموح طوال الليل", cfg.nightBudgetMin, 0, 180, 5, " د", false)
        addStepper(card, "ns", "بداية الليل (الساعة)", cfg.nightStartHour, 0, 23, 1, ":00", true)
        addStepper(card, "ne", "نهاية الليل (الساعة)", cfg.nightEndHour, 0, 23, 1, ":00", true)
        col.addView(card)

        col.addView(Ui.button(this, "حفظ") { save(cfg) })
        col.addView(Ui.button(this, "رجوع") { finish() })
    }

    private fun addStepper(
        parent: LinearLayout, key: String, label: String, init: Int,
        min: Int, max: Int, step: Int, suffix: String, wrap: Boolean
    ) {
        var v = init
        val t = TextView(this)
        t.setTextColor(Color.WHITE)
        t.textSize = 18f
        t.gravity = Gravity.CENTER
        t.minWidth = Ui.dp(this, 80)
        fun show() {
            t.text = "$v$suffix"
        }
        show()

        val minus = Button(this)
        minus.text = "−"
        minus.setOnClickListener {
            v -= step
            if (v < min) v = if (wrap) max else min
            show()
        }
        val plus = Button(this)
        plus.text = "+"
        plus.setOnClickListener {
            v += step
            if (v > max) v = if (wrap) min else max
            show()
        }

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, Ui.dp(this, 12), 0, 0)
        row.addView(
            Ui.text(this, label, 15f),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        row.addView(plus)
        row.addView(t)
        row.addView(minus)
        parent.addView(row)
        getters[key] = { v }
    }

    private fun save(old: Config) {
        val n = old.copy(
            sessionMin = getters["s"]!!(),
            breakMin = getters["b"]!!(),
            nightBudgetMin = getters["n"]!!(),
            nightStartHour = getters["ns"]!!(),
            nightEndHour = getters["ne"]!!()
        )
        when (Engine.propose(this, n)) {
            Engine.Proposal.UNCHANGED -> Ui.toast(this, "لا تغيير.")
            Engine.Proposal.APPLIED -> {
                Ui.toast(this, "تم التطبيق فوراً.")
                finish()
            }
            Engine.Proposal.PENDING -> {
                AlertDialog.Builder(this)
                    .setTitle("تغيير أقل صرامة")
                    .setMessage(
                        "ما كان أشد طُبّق الآن. أما ما فيه تخفيف فسيُطبّق تلقائياً بعد 24 ساعة، " +
                            "أو فوراً إن أدخلت كلمة سر الشخص الموثوق."
                    )
                    .setPositiveButton("حسناً") { _, _ -> finish() }
                    .setNeutralButton("تطبيق الآن (بوابة)") { _, _ ->
                        GateUi.ask(this, "لتطبيق التخفيف فوراً:") {
                            Ui.toast(this, "طُبّق التعديل.")
                            finish()
                        }
                    }
                    .show()
            }
        }
    }
}
