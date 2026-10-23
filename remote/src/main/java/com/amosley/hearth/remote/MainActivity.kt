package com.amosley.hearth.remote

import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.amosley.hearth.remote.ui.RemoteRoot
import com.amosley.hearth.shared.protocol.Keys

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        setContent { RemoteRoot() }
    }

    override fun onResume() {
        super.onResume()
        RemoteApp.get().tv.start()
    }

    /** Phone volume buttons drive the TV volume while the remote is open. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val tv = RemoteApp.get().tv
        if (tv.anyControl) when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> { tv.key(Keys.VOL_UP); return true }
            KeyEvent.KEYCODE_VOLUME_DOWN -> { tv.key(Keys.VOL_DOWN); return true }
        }
        return super.onKeyDown(keyCode, event)
    }
}
