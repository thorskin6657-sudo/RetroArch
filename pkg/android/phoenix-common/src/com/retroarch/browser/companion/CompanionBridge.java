package com.retroarch.browser.companion;

import android.os.SystemClock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Java half of the Thor companion bridge (native half:
 * companion/companion_bridge.c). Holds the watch list, pulls the
 * per-frame snapshot over JNI and serves reads from it.
 *
 * {@link #poll()} is driven once per display vsync by
 * {@link CompanionController}; everything else is thread safe.
 */
public final class CompanionBridge implements CompanionMemory {

  // Native side (libretroarch-activity, already loaded by the activity)
  static native int nativeSetWatches(int[] addresses, int[] lengths);
  static native long nativeSnapshot(byte[] out, boolean[] valid);
  static native String nativeGetStatus();

  private static final int MAX_WATCHES = 64;           // matches native caps
  private static final int MAX_WATCH_LEN = 0x10000;
  private static final int MAX_TOTAL_BYTES = 0x80000;
  private static final int MERGE_GAP = 256;             // join nearby reads into one range
  private static final long UNUSED_EXPIRE_MS = 5000;    // drop auto ranges nobody reads
  private static final long PAUSED_AFTER_MS = 300;
  private static final int STATUS_EVERY_POLLS = 15;

  private static final CompanionBridge INSTANCE = new CompanionBridge();

  public static CompanionBridge get() {
    return INSTANCE;
  }

  private static final class Watch {
    long address;          // unsigned 32-bit
    int length;
    int offset;            // into snapshot, set on push
    boolean pinned;
    long lastUsedMs;
  }

  private static final class Layout {
    final long[] addresses;
    final int[] lengths;
    final int[] offsets;
    final byte[] data;
    final boolean[] valid;

    Layout(ArrayList<Watch> watches, int total) {
      int n = watches.size();
      addresses = new long[n];
      lengths = new int[n];
      offsets = new int[n];
      for (int i = 0; i < n; i++) {
        Watch w = watches.get(i);
        addresses[i] = w.address;
        lengths[i] = w.length;
        offsets[i] = w.offset;
      }
      data = new byte[total];
      valid = new boolean[n];
    }

    /** Copy of a fully covered, valid range, or null. */
    byte[] slice(long address, int length) {
      for (int i = 0; i < addresses.length; i++) {
        long start = addresses[i];
        if (address >= start && address + length <= start + lengths[i]) {
          if (!valid[i])
            return null;
          int from = offsets[i] + (int) (address - start);
          return Arrays.copyOfRange(data, from, from + length);
        }
      }
      return null;
    }
  }

  private final ArrayList<Watch> watches = new ArrayList<>();
  private final CopyOnWriteArrayList<FrameListener> listeners = new CopyOnWriteArrayList<>();

  private Layout current = new Layout(new ArrayList<Watch>(), 0);
  // Previous layout, kept until the new one has a frame, so reads don't
  // blink to null for a frame whenever the watch list changes.
  private Layout previous = null;
  private boolean dirty = false;

  private long frame = -1;
  private long lastFrameChangeMs = 0;
  private String status = null;
  private int pollsSinceStatus = STATUS_EVERY_POLLS;
  private int pollsSinceExpire = 0;

  private CompanionBridge() {}

  // ---------------------------------------------------------------
  // CompanionMemory
  // ---------------------------------------------------------------

  @Override
  public synchronized byte[] read(int address, int length) {
    if (length <= 0 || length > MAX_WATCH_LEN || frame < 0)
      return null;

    long addr = address & 0xFFFFFFFFL;
    Watch w = find(addr, length);
    if (w == null) {
      add(addr, length, false);
      if (dirty)
        push();   // so the very next emulated frame copies it
      return null;
    }
    w.lastUsedMs = SystemClock.uptimeMillis();

    byte[] out = current.slice(addr, length);
    if (out == null && previous != null)
      out = previous.slice(addr, length);
    return out;
  }

  @Override
  public int u8(int address) {
    byte[] b = read(address, 1);
    return b == null ? -1 : (b[0] & 0xFF);
  }

  @Override
  public int u16(int address) {
    byte[] b = read(address, 2);
    return b == null ? -1 : ((b[0] & 0xFF) | ((b[1] & 0xFF) << 8));
  }

  @Override
  public long u32(int address) {
    byte[] b = read(address, 4);
    if (b == null)
      return -1;
    return (b[0] & 0xFFL) | ((b[1] & 0xFFL) << 8) | ((b[2] & 0xFFL) << 16) | ((b[3] & 0xFFL) << 24);
  }

  @Override
  public synchronized void watch(int address, int length) {
    if (length <= 0 || length > MAX_WATCH_LEN)
      return;
    long addr = address & 0xFFFFFFFFL;
    Watch w = find(addr, length);
    if (w != null)
      w.pinned = true;
    else
      add(addr, length, true);
    if (dirty && frame >= 0)
      push();
  }

  @Override
  public synchronized String getStatus() {
    return status;
  }

  @Override
  public synchronized boolean isPaused() {
    return frame < 0 || SystemClock.uptimeMillis() - lastFrameChangeMs > PAUSED_AFTER_MS;
  }

  @Override
  public synchronized long getFrame() {
    return frame;
  }

  @Override
  public void addFrameListener(FrameListener listener) {
    listeners.addIfAbsent(listener);
  }

  @Override
  public void removeFrameListener(FrameListener listener) {
    listeners.remove(listener);
  }

  // ---------------------------------------------------------------
  // Driven by CompanionController, once per vsync on the main thread
  // ---------------------------------------------------------------

  void poll() {
    boolean newFrame;

    synchronized (this) {
      long now = SystemClock.uptimeMillis();

      if (++pollsSinceExpire >= 60) {
        pollsSinceExpire = 0;
        expireUnused(now);
      }
      if (dirty)
        push();

      long f = nativeSnapshot(current.data, current.valid);
      newFrame = f >= 0 && f != frame;
      if (newFrame) {
        lastFrameChangeMs = now;
        // Drop the old layout once the new one has real data.
        if (previous != null && anyValid(current.valid))
          previous = null;
      }
      frame = f;

      if (f < 0) {
        if (status != null)
          onGameChanged(null);
      } else if (++pollsSinceStatus >= STATUS_EVERY_POLLS || status == null) {
        pollsSinceStatus = 0;
        String s = nativeGetStatus();
        if (s != null && !s.equals(status))
          onGameChanged(s);
      }
    }

    if (newFrame)
      for (FrameListener l : listeners)
        l.onFrame(this);
  }

  // ---------------------------------------------------------------
  // Internals (call with the lock held)
  // ---------------------------------------------------------------

  private void onGameChanged(String newStatus) {
    // A different game means different addresses: start clean.
    status = newStatus;
    watches.clear();
    current = new Layout(watches, 0);
    previous = null;
    dirty = true;
  }

  private Watch find(long addr, int length) {
    for (Watch w : watches)
      if (addr >= w.address && addr + length <= w.address + w.length)
        return w;
    return null;
  }

  private void add(long addr, int length, boolean pinned) {
    long now = SystemClock.uptimeMillis();
    long end = addr + length;

    // Grow a nearby range instead of adding a new one, so a screen
    // that reads many small values near each other uses one copy.
    for (Watch w : watches) {
      long wEnd = w.address + w.length;
      if (addr <= wEnd + MERGE_GAP && end + MERGE_GAP >= w.address) {
        long nStart = Math.min(addr, w.address);
        long nEnd = Math.max(end, wEnd);
        if (nEnd - nStart <= MAX_WATCH_LEN && totalBytes() - w.length + (nEnd - nStart) <= MAX_TOTAL_BYTES) {
          w.address = nStart;
          w.length = (int) (nEnd - nStart);
          w.pinned |= pinned;
          w.lastUsedMs = now;
          dirty = true;
          return;
        }
      }
    }

    if (watches.size() >= MAX_WATCHES || totalBytes() + length > MAX_TOTAL_BYTES)
      return;

    Watch w = new Watch();
    w.address = addr;
    w.length = length;
    w.pinned = pinned;
    w.lastUsedMs = now;
    watches.add(w);
    dirty = true;
  }

  private void expireUnused(long now) {
    Iterator<Watch> it = watches.iterator();
    while (it.hasNext()) {
      Watch w = it.next();
      if (!w.pinned && now - w.lastUsedMs > UNUSED_EXPIRE_MS) {
        it.remove();
        dirty = true;
      }
    }
  }

  private static boolean anyValid(boolean[] valid) {
    for (boolean v : valid)
      if (v)
        return true;
    return valid.length == 0;
  }

  private int totalBytes() {
    int t = 0;
    for (Watch w : watches)
      t += w.length;
    return t;
  }

  private void push() {
    int n = watches.size();
    int[] a = new int[n];
    int[] l = new int[n];
    int total = 0;
    for (int i = 0; i < n; i++) {
      Watch w = watches.get(i);
      w.offset = total;
      a[i] = (int) w.address;
      l[i] = w.length;
      total += w.length;
    }
    nativeSetWatches(a, l);
    if (current.addresses.length > 0)
      previous = current;
    current = new Layout(watches, total);
    dirty = false;
  }
}
