package com.hares.focus

import org.json.JSONArray
import org.json.JSONObject

/**
 * إعدادات القيود. أي تغيير "يخفف" القيود يمر عبر بوابة (انتظار 24 ساعة أو كلمة سر الشخص الموثوق)،
 * أما التغيير الذي "يشدد" القيود فيُطبَّق فوراً.
 */
data class Config(
    val sessionMin: Int = 30,          // أقصى مدة استخدام متواصل
    val breakMin: Int = 10,            // مدة الاستراحة الإجبارية (وزمن الابتعاد الذي يصفّر الجلسة)
    val nightBudgetMin: Int = 60,      // إجمالي المسموح طوال الليل
    val nightStartHour: Int = 0,       // بداية الليل (0 = منتصف الليل)
    val nightEndHour: Int = 6,         // نهاية الليل
    val appLimits: Map<String, Int> = emptyMap(),  // حد يومي لتطبيقات محددة (بالدقائق)
    val essentials: Set<String> = emptySet()       // تطبيقات مسموحة دائماً حتى أثناء القفل
) {
    fun nightHours(): Set<Int> {
        if (nightStartHour == nightEndHour) return emptySet()
        val out = HashSet<Int>()
        var h = nightStartHour
        while (h != nightEndHour) {
            out.add(h)
            h = (h + 1) % 24
        }
        return out
    }

    fun toJson(): String {
        val o = JSONObject()
        o.put("s", sessionMin)
        o.put("b", breakMin)
        o.put("n", nightBudgetMin)
        o.put("ns", nightStartHour)
        o.put("ne", nightEndHour)
        val lim = JSONObject()
        for ((k, v) in appLimits) lim.put(k, v)
        o.put("lim", lim)
        val ess = JSONArray()
        for (e in essentials) ess.put(e)
        o.put("ess", ess)
        return o.toString()
    }

    companion object {
        fun fromJson(js: String?): Config {
            if (js.isNullOrEmpty()) return Config()
            return try {
                val o = JSONObject(js)
                val d = Config()
                val limO = o.optJSONObject("lim")
                val lim = HashMap<String, Int>()
                if (limO != null) {
                    val keys = limO.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        lim[k] = limO.optInt(k, 0)
                    }
                }
                val essA = o.optJSONArray("ess")
                val ess = HashSet<String>()
                if (essA != null) for (i in 0 until essA.length()) ess.add(essA.getString(i))
                Config(
                    sessionMin = o.optInt("s", d.sessionMin),
                    breakMin = o.optInt("b", d.breakMin),
                    nightBudgetMin = o.optInt("n", d.nightBudgetMin),
                    nightStartHour = o.optInt("ns", d.nightStartHour),
                    nightEndHour = o.optInt("ne", d.nightEndHour),
                    appLimits = lim,
                    essentials = ess
                )
            } catch (e: Exception) {
                Config()
            }
        }

        /**
         * يدمج الإعدادات الجديدة مع القديمة ويُبقي الأشد من كل منهما.
         * إن كان الناتج مختلفاً عن [n] فهذا يعني أن [n] فيه تخفيف يحتاج بوابة.
         */
        fun tighten(o: Config, n: Config): Config {
            val keys = HashSet<String>()
            keys.addAll(o.appLimits.keys)
            keys.addAll(n.appLimits.keys)
            val lim = HashMap<String, Int>()
            for (k in keys) {
                lim[k] = minOf(o.appLimits[k] ?: Int.MAX_VALUE, n.appLimits[k] ?: Int.MAX_VALUE)
            }
            val newerNight = n.nightHours().containsAll(o.nightHours())
            return Config(
                sessionMin = minOf(o.sessionMin, n.sessionMin),
                breakMin = maxOf(o.breakMin, n.breakMin),
                nightBudgetMin = minOf(o.nightBudgetMin, n.nightBudgetMin),
                nightStartHour = if (newerNight) n.nightStartHour else o.nightStartHour,
                nightEndHour = if (newerNight) n.nightEndHour else o.nightEndHour,
                appLimits = lim,
                essentials = o.essentials.intersect(n.essentials)
            )
        }
    }
}
