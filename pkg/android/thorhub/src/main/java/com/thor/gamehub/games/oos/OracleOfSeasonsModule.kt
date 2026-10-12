package com.thor.gamehub.games.oos

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thor.gamehub.core.GameModule
import com.thor.gamehub.net.MemorySource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * STUB — this is the one game where you already have working addresses and
 * a tested second screen in your existing standalone project. I don't have
 * that project's source in this container, so I can't port the real logic
 * over myself tonight.
 *
 * To finish this module: copy the WRAM address constants and the map/
 * inventory read logic from your existing OoS companion's poller into a
 * `start()` implementation here (same shape as FomtModule.start()), and
 * swap this placeholder Screen() for that project's actual Composable(s).
 * The RetroArchClient/GameModule/GameRegistry plumbing around it needs no
 * changes to accept the real implementation.
 */
class OracleOfSeasonsModule : GameModule {
    override val displayName = "Oracle of Seasons"
    override val contentNameMatchers = listOf(
        "Oracle of Seasons", "Zelda - Oracle of Seasons"
    )

    private val _connected = MutableStateFlow(false)
    override val connected: StateFlow<Boolean> = _connected.asStateFlow()

    override fun start(client: MemorySource, scope: CoroutineScope) {
        // TODO: port real WRAM reads (map/inventory) from your existing
        // standalone OoS companion project here.
        _connected.value = false
    }

    @Composable
    override fun Screen() {
        val connected by connected.collectAsState()
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("Oracle of Seasons", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(if (connected) "● connected" else "○ stub — not wired up yet")
            Spacer(Modifier.height(12.dp))
            Text(
                "Port your existing OoS companion's address table and map/" +
                    "inventory UI in here — see the comment at the top of " +
                    "OracleOfSeasonsModule.kt.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
