package android.hardware.display; public class DisplayManager { public static final String DISPLAY_CATEGORY_PRESENTATION = "android.hardware.display.category.PRESENTATION";
 public interface DisplayListener { void onDisplayAdded(int id); void onDisplayRemoved(int id); void onDisplayChanged(int id); }
 public java.util.List<android.view.Display> all = new java.util.ArrayList<>(); public java.util.List<android.view.Display> pres = new java.util.ArrayList<>();
 public DisplayListener listener; public int lookups;
 public android.view.Display[] getDisplays() { lookups++; return all.toArray(new android.view.Display[0]); }
 public android.view.Display[] getDisplays(String c) { lookups++; return pres.toArray(new android.view.Display[0]); }
 public void registerDisplayListener(DisplayListener l, android.os.Handler h) { listener = l; } public void unregisterDisplayListener(DisplayListener l) { listener = null; } }
