package android.app; public class Activity extends android.content.Context {
 public android.content.Intent intent = new android.content.Intent(); public android.content.pm.PackageManager pm = new android.content.pm.PackageManager();
 public Object displayService; public android.view.Display display;
 public android.content.Intent getIntent() { return intent; }
 @Override public Object getSystemService(String n) { return displayService; }
 @Override public android.content.pm.PackageManager getPackageManager() { return pm; }
 public android.view.Display getDisplay() { return display; }
 public android.view.WindowManager getWindowManager() { return () -> display; } }
