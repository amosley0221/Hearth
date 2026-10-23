package com.amosley.hearth.tv.control

import com.amosley.hearth.tv.HearthApp
import com.amosley.hearth.tv.UiEvent
import com.amosley.hearth.tv.data.ConsoleConfig
import com.amosley.hearth.tv.data.GameTileConfig
import com.amosley.hearth.tv.data.ModeAction
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

data class WakeStep(val label: String, /** pending | running | done | skipped | failed */ val status: String = "pending")

data class WakeState(
    val consoleId: String,
    val title: String,
    val subtitle: String,
    val hue: Float,
    val steps: List<WakeStep>,
    val progress: Float,
    val done: Boolean = false,
)

/** Runs Modes, console wake-ups and game launches. */
class Actions(private val app: HearthApp) {
    private val _activeMode = MutableStateFlow<String?>(null)
    val activeMode: StateFlow<String?> = _activeMode.asStateFlow()

    /** Movie Night silences every pop-up except the doorbell. */
    private val _silenced = MutableStateFlow(false)
    val silenced: StateFlow<Boolean> = _silenced.asStateFlow()

    private val _wake = MutableStateFlow<WakeState?>(null)
    val wake: StateFlow<WakeState?> = _wake.asStateFlow()
    private var wakeJob: Job? = null

    fun runMode(id: String) {
        val mode = app.config.value.modes.firstOrNull { it.id == id } ?: return
        app.scope.launch {
            if (_activeMode.value == id) {
                _activeMode.value = null
                run(mode.endActions)
                app.toast("${mode.name} ended")
            } else {
                _activeMode.value = id
                app.toast("Running ${mode.name}…")
                run(mode.actions)
            }
            app.state.changed()
        }
    }

    /** Test-runs a list of actions (mode editor). */
    fun testRun(actions: List<ModeAction>) = app.scope.launch { run(actions) }

    private suspend fun run(actions: List<ModeAction>) {
        for (a in actions) {
            try { runOne(a) } catch (_: Exception) {}
        }
    }

    private suspend fun runOne(a: ModeAction) {
        val cfg = app.config.value
        when (a.type) {
            "ha" -> a.ha?.let { call ->
                // A light call with no entity targets the configured room lights; default to 20%.
                val fallback = if (call.service.startsWith("light.")) cfg.home.lights else emptyList()
                val data = if (call.service == "light.turn_on" && call.data == null)
                    buildJsonObject { put("brightness_pct", JsonPrimitive(20)) } else call.data
                app.ha.call(call.copy(data = data), fallback)
            }
            "app" -> app.apps.launch(a.value)
            "input" -> app.inputs.switchTo(a.value)
            "console" -> wakeConsole(a.value, null, waitForIt = true)
            "volume" -> a.value.toIntOrNull()?.let { app.input.setVolume(it) }
            "profile" -> app.profiles.switchTo(a.value)
            "wait" -> delay(((a.value.toDoubleOrNull() ?: 1.0) * 1000).toLong())
            "silence" -> _silenced.value = a.value != "off"
            "section" -> { app.input.goHome(); app.emit(UiEvent.OpenSection(a.value)) }
        }
    }

    fun console(id: String): ConsoleConfig? = app.config.value.consoles.firstOrNull { it.id == id }

    /** on | standby | off | unknown, from the console's HA entity. */
    fun consoleStatus(c: ConsoleConfig): String {
        val e = app.ha[c.stateEntity] ?: return "unknown"
        return when (e.state.lowercase()) {
            "on", "playing", "home", "idle", "paused", "online" -> "on"
            "standby", "rest", "rest mode" -> "standby"
            "off", "unavailable", "offline" -> "off"
            else -> e.state
        }
    }

    fun consoleActivity(c: ConsoleConfig): String {
        val e = app.ha[c.stateEntity] ?: return ""
        return e.attr("media_title") ?: e.attr("title") ?: e.attr("game") ?: ""
    }

    fun playGame(g: GameTileConfig) {
        if (g.runMode.isNotBlank()) {
            val mode = app.config.value.modes.firstOrNull { it.id == g.runMode }
            if (mode != null && _activeMode.value != mode.id) {
                _activeMode.value = mode.id
                app.scope.launch { run(mode.actions.filter { it.type != "console" }) }
            }
        }
        wakeConsole(g.consoleId, g, waitForIt = false)
    }

    /** Wake → switch input → (Xbox) launch the game, with the 4h progress screen. */
    fun wakeConsole(id: String, game: GameTileConfig?, waitForIt: Boolean): Job? {
        val c = console(id) ?: return null
        wakeJob?.cancel()
        val canLaunch = game?.launch?.valid == true
        val steps = buildList {
            add(WakeStep(if (c.wake.valid) "Wake ${c.name}" else "${c.name} wakes by itself over HDMI-CEC"))
            add(WakeStep(if (c.inputId.isNotBlank()) "Switch to ${c.inputLabel.ifBlank { "its HDMI input" }}" else "Input not set (Settings › Consoles)"))
            if (canLaunch) add(WakeStep("Launch ${game!!.name}"))
            else add(WakeStep("Waiting for the ${c.name} home screen"))
        }
        val subtitle = when {
            game == null -> "Wake and switch input"
            canLaunch -> "Launching on ${c.name}"
            c.kind == "ps5" -> "Opens PS5 · pick the game on the PS5 home screen"
            else -> "Switches to ${c.name}"
        }
        _wake.value = WakeState(c.id, game?.name ?: c.name, subtitle, game?.hue ?: 230f, steps, 0f)
        val job = app.scope.launch {
            fun step(i: Int, status: String, progress: Float) {
                val cur = _wake.value ?: return
                _wake.value = cur.copy(steps = cur.steps.mapIndexed { idx, s -> if (idx == i) s.copy(status = status) else s }, progress = progress)
            }
            step(0, "running", 0.1f)
            val woke = if (c.wake.valid) app.ha.call(c.wake) else false
            delay(if (c.wake.valid) 1500 else 400)
            step(0, if (c.wake.valid) (if (woke) "done" else "failed") else "skipped", 0.35f)
            step(1, "running", 0.45f)
            if (c.inputId.isNotBlank()) {
                // CEC wake-ups take a few seconds before the input has signal.
                if (c.wake.valid) delay(3000)
                step(1, if (app.inputs.switchTo(c.inputId)) "done" else "failed", 0.7f)
            } else step(1, "skipped", 0.7f)
            step(2, "running", 0.8f)
            if (canLaunch) {
                step(2, if (app.ha.call(game!!.launch)) "done" else "failed", 0.95f)
            } else {
                delay(4000)
                step(2, "done", 1f)
            }
            _wake.value = _wake.value?.copy(progress = 1f, done = true)
            delay(1500)
            _wake.value = null
        }
        wakeJob = job
        return job
    }

    fun cancelWake() { wakeJob?.cancel(); _wake.value = null }
}
