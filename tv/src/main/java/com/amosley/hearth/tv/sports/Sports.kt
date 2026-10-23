package com.amosley.hearth.tv.sports

import com.amosley.hearth.shared.protocol.Game
import com.amosley.hearth.shared.protocol.Protocol
import com.amosley.hearth.shared.protocol.StatRow
import com.amosley.hearth.shared.protocol.TeamScore
import com.amosley.hearth.tv.data.ConfigStore
import com.amosley.hearth.tv.data.TeamRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class League(val key: String, val path: String, val abbr: String, val name: String)

data class SportsData(
    val live: List<Game> = emptyList(),
    val upcoming: List<Game> = emptyList(),
    val recent: List<Game> = emptyList(),
    val today: List<Game> = emptyList(),
    val paused: Boolean = false,
    val loaded: Boolean = false,
)

/** Scores, schedules and stats from ESPN's public site API. */
class SportsRepository(private val configStore: ConfigStore, private val scope: CoroutineScope) {
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    private val _data = MutableStateFlow(SportsData())
    val data: StateFlow<SportsData> = _data.asStateFlow()

    private var schedules: Pair<List<Game>, List<Game>> = emptyList<Game>() to emptyList()
    private var schedulesAt = 0L
    private val stats = HashMap<String, Pair<Long, List<StatRow>>>()

    /** Visible = TV is showing Hearth; polls faster then. */
    @Volatile var visible = true

    fun start() = scope.launch(Dispatchers.IO) {
        while (true) {
            val anyFavLive = refresh()
            delay(if (anyFavLive) 10_000 else if (visible) 30_000 else 90_000)
        }
    }

    suspend fun refreshNow() = withContext(Dispatchers.IO) { refresh() }

    private suspend fun refresh(): Boolean {
        val cfg = configStore.value.sports
        val leagues = cfg.leagues.mapNotNull { LEAGUES[it] }
        val favIds = cfg.teams.map { "${it.league}:${it.id}" }.toSet()
        return try {
            val boards = withContext(Dispatchers.IO) {
                leagues.map { l -> scope.async(Dispatchers.IO) { scoreboard(l, favIds) } }.awaitAll()
            }.flatten()
            if (System.currentTimeMillis() - schedulesAt > 20 * 60_000 || schedulesAt == 0L) {
                schedules = teamSchedules(cfg.teams, favIds)
                schedulesAt = System.currentTimeMillis()
            }
            val live = boards.filter { it.state == "in" }
                .sortedWith(compareByDescending<Game> { it.favorite }.thenBy { it.startTime })
                .map { g -> if (g.favorite || g === boards.firstOrNull { it.state == "in" }) withStats(g) else g }
            _data.value = SportsData(
                live = live,
                upcoming = schedules.first,
                recent = schedules.second,
                today = boards.sortedBy { it.startTime },
                loaded = true,
            )
            live.any { it.favorite }
        } catch (e: Exception) {
            _data.value = _data.value.copy(paused = true, loaded = true)
            false
        }
    }

    private fun get(url: String): JsonObject? = try {
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) null else Protocol.json.parseToJsonElement(r.body!!.string()).jsonObject
        }
    } catch (_: Exception) { null }

    private fun scoreboard(l: League, favIds: Set<String>): List<Game> {
        val extra = if (l.key == "college-football") "?groups=80" else ""
        val root = get("$BASE/${l.path}/scoreboard$extra") ?: throw IllegalStateException("scoreboard")
        return (root["events"] as? JsonArray ?: return emptyList()).mapNotNull { parseEvent(it, l, favIds) }
    }

    private fun teamSchedules(teams: List<TeamRef>, favIds: Set<String>): Pair<List<Game>, List<Game>> {
        val now = System.currentTimeMillis()
        val all = teams.flatMap { t ->
            val l = LEAGUES[t.league] ?: return@flatMap emptyList()
            val root = get("$BASE/${l.path}/teams/${t.id}/schedule") ?: return@flatMap emptyList()
            (root["events"] as? JsonArray ?: return@flatMap emptyList()).mapNotNull { parseEvent(it, l, favIds) }
        }.distinctBy { it.id }
        val upcoming = all.filter { it.state == "pre" && it.startTime > now - 3 * 3600_000 }.sortedBy { it.startTime }.take(6)
        val recent = all.filter { it.state == "post" }.sortedByDescending { it.startTime }.take(6)
        return upcoming to recent
    }

    private fun withStats(g: Game): Game {
        val cached = stats[g.id]
        if (cached != null && System.currentTimeMillis() - cached.first < 30_000) return g.copy(stats = cached.second)
        val l = LEAGUES.values.firstOrNull { it.abbr == g.league } ?: return g
        val root = get("$BASE/${l.path}/summary?event=${g.id}") ?: return g
        val teams = root["boxscore"]?.jsonObject?.get("teams") as? JsonArray ?: return g
        fun statsFor(side: String): Map<String, String> {
            val t = teams.map { it.jsonObject }.firstOrNull { it.str("homeAway") == side } ?: return emptyMap()
            return (t["statistics"] as? JsonArray)?.mapNotNull {
                val o = it.jsonObject
                val label = o.str("label") ?: o.str("name") ?: return@mapNotNull null
                label to (o.str("displayValue") ?: "")
            }?.toMap() ?: emptyMap()
        }
        val away = statsFor("away"); val home = statsFor("home")
        val wanted = PREFERRED_STATS.filter { it in away && it in home }.ifEmpty { away.keys.filter { it in home }.toList() }
        val rows = wanted.take(6).map { StatRow(it, away[it] ?: "", home[it] ?: "") }
        stats[g.id] = System.currentTimeMillis() to rows
        return g.copy(stats = rows)
    }

    /** Teams for the My Teams setup grid. */
    suspend fun teams(leagueKey: String): List<TeamRef> = withContext(Dispatchers.IO) {
        val l = LEAGUES[leagueKey] ?: return@withContext emptyList()
        val root = get("$BASE/${l.path}/teams?limit=1000") ?: return@withContext emptyList()
        val list = root["sports"]?.jsonArray?.firstOrNull()?.jsonObject?.get("leagues")?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("teams") as? JsonArray ?: return@withContext emptyList()
        list.mapNotNull {
            val t = it.jsonObject["team"]?.jsonObject ?: return@mapNotNull null
            TeamRef(
                league = leagueKey,
                id = t.str("id") ?: return@mapNotNull null,
                abbr = t.str("abbreviation") ?: "",
                name = t.str("displayName") ?: "",
                color = "#" + (t.str("color") ?: "555555"),
                logo = (t["logos"] as? JsonArray)?.firstOrNull()?.jsonObject?.str("href"),
            )
        }.sortedBy { it.name }
    }

    private fun parseEvent(el: JsonElement, l: League, favIds: Set<String>): Game? {
        val ev = el as? JsonObject ?: return null
        val comp = (ev["competitions"] as? JsonArray)?.firstOrNull()?.jsonObject ?: return null
        val competitors = (comp["competitors"] as? JsonArray)?.map { it.jsonObject } ?: return null
        val homeC = competitors.firstOrNull { it.str("homeAway") == "home" } ?: return null
        val awayC = competitors.firstOrNull { it.str("homeAway") == "away" } ?: return null
        val status = (comp["status"] ?: ev["status"])?.jsonObject
        val type = status?.get("type")?.jsonObject
        val state = type?.str("state") ?: "pre"
        val start = parseDate(ev.str("date") ?: comp.str("date"))
        val home = team(homeC); val away = team(awayC)
        val fav = "${l.key}:${home.id}" in favIds || "${l.key}:${away.id}" in favIds
        val network = (comp["broadcasts"] as? JsonArray)?.flatMap { b ->
            (b.jsonObject["names"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
        }?.firstOrNull()
            ?: (comp["geoBroadcasts"] as? JsonArray)?.firstOrNull()?.jsonObject?.get("media")?.jsonObject?.str("shortName")
            ?: ""
        val sit = comp["situation"] as? JsonObject
        var situation = ""; var highlight = ""
        var lastPlay = sit?.get("lastPlay")?.jsonObject?.str("text") ?: ""
        when (state) {
            "in" -> {
                val dd = sit?.str("downDistanceText") ?: sit?.str("shortDownDistanceText")
                if (dd != null && dd.contains(" at ")) {
                    situation = dd.substringBefore(" at "); highlight = dd.substringAfter(" at ")
                } else if (dd != null) situation = dd
                if (situation.isEmpty()) {
                    // Basketball / hockey / baseball: show the period + venue.
                    situation = type?.str("detail") ?: ""
                    highlight = comp["venue"]?.jsonObject?.str("fullName") ?: ""
                }
            }
            "post" -> {
                val hs = home.score.toDoubleOrNull() ?: 0.0; val asc = away.score.toDoubleOrNull() ?: 0.0
                val winner = if (hs >= asc) home else away
                situation = "${winner.abbr} Win"
                val favTeam = listOf(home, away).firstOrNull { "${l.key}:${it.id}" in favIds }
                highlight = if (favTeam == null || favTeam.id == winner.id) "win" else "loss"
                lastPlay = ""
            }
        }
        val detail = when (state) {
            "in" -> type?.str("shortDetail")?.let { prettyClock(it, status) } ?: ""
            "post" -> type?.str("shortDetail") ?: "Final"
            else -> if (start > 0) DATE_FMT.format(start) else (type?.str("shortDetail") ?: "")
        }
        return Game(
            id = ev.str("id") ?: return null,
            league = l.abbr,
            leagueName = l.name,
            state = state,
            away = away,
            home = home,
            detail = detail,
            situation = situation,
            highlight = highlight,
            startTime = start,
            network = network,
            lastPlay = lastPlay,
            favorite = fav,
        )
    }

    private fun prettyClock(shortDetail: String, status: JsonObject?): String {
        // ESPN gives "8:12 - 4th"; Hearth shows "4th · 8:12".
        val parts = shortDetail.split(" - ")
        return if (parts.size == 2) "${parts[1]} · ${parts[0]}" else shortDetail
    }

    private fun team(c: JsonObject): TeamScore {
        val t = c["team"]?.jsonObject
        val score = when (val s = c["score"]) {
            is JsonPrimitive -> s.contentOrNull ?: ""
            is JsonObject -> s.str("displayValue") ?: ""
            else -> ""
        }
        val logo = t?.str("logo") ?: (t?.get("logos") as? JsonArray)?.firstOrNull()?.jsonObject?.str("href")
        return TeamScore(
            abbr = t?.str("abbreviation") ?: "?",
            name = t?.str("displayName") ?: t?.str("shortDisplayName") ?: "",
            color = "#" + (t?.str("color") ?: "555555"),
            logo = logo,
            score = score,
            id = t?.str("id") ?: "",
        )
    }

    private fun parseDate(s: String?): Long {
        if (s == null) return 0
        for (f in listOf("yyyy-MM-dd'T'HH:mm'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'")) {
            try {
                return SimpleDateFormat(f, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(s)!!.time
            } catch (_: Exception) {}
        }
        return 0
    }

    companion object {
        const val BASE = "https://site.api.espn.com/apis/site/v2/sports"
        private val DATE_FMT = SimpleDateFormat("EEE, MMM d • h:mm a", Locale.US)
        val LEAGUES: Map<String, League> = listOf(
            League("nfl", "football/nfl", "NFL", "NFL"),
            League("college-football", "football/college-football", "NCAA", "College Football"),
            League("nba", "basketball/nba", "NBA", "NBA"),
            League("wnba", "basketball/wnba", "WNBA", "WNBA"),
            League("mens-college-basketball", "basketball/mens-college-basketball", "NCAAM", "College Basketball"),
            League("mlb", "baseball/mlb", "MLB", "MLB"),
            League("nhl", "hockey/nhl", "NHL", "NHL"),
            League("mls", "soccer/usa.1", "MLS", "MLS"),
            League("epl", "soccer/eng.1", "EPL", "Premier League"),
        ).associateBy { it.key }

        private val PREFERRED_STATS = listOf(
            "Total Yards", "Passing", "Rushing", "Turnovers", "Possession", "3rd down efficiency",
            "Field Goal %", "Three Point %", "Rebounds", "Assists", "Turnovers",
            "Shots", "Power Play Goals", "Hits", "Faceoffs Won",
            "Hits", "Errors", "Home Runs",
            "Possession", "Shots on Goal",
        ).distinct()

        /** Known streaming apps per network name. */
        val NETWORK_APPS: Map<String, List<String>> = mapOf(
            "peacock" to listOf("com.peacocktv.peacockandroid"),
            "nbc" to listOf("com.peacocktv.peacockandroid", "com.google.android.youtube.tvunplugged"),
            "cbs" to listOf("com.cbs.ott", "com.google.android.youtube.tvunplugged"),
            "paramount" to listOf("com.cbs.ott"),
            "prime" to listOf("com.amazon.amazonvideo.livingroom"),
            "espn" to listOf("com.espn.gtv", "com.espn.score_center", "com.google.android.youtube.tvunplugged"),
            "abc" to listOf("com.google.android.youtube.tvunplugged"),
            "fox" to listOf("com.google.android.youtube.tvunplugged", "com.foxsports.android"),
            "netflix" to listOf("com.netflix.ninja"),
            "apple" to listOf("com.apple.atve.androidtv.appletv"),
            "max" to listOf("com.wbd.stream"),
            "tnt" to listOf("com.wbd.stream", "com.google.android.youtube.tvunplugged"),
            "nfl" to listOf("com.gotv.nflgamecenter.us.lite"),
            "youtube" to listOf("com.google.android.youtube.tvunplugged", "com.google.android.youtube.tv"),
        )
    }
}

private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
