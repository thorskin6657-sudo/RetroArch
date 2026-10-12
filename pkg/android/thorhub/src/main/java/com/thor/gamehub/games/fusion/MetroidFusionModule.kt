package com.thor.gamehub.games.fusion

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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * PARTIAL, lower confidence than FireRed or FE8. Unlike those two, there's
 * no full disassembly or documented struct for Metroid Fusion that turned
 * up in research — what's here comes from decoding widely-mirrored
 * CodeBreaker cheat codes (almarsguides.com), which for this game happen
 * to use GBA CodeBreaker's *unscrambled* direct-address format (type "03"/
 * "13" prefix + literal IWRAM address), unlike FoMT's scrambled EWRAM
 * codes from earlier in this project.
 *
 * CURRENT_HP_ADDR (0x03001310): moderate confidence. Decoded from the
 * "Unlimited Health" cheat code (13001310 00000833) — the address is
 * unscrambled and reliable, but I haven't independently confirmed this is
 * current HP specifically (as opposed to some adjacent/related field) the
 * way FireRed's trailer bytes were confirmed against Bulbapedia's actual
 * documented struct.
 *
 * INVULNERABLE_FLAG_ADDR (0x03001249): a status byte, confirmed to exist
 * and toggle invulnerability, but not otherwise useful for a companion
 * display.
 *
 * NOT included: missiles, power bombs, super missiles. Two different
 * cheat codes reference the same neighboring address (0x03001318) for
 * seemingly different things, which I couldn't reconcile without
 * guessing - so nothing for ammo is wired up rather than showing you
 * something potentially wrong.
 *
 * Given Metroid Zero Mission (same engine family, made by the same team)
 * keeps its whole "Samus Info" struct - max/current HP, max/current
 * missiles, super missiles, power bombs - as one contiguous block in
 * IWRAM (0x03001530 in that game), Fusion very likely has an equivalent
 * struct near this 0x03001310-0x03001320 neighborhood. The fastest way to
 * finish this module: once at the Thor, use RetroArch's memory search
 * starting from 0x03001310, take missile damage, and search for
 * "changed" - the whole struct is probably only a dozen-some bytes away.
 */
class MetroidFusionModule : GameModule {
    override val displayName = "Metroid Fusion"
    override val contentNameMatchers = listOf("Metroid Fusion")

    companion object {
        const val CURRENT_HP_ADDR = 0x03001310L
        const val INVULNERABLE_FLAG_ADDR = 0x03001249L
    }

    private data class State(
        val connected: Boolean = false,
        val currentHp: Int? = null,
        val invulnerable: Boolean = false
    )

    private val _connected = MutableStateFlow(false)
    override val connected: StateFlow<Boolean> = _connected.asStateFlow()
    private val _state = MutableStateFlow(State())

    override fun start(client: MemorySource, scope: CoroutineScope) {
        scope.launch {
            client.frames().collect {
                val hp = client.readU16(CURRENT_HP_ADDR)
                val invuln = client.readU8(INVULNERABLE_FLAG_ADDR)
                val ok = hp != null
                _connected.value = ok
                _state.value = State(connected = ok, currentHp = hp, invulnerable = (invuln ?: 0) != 0)
            }
        }
    }

    @Composable
    override fun Screen() {
        val s by _state.collectAsState()
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("Metroid Fusion", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(if (s.connected) "\u25cf connected" else "\u25cb no signal")
            Spacer(Modifier.height(12.dp))
            Text("HP (unconfirmed field): ${s.currentHp ?: "--"}")
            Spacer(Modifier.height(12.dp))
            Text(
                "Lowest-confidence module in the app - only a candidate HP " +
                    "address (from a decoded cheat code, not a documented " +
                    "struct) is wired up. Missiles/bombs deliberately left " +
                    "out rather than guessed. See the comment at the top of " +
                    "this file for the live-verification plan.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
