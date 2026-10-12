package android.app; public class Presentation extends Dialog { android.view.Display d;
 public Presentation(android.content.Context outer, android.view.Display display) { super(outer); d = display; }
 @Override public void show() { if (d.failShow) throw new android.view.WindowManager.InvalidDisplayException("gone"); super.show(); }
 public android.view.Display getDisplay() { return d; } }
