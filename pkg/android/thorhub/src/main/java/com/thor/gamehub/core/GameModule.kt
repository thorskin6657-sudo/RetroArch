package com.thor.gamehub.core

import androidx.compose.runtime.Composable
import com.thor.gamehub.net.MemorySource
import kotlinx.coroutines.flow.StateFlow

/**
 * The contract every supported game implements. GameRegistry picks a module
 * based on RetroArch's GET_STATUS content-name field; MainActivity/second
 * screen just render whatever module is currently active without needing to
 * know anything game-specific.
 */
interface GameModule {
    /** Human-readable name, shown in the game-picker / status bar. */
    val displayName: String

    /**
     * Substrings to match against RetroArch's GET_STATUS content-name field
     * for auto-detection (case-insensitive). Content names vary by ROM
     * dump/region, so list a few reasonable variants rather than one exact
     * string — e.g. FireRed dumps show up as "Pokemon - Fire Red Version"
     * or "Pokemon FireRed" depending on the dump.
     */
    val contentNameMatchers: List<String>

    /** Starts this module's poll loop against the given client. Idempotent. */
    fun start(client: MemorySource, scope: kotlinx.coroutines.CoroutineScope)

    /** Whether the poll loop currently considers itself connected/reading valid data. */
    val connected: StateFlow<Boolean>

    /** The module's own screen content, rendered into whichever screen (main or second) hosts it. */
    @Composable
    fun Screen()

    /** Release any per-module resources (most modules won't need this). */
    fun stop() {}
}
