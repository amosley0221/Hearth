package com.amosley.hearth.tv.control

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.amosley.hearth.tv.HearthApp
import com.amosley.hearth.tv.MainActivity
import com.amosley.hearth.tv.data.ConfigStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/**
 * Adult and kid profiles. The kid profile is the look plus light enforcement
 * (bounces unapproved apps back to Hearth, daily limit, bedtime). Family Link does the real blocking.
 */
class Profiles(private val app: HearthApp) {
    private val prefs = app.getSharedPreferences("profiles", Context.MODE_PRIVATE)

    private val _current = MutableStateFlow(prefs.getString("current", "adult") ?: "adult")
    val current: StateFlow<String> = _current.asStateFlow()
    val isKids get() = _current.value == "kids"

    /** null | "limit" | "bedtime" */
    private val _locked = MutableStateFlow<String?>(null)
    val locked: StateFlow<String?> = _locked.asStateFlow()

    private val _usedMin = MutableStateFlow(0)
    val usedMinutesToday: StateFlow<Int> = _usedMin.asStateFlow()
    private var warned = false

    fun start() {
        _usedMin.value = todayUsageSec() / 60
        app.scope.launch {
            while (true) {
                delay(30_000)
                tick(30)
            }
        }
    }

    private fun todayUsageSec(): Int =
        if (prefs.getString("usage_date", "") == LocalDate.now().toString()) prefs.getInt("usage_sec", 0) else 0

    private fun tick(sec: Int) {
        val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        val kids = app.config.value.kids
        if (isKids && pm.isInteractive) {
            val used = todayUsageSec() + sec
            prefs.edit().putString("usage_date", LocalDate.now().toString()).putInt("usage_sec", used).apply()
            _usedMin.value = used / 60
            val left = minutesLeft()
            if (left in 1..10 && !warned) { warned = true; app.toast("$left minutes of TV left today") }
        }
        val lock = when {
            !isKids -> null
            inBedtime() -> "bedtime"
            kids.dailyLimitMin > 0 && _usedMin.value >= kids.dailyLimitMin -> "limit"
            else -> null
        }
        if (lock != _locked.value) {
            _locked.value = lock
            if (lock != null) bringHearthToFront()
        }
    }

    fun minutesLeft(): Int {
        val k = app.config.value.kids
        val byLimit = if (k.dailyLimitMin > 0) k.dailyLimitMin - _usedMin.value else Int.MAX_VALUE
        val bed = parse(k.bedtime)
        val now = LocalTime.now()
        val byBed = if (bed != null && now.isBefore(bed)) ((bed.toSecondOfDay() - now.toSecondOfDay()) / 60) else Int.MAX_VALUE
        return minOf(byLimit, byBed).coerceAtLeast(0)
    }

    fun inBedtime(): Boolean {
        val k = app.config.value.kids
        val bed = parse(k.bedtime) ?: return false
        val wake = parse(k.wakeTime) ?: return false
        val now = LocalTime.now()
        return if (bed.isAfter(wake)) now.isAfter(bed) || now.isBefore(wake) else now.isAfter(bed) && now.isBefore(wake)
    }

    private fun parse(s: String): LocalTime? = try { LocalTime.parse(s) } catch (_: Exception) { null }

    fun switchTo(profile: String) {
        if (profile != "adult" && profile != "kids") return
        _current.value = profile
        prefs.edit().putString("current", profile).apply()
        warned = false
        tick(0)
        app.state.changed()
    }

    fun hasPin() = app.config.value.kids.pinHash.isNotBlank()

    fun checkPin(pin: String): Boolean {
        val k = app.config.value.kids
        return k.pinHash.isNotBlank() && ConfigStore.hashPin(pin, k.pinSalt) == k.pinHash
    }

    fun setPin(pin: String) {
        val salt = ConfigStore.newSalt()
        app.configStore.update { it.copy(kids = it.kids.copy(pinHash = ConfigStore.hashPin(pin, salt), pinSalt = salt)) }
    }

    /** Parents can add extra time from the lock screen (after PIN) or the remote. */
    fun grantExtra(minutes: Int) {
        val used = (todayUsageSec() - minutes * 60).coerceAtLeast(0)
        prefs.edit().putString("usage_date", LocalDate.now().toString()).putInt("usage_sec", used).apply()
        _usedMin.value = used / 60
        tick(0)
    }

    /** Called for every foreground app change. */
    fun enforce(pkg: String) {
        if (!isKids || pkg == app.packageName) return
        val k = app.config.value.kids
        if (_locked.value != null || (pkg !in k.approvedApps && pkg !in ALWAYS_ALLOWED)) {
            bringHearthToFront()
            if (_locked.value == null) app.toast("Ask a grown-up to add that app")
        }
    }

    private fun bringHearthToFront() {
        app.startActivity(Intent(app, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
    }

    companion object {
        private val ALWAYS_ALLOWED = setOf(
            "com.google.android.inputmethod.latin", "com.google.android.leanback.ime", "com.android.systemui",
            "com.google.android.katniss", "com.android.packageinstaller", "com.google.android.packageinstaller",
        )
    }
}
