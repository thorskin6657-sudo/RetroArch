package android.view; public interface WindowManager { Display getDefaultDisplay();
 class LayoutParams { public static final int FLAG_NOT_FOCUSABLE = 8, FLAG_NOT_TOUCH_MODAL = 32, FLAG_KEEP_SCREEN_ON = 128; }
 class InvalidDisplayException extends RuntimeException { public InvalidDisplayException(String s) { super(s); } }
 class BadTokenException extends RuntimeException {} }
