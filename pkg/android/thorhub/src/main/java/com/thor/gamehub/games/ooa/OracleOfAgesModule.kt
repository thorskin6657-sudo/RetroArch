package com.thor.gamehub.games.ooa

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
 * STUB, but a much shorter path to "real" than any other module here.
 *
 * Oracle of Ages and Oracle of Seasons are NOT two independently
 * reverse-engineered games - they're two builds of one shared codebase
 * (Stewmath/oracles-disasm, forked from Drenn's original project), and
 * that project has ONE shared `include/wram.s` used by both games'
 * builds. Player-state addresses (current HP, max HP, rupees, keys,
 * bombs, current room, Link's position/direction) are very likely
 * identical between the two games, since they come from the same shared
 * struct definitions rather than being independently discovered per game.
 * Game-specific data (item IDs for Ages-exclusive items, dungeon/room
 * layout tables, the Seasons rod / Ages harp mechanics) will differ, but
 * that's not what a live HP/rupee sidebar needs anyway.
 *
 * TO FINISH THIS MODULE: take whatever address constants your existing,
 * working standalone OoS companion project uses for HP/rupees/keys/room,
 * and paste them in here unchanged as a first pass. Given the shared
 * wram.s, there's a good chance they just work. If something reads
 * garbage, that specific field is one of the few that differs between
 * builds - worth a quick live memory-search check for that field only,
 * same technique as everywhere else in this project.
 *
 * Source: https://github.com/Stewmath/oracles-disasm (see ages.s vs
 * seasons.s both including the same include/wram.s)
 */
class OracleOfAgesModule : GameModule {
    override val displayName = "Oracle of Ages"
    override val contentNameMatchers = listOf(
        "Oracle of Ages", "Zelda - Oracle of Ages"
    )

    private val _connected = MutableStateFlow(false)
    override val connected: StateFlow<Boolean> = _connected.asStateFlow()

    override fun start(client: MemorySource, scope: CoroutineScope) {
        // TODO: paste in your existing OoS companion's HP/rupee/key/room
        // address constants here as a first pass - see class comment.
        _connected.value = false
    }

    @Composable
    override fun Screen() {
        val connected by connected.collectAsState()
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("Oracle of Ages", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(if (connected) "\u25cf connected" else "\u25cb stub - not wired up yet")
            Spacer(Modifier.height(12.dp))
            Text(
                "Shares its engine and WRAM layout with Oracle of Seasons - " +
                    "paste your existing OoS address constants in here as a " +
                    "first pass, they'll likely just work. See the comment " +
                    "at the top of this file.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
