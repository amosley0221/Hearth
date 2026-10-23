package com.amosley.hearth.tv.system

import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.amosley.hearth.tv.HearthApp
import com.amosley.hearth.tv.UiEvent
import com.amosley.hearth.tv.ui.DesignScale
import com.amosley.hearth.tv.ui.screens.DoorbellCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shows the doorbell card (4i) in the top-right corner over whatever is playing.
 * Inside Hearth the card is drawn by the UI itself; over other apps it uses an overlay
 * window (needs "display over other apps", granted during setup).
 */
class DoorbellOverlay(private val app: HearthApp) {
    private var view: android.view.View? = null
    private var owner: OverlayOwner? = null
    private var hideJob: Job? = null

    fun start() {
        app.scope.launch {
            app.ha.changes.collect { e ->
                val bell = app.config.value.home.doorbell
                if (bell.isNotBlank() && e.id == bell && (e.state == "on" || e.domain == "event")) ring()
            }
        }
        app.scope.launch {
            app.events.collect { if (it is UiEvent.DismissDoorbell) withContext(Dispatchers.Main) { hide() } }
        }
    }

    /** Also used by the "Test doorbell" button in Settings. */
    fun ring() {
        val camera = app.config.value.home.camera
        app.emit(UiEvent.Doorbell(camera))
        if (app.foreground.isHearth) return
        app.scope.launch(Dispatchers.Main) { show(camera) }
    }

    private fun show(camera: String) {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(app)) return
        hide()
        val wm = app.getSystemService(WindowManager::class.java)
        val o = OverlayOwner().also { owner = it }
        val compose = ComposeView(app)
        val v = object : android.widget.FrameLayout(app) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) { hide(); return true }
                return super.dispatchKeyEvent(event)
            }
        }
        v.addView(compose)
        v.setViewTreeLifecycleOwner(o)
        v.setViewTreeSavedStateRegistryOwner(o)
        compose.setContent { DesignScale { DoorbellCard(camera = camera, overlay = true, onClose = { hide() }) } }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.END }
        try {
            wm.addView(v, lp)
            o.resume()
            view = v
            v.requestFocus()
        } catch (_: Exception) { return }
        hideJob = app.scope.launch(Dispatchers.Main) { delay(20_000); hide() }
    }

    fun hide() {
        hideJob?.cancel()
        val v = view ?: return
        view = null
        try { app.getSystemService(WindowManager::class.java).removeView(v) } catch (_: Exception) {}
        owner?.destroy(); owner = null
    }

    private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val ssrc = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = ssrc.savedStateRegistry
        init { ssrc.performRestore(null); registry.currentState = Lifecycle.State.CREATED }
        fun resume() { registry.currentState = Lifecycle.State.RESUMED }
        fun destroy() { registry.currentState = Lifecycle.State.DESTROYED }
    }
}
