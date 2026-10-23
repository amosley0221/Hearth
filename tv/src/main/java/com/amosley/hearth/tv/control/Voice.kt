package com.amosley.hearth.tv.control

import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import com.amosley.hearth.shared.protocol.Keys
import com.amosley.hearth.shared.protocol.Protocol
import com.amosley.hearth.tv.HearthApp
import com.amosley.hearth.tv.UiEvent
import com.amosley.hearth.tv.sports.SportsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Voice commands from the phone, iPad or a Siri Shortcut: "play Breaking Bad", "open Netflix",
 * "Movie Night", "lights off", "volume 20", "put on the Eagles game"…
 *
 * "Play <title>" picks the app: Continue Watching first, then TMDB's streaming availability
 * (installed apps you subscribe to), then opens that app's search for the title. Without a TMDB key
 * it hands the query to Google TV's own search, which also picks an app.
 */
class Voice(private val app: HearthApp) {
    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()

    /** Runs a spoken command; returns what Hearth did, for the toast and the remote. */
    suspend fun handle(raw: String): String {
        val text = normalize(raw)
        if (text.isBlank()) return "I didn't catch that"
        val reply = try { route(text) } catch (e: Exception) { "Sorry, that didn't work (${e.message})" }
        app.toast(reply)
        return reply
    }

    private fun normalize(s: String) = s.lowercase()
        .replace(Regex("[^a-z0-9+&' ]"), " ")
        .replace(Regex("^(hey |ok |okay )?(hearth|tv)[, ]+"), "")
        .replace(Regex("\\b(please|can you|could you|i want to|i'd like to|let's)\\b"), " ")
        .replace(Regex("\\s+"), " ").trim()

    private suspend fun route(t: String): String {
        val cfg = app.config.value

        // ---- volume / transport / navigation ----
        Regex("^(?:set )?(?:the )?volume (?:to )?(.+)$").find(t)?.let { m ->
            val v = m.groupValues[1].trim()
            when {
                v.startsWith("up") -> { repeat(3) { app.input.key(Keys.VOL_UP) }; return "Volume up" }
                v.startsWith("down") -> { repeat(3) { app.input.key(Keys.VOL_DOWN) }; return "Volume down" }
                else -> number(v)?.let { n ->
                    val max = app.input.volumeLevel().second
                    val level = if (max != 100 && n > max) (n * max / 100) else n
                    app.input.setVolume(level); return "Volume $n"
                }
            }
        }
        when (t) {
            "louder", "turn it up", "volume up", "turn up the volume" -> { repeat(3) { app.input.key(Keys.VOL_UP) }; return "Volume up" }
            "quieter", "turn it down", "volume down", "turn down the volume" -> { repeat(3) { app.input.key(Keys.VOL_DOWN) }; return "Volume down" }
            "mute", "unmute", "be quiet" -> { app.input.key(Keys.MUTE); return "Mute" }
            "pause", "stop", "pause it", "resume", "play", "continue", "unpause", "keep playing" -> { app.input.key(Keys.PLAY_PAUSE); return if (t.startsWith("p") || t == "stop") "Paused" else "Playing" }
            "skip ahead", "skip forward", "fast forward", "skip" -> { app.input.key(Keys.SKIP_FORWARD); return "Skipped ahead" }
            "go back", "rewind", "back up", "skip back" -> { app.input.key(Keys.SKIP_BACK); return "Back 10 seconds" }
            "home", "go home", "go to home", "home screen" -> { app.input.goHome(); return "Home" }
            "turn off the tv", "tv off", "turn the tv off", "power off", "turn off" -> { app.input.key(Keys.POWER); return "Turning off the TV" }
        }

        // ---- modes and scenes ----
        val endMode = Regex("^(?:end|stop|turn off|cancel) (.+)$").find(t)?.groupValues?.get(1)
        for (m in cfg.modes) {
            val name = normalize(m.name)
            if (endMode != null && endMode.contains(name)) {
                if (app.actions.activeMode.value == m.id) app.actions.runMode(m.id)
                return "${m.name} ended"
            }
            if (t == name || t.matches(Regex("^(?:start|run|turn on|it's|its|time for|activate) (?:the )?$name(?: mode)?$"))) {
                if (app.actions.activeMode.value != m.id) app.actions.runMode(m.id)
                return "Running ${m.name}"
            }
        }
        for (s in cfg.home.scenes) {
            val name = normalize(app.ha[s]?.name ?: s.substringAfter('.').replace('_', ' '))
            if (t == name || t == "activate $name" || t == "run $name" || t == "$name scene") { app.ha.toggle(s); return app.ha[s]?.name ?: name }
        }

        // ---- lights ----
        if (Regex("\\blights?\\b").containsMatchIn(t) && !Regex("^(play|watch|put on)\\b").containsMatchIn(t)) {
            val lights = cfg.home.lights
            if (lights.isEmpty()) return "Add your lights in Settings first"
            Regex("(\\d+|[a-z]+)\\s*(?:percent|%)").find(t)?.let { m ->
                number(m.groupValues[1])?.let { pct ->
                    app.ha.call("light.turn_on", lights, buildJsonObject { put("brightness_pct", JsonPrimitive(pct.coerceIn(1, 100))) })
                    return "Lights to $pct%"
                }
            }
            return when {
                t.contains("dim") -> { app.ha.call("light.turn_on", lights, buildJsonObject { put("brightness_pct", JsonPrimitive(20)) }); "Dimming the lights" }
                t.contains("bright") || t.contains("full") -> { app.ha.call("light.turn_on", lights, buildJsonObject { put("brightness_pct", JsonPrimitive(100)) }); "Lights up" }
                Regex("\\boff\\b").containsMatchIn(t) -> { app.ha.call("light.turn_off", lights); "Lights off" }
                else -> { app.ha.call("light.turn_on", lights); "Lights on" }
            }
        }
        if (Regex("\\b(lock|unlock)\\b.*\\bdoor\\b|\\bdoor\\b.*\\b(lock|unlock)").containsMatchIn(t) && cfg.home.lock.isNotBlank()) {
            val unlock = t.contains("unlock")
            app.ha.call(if (unlock) "lock.unlock" else "lock.lock", listOf(cfg.home.lock))
            return if (unlock) "Unlocking the door" else "Locking the door"
        }

        // ---- consoles, inputs, profiles, sections ----
        Regex("^(?:switch|change|go) to (?:input )?hdmi ?(\\d)|^hdmi ?(\\d)$|^input (\\d)$").find(t)?.let { m ->
            val n = m.groupValues.drop(1).firstOrNull { it.isNotBlank() }
            val input = app.inputs.list().firstOrNull { it.label.replace(" ", "").contains("hdmi$n", true) || it.id.endsWith("HW$n") }
            if (input != null) { app.inputs.switchTo(input.id); return "Switching to ${input.label}" }
            return "I couldn't find HDMI $n"
        }
        for (c in cfg.consoles) {
            val names = listOf(normalize(c.name)) + when (c.kind) { "ps5" -> listOf("playstation", "ps5", "ps 5"); "xbox" -> listOf("xbox"); "switch" -> listOf("nintendo", "switch"); else -> emptyList() }
            if (names.any { n -> t.matches(Regex("^(?:switch to|turn on|wake(?: up)?|start|play|open|go to|put on)? ?(?:the |my )?$n$")) }) {
                app.actions.wakeConsole(c.id, null, waitForIt = false)
                return "Waking ${c.name}"
            }
        }
        if (Regex("\\b(kids? (mode|profile)|switch to kids)\\b").containsMatchIn(t)) { app.profiles.switchTo("kids"); return "Kids profile" }
        if (Regex("^(show|open|go to) (me )?(the )?(scores|sports|my teams)$").matches(t)) { app.input.goHome(); app.emit(UiEvent.OpenSection("sports")); return "Sports" }
        if (Regex("^(show|open|go to) (me )?(the )?(games|consoles)$").matches(t)) { app.input.goHome(); app.emit(UiEvent.OpenSection("games")); return "Games" }
        if (Regex("^(show|open|go to) (me )?(the )?(library|apps|all apps)$").matches(t)) { app.input.goHome(); app.emit(UiEvent.OpenSection("library")); return "Library" }
        if (Regex("^(show|open|go to) (me )?(the )?(home control|house|smart home)$").matches(t)) { app.input.goHome(); app.emit(UiEvent.OpenSection("smart")); return "Home control" }
        if (Regex("^(show|open) (me )?(the )?(camera|front door|doorbell)$").matches(t) && cfg.home.camera.isNotBlank()) { app.doorbell.ring(); return "Front door camera" }

        // ---- sports: "put on the Eagles game", "watch the Lakers" ----
        val sportsHit = findGame(t)
        if (sportsHit != null) {
            val pkg = SportsRepository.appFor(app, sportsHit) ?: return "I don't know which app has ${sportsHit.away.abbr} vs ${sportsHit.home.abbr}"
            app.apps.launch(pkg)
            return "${sportsHit.away.abbr} vs ${sportsHit.home.abbr} · opening ${app.apps.labelFor(pkg)}"
        }

        // ---- apps: "open Netflix" ----
        Regex("^(?:open|launch|start|go to|switch to) (?:the )?(.+?)(?: app)?$").find(t)?.let { m ->
            val pkg = findApp(m.groupValues[1])
            if (pkg != null) { app.apps.launch(pkg); return "Opening ${app.apps.labelFor(pkg)}" }
        }
        findApp(t)?.takeIf { t.split(" ").size <= 3 }?.let { pkg -> app.apps.launch(pkg); return "Opening ${app.apps.labelFor(pkg)}" }

        // ---- content: "play Breaking Bad (on Netflix)" ----
        val m = Regex("^(?:play|watch|put on|find|search for|search|show me|show|i want to watch|start watching)?\\s*(?:the (?:show|movie|series) )?(.+?)(?: on (.+))?$").find(t)
        val onPart = m?.groupValues?.getOrNull(2)?.takeIf { it.isNotBlank() }
        val onApp = onPart?.let { findApp(it) }
        // "Murder on the Orient Express": only split off " on X" when X is an app.
        var title = m?.groupValues?.get(1)?.trim().orEmpty().ifBlank { t }
        if (onPart != null && onApp == null) title = "$title on $onPart"
        return playTitle(title, onApp)
    }

    // ------------------------------------------------------------------ content

    private suspend fun playTitle(title: String, forcedPkg: String?): String {
        // 1. Something already in Continue Watching resumes where you left off.
        if (forcedPkg == null) {
            app.continueWatching.items.value.firstOrNull { normalize(it.title).contains(title) || title.contains(normalize(it.title)) }?.let { item ->
                app.continueWatching.open(item) { app.apps.launch(it) }
                return "Resuming ${item.title}"
            }
        }
        // 2. Which installed app has it (TMDB), or the app you named.
        val found = if (forcedPkg == null) lookup(title) else null
        val pkg = forcedPkg ?: found?.second
        val niceTitle = found?.first ?: title.split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        if (pkg != null) {
            val label = app.apps.labelFor(pkg)
            if (openSearchIn(pkg, niceTitle)) return "$niceTitle · opening in $label"
            app.apps.launch(pkg)
            return "$niceTitle is on $label · search for it there"
        }
        // 3. Google TV's own search picks the app.
        if (globalSearch(niceTitle)) return "Searching for $niceTitle"
        return if (app.config.value.voice.tmdbKey.isBlank()) "Add a TMDB key in Settings so I can find which app has $niceTitle"
        else "I couldn't find $niceTitle in your apps"
    }

    /** TMDB: best match for the title + the first installed app that streams it. */
    private suspend fun lookup(title: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        val v = app.config.value.voice
        if (v.tmdbKey.isBlank()) return@withContext null
        val search = tmdb("search/multi?include_adult=false&query=${enc(title)}") ?: return@withContext null
        val hit = (search["results"] as? JsonArray)?.map { it.jsonObject }
            ?.firstOrNull { it.str("media_type") == "tv" || it.str("media_type") == "movie" } ?: return@withContext null
        val type = hit.str("media_type")!!
        val id = hit["id"]?.jsonPrimitive?.intOrNull ?: return@withContext null
        val name = hit.str("name") ?: hit.str("title") ?: title
        val prov = tmdb("$type/$id/watch/providers")?.get("results")?.jsonObject?.get(v.region.uppercase())?.jsonObject
            ?: return@withContext null
        val names = listOf("flatrate", "free", "ads", "rent", "buy").flatMap { k ->
            (prov[k] as? JsonArray)?.mapNotNull { it.jsonObject.str("provider_name") } ?: emptyList()
        }
        val installed = app.apps.apps.value.map { it.pkg }.toSet()
        val candidates = names.flatMap { providerPackages(it) }.distinct().filter { it in installed }
        val preferred = v.preferredApps.filter { it in candidates }
        val pick = preferred.firstOrNull() ?: candidates.firstOrNull()
        if (pick == null) null else name to pick
    }

    private fun tmdb(path: String): JsonObject? {
        val key = app.config.value.voice.tmdbKey.trim()
        val v4 = key.length > 40
        val url = "https://api.themoviedb.org/3/$path" + if (v4) "" else "${if (path.contains('?')) "&" else "?"}api_key=$key"
        val req = Request.Builder().url(url).apply { if (v4) header("Authorization", "Bearer $key") }.build()
        return http.newCall(req).execute().use { r -> if (!r.isSuccessful) null else Protocol.json.parseToJsonElement(r.body!!.string()).jsonObject }
    }

    /** TMDB provider names → Android TV packages; unknown names fall back to matching installed app labels. */
    private fun providerPackages(provider: String): List<String> {
        val p = provider.lowercase()
        val known = PROVIDERS.entries.filter { p.contains(it.key) }.flatMap { it.value }
        val byLabel = findApp(provider.replace(Regex("(?i)\\b(with ads|basic|standard|premium|plus|channel|amazon channel|apple tv channel)\\b"), "").trim())
        return known + listOfNotNull(byLabel)
    }

    /** Opens an app straight to its search for the title, where the app supports it. */
    private fun openSearchIn(pkg: String, title: String): Boolean {
        val q = enc(title)
        val attempts = buildList {
            SEARCH_URLS[pkg]?.let { add(Intent(Intent.ACTION_VIEW, Uri.parse(it + q)).setPackage(pkg)) }
            add(Intent(Intent.ACTION_SEARCH).setPackage(pkg).putExtra(SearchManager.QUERY, title))
            add(Intent("com.google.android.gms.actions.SEARCH_ACTION").setPackage(pkg).putExtra(SearchManager.QUERY, title))
        }
        for (i in attempts) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (app.packageManager.queryIntentActivities(i, 0).isEmpty()) continue
            try { app.startActivity(i); return true } catch (_: Exception) {}
        }
        return false
    }

    /** Google TV / Android TV system search with the query filled in. */
    private fun globalSearch(title: String): Boolean {
        val attempts = listOf(
            Intent(SearchManager.INTENT_ACTION_GLOBAL_SEARCH).putExtra(SearchManager.QUERY, title),
            Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.katniss").putExtra(SearchManager.QUERY, title),
            Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, title),
        )
        for (i in attempts) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (app.packageManager.queryIntentActivities(i, 0).isEmpty()) continue
            try { app.startActivity(i); return true } catch (_: Exception) {}
        }
        return false
    }

    // ------------------------------------------------------------------ helpers

    private fun findGame(t: String): com.amosley.hearth.shared.protocol.Game? {
        val wantsGame = Regex("\\b(game|match|watch|put on|play)\\b").containsMatchIn(t)
        if (!wantsGame) return null
        val data = app.sports.data.value
        val games = data.live + data.today.filter { it.state != "post" }
        fun hits(team: com.amosley.hearth.shared.protocol.TeamScore): Boolean {
            val words = team.name.lowercase().split(" ")
            val nick = words.lastOrNull() ?: return false
            return Regex("\\b${Regex.escape(nick)}\\b").containsMatchIn(t) || Regex("\\b${Regex.escape(team.abbr.lowercase())} game\\b").containsMatchIn(t) ||
                (words.size > 1 && t.contains(team.name.lowercase()))
        }
        return games.firstOrNull { hits(it.home) || hits(it.away) }
    }

    /** Fuzzy match a spoken app name to an installed app. */
    fun findApp(spoken: String): String? {
        val s = squash(ALIASES[spoken.trim()] ?: spoken)
        if (s.length < 2) return null
        val apps = app.apps.apps.value
        return apps.firstOrNull { squash(it.label) == s }?.pkg
            ?: apps.firstOrNull { squash(it.label).startsWith(s) }?.pkg
            ?: apps.firstOrNull { s.length >= 4 && squash(it.label).contains(s) }?.pkg
            ?: apps.firstOrNull { val l = squash(it.label); l.length >= 4 && s.contains(l) }?.pkg
    }

    private fun squash(s: String) = s.lowercase().replace("+", "plus").replace("&", "and").replace(Regex("[^a-z0-9]"), "")

    private fun number(s: String): Int? {
        s.trim().toIntOrNull()?.let { return it }
        var total = 0; var any = false
        for (w in s.split(" ", "-")) {
            val v = WORDS[w] ?: continue
            any = true
            total = if (v == 100) (if (total == 0) 100 else total * 100) else total + v
        }
        return if (any) total else null
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull

    companion object {
        private val WORDS = mapOf("zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7,
            "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14, "fifteen" to 15,
            "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19, "twenty" to 20, "thirty" to 30, "forty" to 40,
            "fifty" to 50, "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90, "hundred" to 100, "max" to 100, "half" to 50)

        private val ALIASES = mapOf("prime" to "prime video", "amazon" to "prime video", "hbo" to "max", "hbo max" to "max",
            "disney" to "disney+", "paramount" to "paramount+", "you tube" to "youtube", "youtube tv" to "youtube tv",
            "apple" to "apple tv", "the tv app" to "apple tv")

        val PROVIDERS: Map<String, List<String>> = mapOf(
            "netflix" to listOf("com.netflix.ninja"),
            "hulu" to listOf("com.hulu.livingroomplus"),
            "disney" to listOf("com.disney.disneyplus"),
            "max" to listOf("com.wbd.stream", "com.hbo.hbonow"),
            "hbo" to listOf("com.wbd.stream", "com.hbo.hbonow"),
            "peacock" to listOf("com.peacocktv.peacockandroid"),
            "paramount" to listOf("com.cbs.ott"),
            "amazon" to listOf("com.amazon.amazonvideo.livingroom"),
            "apple tv" to listOf("com.apple.atve.androidtv.appletv"),
            "youtube tv" to listOf("com.google.android.youtube.tvunplugged"),
            "youtube" to listOf("com.google.android.youtube.tv"),
            "tubi" to listOf("com.tubitv"),
            "pluto" to listOf("tv.pluto.android"),
            "plex" to listOf("com.plexapp.android"),
            "crunchyroll" to listOf("com.crunchyroll.crunchyroid"),
            "starz" to listOf("com.bydeluxe.d3.android.program.starz"),
            "espn" to listOf("com.espn.gtv"),
        )

        /** Web search URLs these apps' Android TV builds accept as deep links. */
        private val SEARCH_URLS = mapOf(
            "com.google.android.youtube.tv" to "https://www.youtube.com/results?search_query=",
            "com.netflix.ninja" to "https://www.netflix.com/search?q=",
            "com.amazon.amazonvideo.livingroom" to "https://app.primevideo.com/search?phrase=",
            "com.disney.disneyplus" to "https://www.disneyplus.com/search?q=",
            "com.hulu.livingroomplus" to "https://www.hulu.com/search?q=",
            "com.wbd.stream" to "https://play.max.com/search?q=",
            "com.peacocktv.peacockandroid" to "https://www.peacocktv.com/search?q=",
            "com.cbs.ott" to "https://www.paramountplus.com/search/?q=",
            "com.plexapp.android" to "plex://search?query=",
        )
    }
}
