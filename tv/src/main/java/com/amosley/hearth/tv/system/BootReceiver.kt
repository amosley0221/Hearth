package com.amosley.hearth.tv.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.amosley.hearth.tv.HearthApp
import com.amosley.hearth.tv.MainActivity

/** Starts Hearth's server after boot, and reopens Hearth right after it updates itself. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        HearthApp.get() // Application.onCreate already started the server
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            try {
                context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {}
        }
    }
}
