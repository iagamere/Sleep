package com.hares.focus

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

/** حدود يومية لتطبيقات محددة + تحديد التطبيقات "الأساسية" المسموحة أثناء القفل. */
class AppsActivity : Activity() {

    private data class App(val pkg: String, val label: String)

    private var apps: List<App> = emptyList()
    private lateinit var list: ListView
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor(Ui.BG))
        root.layoutDirection = View.LAYOUT_DIRECTION_RTL
        root.setPadding(Ui.dp(this, 16), Ui.dp(this, 28), Ui.dp(this, 16), Ui.dp(this, 12))
        window.statusBarColor = Color.parseColor(Ui.BG)
        window.navigationBarColor = Color.parseColor(Ui.BG)

        root.addView(Ui.title(this, "📱 التطبيقات"))
        root.addView(
            Ui.text(
                this,
                "اضغط على تطبيق لتحديد حد يومي له، أو لجعله \"أساسياً\" (مسموحاً أثناء القفل، مثل الخرائط أو البنك). تقليل الحد فوري، وزيادته أو إضافة أساسي تمر عبر البوابة.",
                13f, Ui.MUTED
            )
        )

        val pm = packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        apps = pm.queryIntentActivities(main, 0)
            .map { App(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.pkg != packageName }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }

        list = ListView(this)
        adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, ArrayList<String>()) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent) as TextView
                v.setTextColor(Color.parseColor(Ui.TEXT))
                v.textSize = 16f
                return v
            }
        }
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ -> choose(apps[pos]) }
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        render()
    }

    private fun render() {
        val cfg = Store.get(this).cfg
        adapter.clear()
        for (a in apps) {
            val tag = when {
                a.pkg in cfg.essentials -> "  ✅ أساسي"
                cfg.appLimits.containsKey(a.pkg) -> "  ⏱ ${cfg.appLimits[a.pkg]} د/يوم"
                else -> ""
            }
            adapter.add(a.label + tag)
        }
        adapter.notifyDataSetChanged()
    }

    private fun choose(a: App) {
        val labels = arrayOf(
            "حد يومي: 15 دقيقة", "حد يومي: 30 دقيقة", "حد يومي: 60 دقيقة", "حد يومي: 120 دقيقة",
            "تطبيق أساسي (مسموح دائماً)", "إزالة أي إعداد"
        )
        val limits = intArrayOf(15, 30, 60, 120)
        AlertDialog.Builder(this)
            .setTitle(a.label)
            .setItems(labels) { _, i ->
                val cfg = Store.get(this).cfg
                val lim = HashMap(cfg.appLimits)
                val ess = HashSet(cfg.essentials)
                when {
                    i < 4 -> {
                        lim[a.pkg] = limits[i]
                        ess.remove(a.pkg)
                    }
                    i == 4 -> {
                        ess.add(a.pkg)
                        lim.remove(a.pkg)
                    }
                    else -> {
                        lim.remove(a.pkg)
                        ess.remove(a.pkg)
                    }
                }
                apply(cfg.copy(appLimits = lim, essentials = ess))
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun apply(n: Config) {
        when (Engine.propose(this, n)) {
            Engine.Proposal.UNCHANGED -> {}
            Engine.Proposal.APPLIED -> Ui.toast(this, "تم التطبيق فوراً.")
            Engine.Proposal.PENDING -> {
                AlertDialog.Builder(this)
                    .setTitle("تغيير أقل صرامة")
                    .setMessage("سيُطبّق بعد 24 ساعة، أو فوراً بكلمة سر الشخص الموثوق.")
                    .setPositiveButton("حسناً", null)
                    .setNeutralButton("تطبيق الآن (بوابة)") { _, _ ->
                        GateUi.ask(this, "لتطبيق التخفيف فوراً:") {
                            Ui.toast(this, "طُبّق التعديل.")
                            render()
                        }
                    }
                    .show()
            }
        }
        render()
    }
}
