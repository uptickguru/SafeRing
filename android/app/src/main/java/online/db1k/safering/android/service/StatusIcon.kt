package online.db1k.safering.android.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import online.db1k.safering.android.MainActivity
import online.db1k.safering.android.R

object StatusIcon {
    private const val PREFS = "humananswer_status"
    private const val KEY = "top_bar_icon"
    private const val CHANNEL = "call_safety_status"
    private const val NOTE_ID = 4101

    fun isOn(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)

    fun setOn(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
        if (on) show(context) else hide(context)
    }

    fun allowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun show(context: Context) {
        if (!isOn(context) || !allowed(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Top bar icon", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "A quiet icon so you can see HumanAnswer is on this phone."
                    setSound(null, null)
                    enableVibration(false)
                }
            )
        }
        val open = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val note = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_status_hand)
            .setContentTitle("Call safety")
            .setContentText("HumanAnswer is on this phone")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .build()
        manager.notify(NOTE_ID, note)
    }

    fun hide(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTE_ID)
    }
}
