package com.amosley.hearth.tv

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.amosley.hearth.tv.ui.DesignScale
import com.amosley.hearth.tv.ui.TvRoot
import com.amosley.hearth.tv.ui.TvUi

/** Hearth home screen. Registered for HOME + LEANBACK_LAUNCHER so it can be the default launcher. */
class MainActivity : ComponentActivity() {
    private val ui = TvUi()
    private val app get() = HearthApp.get()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DesignScale { TvRoot(ui) } }
        if (app.config.value.askProfileOnWake && app.config.value.kids.enabled) ui.profilePicker = true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_HOME_PRESSED, false) || intent.hasCategory(Intent.CATEGORY_HOME)) {
            if (!app.profiles.isKids || app.profiles.locked.value == null) ui.backToHome()
        }
    }

    override fun onResume() {
        super.onResume()
        app.foreground.hearthResumed(true)
        app.sports.visible = true
        app.input.hearthKeySink = { code ->
            dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }
        app.continueWatching.refresh()
    }

    override fun onPause() {
        super.onPause()
        app.foreground.hearthResumed(false)
        app.sports.visible = false
        app.input.hearthKeySink = null
    }

    @Deprecated("Launcher never finishes on Back")
    override fun onBackPressed() {
        // Compose BackHandler handles Back; never leave the launcher.
        @Suppress("DEPRECATION") if (onBackPressedDispatcher.hasEnabledCallbacks()) super.onBackPressed()
    }

    companion object {
        const val EXTRA_HOME_PRESSED = "hearth.home_pressed"
    }
}
