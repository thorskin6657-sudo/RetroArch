package com.retroarch.browser.companion;

/**
 * Direct, per-frame view of the running core's memory, for companion
 * screens. Addresses are the same ones RetroArch's READ_CORE_MEMORY
 * network command uses (the core's memory map), so address tables
 * written for the UDP client work unchanged.
 *
 * Reads are served from the latest completed frame. A range the
 * screen has not read before is picked up automatically: the first
 * read returns null, and from the next frame on it returns data.
 * Call {@link #watch} up front for ranges you know you need, so even
 * the first read has data. Safe to call from any thread.
 */
public interface CompanionMemory {

  /** Called on the main thread once per new emulated frame. */
  interface FrameListener {
    void onFrame(CompanionMemory memory);
  }

  /** Bytes from the latest frame, or null if not available yet. */
  byte[] read(int address, int length);

  /** Unsigned byte, or -1 if not available. */
  int u8(int address);

  /** Unsigned little-endian 16-bit value, or -1 if not available. */
  int u16(int address);

  /** Unsigned little-endian 32-bit value, or -1 if not available. */
  long u32(int address);

  /** Keep this range copied every frame for as long as the game runs. */
  void watch(int address, int length);

  /**
   * "system_id,rom_name" for the running game (the same text as
   * RetroArch's GET_STATUS reply after the PLAYING/PAUSED word),
   * or null when no game is loaded.
   */
  String getStatus();

  /** True when no new frame has been emulated for a moment (menu, pause). */
  boolean isPaused();

  /** Frame number of the latest snapshot, or -1 when no game is loaded. */
  long getFrame();

  void addFrameListener(FrameListener listener);

  void removeFrameListener(FrameListener listener);
}
