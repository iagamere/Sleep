package com.hares.focus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** يشغّل المراقبة بعد إعادة تشغيل الهاتف أو تحديث التطبيق. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        MonitorService.start(context)
    }
}
