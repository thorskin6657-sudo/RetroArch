package com.thor.gamehub.net
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

interface MemorySource {
    suspend fun getStatus(): String?
    suspend fun getLoadedContentName(): String?
    suspend fun readCoreMemory(addr: Long, length: Int): ByteArray?
    suspend fun readU8(addr: Long): Int? = readCoreMemory(addr, 1)?.firstOrNull()?.toInt()?.and(255)
    suspend fun readU16(addr: Long): Int? {
        val bytes = readCoreMemory(addr, 2) ?: return null
        if (bytes.size < 2) return null
        return (bytes[0].toInt() and 255) or ((bytes[1].toInt() and 255) shl 8)
    }
    suspend fun readU32(addr: Long): Long? {
        val bytes = readCoreMemory(addr, 4) ?: return null
        if (bytes.size < 4) return null
        return (0..3).fold(0L) { value, i -> value or ((bytes[i].toLong() and 255) shl (i * 8)) }
    }
    fun frames(): Flow<Long> = flow {
        var frame = 0L
        while (currentCoroutineContext().isActive) { emit(frame++); delay(500) }
    }
    fun close()
}
