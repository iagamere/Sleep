package com.hares.focus

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** أدوات بناء الواجهة برمجياً (بدون ملفات XML) بتصميم داكن بسيط. */
object Ui {
    const val BG = "#0F172A"
    const val CARD = "#1E293B"
    const val TEXT = "#F1F5F9"
    const val MUTED = "#94A3B8"
    const val ACCENT = "#14B8A6"
    const val WARN = "#F59E0B"
    const val BAD = "#EF4444"

    fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density).toInt()

    /** صفحة قابلة للتمرير؛ يعيد الحاوية الداخلية. */
    fun page(a: Activity): LinearLayout {
        val sv = ScrollView(a)
        sv.setBackgroundColor(Color.parseColor(BG))
        sv.layoutDirection = View.LAYOUT_DIRECTION_RTL
        val col = LinearLayout(a)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(a, 16), dp(a, 28), dp(a, 16), dp(a, 28))
        sv.addView(col, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        a.setContentView(sv)
        a.window.statusBarColor = Color.parseColor(BG)
        a.window.navigationBarColor = Color.parseColor(BG)
        return col
    }

    fun title(c: Context, text: String): TextView = TextView(c).apply {
        this.text = text
        setTextColor(Color.parseColor(TEXT))
        textSize = 26f
        setPadding(0, 0, 0, dp(c, 12))
    }

    fun text(c: Context, text: String, size: Float = 15f, color: String = TEXT): TextView = TextView(c).apply {
        this.text = text
        setTextColor(Color.parseColor(color))
        textSize = size
        gravity = Gravity.START
    }

    fun card(c: Context): LinearLayout = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14))
        background = GradientDrawable().apply {
            setColor(Color.parseColor(CARD))
            cornerRadius = dp(c, 16).toFloat()
        }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = dp(c, 12)
        layoutParams = lp
    }

    fun button(c: Context, text: String, onClick: () -> Unit): Button = Button(c).apply {
        this.text = text
        setAllCaps(false)
        setTextColor(Color.WHITE)
        textSize = 15f
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#0F766E"))
            cornerRadius = dp(c, 12).toFloat()
        }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(c, 8)
        layoutParams = lp
        setOnClickListener { onClick() }
    }

    fun bar(c: Context): ProgressBar =
        ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(ACCENT))
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#334155"))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 10))
            lp.topMargin = dp(c, 6)
            lp.bottomMargin = dp(c, 10)
            layoutParams = lp
        }

    fun toast(c: Context, s: String) = Toast.makeText(c, s, Toast.LENGTH_LONG).show()

    /** يسأل كلمة سر ويعيدها. */
    fun askText(a: Activity, title: String, msg: String, password: Boolean, ok: (String) -> Unit) {
        val et = EditText(a)
        et.inputType = if (password) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        else InputType.TYPE_CLASS_TEXT
        et.layoutDirection = View.LAYOUT_DIRECTION_LTR
        AlertDialog.Builder(a)
            .setTitle(title)
            .setMessage(msg)
            .setView(et)
            .setPositiveButton("موافق") { _, _ -> ok(et.text.toString()) }
            .setNegativeButton("إلغاء", null)
            .show()
    }
}

/** نافذة البوابة: كلمة سر الشخص الموثوق أو انتظار 24 ساعة. */
object GateUi {
    fun ask(a: Activity, reason: String, onOk: () -> Unit) {
        val st = Gate.waitState(a)
        val box = LinearLayout(a)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(Ui.dp(a, 20), Ui.dp(a, 8), Ui.dp(a, 20), 0)
        box.layoutDirection = View.LAYOUT_DIRECTION_RTL

        val info = TextView(a)
        info.text = reason + "\n\n" + when (st.state) {
            Gate.Wait.NONE -> "اختر: أدخل كلمة سر الشخص الموثوق، أو ابدأ انتظار 24 ساعة."
            Gate.Wait.WAITING -> "الانتظار جارٍ. المتبقي: ${Clock.fmtDuration(st.remainingMs)}"
            Gate.Wait.READY -> "انتهى الانتظار. يمكنك الآن المتابعة (الطلب صالح لعدة ساعات فقط)."
        }
        box.addView(info)

        val et = EditText(a)
        et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        et.hint = "كلمة سر الشخص الموثوق"
        et.layoutDirection = View.LAYOUT_DIRECTION_LTR
        if (Gate.hasPassword(a)) box.addView(et)

        val neutral = when (st.state) {
            Gate.Wait.NONE -> "ابدأ انتظار 24 ساعة"
            Gate.Wait.WAITING -> "كم بقي؟"
            Gate.Wait.READY -> "متابعة (انتهى الانتظار)"
        }

        val d = AlertDialog.Builder(a)
            .setTitle("بوابة الحماية")
            .setView(box)
            .setPositiveButton("تأكيد كلمة السر", null)
            .setNeutralButton(neutral, null)
            .setNegativeButton("إلغاء", null)
            .create()
        d.show()

        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (!Gate.hasPassword(a)) {
                Ui.toast(a, "لم تُعيَّن كلمة سر بعد. استعمل طريقة الانتظار.")
                return@setOnClickListener
            }
            when (Gate.check(a, et.text.toString())) {
                Gate.Check.OK -> {
                    Gate.authorize(a)
                    d.dismiss()
                    onOk()
                }
                Gate.Check.WRONG -> Ui.toast(a, "كلمة السر خاطئة.")
                Gate.Check.LOCKED -> Ui.toast(a, "محاولات كثيرة. جرّب بعد ${Clock.fmtDuration(Gate.lockedForMs(a))}")
            }
        }
        d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            when (st.state) {
                Gate.Wait.NONE -> {
                    Gate.requestDelay(a)
                    Ui.toast(a, "بدأ الانتظار. عد بعد 24 ساعة.")
                    d.dismiss()
                }
                Gate.Wait.WAITING -> Ui.toast(a, "المتبقي: ${Clock.fmtDuration(st.remainingMs)}")
                Gate.Wait.READY -> {
                    Gate.authorize(a)
                    d.dismiss()
                    onOk()
                }
            }
        }
    }
}
