package com.amosley.hearth.tv.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

data class TvApp(
    val pkg: String,
    val label: String,
    val activity: String,
    val hasBanner: Boolean,
)

/** Every launchable app (TV banner apps and sideloaded phone apps), auto-detected. */
class AppsRepository(private val context: Context, private val scope: CoroutineScope) {
    private val pm = context.packageManager
    private val _apps = MutableStateFlow<List<TvApp>>(emptyList())
    val apps: StateFlow<List<TvApp>> = _apps.asStateFlow()

    private val bannerCache = LruCache<String, Bitmap>(64)
    private val iconPngCache = LruCache<String, ByteArray>(64)

    init {
        refresh()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                i.data?.schemeSpecificPart?.let { bannerCache.remove(it); iconPngCache.remove(it) }
                refresh()
            }
        }, filter)
    }

    fun refresh() = scope.launch(Dispatchers.IO) {
        val seen = LinkedHashMap<String, TvApp>()
        fun add(list: List<ResolveInfo>, leanback: Boolean) {
            for (ri in list) {
                val ai = ri.activityInfo ?: continue
                if (ai.packageName == context.packageName) continue
                if (seen.containsKey(ai.packageName)) continue
                seen[ai.packageName] = TvApp(
                    pkg = ai.packageName,
                    label = ri.loadLabel(pm).toString(),
                    activity = ai.name,
                    hasBanner = leanback && (ai.banner != 0 || ai.applicationInfo.banner != 0),
                )
            }
        }
        add(query(Intent.CATEGORY_LEANBACK_LAUNCHER), true)
        add(query(Intent.CATEGORY_LAUNCHER), false)
        _apps.value = seen.values.sortedBy { it.label.lowercase() }
    }

    @Suppress("DEPRECATION")
    private fun query(category: String): List<ResolveInfo> =
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)

    fun find(pkg: String): TvApp? = _apps.value.firstOrNull { it.pkg == pkg }

    fun labelFor(pkg: String): String = find(pkg)?.label ?: try {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) { pkg }

    fun launchIntent(pkg: String): Intent? =
        (pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg))
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

    fun launch(pkg: String): Boolean {
        val i = launchIntent(pkg) ?: return false
        return try { context.startActivity(i); true } catch (_: Exception) { false }
    }

    /** TV banner (16:9) if the app has one, else its icon. */
    fun banner(pkg: String): Bitmap? {
        bannerCache.get(pkg)?.let { return it }
        val d: Drawable = try {
            val ai = pm.getApplicationInfo(pkg, 0)
            val li = launchIntent(pkg)?.let { pm.resolveActivity(it, 0)?.activityInfo }
            li?.loadBanner(pm) ?: ai.loadBanner(pm) ?: li?.loadIcon(pm) ?: ai.loadIcon(pm)
        } catch (_: PackageManager.NameNotFoundException) { return null } ?: return null
        val bmp = d.toBitmap(320, 180)
        bannerCache.put(pkg, bmp)
        return bmp
    }

    fun isBanner(pkg: String): Boolean = find(pkg)?.hasBanner == true

    /** Square icon as PNG bytes, for the phone / iPad app grid. */
    fun iconPng(pkg: String): ByteArray? {
        iconPngCache.get(pkg)?.let { return it }
        val d = try { pm.getApplicationIcon(pkg) } catch (_: Exception) { return null }
        val out = ByteArrayOutputStream()
        d.toBitmap(192, 192).compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray().also { iconPngCache.put(pkg, it) }
    }

    fun bannerPng(pkg: String): ByteArray? {
        val b = banner(pkg) ?: return null
        val out = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    private fun Drawable.toBitmap(w: Int, h: Int): Bitmap {
        if (this is BitmapDrawable && bitmap != null && intrinsicWidth > 0) return bitmap
        val iw = if (intrinsicWidth > 0) intrinsicWidth else w
        val ih = if (intrinsicHeight > 0) intrinsicHeight else h
        val bmp = Bitmap.createBitmap(iw, ih, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        setBounds(0, 0, iw, ih)
        draw(c)
        return bmp
    }
}
