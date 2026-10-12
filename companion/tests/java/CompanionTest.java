import com.retroarch.browser.companion.*;
import android.app.Activity;
import android.hardware.display.DisplayManager;
import android.view.*;
import android.os.SystemClock;
import java.util.*;

public class CompanionTest {
  static native void nativeStep(int v);
  static native void nativeUnload();
  static native void nativeSetRom(String s);
  static native void nativeMode(int mode);

  static int checks, fails;
  static void check(boolean c, String m) { checks++; if (!c) { fails++; System.out.println("FAIL: " + m); } }

  static CompanionBridge b = CompanionBridge.get();
  static void poll() throws Exception {
    java.lang.reflect.Method m = CompanionBridge.class.getDeclaredMethod("poll"); m.setAccessible(true); m.invoke(b);
  }
  static int watchCount() throws Exception {
    java.lang.reflect.Field f = CompanionBridge.class.getDeclaredField("watches"); f.setAccessible(true); return ((List<?>) f.get(b)).size();
  }
  static void frame(int v) throws Exception { nativeStep(v); SystemClock.now += 16; poll(); }

  // custom screens for controller tests
  public static int created, destroyed;
  public static class TestScreen implements CompanionScreen {
    public View onCreateView(android.content.Context c, CompanionMemory m) { created++; return new View(c); }
    public void onDestroy() { destroyed++; }
  }
  public static class BrokenScreen implements CompanionScreen {
    public View onCreateView(android.content.Context c, CompanionMemory m) { throw new IllegalStateException("boom"); }
    public void onDestroy() {}
  }

  public static void main(String[] a) throws Exception {
    System.load(new java.io.File("libretroarch-activity.so").getAbsolutePath());

    // ---------------- bridge ----------------
    poll();
    check(b.getStatus() == null && b.getFrame() == -1, "nothing loaded yet");
    check(b.read(0x02000100, 4) == null, "read before game is null");

    frame(1);
    check("game_boy_advance,Oracle of Seasons (USA)".equals(b.getStatus()), "status: " + b.getStatus());
    check(b.getFrame() == 1, "frame 1");
    check(b.u32(0x02000100) == -1, "first read of a new range is unavailable");
    frame(2);
    check(b.u32(0x02000100) == 2, "counter after auto-watch: " + b.u32(0x02000100));

    // nearby read merges into the same range, without blinking the old one
    check(b.read(0x02000200, 8) == null, "new nearby range not ready yet");
    poll(); // pushes new layout, no new emulated frame yet
    check(watchCount() == 1, "nearby reads merged into one watch, got " + watchCount());
    check(b.u32(0x02000100) == 2, "old value still served during layout change: " + b.u32(0x02000100));
    frame(3);
    byte[] t = b.read(0x02000200, 8);
    check(t != null && (t[0] & 0xFF) == 3 && (t[7] & 0xFF) == 10, "table bytes " + Arrays.toString(t));
    check(b.u32(0x02000100) == 3, "counter frame 3");

    check(b.u8(0x03000010) == -1, "iwram first read");
    frame(4);
    check(b.u8(0x03000010) == (4 * 7 & 0xFF), "iwram byte " + b.u8(0x03000010));
    check(watchCount() == 2, "separate region = second watch");

    b.read(0x09000000, 4); frame(5); frame(6);
    check(b.u8(0x09000000) == -1, "unmapped address stays unavailable");

    // paused detection
    SystemClock.now += 500; poll();
    check(b.isPaused(), "paused when no frames arrive");
    frame(7);
    check(!b.isPaused(), "running again");

    // pinned watches survive, unused auto ones expire
    b.watch(0x03000000, 32);
    int before = watchCount();
    SystemClock.now += 6000;
    for (int i = 0; i < 61; i++) { b.u32(0x02000100); frame(8 + i); }
    check(watchCount() < before, "unused auto watch expired (" + before + " -> " + watchCount() + ")");
    check(b.u8(0x03000010) != -1, "pinned IWRAM watch kept");
    check(b.u32(0x02000100) == 68, "counter still live: " + b.u32(0x02000100));

    // listeners fire once per new emulated frame
    final int[] heard = {0};
    CompanionMemory.FrameListener l = m -> heard[0]++;
    b.addFrameListener(l);
    frame(100); poll(); poll(); frame(101);
    check(heard[0] == 2, "listener per frame, got " + heard[0]);
    b.removeFrameListener(l);

    // unload
    nativeUnload(); poll();
    check(b.getStatus() == null && b.getFrame() == -1 && b.read(0x02000100, 4) == null, "unloaded");

    // different game starts clean (RetroArch unloads the old content first)
    nativeUnload();
    nativeSetRom("Harvest Moon - Friends of Mineral Town (USA)");
    frame(200);
    check(b.getStatus().endsWith("Friends of Mineral Town (USA)"), "new game status");
    check(watchCount() == 0, "watches cleared for new game, got " + watchCount());
    b.u32(0x02000100); frame(201);
    check(b.u32(0x02000100) == 201, "new game reads work");

    // ---------------- controller ----------------
    Choreographer ch = Choreographer.getInstance();
    Activity act = new Activity();
    DisplayManager dm = new DisplayManager();
    act.displayService = dm;
    Display top = new Display(0), bottom = new Display(1);
    act.display = top;
    dm.all.add(top); dm.all.add(bottom);
    dm.pres.add(bottom);

    nativeUnload(); poll();
    CompanionController c = new CompanionController(act);
    c.start();
    for (int i = 0; i < 5; i++) ch.vsync();
    check(field(c, "presentation") == null, "no bottom screen without a game");

    nativeSetRom("Zelda - Oracle of Seasons (USA)");
    nativeStep(300); ch.vsync();
    Object p = field(c, "presentation");
    check(p != null, "bottom screen appears when game starts");
    android.app.Presentation pres = (android.app.Presentation) p;
    check(pres.getDisplay() == bottom, "shown on the second display");
    check((pres.getWindow().flags & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0, "never steals controller focus");
    for (int i = 0; i < 4; i++) { nativeStep(301 + i); SystemClock.now += 70; ch.vsync(); }
    String txt = debugText(pres);
    check(txt.contains("Oracle of Seasons") && txt.contains("GBA EWRAM @ 02000000\n  0000  ") && txt.contains("GB/GBC WRAM @ 0000C000\n  --"), "debug screen shows live RAM:\n" + txt);

    // custom screen from manifest meta-data, rebuilt on game change
    c.stop();
    check(!pres.isShowing(), "stop() closes bottom screen");
    act.pm.info.metaData = new android.os.Bundle();
    act.pm.info.metaData.m.put(CompanionController.META_SCREEN, "CompanionTest$TestScreen");
    c.start(); nativeStep(400); ch.vsync();
    check(created == 1, "custom screen created");
    nativeUnload();   // RetroArch closes the old content first
    nativeSetRom("Harvest Moon - Friends of Mineral Town (USA)");
    nativeStep(401);
    for (int i = 0; i < 16; i++) ch.vsync();   // status refresh interval
    check(created == 2 && destroyed == 1, "rebuilt for new game: created=" + created + " destroyed=" + destroyed);

    // game closes -> bottom screen goes after debounce
    nativeUnload();
    for (int i = 0; i < 30; i++) ch.vsync();
    check(field(c, "presentation") != null, "debounced: still shown briefly");
    for (int i = 0; i < 30; i++) ch.vsync();
    check(field(c, "presentation") == null && destroyed == 2, "closed after game ends");
    c.stop();

    // broken screen falls back instead of crashing
    act.pm.info.metaData.m.put(CompanionController.META_SCREEN, "CompanionTest$BrokenScreen");
    c.start(); nativeStep(500); ch.vsync();
    android.app.Presentation bp = (android.app.Presentation) field(c, "presentation");
    check(bp != null && bp.isShowing() && debugText(bp).contains("Thor companion bridge"), "broken screen -> debug fallback");
    c.stop();

    // no second display: don't hammer display lookups
    dm.pres.clear(); dm.all.remove(bottom);
    act.pm.info.metaData = null;
    c.start();
    int before2 = dm.lookups;
    for (int i = 0; i < 120; i++) { nativeStep(600 + i); ch.vsync(); }
    check(field(c, "presentation") == null, "nothing shown with one display");
    check(dm.lookups - before2 <= 6, "display lookups throttled, got " + (dm.lookups - before2));
    // second screen turned back on
    dm.all.add(bottom); dm.pres.add(bottom); dm.listener.onDisplayAdded(1);
    nativeStep(800); ch.vsync();
    check(field(c, "presentation") != null, "appears when the display comes back");
    // display removed by the system
    ((android.app.Presentation) field(c, "presentation")).dismiss();
    dm.pres.clear(); dm.all.remove(bottom); dm.listener.onDisplayRemoved(1);
    nativeStep(801); ch.vsync();
    check(field(c, "presentation") == null, "handles display removal");
    c.stop();

    // turned off via preference or launch extra
    android.preference.PreferenceManager.enabled = false;
    dm.all.add(bottom); dm.pres.add(bottom);
    c.start(); nativeStep(900); ch.vsync();
    check(field(c, "presentation") == null, "pref off -> nothing");
    android.preference.PreferenceManager.enabled = true;
    act.intent.x.put("COMPANION", false);
    c.start(); ch.vsync();
    check(field(c, "presentation") == null, "COMPANION=false extra -> nothing");

    // Missing memory-map descriptors use bounded system RAM only.
    for (int mode : new int[]{1, 2, 3}) {
      nativeMode(mode); poll(); frame(20);
      int base = mode == 3 ? 0x80000000 : 0xC000;
      int size = mode == 2 ? 0x8000 : 0x2000;
      b.watch(base, 7); b.watch(base + 1, 6);
      b.watch(base + size - 1, 1);
      frame(21);
      byte[] bytes = b.read(base, 7);
      check(bytes != null, "fallback mode " + mode + " mapped");
      if (bytes != null) for (int i = 0; i < bytes.length; i++)
        check((bytes[i] & 255) == 21 + i, "CPU byte order mode " + mode + " byte " + i);
      bytes = b.read(base + 1, 6);
      check(bytes != null && (bytes[0] & 255) == 22 && (bytes[5] & 255) == 27,
          "unaligned cross-word read mode " + mode);
      check(b.u8(base + size - 1) == ((21 + size - 1) & 255), "last valid byte mode " + mode);
      b.watch(base - 1, 1); b.watch(base + size - 1, 2); b.watch(0xFFFFFFFE, 4); frame(22);
      check(b.read(base - 1, 1) == null, "below base mode " + mode);
      check(b.read(base + size - 1, 2) == null, "crosses RAM end mode " + mode);
      check(b.read(0xFFFFFFFE, 4) == null, "wrapped address rejected mode " + mode);
    }
    for (int mode : new int[]{4, 5, 6}) {
      nativeMode(mode); poll(); frame(22); b.watch(0xC000, 4); b.watch(0x80000000, 4); frame(23);
      check(b.read(0xC000, 4) == null && b.read(0x80000000, 4) == null,
          "unknown/null/mapped core must not use fallback mode " + mode);
    }
    nativeMode(7); frame(24);
    check(b.getFrame() == -1 && b.getStatus() == null, "dummy menu core does not reopen companion");
    System.out.println("JAVA RESULT: " + (fails == 0 ? "PASS" : "FAIL") + " (" + checks + " checks, " + fails + " failures)");
    System.exit(fails == 0 ? 0 : 1);
  }

  static Object field(Object o, String n) throws Exception {
    java.lang.reflect.Field f = o.getClass().getDeclaredField(n); f.setAccessible(true); return f.get(o);
  }
  static String debugText(android.app.Presentation p) {
    android.view.ViewGroup g = (android.view.ViewGroup) ((android.app.Dialog) p).content;
    return ((android.widget.TextView) g.kids.get(0)).text.toString();
  }
}
