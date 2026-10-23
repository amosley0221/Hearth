package com.amosley.hearth.tv.system

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.amosley.hearth.tv.HearthApp
import com.amosley.hearth.tv.MainActivity

/**
 * Accessibility service: lets the phone/iPad remote drive other apps (back, home, d-pad on
 * Android 13+, typing into search fields), reports which app is in front, and brings Hearth
 * back when the stock Google TV home appears.
 */
class InputService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
        HearthApp.get().state.refreshPermissions()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val app = HearthApp.get()
        val pkg = event.packageName?.toString() ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (pkg in IGNORED) return
                app.foreground.update(pkg)
                if (pkg in STOCK_HOMES && app.config.value.replaceStockHome) {
                    goHearth()
                    return
                }
                app.profiles.enforce(pkg)
            }
            AccessibilityEvent.TYPE_VIEW_FOCUSED, AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> {
                val src = event.source
                val editable = src?.isEditable == true
                app.state.setKeyboard(editable, if (editable) (src?.hintText?.toString() ?: src?.text?.toString() ?: "") else "")
            }
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_HOME && HearthApp.get().config.value.replaceStockHome) {
            if (event.action == KeyEvent.ACTION_UP) goHearth()
            return true
        }
        return false
    }

    private fun goHearth() {
        startActivity(Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_HOME_PRESSED, true))
    }

    fun global(action: Int): Boolean = performGlobalAction(action)

    fun dpad(key: String): Boolean {
        if (Build.VERSION.SDK_INT < 33) return false
        val a = when (key) {
            "up" -> GLOBAL_ACTION_DPAD_UP
            "down" -> GLOBAL_ACTION_DPAD_DOWN
            "left" -> GLOBAL_ACTION_DPAD_LEFT
            "right" -> GLOBAL_ACTION_DPAD_RIGHT
            "ok" -> GLOBAL_ACTION_DPAD_CENTER
            else -> return false
        }
        return performGlobalAction(a)
    }

    /** Types into whatever text field has focus in the foreground app. */
    fun typeText(text: String, mode: String): Boolean {
        val node = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        if (!node.isEditable) return false
        val current = node.text?.toString().orEmpty().let { if (node.isShowingHintText) "" else it }
        val next = when (mode) {
            "set" -> text
            "append" -> current + text
            "backspace" -> current.dropLast(1)
            "clear" -> ""
            else -> current
        }
        if (mode == "submit") {
            if (Build.VERSION.SDK_INT >= 30) {
                return node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
            }
            return false
        }
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, next) }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    companion object {
        @Volatile var instance: InputService? = null
        val STOCK_HOMES = setOf(
            "com.google.android.apps.tv.launcherx",
            "com.google.android.tvlauncher",
            "com.google.android.leanbacklauncher",
            "com.tcl.launcherpro",
            "com.tcl.launcher",
        )
        private val IGNORED = setOf("com.android.systemui", "android")
    }
}
