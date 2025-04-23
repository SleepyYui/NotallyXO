package com.sleepyyui.notallyx.utils.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sleepyyui.notallyx.NotallyXApplication

class UnlockReceiver(private val application: NotallyXApplication) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent) {
        if (intent.action == Intent.ACTION_SCREEN_OFF) {
            application.locked.value = true
        }
    }
}
