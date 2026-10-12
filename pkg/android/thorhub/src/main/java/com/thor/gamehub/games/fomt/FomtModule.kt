package com.thor.gamehub.games.fomt

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thor.gamehub.core.GameModule
import com.thor.gamehub.net.MemorySource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Same address table and read logic as the standalone FomtCompanion scaffold
 * from earlier tonight — see that project's MemoryAddresses.kt comments for
 * the full verification-status caveats. Copied here rather than shared as a
 * library module to keep this scaffold to a single Gradle module; worth
 * splitting into a real `:core` + `:game-fomt` module setup once more than
 * one game module actually has verified addresses and the duplication starts
 * to hurt.
 */
class FomtModule : GameModule {
    override val displayName = "Friends of Mineral Town"
    override val contentNameMatchers = listOf(
        "Harvest Moon", "Friends of Mineral Town", "Bokujou Monogatari"
    )

    private data class State(
        val connected: Boolean = false,
        val gold: Long? = null,
        val year: Int? = null
    )

    private val _connected = MutableStateFlow(false)
    override val connected: StateFlow<Boolean> = _connected.asStateFlow()
    private val _state = MutableStateFlow(State())

    companion object {
        const val EWRAM_BASE = 0x02000000L
        // Same unverified candidate offsets as the standalone build.
        const val GOLD_OFFSET = 0x4090L
        const val YEAR_OFFSET = 0x4E1CL
    }

    override fun start(client: MemorySource, scope: CoroutineScope) {
        scope.launch {
            client.frames().collect {
                val gold = client.readU32(EWRAM_BASE + GOLD_OFFSET)
                val year = client.readU8(EWRAM_BASE + YEAR_OFFSET)
                val ok = gold != null || year != null
                _connected.value = ok
                _state.value = State(connected = ok, gold = gold, year = year?.plus(1))
            }
        }
    }

    @Composable
    override fun Screen() {
        val s by _state.collectAsState()
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("Friends of Mineral Town", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(if (s.connected) "● connected" else "○ no signal")
            Spacer(Modifier.height(12.dp))
            Text("Gold: ${s.gold ?: "--"}")
            Text("Year: ${s.year ?: "--"}")
            Spacer(Modifier.height(12.dp))
            Text(
                "Addresses are unverified — see the standalone FomtCompanion " +
                    "project's MemoryAddresses.kt for the full villager table " +
                    "and verification checklist; this module currently only " +
                    "ports gold/year over as a smoke test.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
