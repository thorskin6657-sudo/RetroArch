package com.thor.gamehub.retroarch
import com.retroarch.browser.companion.CompanionMemory
import com.thor.gamehub.net.MemorySource
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow

class DirectRetroArchClient(private val memory: CompanionMemory) : MemorySource {
    override suspend fun getStatus(): String {
        val status = memory.status ?: return "GET_STATUS CONTENTLESS"
        return "GET_STATUS ${if (memory.isPaused) "PAUSED" else "PLAYING"} $status"
    }
    override suspend fun getLoadedContentName(): String? = memory.status?.split(',')?.getOrNull(1)?.trim()
    override suspend fun readCoreMemory(addr: Long, length: Int): ByteArray? {
        if (addr !in 0..0xffffffffL || length !in 1..0x10000) return null
        return memory.read(addr.toInt(), length)
    }
    suspend fun readMemory(address: Int, length: Int) = readCoreMemory(address.toLong() and 0xffffffffL, length)
    fun watch(address: Int, length: Int) = memory.watch(address, length)
    override fun frames(): Flow<Long> = callbackFlow {
        val listener = CompanionMemory.FrameListener { trySend(it.frame) }
        memory.addFrameListener(listener)
        if (memory.frame >= 0) trySend(memory.frame)
        awaitClose { memory.removeFrameListener(listener) }
    }.buffer(Channel.CONFLATED)
    override fun close() {}
}
