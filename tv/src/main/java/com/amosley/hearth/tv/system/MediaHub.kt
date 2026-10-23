package com.amosley.hearth.tv.system

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import com.amosley.hearth.shared.protocol.NowPlaying
import com.amosley.hearth.shared.protocol.Protocol
import com.amosley.hearth.tv.data.AppsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.ByteArrayOutputStream
import java.io.File

@Serializable
data class WatchEntry(
    val key: String,
    val pkg: String,
    val title: String,
    val subtitle: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val artFile: String? = null,
    /** Deep link from the TV provider, if the entry came from Watch Next. */
    val intentUri: String? = null,
    val artUri: String? = null,
)

/**
 * Follows the active media session (any app that publishes one) and keeps a
 * Continue Watching history of things paused part-way through.
 */
class MediaHub(private val context: Context, private val apps: AppsRepository) {
    private val main = Handler(Looper.getMainLooper())
    private val _now = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _now.asStateFlow()

    private val _history = MutableStateFlow(loadHistory())
    val history: StateFlow<List<WatchEntry>> = _history.asStateFlow()

    @Volatile var artwork: Bitmap? = null
        private set
    private var artPng: ByteArray? = null

    private var controller: MediaController? = null
    private var manager: MediaSessionManager? = null

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            publish()
            if (state?.state == PlaybackState.STATE_PAUSED || state?.state == PlaybackState.STATE_STOPPED) record()
        }
        override fun onSessionDestroyed() { record(); pick() }
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { pick() }

    fun attach() {
        main.post {
            try {
                val m = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
                manager = m
                val cn = ComponentName(context, MediaListener::class.java)
                m.removeOnActiveSessionsChangedListener(sessionsListener)
                m.addOnActiveSessionsChangedListener(sessionsListener, cn, main)
                pick()
            } catch (_: SecurityException) {
                // Notification access not granted yet.
            }
        }
    }

    private fun pick() {
        val m = manager ?: return
        val list = try { m.getActiveSessions(ComponentName(context, MediaListener::class.java)) } catch (_: Exception) { return }
        val best = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
        if (best?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = best
            best?.registerCallback(callback, main)
        }
        publish()
    }

    private fun publish() {
        val c = controller
        val md = c?.metadata
        if (c == null || md == null) { _now.value = null; artwork = null; artPng = null; return }
        val st = c.playbackState
        val title = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val sub = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: md.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
        val art = md.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (art !== artwork) {
            artwork = art
            artPng = art?.let { b -> ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray() }
        }
        val artUri = md.getString(MediaMetadata.METADATA_KEY_ART_URI) ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
        _now.value = NowPlaying(
            title = title,
            subtitle = sub,
            app = c.packageName,
            appLabel = apps.labelFor(c.packageName),
            artUrl = if (artPng != null) "/api/nowplaying/art?v=${title.hashCode()}" else artUri,
            positionMs = st?.position ?: 0,
            durationMs = md.getLong(MediaMetadata.METADATA_KEY_DURATION),
            playing = st?.state == PlaybackState.STATE_PLAYING,
            sampledAt = st?.lastPositionUpdateTime?.let { System.currentTimeMillis() - (android.os.SystemClock.elapsedRealtime() - it) }
                ?: System.currentTimeMillis(),
        )
    }

    fun artJpeg(): ByteArray? = artPng

    // ---- transport ----
    fun playPause(): Boolean {
        val c = controller ?: return false
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
        return true
    }

    fun seekBy(deltaMs: Long): Boolean {
        val c = controller ?: return false
        val st = c.playbackState ?: return false
        if (st.actions and PlaybackState.ACTION_SEEK_TO == 0L) return false
        val dur = c.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0
        val elapsed = if (st.state == PlaybackState.STATE_PLAYING)
            (android.os.SystemClock.elapsedRealtime() - st.lastPositionUpdateTime) * st.playbackSpeed else 0f
        var target = st.position + elapsed.toLong() + deltaMs
        if (dur > 0) target = target.coerceAtMost(dur - 1000)
        c.transportControls.seekTo(target.coerceAtLeast(0))
        return true
    }

    fun next() = controller?.transportControls?.skipToNext() != null
    fun previous() = controller?.transportControls?.skipToPrevious() != null

    // ---- Continue Watching history ----
    private val historyFile = File(context.filesDir, "continue-watching.json")
    private val artDir = File(context.filesDir, "cw-art").apply { mkdirs() }

    private fun loadHistory(): List<WatchEntry> = try {
        Protocol.json.decodeFromString(ListSerializer(WatchEntry.serializer()), File(context.filesDir, "continue-watching.json").readText())
    } catch (_: Exception) { emptyList() }

    private fun record() {
        val c = controller ?: return
        val md = c.metadata ?: return
        val now = _now.value ?: return
        val dur = md.getLong(MediaMetadata.METADATA_KEY_DURATION)
        val pos = c.playbackState?.position ?: now.positionMs
        if (now.title.isBlank()) return
        val key = "${c.packageName}|${now.title}"
        // Only long-form video/audio that's part-way through.
        if (dur < 5 * 60_000 || pos < 60_000) return
        val finished = pos > dur * 0.95
        val list = _history.value.filterNot { it.key == key }.toMutableList()
        if (!finished) {
            val artFile = artwork?.let { b ->
                File(artDir, "${key.hashCode()}.jpg").also { f -> f.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 85, it) } }.name
            }
            list.add(0, WatchEntry(key, c.packageName, now.title, now.subtitle, pos, dur, System.currentTimeMillis(), artFile))
        }
        val trimmed = list.take(20)
        _history.value = trimmed
        try { historyFile.writeText(Protocol.json.encodeToString(ListSerializer(WatchEntry.serializer()), trimmed)) } catch (_: Exception) {}
    }

    fun remove(key: String) {
        val list = _history.value.filterNot { it.key == key }
        _history.value = list
        try { historyFile.writeText(Protocol.json.encodeToString(ListSerializer(WatchEntry.serializer()), list)) } catch (_: Exception) {}
    }

    fun artFile(name: String): File = File(artDir, name)
}
