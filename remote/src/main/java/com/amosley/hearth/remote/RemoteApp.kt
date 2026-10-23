package com.amosley.hearth.remote

import android.app.Application
import com.amosley.hearth.remote.atv.AtvRemote
import com.amosley.hearth.remote.net.TvConnection
import com.amosley.hearth.shared.update.GitHubUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RemoteApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var atv: AtvRemote; private set
    lateinit var tv: TvConnection; private set
    lateinit var updater: GitHubUpdater; private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        atv = AtvRemote(this, scope)
        tv = TvConnection(this, scope, atv)
        updater = GitHubUpdater(this, BuildConfig.GITHUB_REPO, "hearth-remote", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toLong())
        tv.start()
        scope.launch { updater.check() }
    }

    companion object {
        lateinit var instance: RemoteApp; private set
        fun get() = instance
    }
}
