package com.thor.gamehub.games.fe8

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
 * MOSTLY REAL - struct Unit's field layout below is a direct, exact port of
 * the actual fireemblem8u decompilation header (include/bmunit.h) that the
 * user pulled from the repo. Every field offset here is computed from that
 * source, not guessed:
 *
 *   struct Unit {
 *     0x00 pCharacterData (ptr)   0x04 pClassData (ptr)
 *     0x08 level (s8)             0x09 exp (u8)
 *     0x0A aiFlags                0x0B index (s8, encodes faction in high bits)
 *     0x0C state (u32)
 *     0x10 xPos (s8)  0x11 yPos (s8)
 *     0x12 maxHP (s8) 0x13 curHP (s8)
 *     0x14 pow  0x15 skl  0x16 spd  0x17 def  0x18 res  0x19 lck  (all s8)
 *     0x1A conBonus  0x1B rescue  0x1C ballistaIndex  0x1D movBonus
 *     0x1E items[5] (u16 each)    0x28 ranks[8]
 *     0x30 statusIndex:4/statusDuration:4 (1 byte, bitfield)
 *     0x31 torchDuration:4/barrierDuration:4 (1 byte, bitfield)
 *     0x32 supports[7]  0x39 supportBits
 *     0x3C pMapSpriteHandle (ptr)
 *     0x40 ai_config (u16) 0x42-0x47 ai fields
 *   } // sizeof == 0x48 (72 bytes) - computed from the layout above, solid.
 *
 * ONE REMAINING ASSUMPTION, clearly flagged: the base address below
 * (0x0202BE4C, from a community SYM file labeling a 0x2688-byte region
 * "gUnitArray") is assumed to be the start of gUnitArrayBlue[62], with
 * gUnitArrayRed[50] and gUnitArrayGreen[20] immediately following in that
 * order (matching the header's extern declaration order). The arithmetic
 * doesn't perfectly reconcile — 62+50+20 units at 72 bytes = 9504 bytes,
 * but the SYM region is 9864 bytes (5 units' worth more) — so either
 * there's padding/a scratch buffer at the end, or the base label is
 * slightly off. This module reads all three arrays under that assumption;
 * LIVE VERIFICATION (once you're at the Thor): pick a unit, note their
 * on-screen HP, and check whether this module's reported HP for that
 * unit's ID matches. If Blue units check out but Green/Red look wrong,
 * the ordering assumption is the thing to fix first.
 *
 * Source for the struct: bmunit.h, pulled directly from
 * github.com/FireEmblemUniverse/fireemblem8u by the user.
 */
class FireEmblemSacredStonesModule : GameModule {
    override val displayName = "Fire Emblem: Sacred Stones"
    override val contentNameMatchers = listOf(
        "Sacred Stones", "Fire Emblem - The Sacred Stones", "FE8"
    )

    data class UnitInfo(
        val id: Int,
        val faction: String,
        val level: Int,
        val curHp: Int,
        val maxHp: Int,
        val pow: Int,
        val skl: Int,
        val spd: Int,
        val def: Int,
        val res: Int,
        val lck: Int
    )

    companion object {
        const val UNIT_STRUCT_SIZE = 0x48 // 72 bytes, computed from bmunit.h

        // Confirmed-solid field offsets within struct Unit:
        const val OFF_LEVEL = 0x08
        const val OFF_MAX_HP = 0x12
        const val OFF_CUR_HP = 0x13
        const val OFF_POW = 0x14
        const val OFF_SKL = 0x15
        const val OFF_SPD = 0x16
        const val OFF_DEF = 0x17
        const val OFF_RES = 0x18
        const val OFF_LCK = 0x19

        // Assumed base (see class-level comment) - needs live verification.
        const val BLUE_BASE = 0x0202BE4CL
        const val BLUE_COUNT = 62
        val RED_BASE = BLUE_BASE + BLUE_COUNT * UNIT_STRUCT_SIZE
        const val RED_COUNT = 50
        val GREEN_BASE = RED_BASE + RED_COUNT * UNIT_STRUCT_SIZE
        const val GREEN_COUNT = 20

        const val ACTIVE_UNIT_ID = 0x0202BE44L
    }

    private data class State(
        val connected: Boolean = false,
        val activeUnitId: Int? = null,
        val units: List<UnitInfo> = emptyList()
    )

    private val _connected = MutableStateFlow(false)
    override val connected: StateFlow<Boolean> = _connected.asStateFlow()
    private val _state = MutableStateFlow(State())

    override fun start(client: MemorySource, scope: CoroutineScope) {
        scope.launch {
            client.frames().collect {
                val activeId = client.readU8(ACTIVE_UNIT_ID)

                val units = mutableListOf<UnitInfo>()
                units += readFaction(client, "Blue", BLUE_BASE, BLUE_COUNT, idOffset = 1)
                units += readFaction(client, "Red", RED_BASE, RED_COUNT, idOffset = 0x81)
                units += readFaction(client, "Green", GREEN_BASE, GREEN_COUNT, idOffset = 0x41)

                val ok = activeId != null
                _connected.value = ok
                _state.value = State(connected = ok, activeUnitId = activeId, units = units)
            }
        }
    }

    private suspend fun readFaction(
        client: MemorySource,
        faction: String,
        base: Long,
        count: Int,
        idOffset: Int
    ): List<UnitInfo> {
        val result = mutableListOf<UnitInfo>()
        for (slot in 0 until count) {
            val unitBase = base + slot * UNIT_STRUCT_SIZE
            val level = client.readU8(unitBase + OFF_LEVEL) ?: continue
            if (level == 0) continue // empty/invalid slot
            val curHp = client.readU8(unitBase + OFF_CUR_HP) ?: continue
            val maxHp = client.readU8(unitBase + OFF_MAX_HP) ?: continue
            if (maxHp == 0) continue // filters out garbage/unallocated slots
            val pow = client.readU8(unitBase + OFF_POW) ?: 0
            val skl = client.readU8(unitBase + OFF_SKL) ?: 0
            val spd = client.readU8(unitBase + OFF_SPD) ?: 0
            val def = client.readU8(unitBase + OFF_DEF) ?: 0
            val res = client.readU8(unitBase + OFF_RES) ?: 0
            val lck = client.readU8(unitBase + OFF_LCK) ?: 0
            result += UnitInfo(idOffset + slot, faction, level, curHp, maxHp, pow, skl, spd, def, res, lck)
        }
        return result
    }

    @Composable
    override fun Screen() {
        val s by _state.collectAsState()
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("Fire Emblem: Sacred Stones", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(if (s.connected) "\u25cf connected" else "\u25cb no signal")
            Spacer(Modifier.height(8.dp))
            Text("Active unit ID: ${s.activeUnitId ?: "--"}")
            Spacer(Modifier.height(8.dp))
            Text(
                "Base address is an unverified assumption (see file comment) " +
                    "- cross-check HP shown here against the game's HUD.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(12.dp))

            LazyColumn(Modifier.fillMaxSize()) {
                items(s.units) { u ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${u.faction} #${u.id}  Lv ${u.level}")
                                Text("HP ${u.curHp}/${u.maxHp}")
                            }
                            Text(
                                "Pow ${u.pow} Skl ${u.skl} Spd ${u.spd} Def ${u.def} Res ${u.res} Lck ${u.lck}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}
