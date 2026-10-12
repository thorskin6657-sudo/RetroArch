package android.content; public class Intent { public java.util.HashMap<String,Boolean> x = new java.util.HashMap<>();
 public boolean hasExtra(String k) { return x.containsKey(k); } public boolean getBooleanExtra(String k, boolean d) { return x.getOrDefault(k, d); } }
