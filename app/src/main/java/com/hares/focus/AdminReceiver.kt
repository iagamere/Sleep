package com.hares.focus

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/** مدير الجهاز: وجوده يجعل حذف التطبيق يتطلب أولاً إلغاء تفعيله (وهذه الشاشة محروسة). */
class AdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "إلغاء هذه الصلاحية يعني أنك تريد حذف حارس التركيز. هل أنت متأكد أنها رغبتك الحقيقية وليست لحظة ضعف؟"
}
