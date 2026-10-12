package android.app; public class Dialog { boolean showing; android.content.Context ctx; android.view.Window win = new android.view.Window(); public android.view.View content;
 public Dialog(android.content.Context c) { ctx = c; }
 protected void onCreate(android.os.Bundle b) {} protected void onStop() {}
 public void show() { onCreate(null); showing = true; } public void dismiss() { if (showing) { showing = false; onStop(); } }
 public boolean isShowing() { return showing; } public android.view.Window getWindow() { return win; }
 public final android.content.Context getContext() { return ctx; } public void setContentView(android.view.View v) { content = v; } }
