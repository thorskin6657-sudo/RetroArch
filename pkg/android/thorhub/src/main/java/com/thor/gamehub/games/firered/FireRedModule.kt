package com.thor.gamehub.games.firered

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
 * REAL - Gen III's save format is fully documented on Bulbapedia (not
 * reverse-engineered from cheat codes), so everything below is a direct
 * port of that spec. Two independent sources corroborate the player party
 * base address for FireRed.
 *
 * Reads the full 100-byte structure per party slot in one UDP round-trip,
 * decodes species/held item/experience/moves via GenIIIPartyDecoder (the
 * documented XOR-decrypt + substructure-reorder algorithm), and looks up
 * Kanto species names via SpeciesNames.
 *
 * Known gaps: nicknames aren't decoded (proprietary non-ASCII character
 * table, not implemented), move names are IDs only (no move-name table
 * yet), and Hoenn-dex species (from trades/events) show as "Unknown" since
 * SpeciesNames only covers the Kanto 151.
 *
 * Source: https://bulbapedia.bulbagarden.net/wiki/Pok%C3%A9mon_data_substructures_(Generation_III)
 */
class FireRedModule : GameModule {
    override val displayName = "Pokemon FireRed"
    override val contentNameMatchers = listOf(
        "FireRed", "Fire Red", "Pokemon - Fire Red"
    )

    data class PartySlot(
        val index: Int,
        val speciesName: String?,
        val level: Int?,
        val currentHp: Int?,
        val totalHp: Int?,
        val statusRaw: Long?,
        val moveIds: List<Int>
    ) {
        val isAsleep: Boolean get() = ((statusRaw ?: 0) and 0x7) != 0L
        val isPoisoned: Boolean get() = ((statusRaw ?: 0) and 0x8) != 0L
        val isBurned: Boolean get() = ((statusRaw ?: 0) and 0x10) != 0L
        val isFrozen: Boolean get() = ((statusRaw ?: 0) and 0x20) != 0L
        val isParalyzed: Boolean get() = ((statusRaw ?: 0) and 0x40) != 0L
        val statusLabel: String
            get() = when {
                isFrozen -> "FRZ"
                isBurned -> "BRN"
                isParalyzed -> "PAR"
                isPoisoned -> "PSN"
                isAsleep -> "SLP"
                else -> ""
            }
    }

    companion object {
        const val PARTY_BASE = 0x02024284L
        const val PARTY_MON_SIZE = 100L
        const val PARTY_SIZE = 6

        const val MONEY_ADDR = 0x02025840L

        const val OFFSET_STATUS = 0x50
        const val OFFSET_LEVEL = 0x54
        const val OFFSET_CURRENT_HP = 0x56
        const val OFFSET_TOTAL_HP = 0x58
    }

    private data class State(
        val connected: Boolean = false,
        val money: Long? = null,
        val party: List<PartySlot> = emptyList()
    )

    private val _connected = MutableStateFlow(false)
    override val connected: StateFlow<Boolean> = _connected.asStateFlow()
    private val _state = MutableStateFlow(State())

    override fun start(client: MemorySource, scope: CoroutineScope) {
        scope.launch {
            client.frames().collect {
                val money = client.readU32(MONEY_ADDR)

                val party = (0 until PARTY_SIZE).mapNotNull { slot ->
                    val base = PARTY_BASE + slot * PARTY_MON_SIZE
                    val raw = client.readCoreMemory(base, PARTY_MON_SIZE.toInt()) ?: return@mapNotNull null

                    val level = raw[OFFSET_LEVEL].toInt() and 0xFF
                    if (level == 0) return@mapNotNull null // empty slot

                    val currentHp = readU16(raw, OFFSET_CURRENT_HP)
                    val totalHp = readU16(raw, OFFSET_TOTAL_HP)
                    val status = readU32(raw, OFFSET_STATUS)
                    val decoded = GenIIIPartyDecoder.decode(raw)

                    PartySlot(
                        index = slot + 1,
                        speciesName = decoded?.let { SpeciesNames.nameFor(it.speciesId) },
                        level = level,
                        currentHp = currentHp,
                        totalHp = totalHp,
                        statusRaw = status,
                        moveIds = decoded?.moveIds ?: emptyList()
                    )
                }

                val ok = money != null || party.isNotEmpty()
                _connected.value = ok
                _state.value = State(connected = ok, money = money, party = party)
            }
        }
    }

    private fun readU16(b: ByteArray, off: Int): Int? {
        if (off + 1 >= b.size) return null
        return (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)
    }

    private fun readU32(b: ByteArray, off: Int): Long? {
        if (off + 3 >= b.size) return null
        var v = 0L
        for (i in 3 downTo 0) v = (v shl 8) or (b[off + i].toLong() and 0xFF)
        return v
    }

    @Composable
    override fun Screen() {
        val s by _state.collectAsState()
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("Pokemon FireRed", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(if (s.connected) "\u25cf connected" else "\u25cb no signal")
            Spacer(Modifier.height(8.dp))
            Text("Money: ${s.money?.toString() ?: "--"}")
            Spacer(Modifier.height(12.dp))
            Text("Party", style = MaterialTheme.typography.titleMedium)

            LazyColumn(Modifier.fillMaxSize()) {
                items(s.party) { slot ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "${slot.speciesName ?: "?"}  Lv ${slot.level}",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text("HP ${slot.currentHp ?: "--"}/${slot.totalHp ?: "--"}")
                            }
                            if (slot.statusLabel.isNotEmpty()) {
                                Text(slot.statusLabel, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                "Moves: ${slot.moveIds.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}
