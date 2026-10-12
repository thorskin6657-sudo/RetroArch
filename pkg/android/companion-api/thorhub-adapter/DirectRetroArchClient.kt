// Copy into Thor Game Hub's library module. Drop-in replacement for the
// UDP RetroArchClient when the hub runs inside RetroArch Thor: same
// method shapes and the same GET_STATUS text, so GameRegistry's
// detection and every module's address table keep working unchanged.
//
// Easiest wiring: extract an interface from your existing client
// (e.g. `interface MemorySource { suspend fun getStatus(): String?;
// suspend fun readMemory(address: Int, length: Int): ByteArray? }`),
// have both the UDP client and this class implement it, and give your
// modules a MemorySource instead of the concrete UDP client.
package com.thorgamehub.retroarch

import com.retroarch.browser.companion.CompanionMemory
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class DirectRetroArchClient(private val memory: CompanionMemory) {

    /**
     * Same text the UDP GET_STATUS reply carried:
     * "GET_STATUS PLAYING game_boy_advance,Rom Name" (or PAUSED), or
     * "GET_STATUS CONTENTLESS" when no game is loaded.
     */
    suspend fun getStatus(): String {
        val status = memory.status ?: return "GET_STATUS CONTENTLESS"
        val state = if (memory.isPaused) "PAUSED" else "PLAYING"
        return "GET_STATUS $state $status"
    }

    /**
     * Bytes from the latest emulated frame. A range read for the first
     * time is picked up and arrives on the next frame, so this waits a
     * few frames before giving up. Null means the core doesn't map it.
     */
    suspend fun readMemory(address: Int, length: Int): ByteArray? {
        repeat(6) {
            memory.read(address, length)?.let { return it }
            delay(17)
        }
        return null
    }

    /** Non-suspending read for drawing code: latest frame or null. */
    fun readNow(address: Int, length: Int): ByteArray? = memory.read(address, length)

    /** Keep a range copied every frame (call once per range at module start). */
    fun watch(address: Int, length: Int) = memory.watch(address, length)

    /**
     * Emits once per emulated frame. Collect this instead of polling on
     * a timer, so the bottom screen updates in lockstep with the game.
     */
    fun frames(): Flow<Long> = callbackFlow {
        val listener = CompanionMemory.FrameListener { trySend(it.frame) }
        memory.addFrameListener(listener)
        awaitClose { memory.removeFrameListener(listener) }
    }
}
