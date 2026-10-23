package com.amosley.hearth.shared.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.amosley.hearth.shared.R

/** Hearth design tokens, shared by the TV launcher and the phone remote. */
object Hearth {
    val Ink = Color(0xFF07080C)
    val Ink2 = Color(0xFF0D1018)
    val Paper = Color(0xFFF4F4F6)
    val Amber = Color(0xFFE0B35A)
    val Live = Color(0xFF3DDC84)
    val Alert = Color(0xFFFF4545)
    val Destructive = Color(0xFFFF8A8A)
    val Win = Color(0xFF1F7A3A)
    val Loss = Color(0xFF5A2A2A)
    val Glass = Color(0xA60E1016)        // rgba(14,16,22,.65)
    val GlassPhone = Color(0x990E1016)   // rgba(14,16,22,.6)
    val GlassBorder = Color(0x14FFFFFF)  // rgba(255,255,255,.08)
    val GlassBorderPhone = Color(0x1AFFFFFF)
    val RailGlass = Color(0x8C0A0B10)
    val KidsYellow = Color(0xFFFFD166)
    val KidsCoral = Color(0xFFFF7B6B)
    val KidsMint = Color(0xFF6BE3B5)

    fun paper(alpha: Float) = Paper.copy(alpha = alpha)
    fun white(alpha: Float) = Color.White.copy(alpha = alpha)

    val Figtree = FontFamily(
        Font(R.font.figtree_light, FontWeight.Light),
        Font(R.font.figtree_regular, FontWeight.Normal),
        Font(R.font.figtree_medium, FontWeight.Medium),
        Font(R.font.figtree_semibold, FontWeight.SemiBold),
        Font(R.font.figtree_bold, FontWeight.Bold),
    )
    val Newsreader = FontFamily(
        Font(R.font.newsreader_light, FontWeight.Light),
        Font(R.font.newsreader_regular, FontWeight.Normal),
    )

    /** Placeholder art colour, oklch(27% .035 H) approximated in HSL. */
    fun placeholder(hue: Float): Color = Color.hsl(hue % 360f, 0.28f, 0.17f)

    /** Backdrop top colour for a hue, oklch(30% .04 H) approximated. */
    fun backdrop(hue: Float): Color = Color.hsl(hue % 360f, 0.32f, 0.19f)
    fun backdropMid(hue: Float): Color = Color.hsl((hue + 20f) % 360f, 0.30f, 0.09f)

    /** Stable hue for any string (app package, title...). */
    fun hueFor(key: String): Float = ((key.hashCode() and 0x7fffffff) % 360).toFloat()

    /** Known brand colours for app tiles: background to foreground. */
    val brands: Map<String, Pair<Color, Color>> = mapOf(
        "netflix" to (Color(0xFF141414) to Color(0xFFE50914)),
        "youtube tv" to (Color(0xFF1C1C1E) to Paper),
        "youtube kids" to (Color(0xFFFF0000) to Color.White),
        "youtube" to (Paper to Color(0xFF1C1C1E)),
        "prime" to (Color(0xFF1A98FF) to Color.White),
        "peacock" to (Color(0xFF0A0A0A) to Color.White),
        "paramount" to (Color(0xFF0064FF) to Color.White),
        "disney" to (Color(0xFF0B1A48) to Color.White),
        "max" to (Color(0xFF002BE7) to Color.White),
        "hulu" to (Color(0xFF1CE783) to Color(0xFF0B0B0B)),
        "espn" to (Color(0xFFDD0000) to Color.White),
        "apple tv" to (Color(0xFF1C1C1E) to Color.White),
        "twitch" to (Color(0xFF9146FF) to Color.White),
        "plex" to (Color(0xFF1F1F1F) to Color(0xFFE5A00D)),
        "spotify" to (Color(0xFF121212) to Color(0xFF1DB954)),
    )

    fun brandFor(label: String): Pair<Color, Color>? {
        val l = label.lowercase()
        return brands.entries.firstOrNull { l.contains(it.key) }?.value
    }
}
