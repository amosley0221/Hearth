package com.amosley.hearth.tv.system

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import kotlinx.serialization.Serializable

@Serializable
data class TvInput(val id: String, val label: String, val type: Int)

/** HDMI inputs, so console tiles and Modes can switch the TV straight to an input. */
class TvInputs(private val context: Context) {
    fun list(): List<TvInput> = try {
        val tim = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager ?: return emptyList()
        tim.tvInputList.filter { it.isPassthroughInput || it.type == TvInputInfo.TYPE_HDMI }.map {
            val custom = try { it.loadCustomLabel(context)?.toString() } catch (_: Exception) { null }
            TvInput(it.id, custom?.takeIf { l -> l.isNotBlank() } ?: it.loadLabel(context).toString(), it.type)
        }
    } catch (_: Exception) { emptyList() }

    fun switchTo(inputId: String): Boolean {
        if (inputId.isBlank()) return false
        return try {
            val uri = TvContract.buildChannelUriForPassthroughInput(inputId)
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: Exception) { false }
    }

    fun label(inputId: String): String = list().firstOrNull { it.id == inputId }?.label ?: ""
}
