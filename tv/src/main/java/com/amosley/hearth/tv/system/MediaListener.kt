package com.amosley.hearth.tv.system

import android.service.notification.NotificationListenerService
import com.amosley.hearth.tv.HearthApp

/**
 * Notification-listener access is what Android requires to read other apps' media sessions
 * (title, artwork, position) for the remote's Now tab and Continue Watching.
 */
class MediaListener : NotificationListenerService() {
    override fun onListenerConnected() {
        connected = true
        HearthApp.get().media.attach()
        HearthApp.get().state.refreshPermissions()
    }

    override fun onListenerDisconnected() {
        connected = false
    }

    companion object {
        @Volatile var connected = false
    }
}
