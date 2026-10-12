package com.thor.gamehub.ui
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thor.gamehub.core.GameModule
import com.thor.gamehub.core.GameRegistry
import com.thor.gamehub.net.MemorySource
import kotlinx.coroutines.cancel

class HubViewModel(val client: MemorySource) : ViewModel() {
    val registry = GameRegistry(client)
    val activeModule = registry.activeModule
    val lastContentName = registry.lastContentName
    init { registry.startWatching(viewModelScope) }
    fun forceModule(module: GameModule?) = registry.forceModule(module)
    override fun onCleared() {
        viewModelScope.cancel()
        registry.modules.forEach { it.stop() }
        client.close()
        super.onCleared()
    }
}
