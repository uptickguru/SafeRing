package online.db1k.safering.android.service

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build

object PhoneRoles {

    fun holdsCallScreening(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val rm = context.getSystemService(RoleManager::class.java) ?: return false
        return rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) &&
            rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    }

    fun callScreeningAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val rm = context.getSystemService(RoleManager::class.java) ?: return false
        return rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)
    }

    /** Role chooser only. Never the full Settings app — that screen has no way back. */
    fun requestCallScreeningIntent(context: Context): Intent? {
        if (!callScreeningAvailable(context) || holdsCallScreening(context)) return null
        val rm = context.getSystemService(RoleManager::class.java) ?: return null
        return rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
    }
}
