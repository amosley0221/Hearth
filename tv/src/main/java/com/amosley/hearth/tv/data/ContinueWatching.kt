package com.amosley.hearth.tv.data

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import com.amosley.hearth.tv.system.MediaHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class ContinueItem(
    val key: String,
    val title: String,
    val subtitle: String,
    val progress: Float,
    val pkg: String,
    /** Coil model: File, String URL / content URI, or null for placeholder art. */
    val art: Any?,
    val intentUri: String? = null,
    val source: String,
    val updatedAt: Long = 0,
)

/**
 * Continue Watching = Media Library app rows (its content provider), Google TV "Watch Next"
 * (when readable), and Hearth's own history of things paused part-way through.
 */
class ContinueWatching(
    private val context: Context,
    private val media: MediaHub,
    private val config: ConfigStore,
    private val scope: CoroutineScope,
) {
    private val _external = MutableStateFlow<List<ContinueItem>>(emptyList())
    private val _items = MutableStateFlow<List<ContinueItem>>(emptyList())
    val items: StateFlow<List<ContinueItem>> = _items.asStateFlow()

    private val _recentlyAdded = MutableStateFlow<List<ContinueItem>>(emptyList())
    val recentlyAdded: StateFlow<List<ContinueItem>> = _recentlyAdded.asStateFlow()

    init {
        scope.launch {
            combine(media.history, _external, config.config) { h, ext, cfg ->
                val own = h.map {
                    ContinueItem(
                        key = it.key, title = it.title, subtitle = it.subtitle.ifBlank { remaining(it.positionMs, it.durationMs) },
                        progress = if (it.durationMs > 0) it.positionMs.toFloat() / it.durationMs else 0f,
                        pkg = it.pkg, art = it.artFile?.let { f -> media.artFile(f) }, source = "hearth", updatedAt = it.updatedAt,
                    )
                }
                (ext + own).distinctBy { it.title.lowercase() }
                    .filterNot { it.key in cfg.hiddenContinue }
                    .sortedByDescending { it.updatedAt }
                    .take(12)
            }.collect { _items.value = it }
        }
        scope.launch(Dispatchers.IO) {
            while (true) {
                refreshExternal()
                delay(60_000)
            }
        }
    }

    fun refresh() = scope.launch(Dispatchers.IO) { refreshExternal() }

    private fun refreshExternal() {
        _external.value = mediaLibrary("continue_watching") + watchNext()
        _recentlyAdded.value = mediaLibrary("recently_added")
    }

    private fun remaining(pos: Long, dur: Long): String {
        if (dur <= 0) return ""
        val left = (dur - pos) / 60_000
        return if (left >= 60) "${left / 60}h ${left % 60}m left" else "${left}m left"
    }

    /**
     * Media Library contract: content://<authority>/continue_watching and /recently_added with
     * columns title, subtitle, progress (0..1), art_uri, intent_uri, updated_at.
     */
    private fun mediaLibrary(path: String): List<ContinueItem> {
        val out = ArrayList<ContinueItem>()
        for (authority in MEDIA_LIBRARY_AUTHORITIES) {
            try {
                context.contentResolver.query(Uri.parse("content://$authority/$path"), null, null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        val title = c.str("title") ?: continue
                        out += ContinueItem(
                            key = "lib|$title", title = title, subtitle = c.str("subtitle") ?: "",
                            progress = c.dbl("progress")?.toFloat() ?: 0f,
                            pkg = c.str("package") ?: config.value.mediaLibraryPkg,
                            art = c.str("art_uri"), intentUri = c.str("intent_uri"), source = "library",
                            updatedAt = c.lng("updated_at") ?: System.currentTimeMillis(),
                        )
                    }
                }
            } catch (_: Exception) {}
        }
        return out
    }

    /** Google TV Watch Next. Readable on some builds once READ_EPG_DATA is granted over ADB. */
    private fun watchNext(): List<ContinueItem> = try {
        val uri = Uri.parse("content://android.media.tv/watch_next_program")
        context.contentResolver.query(uri, null, null, null, "last_engagement_time_utc_millis DESC")?.use { c ->
            val list = ArrayList<ContinueItem>()
            while (c.moveToNext() && list.size < 12) {
                val title = c.str("title") ?: continue
                val pos = c.lng("last_playback_position_millis") ?: 0
                val dur = c.lng("duration_millis") ?: 0
                val season = c.str("season_display_number"); val ep = c.str("episode_display_number")
                val sub = listOfNotNull(
                    if (season != null && ep != null) "S$season E$ep" else null,
                    remaining(pos, dur).ifBlank { null },
                ).joinToString(" · ")
                list += ContinueItem(
                    key = "wn|${c.str("package_name")}|$title", title = title, subtitle = sub,
                    progress = if (dur > 0) pos.toFloat() / dur else 0f,
                    pkg = c.str("package_name") ?: "", art = c.str("poster_art_uri") ?: c.str("thumbnail_uri"),
                    intentUri = c.str("intent_uri"), source = "watchnext",
                    updatedAt = c.lng("last_engagement_time_utc_millis") ?: 0,
                )
            }
            list
        } ?: emptyList()
    } catch (_: Exception) { emptyList() }

    fun open(item: ContinueItem, launchApp: (String) -> Boolean): Boolean {
        item.intentUri?.let {
            try {
                context.startActivity(Intent.parseUri(it, Intent.URI_INTENT_SCHEME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: Exception) {}
        }
        return launchApp(item.pkg)
    }

    private fun Cursor.str(col: String) = getColumnIndex(col).takeIf { it >= 0 }?.let { if (isNull(it)) null else getString(it) }
    private fun Cursor.lng(col: String) = getColumnIndex(col).takeIf { it >= 0 }?.let { if (isNull(it)) null else getLong(it) }
    private fun Cursor.dbl(col: String) = getColumnIndex(col).takeIf { it >= 0 }?.let { if (isNull(it)) null else getDouble(it) }

    companion object {
        val MEDIA_LIBRARY_AUTHORITIES = listOf("com.hearth.medialibrary", "com.amosley.medialibrary")
    }
}
