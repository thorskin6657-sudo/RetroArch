package android.preference; public class PreferenceManager { public static boolean enabled = true;
 public static android.content.SharedPreferences getDefaultSharedPreferences(android.content.Context c) { return (k, d) -> enabled; } }
