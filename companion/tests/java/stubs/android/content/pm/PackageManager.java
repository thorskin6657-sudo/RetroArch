package android.content.pm; public class PackageManager { public static final int GET_META_DATA = 128; public ApplicationInfo info = new ApplicationInfo();
 public ApplicationInfo getApplicationInfo(String p, int f) throws NameNotFoundException { return info; }
 public static class NameNotFoundException extends Exception {} }
