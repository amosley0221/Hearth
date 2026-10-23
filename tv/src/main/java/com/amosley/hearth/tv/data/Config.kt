package com.amosley.hearth.tv.data

import android.content.Context
import com.amosley.hearth.shared.protocol.Protocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom

/** A Home Assistant service call, e.g. light.turn_on on light.living_room with {"brightness_pct": 20}. */
@Serializable
data class HaCall(
    val service: String = "",          // "light.turn_on"
    val entityId: String = "",
    val data: JsonObject? = null,
) {
    val valid get() = service.contains('.')
}

@Serializable
data class ConsoleConfig(
    val id: String,
    val name: String,
    /** ps5 | xbox | switch | other */
    val kind: String = "other",
    /** TvInputManager input id (HDMI). Empty = don't switch input. */
    val inputId: String = "",
    val inputLabel: String = "",
    /** HA call that wakes the console (e.g. button.press / script.turn_on). */
    val wake: HaCall = HaCall(),
    /** HA entity whose state says on / standby / off. */
    val stateEntity: String = "",
)

@Serializable
data class GameTileConfig(
    val id: String,
    val name: String,
    val consoleId: String,
    /** Optional HA call that launches the game (Xbox). Empty for PS5 / Switch. */
    val launch: HaCall = HaCall(),
    val hue: Float = 20f,
    val artUrl: String = "",
    val runMode: String = "",
)

@Serializable
data class ModeAction(
    /** ha | app | input | console | volume | profile | wait | silence | section */
    val type: String,
    val label: String = "",
    val ha: HaCall? = null,
    val value: String = "",
)

@Serializable
data class ModeConfig(
    val id: String,
    val name: String,
    /** film | gamepad | trophy | star | moon | spark */
    val icon: String = "spark",
    val actions: List<ModeAction> = emptyList(),
    /** Actions run when the mode is ended (optional). */
    val endActions: List<ModeAction> = emptyList(),
    val showOnHome: Boolean = true,
    val showOnRemote: Boolean = true,
)

@Serializable
data class TeamRef(
    val league: String,      // key in Sports.LEAGUES, e.g. "nfl"
    val id: String,
    val abbr: String,
    val name: String,
    val color: String = "#555555",
    val logo: String? = null,
)

@Serializable
data class SportsConfig(
    val leagues: List<String> = listOf("nfl", "nba", "college-football"),
    val teams: List<TeamRef> = emptyList(),
    /** league key to package name of the app that carries it */
    val apps: Map<String, String> = emptyMap(),
    val heroTakeover: Boolean = true,
    val pushStatsToPhone: Boolean = true,
)

@Serializable
data class KidsConfig(
    val enabled: Boolean = true,
    val name: String = "Kids",
    val pinHash: String = "",
    val pinSalt: String = "",
    val approvedApps: List<String> = listOf("com.google.android.youtube.tvkids"),
    /** minutes per day, 0 = unlimited */
    val dailyLimitMin: Int = 120,
    val bedtime: String = "20:00",
    val wakeTime: String = "07:00",
)

@Serializable
data class HomeConfig(
    val url: String = "",            // http://homeassistant.local:8123
    val token: String = "",          // long-lived access token
    val room: String = "Living Room",
    val lights: List<String> = emptyList(),
    val climate: String = "",
    val camera: String = "",
    val lock: String = "",
    val speaker: String = "",
    val doorbell: String = "",       // binary_sensor / event entity that fires the pop-up
    val weather: String = "",        // weather.* (auto-detected when empty)
    val people: List<String> = emptyList(),
    val scenes: List<String> = emptyList(), // scene.* or script.*
    /** Optional HA call to turn the TV off (Power button). */
    val tvPower: HaCall = HaCall(),
) {
    val configured get() = url.isNotBlank() && token.isNotBlank()
}

@Serializable
data class PairedRemote(val token: String, val name: String, val pairedAt: Long)

@Serializable
data class HearthConfig(
    val tvName: String = "Living Room TV",
    val adultName: String = "Antonio",
    /** Dock / favourites, in order. */
    val favorites: List<String> = emptyList(),
    val hiddenApps: List<String> = emptyList(),
    val hiddenContinue: List<String> = emptyList(),
    val home: HomeConfig = HomeConfig(),
    val consoles: List<ConsoleConfig> = DEFAULT_CONSOLES,
    val games: List<GameTileConfig> = emptyList(),
    val modes: List<ModeConfig> = DEFAULT_MODES,
    val sports: SportsConfig = SportsConfig(),
    val kids: KidsConfig = KidsConfig(),
    val remotes: List<PairedRemote> = emptyList(),
    /** Bring Hearth back whenever the stock Google TV home appears. */
    val replaceStockHome: Boolean = true,
    val askProfileOnWake: Boolean = false,
    val autoUpdate: Boolean = true,
    /** Media library (Plex-style) app package; its tiles open this app. */
    val mediaLibraryPkg: String = "",
) {
    companion object {
        val DEFAULT_CONSOLES = listOf(
            ConsoleConfig(id = "ps5", name = "PS5", kind = "ps5"),
            ConsoleConfig(id = "xbox", name = "Xbox", kind = "xbox"),
            ConsoleConfig(id = "switch", name = "Switch", kind = "switch"),
        )
        val DEFAULT_MODES = listOf(
            ModeConfig("movie_night", "Movie Night", "film", listOf(
                ModeAction("ha", "Dim lights", HaCall("light.turn_on", "", null)),
                ModeAction("silence", "Silence pop-ups except doorbell", value = "on"),
            ), endActions = listOf(ModeAction("silence", value = "off"))),
            ModeConfig("game_night", "Game Night", "gamepad", listOf(
                ModeAction("console", "Wake console", value = "ps5"),
            )),
            ModeConfig("game_day", "Game Day", "trophy", listOf(
                ModeAction("section", "Open My Teams", value = "sports"),
            )),
            ModeConfig("kid_mode", "Kid Mode", "star", listOf(
                ModeAction("profile", "Switch to kid profile", value = "kids"),
            )),
        )
    }
}

/** Persists [HearthConfig] as JSON in app storage. */
class ConfigStore(context: Context) {
    private val file = File(context.filesDir, "hearth-config.json")
    private val _config = MutableStateFlow(load())
    val config: StateFlow<HearthConfig> = _config.asStateFlow()
    val value get() = _config.value

    private fun load(): HearthConfig = try {
        if (file.exists()) Protocol.json.decodeFromString(HearthConfig.serializer(), file.readText()) else HearthConfig()
    } catch (_: Exception) {
        HearthConfig()
    }

    @Synchronized
    fun update(transform: (HearthConfig) -> HearthConfig) {
        val next = transform(_config.value)
        _config.value = next
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(Protocol.json.encodeToString(HearthConfig.serializer(), next))
        tmp.renameTo(file)
    }

    fun toJson(): String = Protocol.json.encodeToString(HearthConfig.serializer(), value)

    fun replaceFromJson(text: String) {
        val incoming = Protocol.json.decodeFromString(HearthConfig.serializer(), text)
        // Pairing tokens and the PIN are never replaced from the web editor.
        update { incoming.copy(remotes = it.remotes, kids = incoming.kids.copy(pinHash = it.kids.pinHash, pinSalt = it.kids.pinSalt)) }
    }

    companion object {
        fun hashPin(pin: String, salt: String): String =
            MessageDigest.getInstance("SHA-256").digest("$salt:$pin".toByteArray())
                .joinToString("") { "%02x".format(it) }

        fun newSalt(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }

        fun newToken(): String = ByteArray(24).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    }
}
