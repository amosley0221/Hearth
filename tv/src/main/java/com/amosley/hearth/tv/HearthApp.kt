package com.amosley.hearth.tv

import android.app.Application
import com.amosley.hearth.tv.control.Actions
import com.amosley.hearth.tv.control.Profiles
import com.amosley.hearth.tv.control.TvUpdater
import com.amosley.hearth.tv.data.AppsRepository
import com.amosley.hearth.tv.data.ConfigStore
import com.amosley.hearth.tv.data.ContinueWatching
import com.amosley.hearth.tv.ha.HomeAssistant
import com.amosley.hearth.tv.server.RemoteServer
import com.amosley.hearth.tv.sports.SportsRepository
import com.amosley.hearth.tv.state.StateHub
import com.amosley.hearth.tv.system.AdbBridge
import com.amosley.hearth.tv.system.DoorbellOverlay
import com.amosley.hearth.tv.system.ForegroundTracker
import com.amosley.hearth.tv.system.InputController
import com.amosley.hearth.tv.system.MediaHub
import com.amosley.hearth.tv.system.TvInputs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Things the TV UI reacts to, raised from services, the remote server and the engine. */
sealed interface UiEvent {
    data class Toast(val text: String) : UiEvent
    data class PairCode(val code: String, val name: String) : UiEvent
    data object PairDone : UiEvent
    data class OpenSection(val section: String) : UiEvent
    data class Text(val text: String, val mode: String) : UiEvent
    data class Doorbell(val camera: String) : UiEvent
    data object DismissDoorbell : UiEvent
    data object ShowInputs : UiEvent
    data object RequestPin : UiEvent
}

class HearthApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var configStore: ConfigStore; private set
    val config get() = configStore.config
    lateinit var apps: AppsRepository; private set
    lateinit var ha: HomeAssistant; private set
    lateinit var sports: SportsRepository; private set
    lateinit var adb: AdbBridge; private set
    lateinit var foreground: ForegroundTracker; private set
    lateinit var media: MediaHub; private set
    lateinit var continueWatching: ContinueWatching; private set
    lateinit var inputs: TvInputs; private set
    lateinit var input: InputController; private set
    lateinit var profiles: Profiles; private set
    lateinit var actions: Actions; private set
    lateinit var updater: TvUpdater; private set
    lateinit var state: StateHub; private set
    lateinit var server: RemoteServer; private set
    lateinit var doorbell: DoorbellOverlay; private set

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<UiEvent> = _events
    fun emit(e: UiEvent) { _events.tryEmit(e) }
    fun toast(text: String) = emit(UiEvent.Toast(text))

    override fun onCreate() {
        super.onCreate()
        instance = this
        configStore = ConfigStore(this)
        apps = AppsRepository(this, scope)
        ha = HomeAssistant(configStore, scope)
        sports = SportsRepository(configStore, scope)
        adb = AdbBridge(this)
        foreground = ForegroundTracker(packageName)
        media = MediaHub(this, apps)
        continueWatching = ContinueWatching(this, media, configStore, scope)
        inputs = TvInputs(this)
        input = InputController(this)
        profiles = Profiles(this)
        actions = Actions(this)
        updater = TvUpdater(this)
        state = StateHub(this)
        server = RemoteServer(this)
        doorbell = DoorbellOverlay(this)

        ha.start()
        sports.start()
        media.attach()
        profiles.start()
        state.start()
        server.startServer()
        doorbell.start()
        updater.start()
        input.start()
    }

    companion object {
        lateinit var instance: HearthApp; private set
        fun get() = instance
    }
}
