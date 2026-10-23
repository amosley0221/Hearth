package com.amosley.hearth.tv.system

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Which app is in front, from the accessibility service and Hearth's own lifecycle. */
class ForegroundTracker(private val hearthPkg: String) {
    private val _pkg = MutableStateFlow(hearthPkg)
    val pkg: StateFlow<String> = _pkg.asStateFlow()

    @Volatile var hearthResumed = false
        private set

    fun update(pkg: String) {
        // Ignore transient system overlays / keyboards.
        if (pkg.contains("inputmethod") || pkg.endsWith(".ime") || pkg == "com.google.android.katniss") return
        _pkg.value = pkg
    }

    fun hearthResumed(resumed: Boolean) {
        hearthResumed = resumed
        if (resumed) _pkg.value = hearthPkg
    }

    val isHearth get() = hearthResumed || _pkg.value == hearthPkg
}
