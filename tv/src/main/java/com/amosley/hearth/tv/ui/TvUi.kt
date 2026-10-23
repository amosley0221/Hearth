package com.amosley.hearth.tv.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester

data class MenuItem(val label: String, val destructive: Boolean = false, val action: () -> Unit)
data class TileMenu(val header: String, val items: List<MenuItem>)

/** create = choose a new PIN; check = verify before [onSuccess]. */
data class PinRequest(val title: String, val create: Boolean = false, val onSuccess: () -> Unit)

/** UI-only state for the TV launcher. */
class TvUi {
    var section by mutableStateOf("home")
    var settingsPage by mutableStateOf("main")
    var sportsTab by mutableStateOf("live")
    var hue by mutableFloatStateOf(35f)
    var art by mutableStateOf<Any?>(null)
    var toast by mutableStateOf<String?>(null)
    var toastSerial by mutableIntStateOf(0)
    var pairCode by mutableStateOf<Pair<String, String>?>(null)
    var tileMenu by mutableStateOf<TileMenu?>(null)
    var pin by mutableStateOf<PinRequest?>(null)
    var doorbell by mutableStateOf<String?>(null)
    var cameraFull by mutableStateOf<String?>(null)
    var inputsPicker by mutableStateOf(false)
    var profilePicker by mutableStateOf(false)
    var picker by mutableStateOf<TileMenu?>(null)
    var editingModeId by mutableStateOf<String?>(null)
    /** Text typed on the phone remote while Hearth is in front goes here. */
    var textTarget: ((String, String) -> Unit)? = null
    var homeSerial by mutableIntStateOf(0)

    val railRequesters = mutableMapOf<String, FocusRequester>()
    val contentRequester = FocusRequester()

    fun toast(text: String) { toast = text; toastSerial++ }

    fun backToHome() {
        tileMenu = null; picker = null; inputsPicker = false; cameraFull = null; profilePicker = false
        section = "home"; settingsPage = "main"; homeSerial++
    }

    val anyOverlay get() = tileMenu != null || picker != null || pin != null || inputsPicker || cameraFull != null ||
        profilePicker || pairCode != null || doorbell != null

    /** Back handling for overlays; returns true if something closed. */
    fun closeTopOverlay(): Boolean = when {
        picker != null -> { picker = null; true }
        tileMenu != null -> { tileMenu = null; true }
        cameraFull != null -> { cameraFull = null; true }
        inputsPicker -> { inputsPicker = false; true }
        doorbell != null -> { doorbell = null; true }
        pairCode != null -> { pairCode = null; true }
        pin != null -> { pin = null; true }
        profilePicker -> { profilePicker = false; true }
        else -> false
    }
}
