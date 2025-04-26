package com.sleepyyui.notallyxo.utils.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sleepyyui.notallyxo.NotallyXOApplication

class UnlockReceiver(private val application: NotallyXOApplication) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent) {
        if (intent.action == Intent.ACTION_SCREEN_OFF) {
            application.locked.value = true
        }
    }
}
