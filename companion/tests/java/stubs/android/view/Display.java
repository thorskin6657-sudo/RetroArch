package android.view; public class Display { public static final int STATE_OFF = 1; public int id, state = 2; public boolean failShow;
 public Display(int id) { this.id = id; } public int getDisplayId() { return id; } public int getState() { return state; } }
