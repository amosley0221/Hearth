package com.amosley.hearth.tv.state

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.amosley.hearth.shared.protocol.AppInfo
import com.amosley.hearth.shared.protocol.ConsoleInfo
import com.amosley.hearth.shared.protocol.HomeSnapshot
import com.amosley.hearth.shared.protocol.ModeInfo
import com.amosley.hearth.shared.protocol.TvState
import com.amosley.hearth.shared.theme.Hearth
import com.amosley.hearth.tv.BuildConfig
import com.amosley.hearth.tv.HearthApp
import com.amosley.hearth.tv.ha.HaEntity
import com.amosley.hearth.tv.sports.SportsRepository
import com.amosley.hearth.tv.system.InputService
import com.amosley.hearth.tv.system.MediaListener
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

data class Permissions(
    val accessibility: Boolean = false,
    val notificationAccess: Boolean = false,
    val overlay: Boolean = false,
    val installUpdates: Boolean = false,
    val adb: String = "off",
)

/** Builds the [TvState] snapshot every remote sees, and the TV's own derived values. */
class StateHub(private val app: HearthApp) {
    private val _tv = MutableStateFlow(TvState())
    val tv: StateFlow<TvState> = _tv.asStateFlow()

    private val _perms = MutableStateFlow(Permissions())
    val permissions: StateFlow<Permissions> = _perms.asStateFlow()

    private val _keyboard = MutableStateFlow(false to "")
    private val tick = MutableStateFlow(0)

    fun changed() { tick.value = tick.value + 1 }
    fun volumeChanged() = changed()
    fun setKeyboard(active: Boolean, hint: String) { _keyboard.value = active to hint; changed() }

    @OptIn(FlowPreview::class)
    fun start() {
        app.scope.launch {
            merge(
                tick, app.config, app.apps.apps, app.ha.entities, app.ha.status, app.sports.data,
                app.media.nowPlaying, app.foreground.pkg, app.profiles.current, app.actions.activeMode,
                app.updater.status, _keyboard, app.adb.status,
            ).debounce(120).collect { rebuild() }
        }
        app.scope.launch {
            while (true) { refreshPermissions(); delay(5000) }
        }
    }

    fun refreshPermissions() {
        val ctx: Context = app
        val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        val nls = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: ""
        _perms.value = Permissions(
            accessibility = InputService.instance != null || enabled.contains(ctx.packageName),
            notificationAccess = MediaListener.connected || nls.contains(ComponentName(ctx, MediaListener::class.java).flattenToString()),
            overlay = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(ctx),
            installUpdates = app.updater.github.canInstall(),
            adb = app.adb.status.value,
        )
    }

    private fun rebuild() {
        val cfg = app.config.value
        val fg = app.foreground.pkg.value
        val now = app.media.nowPlaying.value
        val sports = app.sports.data.value
        val (vol, max, muted) = app.input.volumeLevel()
        val liveGame = pickLiveGame(fg)
        val consoleOnInput = false
        val second = when {
            liveGame != null && cfg.sports.pushStatsToPhone -> "sports"
            now != null && now.title.isNotBlank() -> "nowPlaying"
            consoleOnInput -> "console"
            else -> "none"
        }
        val installed = app.apps.apps.value
        val favs = cfg.favorites.ifEmpty { DEFAULT_FAVORITES.filter { p -> installed.any { it.pkg == p } } }
        val appList = (favs.mapNotNull { p -> installed.firstOrNull { it.pkg == p } } + installed.filter { it.pkg !in favs })
            .filter { it.pkg !in cfg.hiddenApps }
            .filter { !app.profiles.isKids || it.pkg in cfg.kids.approvedApps }
            .map { AppInfo(it.pkg, it.label, it.pkg in favs) }
        val title = now?.title?.takeIf { it.isNotBlank() } ?: app.apps.labelFor(fg).takeIf { fg != app.packageName } ?: "Home"
        _tv.value = TvState(
            tvName = cfg.tvName,
            version = BuildConfig.VERSION_NAME,
            profile = app.profiles.current.value,
            foregroundPkg = fg,
            foregroundLabel = if (fg == app.packageName) "Hearth" else app.apps.labelFor(fg),
            secondScreen = second,
            nowPlaying = now,
            liveGame = liveGame,
            liveGames = sports.live,
            volume = vol,
            maxVolume = max,
            muted = muted,
            apps = appList,
            modes = cfg.modes.filter { it.showOnRemote }.map { ModeInfo(it.id, it.name, it.icon, app.actions.activeMode.value == it.id) },
            consoles = cfg.consoles.map {
                ConsoleInfo(it.id, it.name, app.actions.consoleStatus(it), it.inputLabel, app.actions.consoleActivity(it))
            },
            home = homeSnapshot(),
            backdropHue = Hearth.hueFor(title),
            keyboardActive = _keyboard.value.first,
            keyboardHint = _keyboard.value.second,
            inputControl = app.input.canControlOtherApps,
            update = app.updater.status.value,
        )
    }

    /** The game the TV is most likely showing: a live game whose network matches the app in front. */
    private fun pickLiveGame(fg: String): com.amosley.hearth.shared.protocol.Game? {
        val live = app.sports.data.value.live
        if (live.isEmpty()) return null
        val label = app.apps.labelFor(fg).lowercase()
        val isSportsApp = SportsRepository.NETWORK_APPS.values.any { fg in it } || label.contains("sport") || label.contains("nfl")
        val now = app.media.nowPlaying.value?.title?.lowercase() ?: ""
        val byTitle = live.firstOrNull { g -> listOf(g.home, g.away).any { now.contains(it.name.lowercase()) || now.contains(it.abbr.lowercase()) } }
        if (byTitle != null) return byTitle
        if (!isSportsApp) return live.firstOrNull { it.favorite }
        return live.firstOrNull { it.favorite } ?: live.first()
    }

    fun homeSnapshot(): HomeSnapshot {
        val h = app.config.value.home
        val ha = app.ha
        val lights = h.lights.mapNotNull { ha[it] }
        val on = lights.filter { it.state == "on" }
        val bright = on.mapNotNull { it.attrInt("brightness") }.let { if (it.isEmpty()) 0 else (it.average() / 255.0 * 100).toInt() }
        val climate = ha[h.climate]
        val lock = ha[h.lock]
        val spk = ha[h.speaker]
        val weather = weatherEntity()
        val people = h.people.mapNotNull { ha[it] }
        val presence = when {
            people.isEmpty() -> ""
            people.all { it.state == "home" } -> "Everyone's home"
            people.none { it.state == "home" } -> "Nobody's home"
            else -> people.filter { it.state == "home" }.joinToString(", ") { it.name } + " home"
        }
        return HomeSnapshot(
            connected = ha.connected,
            configured = h.configured,
            room = h.room,
            presence = presence,
            outside = weather?.let { w -> w.attrDouble("temperature")?.let { "${it.toInt()}°" } ?: "" } ?: "",
            lightsOn = on.size,
            lightsTotal = lights.size,
            lightsBrightness = bright,
            climateName = climate?.name ?: "",
            climateCurrent = climate?.attrDouble("current_temperature"),
            climateTarget = climate?.attrDouble("temperature"),
            climateMode = climate?.state ?: "",
            cameraName = ha[h.camera]?.name ?: if (h.camera.isNotBlank()) "Camera" else "",
            cameraEntity = h.camera,
            lockName = lock?.name ?: "",
            lockEntity = h.lock,
            locked = lock?.let { it.state == "locked" },
            speakerName = spk?.name ?: "",
            speakerEntity = h.speaker,
            speakerState = spk?.state ?: "",
            speakerArtist = spk?.let { it.attr("media_artist") ?: it.attr("media_title") } ?: "",
            speakerVolume = spk?.attrDouble("volume_level")?.let { (it * 100).toInt() } ?: 0,
            scenes = h.scenes.map { ModeInfo(it, ha[it]?.name ?: it.substringAfter('.').replace('_', ' ').replaceFirstChar { c -> c.uppercase() }) },
        )
    }

    fun weatherEntity(): HaEntity? {
        val h = app.config.value.home
        return app.ha[h.weather] ?: app.ha.entities.value.values.firstOrNull { it.domain == "weather" }
    }

    /** "61° Clear" for the clock. */
    fun weatherText(): String? {
        val w = weatherEntity() ?: return null
        val t = w.attrDouble("temperature")?.toInt() ?: return null
        val cond = when (w.state) {
            "clear-night", "sunny" -> "Clear"; "partlycloudy" -> "Partly cloudy"; "cloudy" -> "Cloudy"
            "rainy", "pouring" -> "Rain"; "snowy", "snowy-rainy" -> "Snow"; "fog" -> "Fog"; "windy", "windy-variant" -> "Windy"
            "lightning", "lightning-rainy" -> "Storms"; "hail" -> "Hail"; else -> w.state.replaceFirstChar { it.uppercase() }
        }
        return "$t° $cond"
    }

    companion object {
        val DEFAULT_FAVORITES = listOf(
            "com.google.android.youtube.tvunplugged", "com.netflix.ninja", "com.google.android.youtube.tv",
            "com.amazon.amazonvideo.livingroom", "com.peacocktv.peacockandroid", "com.cbs.ott", "com.disney.disneyplus",
            "com.wbd.stream", "com.hulu.livingroomplus", "com.espn.gtv", "com.apple.atve.androidtv.appletv", "tv.twitch.android.app",
        )
    }
}
