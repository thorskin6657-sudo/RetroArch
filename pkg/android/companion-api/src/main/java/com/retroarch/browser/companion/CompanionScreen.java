package com.retroarch.browser.companion;

import android.content.Context;
import android.view.View;

/**
 * A UI shown on the second display (the Thor's bottom screen) while a
 * game runs.
 *
 * To plug in your own (for example Thor Game Hub), implement this,
 * give the class a public no-argument constructor, and declare it in
 * the manifest inside &lt;application&gt;:
 *
 * <pre>
 * &lt;meta-data android:name="com.retroarch.companion.SCREEN"
 *            android:value="com.example.MyCompanionScreen" /&gt;
 * </pre>
 *
 * Without one, RetroArch shows {@link DebugCompanionScreen}.
 */
public interface CompanionScreen {

  /**
   * Build the view. {@code context} belongs to the second display, so
   * use it (not the activity) for inflating and measuring.
   */
  View onCreateView(Context context, CompanionMemory memory);

  /** The screen is going away (game closed, display removed, app stopped). */
  void onDestroy();
}
