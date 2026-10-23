package com.amosley.hearth.shared.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Wire protocol between the TV launcher (server) and remotes (phone app, iPad web app).
 *
 * Transport: WebSocket at ws://<tv>:[PORT]/ws, JSON text frames, every frame has a "t" type.
 * The iPad web remote is served from http://<tv>:[PORT]/ and speaks the same protocol.
 *
 * Client to TV
 *   hello {token, name}           resume a paired session
 *   pair_request {name}           TV shows a 6-digit code on screen
 *   pair_confirm {code, name}     answered with paired {token} or pair_failed
 *   key {key}                     see [Keys]
 *   text {text, mode}             mode: set | append | backspace | submit
 *   launch {pkg}
 *   mode {id}                     run / stop a Mode
 *   ha_call {domain, service, entity_id, data?}
 *   console {id}                  wake + switch input for a console
 *   input {id}                    switch to an HDMI input
 *   profile {id}                  switch profile ("kids" locks the TV to the kid profile)
 *   volume_set {level}
 *   update {action}               check | install   (TV self-update from GitHub releases)
 *
 * TV to client
 *   welcome {tvName, version}, auth_required, pair_pending, paired {token}, pair_failed {reason}
 *   state {state: TvState}        full snapshot, sent on connect and on every change
 *   toast {text}
 *   update_status {status: UpdateStatus}
 */
object Protocol {
    const val PORT = 8765
    const val NSD_TYPE = "_hearth._tcp."

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        isLenient = true
    }

    fun msg(type: String, vararg fields: Pair<String, Any?>): String = buildJsonObject {
        put("t", JsonPrimitive(type))
        for ((k, v) in fields) when (v) {
            null -> {}
            is String -> put(k, JsonPrimitive(v))
            is Number -> put(k, JsonPrimitive(v))
            is Boolean -> put(k, JsonPrimitive(v))
            is JsonObject -> put(k, v)
            else -> put(k, JsonPrimitive(v.toString()))
        }
    }.toString()

    fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
}

object Keys {
    const val UP = "up"; const val DOWN = "down"; const val LEFT = "left"; const val RIGHT = "right"
    const val OK = "ok"; const val BACK = "back"; const val HOME = "home"; const val MENU = "menu"
    const val PLAY_PAUSE = "play_pause"; const val REWIND = "rewind"; const val FAST_FORWARD = "fast_forward"
    const val SKIP_BACK = "skip_back"; const val SKIP_FORWARD = "skip_forward"
    const val VOL_UP = "vol_up"; const val VOL_DOWN = "vol_down"; const val MUTE = "mute"
    const val POWER = "power"; const val SEARCH = "search"; const val INPUTS = "inputs"
}

@Serializable
data class AppInfo(val pkg: String, val label: String, val favorite: Boolean = false)

@Serializable
data class ModeInfo(val id: String, val name: String, val icon: String = "spark", val active: Boolean = false)

@Serializable
data class ConsoleInfo(
    val id: String,
    val name: String,
    /** on | standby | off | unknown */
    val status: String = "unknown",
    val inputLabel: String = "",
    val activity: String = "",
)

@Serializable
data class NowPlaying(
    val title: String = "",
    val subtitle: String = "",
    val app: String = "",
    val appLabel: String = "",
    val artUrl: String? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val playing: Boolean = false,
    /** epoch ms when positionMs was sampled, so remotes can interpolate */
    val sampledAt: Long = 0,
)

@Serializable
data class TeamScore(
    val abbr: String,
    val name: String,
    val color: String = "#555555",
    val logo: String? = null,
    val score: String = "",
    val id: String = "",
)

@Serializable
data class StatRow(val label: String, val away: String, val home: String)

@Serializable
data class Game(
    val id: String,
    val league: String,
    val leagueName: String,
    /** pre | in | post */
    val state: String,
    val away: TeamScore,
    val home: TeamScore,
    val detail: String = "",
    val situation: String = "",
    val highlight: String = "",
    val startTime: Long = 0,
    val network: String = "",
    val lastPlay: String = "",
    val favorite: Boolean = false,
    val stats: List<StatRow> = emptyList(),
)

@Serializable
data class HomeSnapshot(
    val connected: Boolean = false,
    val configured: Boolean = false,
    val room: String = "Living Room",
    val presence: String = "",
    val outside: String = "",
    val lightsOn: Int = 0,
    val lightsTotal: Int = 0,
    val lightsBrightness: Int = 0,
    val climateName: String = "",
    val climateCurrent: Double? = null,
    val climateTarget: Double? = null,
    val climateMode: String = "",
    val cameraName: String = "",
    val cameraEntity: String = "",
    val lockName: String = "",
    val lockEntity: String = "",
    val locked: Boolean? = null,
    val speakerName: String = "",
    val speakerEntity: String = "",
    val speakerState: String = "",
    val speakerArtist: String = "",
    val speakerVolume: Int = 0,
    val scenes: List<ModeInfo> = emptyList(),
)

@Serializable
data class UpdateStatus(
    val current: String = "",
    val latest: String = "",
    /** idle | checking | up_to_date | available | downloading | installing | waiting_for_user | error */
    val status: String = "idle",
    val notes: String = "",
    val progress: Int = 0,
    val error: String = "",
)

@Serializable
data class TvState(
    val tvName: String = "Living Room TV",
    val version: String = "",
    val profile: String = "adult",
    val foregroundPkg: String = "",
    val foregroundLabel: String = "",
    /** sports | nowPlaying | console | none */
    val secondScreen: String = "none",
    val nowPlaying: NowPlaying? = null,
    val liveGame: Game? = null,
    val liveGames: List<Game> = emptyList(),
    val volume: Int = 0,
    val maxVolume: Int = 100,
    val muted: Boolean = false,
    val apps: List<AppInfo> = emptyList(),
    val modes: List<ModeInfo> = emptyList(),
    val consoles: List<ConsoleInfo> = emptyList(),
    val home: HomeSnapshot = HomeSnapshot(),
    val backdropHue: Float = 40f,
    val keyboardActive: Boolean = false,
    val keyboardHint: String = "",
    val inputControl: Boolean = false,
    val update: UpdateStatus = UpdateStatus(),
)
