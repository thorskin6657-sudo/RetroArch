package com.thor.gamehub.games.firered

/**
 * Decrypts the species/item/experience/moves portion of a Gen III Pokemon's
 * 100-byte party structure. Algorithm and substructure-order table are
 * straight from Bulbapedia's "Pokémon data substructures (Generation III)"
 * page — this isn't guesswork, it's a well-documented, deterministic format
 * shared by Ruby/Sapphire/Emerald/FireRed/LeafGreen.
 *
 * https://bulbapedia.bulbagarden.net/wiki/Pok%C3%A9mon_data_substructures_(Generation_III)
 */
object GenIIIPartyDecoder {

    // Index = personality % 24. Each 4-letter string gives which substructure
    // (Growth/Attacks/EVs-condition/Miscellaneous) sits in physical position
    // 0..3 of the decrypted 48-byte block.
    private val SUBSTRUCT_ORDER = arrayOf(
        "GAEM", "GAME", "GEAM", "GEMA", "GMAE", "GMEA",
        "AGEM", "AGME", "AEGM", "AEMG", "AMGE", "AMEG",
        "EGAM", "EGMA", "EAGM", "EAMG", "EMGA", "EMAG",
        "MGAE", "MGEA", "MAGE", "MAEG", "MEGA", "MEAG"
    )

    data class DecodedMon(
        val speciesId: Int,
        val heldItemId: Int,
        val experience: Long,
        val moveIds: List<Int>,
        val movePp: List<Int>
    )

    /** [fullBytes] must be the full 100-byte party structure for one Pokemon slot. */
    fun decode(fullBytes: ByteArray): DecodedMon? {
        if (fullBytes.size < 100) return null

        val personality = readU32LE(fullBytes, 0)
        val otId = readU32LE(fullBytes, 4)
        val key = personality xor otId

        // Decrypt the 48-byte Data block (offset 0x20), 4 bytes at a time.
        val decrypted = ByteArray(48)
        var i = 0
        while (i < 48) {
            val word = readU32LE(fullBytes, 0x20 + i)
            writeU32LE(decrypted, i, word xor key)
            i += 4
        }

        val orderIndex = (personality % 24L).toInt()
        val orderStr = SUBSTRUCT_ORDER[orderIndex]
        val growthPos = orderStr.indexOf('G')
        val attacksPos = orderStr.indexOf('A')

        val growth = decrypted.copyOfRange(growthPos * 12, growthPos * 12 + 12)
        val attacks = decrypted.copyOfRange(attacksPos * 12, attacksPos * 12 + 12)

        val speciesId = readU16LE(growth, 0)
        val heldItemId = readU16LE(growth, 2)
        val experience = readU32LE(growth, 4)

        val moveIds = listOf(
            readU16LE(attacks, 0), readU16LE(attacks, 2),
            readU16LE(attacks, 4), readU16LE(attacks, 6)
        )
        val movePp = listOf(
            attacks[8].toInt() and 0xFF, attacks[9].toInt() and 0xFF,
            attacks[10].toInt() and 0xFF, attacks[11].toInt() and 0xFF
        )

        // Sanity check: a species ID of 0 or absurdly high means either an
        // empty slot or (more worryingly) the decrypt key was wrong — the
        // caller should treat speciesId == 0 as "no Pokemon here", same as
        // the game does.
        return DecodedMon(speciesId.toInt(), heldItemId.toInt(), experience, moveIds, movePp)
    }

    private fun readU16LE(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)

    private fun readU32LE(b: ByteArray, off: Int): Long {
        var v = 0L
        for (i in 3 downTo 0) v = (v shl 8) or (b[off + i].toLong() and 0xFF)
        return v
    }

    private fun writeU32LE(b: ByteArray, off: Int, value: Long) {
        for (i in 0 until 4) {
            b[off + i] = ((value shr (8 * i)) and 0xFF).toByte()
        }
    }
}
