package com.retroarch.browser.companion;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

/**
 * Built-in bottom screen used when no custom screen is bundled. Shows
 * what the bridge sees: the detected game, emulated frame rate, and a
 * live hex view of work RAM, so you can confirm the direct memory
 * path on the device before plugging in Thor Game Hub.
 */
public final class DebugCompanionScreen implements CompanionScreen, CompanionMemory.FrameListener {

  // Work RAM on the systems the companion modules target so far.
  // Whichever one the running core maps shows up; the others show "--".
  private static final int[][] REGIONS = {
      {0x02000000, 128},  // GBA EWRAM
      {0x03000000, 128},  // GBA IWRAM
      {0x0000C000, 128},  // GB/GBC WRAM
  };
  private static final String[] REGION_NAMES = {"GBA EWRAM", "GBA IWRAM", "GB/GBC WRAM"};
  private static final long REDRAW_MS = 66; // ~15 fps is plenty for a debug view

  private TextView text;
  private CompanionMemory memory;
  private long lastDraw = 0;
  private long fpsWindowStart = 0;
  private long fpsWindowFrame = -1;
  private double fps = 0;

  @Override
  public View onCreateView(Context context, CompanionMemory memory) {
    this.memory = memory;
    for (int[] r : REGIONS)
      memory.watch(r[0], r[1]);

    text = new TextView(context);
    text.setTypeface(Typeface.MONOSPACE);
    text.setTextSize(12);
    text.setTextColor(Color.rgb(0xE6, 0xE6, 0xE6));
    text.setGravity(Gravity.TOP | Gravity.START);
    int pad = (int) (16 * context.getResources().getDisplayMetrics().density);
    text.setPadding(pad, pad, pad, pad);

    ScrollView scroll = new ScrollView(context);
    scroll.setBackgroundColor(Color.rgb(0x12, 0x14, 0x18));
    scroll.addView(text);

    memory.addFrameListener(this);
    render();
    return scroll;
  }

  @Override
  public void onFrame(CompanionMemory m) {
    long now = SystemClock.uptimeMillis();
    long f = m.getFrame();
    if (fpsWindowFrame < 0) {
      fpsWindowFrame = f;
      fpsWindowStart = now;
    } else if (now - fpsWindowStart >= 1000) {
      fps = (f - fpsWindowFrame) * 1000.0 / (now - fpsWindowStart);
      fpsWindowFrame = f;
      fpsWindowStart = now;
    }
    if (now - lastDraw >= REDRAW_MS) {
      lastDraw = now;
      render();
    }
  }

  private void render() {
    if (text == null)
      return;
    StringBuilder sb = new StringBuilder(4096);
    String status = memory.getStatus();
    sb.append("Thor companion bridge\n\n");
    sb.append("Game    ").append(status == null ? "(none)" : status).append('\n');
    sb.append("Frame   ").append(memory.getFrame()).append('\n');
    sb.append(String.format(Locale.US, "Speed   %.1f fps%s\n\n", fps, memory.isPaused() ? "  (paused)" : ""));

    for (int i = 0; i < REGIONS.length; i++) {
      int base = REGIONS[i][0];
      byte[] b = memory.read(base, REGIONS[i][1]);
      sb.append(REGION_NAMES[i]).append(String.format(Locale.US, " @ %08X\n", base));
      if (b == null) {
        sb.append("  --\n\n");
        continue;
      }
      for (int row = 0; row < b.length; row += 16) {
        sb.append(String.format(Locale.US, "  %04X ", row));
        for (int col = 0; col < 16 && row + col < b.length; col++)
          sb.append(String.format(Locale.US, " %02X", b[row + col] & 0xFF));
        sb.append('\n');
      }
      sb.append('\n');
    }
    text.setText(sb);
  }

  @Override
  public void onDestroy() {
    if (memory != null)
      memory.removeFrameListener(this);
    text = null;
  }
}
