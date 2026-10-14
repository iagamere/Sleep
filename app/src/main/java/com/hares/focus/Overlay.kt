package com.hares.focus

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** رسائل تظهر فوق كل التطبيقات. */
object Overlay {
    private val h = Handler(Looper.getMainLooper())
    private var flashView: View? = null
    private var flashWm: WindowManager? = null
    private var nagView: View? = null
    private var nagWm: WindowManager? = null

    /** بعد أن يضغط المستخدم "فتح الإعدادات" لا نزعجه لمدة قصيرة ليتمكن من تفعيل الخدمة. */
    @Volatile
    var graceUntil = 0L

    /** بطاقة قصيرة تشرح سبب المنع (لا تعترض اللمس). */
    fun flash(ctx: Context, type: Int, title: String, msg: String, ms: Long = 4500) {
        h.post {
            removeFlash()
            try {
                val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val box = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(56, 40, 56, 40)
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#F2111827"))
                        cornerRadius = 48f
                    }
                    addView(TextView(ctx).apply {
                        text = "🔒 $title"
                        setTextColor(Color.WHITE)
                        textSize = 20f
                        gravity = Gravity.CENTER
                    })
                    addView(TextView(ctx).apply {
                        text = msg
                        setTextColor(Color.parseColor("#CBD5E1"))
                        textSize = 16f
                        gravity = Gravity.CENTER
                        setPadding(0, 16, 0, 0)
                    })
                }
                val w = (ctx.resources.displayMetrics.widthPixels * 0.86).toInt()
                val lp = WindowManager.LayoutParams(
                    w, ViewGroup.LayoutParams.WRAP_CONTENT, type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                )
                lp.gravity = Gravity.CENTER
                wm.addView(box, lp)
                flashView = box
                flashWm = wm
                h.postDelayed({ removeFlash() }, ms)
            } catch (e: Exception) {
            }
        }
    }

    private fun removeFlash() {
        try {
            val v = flashView
            if (v != null) flashWm?.removeView(v)
        } catch (e: Exception) {
        }
        flashView = null
    }

    /**
     * شاشة كاملة تظهر إذا عُطّلت خدمة الحماية (إمكانية الوصول): لا يمكن استخدام الهاتف
     * قبل إعادة تفعيلها. تحتاج إذن "الظهور فوق التطبيقات".
     */
    fun nag(ctx: Context) {
        if (SystemClock.elapsedRealtime() < graceUntil) return
        if (nagView != null) return
        if (!Perms.overlayOn(ctx)) return
        h.post {
            if (nagView != null) return@post
            try {
                val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val box = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setBackgroundColor(Color.parseColor("#F20F172A"))
                    setPadding(64, 64, 64, 64)
                    addView(TextView(ctx).apply {
                        text = "⚠️ خدمة الحماية متوقفة"
                        setTextColor(Color.WHITE)
                        textSize = 24f
                        gravity = Gravity.CENTER
                    })
                    addView(TextView(ctx).apply {
                        text = "لا يمكن استخدام الهاتف حتى تُعيد تفعيل \"${ctx.getString(R.string.app_name)}\" في إمكانية الوصول."
                        setTextColor(Color.parseColor("#CBD5E1"))
                        textSize = 17f
                        gravity = Gravity.CENTER
                        setPadding(0, 24, 0, 32)
                    })
                    addView(Button(ctx).apply {
                        text = "فتح إعدادات إمكانية الوصول"
                        setOnClickListener {
                            graceUntil = SystemClock.elapsedRealtime() + 90_000
                            removeNag()
                            Perms.openAccessibility(ctx)
                        }
                    })
                }
                val lp = WindowManager.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                )
                wm.addView(box, lp)
                nagView = box
                nagWm = wm
            } catch (e: Exception) {
            }
        }
    }

    fun removeNag() {
        h.post {
            try {
                val v = nagView
                if (v != null) nagWm?.removeView(v)
            } catch (e: Exception) {
            }
            nagView = null
        }
    }
}
