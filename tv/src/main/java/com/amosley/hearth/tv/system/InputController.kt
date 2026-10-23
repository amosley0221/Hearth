package com.amosley.hearth.tv.system

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import com.amosley.hearth.shared.protocol.Keys
import com.amosley.hearth.tv.HearthApp
import com.amosley.hearth.tv.MainActivity
import com.amosley.hearth.tv.UiEvent
import kotlinx.coroutines.launch

/**
 * Turns remote key presses into actions on the TV. Order of preference:
 * Hearth's own UI (when it's in front) → accessibility service → ADB → media session / audio APIs.
 */
class InputController(private val app: HearthApp) {
    private val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** Set by MainActivity while resumed so remote keys drive Hearth's own focus. */
    @Volatile var hearthKeySink: ((Int) -> Unit)? = null

    fun start() {
        // Reconnect ADB at start-up only after the owner chose it in Settings, so the TV never
        // shows an unexpected "Allow USB debugging?" prompt.
        if (app.adb.previouslyApproved) app.scope.launch { app.adb.connect(waitForApproval = false) }
    }

    fun key(k: String) = app.scope.launch { handle(k) }

    private suspend fun handle(k: String) {
        val a11y = InputService.instance
        val inHearth = app.foreground.isHearth && hearthKeySink != null
        val code = KEYCODES[k]
        when (k) {
            Keys.UP, Keys.DOWN, Keys.LEFT, Keys.RIGHT, Keys.OK, Keys.MENU -> {
                if (inHearth && code != null) return sinkKey(code)
                if (k != Keys.MENU && a11y?.dpad(k) == true) return
                if (code != null && app.adb.keyevent(code)) return
                if (k == Keys.OK && media()) return
            }
            Keys.BACK -> {
                if (inHearth) return sinkKey(KeyEvent.KEYCODE_BACK)
                if (a11y?.global(AccessibilityService.GLOBAL_ACTION_BACK) == true) return
                app.adb.keyevent(KeyEvent.KEYCODE_BACK)
            }
            Keys.HOME -> goHome()
            Keys.PLAY_PAUSE -> if (!app.media.playPause()) mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            Keys.SKIP_BACK -> if (!app.media.seekBy(-10_000)) mediaKey(KeyEvent.KEYCODE_MEDIA_REWIND)
            Keys.SKIP_FORWARD -> if (!app.media.seekBy(30_000)) mediaKey(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
            Keys.REWIND -> mediaKey(KeyEvent.KEYCODE_MEDIA_REWIND)
            Keys.FAST_FORWARD -> mediaKey(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
            Keys.VOL_UP -> volume(AudioManager.ADJUST_RAISE)
            Keys.VOL_DOWN -> volume(AudioManager.ADJUST_LOWER)
            Keys.MUTE -> volume(AudioManager.ADJUST_TOGGLE_MUTE)
            Keys.POWER -> power()
            Keys.SEARCH -> {
                if (!app.adb.keyevent(KeyEvent.KEYCODE_SEARCH)) {
                    try { app.startActivity(Intent(Intent.ACTION_ASSIST).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
                }
            }
            Keys.INPUTS -> { goHome(); app.emit(UiEvent.ShowInputs) }
        }
    }

    private fun media(): Boolean = app.media.playPause()

    private fun sinkKey(code: Int) {
        val sink = hearthKeySink ?: return
        android.os.Handler(android.os.Looper.getMainLooper()).post { sink(code) }
    }

    fun goHome() {
        app.startActivity(Intent(app, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_HOME_PRESSED, true))
    }

    private fun mediaKey(code: Int) {
        val t = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0))
    }

    fun volume(direction: Int) {
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        app.state.volumeChanged()
    }

    fun setVolume(level: Int) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, level.coerceIn(0, max), AudioManager.FLAG_SHOW_UI)
        app.state.volumeChanged()
    }

    fun volumeLevel(): Triple<Int, Int, Boolean> = Triple(
        audio.getStreamVolume(AudioManager.STREAM_MUSIC),
        audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
        audio.isStreamMute(AudioManager.STREAM_MUSIC),
    )

    private suspend fun power() {
        val p = app.config.value.home.tvPower
        if (p.valid && app.ha.call(p)) return
        if (app.adb.keyevent(KeyEvent.KEYCODE_POWER)) return
        InputService.instance?.global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
    }

    /** Text from the remote keyboard: into Hearth's own search, else the focused field of the foreground app. */
    fun text(text: String, mode: String) = app.scope.launch {
        if (app.foreground.isHearth) { app.emit(UiEvent.Text(text, mode)); return@launch }
        if (InputService.instance?.typeText(text, mode) == true) return@launch
        when (mode) {
            "append", "set" -> app.adb.text(text)
            "backspace" -> app.adb.keyevent(KeyEvent.KEYCODE_DEL)
            "submit" -> app.adb.keyevent(KeyEvent.KEYCODE_ENTER)
        }
    }

    /** Whether remote keys can control other apps (for the remote's "Basic remote" hint). */
    val canControlOtherApps get() = (InputService.instance != null && android.os.Build.VERSION.SDK_INT >= 33) || app.adb.connected

    companion object {
        val KEYCODES = mapOf(
            Keys.UP to KeyEvent.KEYCODE_DPAD_UP,
            Keys.DOWN to KeyEvent.KEYCODE_DPAD_DOWN,
            Keys.LEFT to KeyEvent.KEYCODE_DPAD_LEFT,
            Keys.RIGHT to KeyEvent.KEYCODE_DPAD_RIGHT,
            Keys.OK to KeyEvent.KEYCODE_DPAD_CENTER,
            Keys.MENU to KeyEvent.KEYCODE_MENU,
        )
    }
}
