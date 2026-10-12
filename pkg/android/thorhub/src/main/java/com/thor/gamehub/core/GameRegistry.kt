package com.thor.gamehub.core

import com.thor.gamehub.games.fe8.FireEmblemSacredStonesModule
import com.thor.gamehub.games.firered.FireRedModule
import com.thor.gamehub.games.fomt.FomtModule
import com.thor.gamehub.games.fusion.MetroidFusionModule
import com.thor.gamehub.games.ooa.OracleOfAgesModule
import com.thor.gamehub.games.oos.OracleOfSeasonsModule
import com.thor.gamehub.net.MemorySource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the list of available game modules, watches RetroArch's GET_STATUS
 * to figure out which one should be active, and starts/stops modules as the
 * loaded content changes (e.g. you close FoMT and load Oracle of Seasons
 * without restarting the companion app).
 */
class GameRegistry(private val client: MemorySource) {

    val modules: List<GameModule> = listOf(
        OracleOfSeasonsModule(),
        OracleOfAgesModule(),
        FomtModule(),
        FireRedModule(),
        FireEmblemSacredStonesModule(),
        MetroidFusionModule()
    )

    private val _activeModule = MutableStateFlow<GameModule?>(null)
    val activeModule: StateFlow<GameModule?> = _activeModule.asStateFlow()

    private val _lastContentName = MutableStateFlow<String?>(null)
    val lastContentName: StateFlow<String?> = _lastContentName.asStateFlow()

    private var manualOverride: GameModule? = null
    private var moduleJob: Job? = null

    /** Lets the user force a module regardless of detection — useful while
     *  testing a module before its content-name matchers are dialed in. */
    fun forceModule(module: GameModule?) {
        manualOverride = module
    }

    fun startWatching(scope: CoroutineScope, pollIntervalMs: Long = 2000) {
        scope.launch {
            client.frames().collect {
                detectAndActivate(scope)
            }
        }
    }

    private suspend fun detectAndActivate(scope: CoroutineScope) {
        val contentName = withContext(Dispatchers.IO) { client.getLoadedContentName() }
        _lastContentName.value = contentName

        val target = manualOverride ?: contentName?.let { name ->
            modules.firstOrNull { module ->
                module.contentNameMatchers.any { matcher ->
                    name.contains(matcher, ignoreCase = true)
                }
            }
        }

        if (target !== _activeModule.value) {
            moduleJob?.cancel()
            _activeModule.value?.stop()
            target?.let {
                val job = SupervisorJob(scope.coroutineContext[Job])
                moduleJob = job
                it.start(client, CoroutineScope(scope.coroutineContext + job))
            }
            _activeModule.value = target
        }
    }
}
