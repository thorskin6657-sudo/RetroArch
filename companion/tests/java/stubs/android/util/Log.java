package android.util; public final class Log {
 public static int i(String t, String m) { System.out.println("I/" + t + ": " + m); return 0; }
 public static int w(String t, String m, Throwable e) { System.out.println("W/" + t + ": " + m + " " + e); return 0; }
 public static int e(String t, String m, Throwable e) { System.out.println("E/" + t + ": " + m + " " + e); return 0; } }
