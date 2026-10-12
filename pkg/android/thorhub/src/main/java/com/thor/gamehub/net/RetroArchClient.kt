package com.thor.gamehub.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/**
 * Shared RetroArch UDP network-commands client, used by every game module.
 * This is the one piece that genuinely doesn't vary by game — reading a
 * byte from EWRAM works the same whether the core is loaded with Oracle of
 * Seasons, FireRed, or FoMT. Only the addresses (and what they mean) differ,
 * which is why those live in each game/ package instead of here.
 *
 * Requires in retroarch.cfg:
 *   network_cmd_enable = "true"
 *   network_cmd_port   = "55355"
 */
class RetroArchClient(
    private val host: String = "127.0.0.1",
    private val port: Int = 55355,
    private val timeoutMs: Int = 500
) : MemorySource {
    private var socket: DatagramSocket? = null
    private val address: InetAddress by lazy { InetAddress.getByName(host) }

    private fun getSocket(): DatagramSocket {
        val s = socket
        if (s != null && !s.isClosed) return s
        return DatagramSocket().apply { soTimeout = timeoutMs }.also { socket = it }
    }

    private suspend fun sendAndReceive(cmd: String): String? = withContext(Dispatchers.IO) {
        val outBytes = cmd.toByteArray(Charsets.US_ASCII)
        val outPacket = DatagramPacket(outBytes, outBytes.size, address, port)
        val buf = ByteArray(4096)
        val inPacket = DatagramPacket(buf, buf.size)
        try {
            val sock = getSocket()
            sock.send(outPacket)
            sock.receive(inPacket)
            String(buf, 0, inPacket.length, Charsets.US_ASCII)
        } catch (_: SocketTimeoutException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Asks RetroArch what's currently loaded. Typical reply looks like:
     *   "GET_STATUS PLAYING mgba,Zelda - Oracle of Seasons,a1b2c3d4"
     *   "GET_STATUS CONTENTLESS"
     * Returns the raw content-name field (second comma-separated token)
     * which is what GameRegistry matches against — or null if RetroArch
     * isn't running / nothing is loaded / the reply didn't parse.
     */
    override suspend fun getStatus(): String? = sendAndReceive("GET_STATUS")

    override suspend fun getLoadedContentName(): String? {
        val reply = sendAndReceive("GET_STATUS") ?: return null
        // "GET_STATUS PLAYING <core>,<content>,<crc32>"
        val afterStatus = reply.trim().removePrefix("GET_STATUS").trim()
        if (afterStatus.startsWith("CONTENTLESS") || afterStatus.isEmpty()) return null
        val fields = afterStatus.removePrefix("PLAYING").trim().split(",")
        return fields.getOrNull(1)?.trim()
    }

    override suspend fun readCoreMemory(addr: Long, length: Int): ByteArray? = withContext(Dispatchers.IO) {
        val reply = sendAndReceive("READ_CORE_MEMORY ${addr.toString(16)} $length") ?: return@withContext null
        parseReadReply(reply)
    }

    override suspend fun readU8(addr: Long): Int? {
        val bytes = readCoreMemory(addr, 1) ?: return null
        if (bytes.isEmpty()) return null
        return bytes[0].toInt() and 0xFF
    }

    override suspend fun readU16(addr: Long): Int? {
        val bytes = readCoreMemory(addr, 2) ?: return null
        if (bytes.size < 2) return null
        return (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8)
    }

    override suspend fun readU32(addr: Long): Long? {
        val bytes = readCoreMemory(addr, 4) ?: return null
        if (bytes.size < 4) return null
        var v = 0L
        for (i in 3 downTo 0) v = (v shl 8) or (bytes[i].toLong() and 0xFF)
        return v
    }

    private fun parseReadReply(reply: String): ByteArray? {
        val parts = reply.trim().split(" ")
        if (parts.size < 3 || parts[0] != "READ_CORE_MEMORY") return null
        if (parts[2] == "-1") return null
        val dataTokens = parts.drop(2)
        return try {
            ByteArray(dataTokens.size) { i -> dataTokens[i].toInt(16).toByte() }
        } catch (_: NumberFormatException) {
            null
        }
    }

    override fun close() {
        socket?.close()
        socket = null
    }
}
