package com.retroarch.browser.companion;

import android.app.Presentation;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

/** The window on the second display that hosts a {@link CompanionScreen}. */
final class CompanionPresentation extends Presentation {

  private static final String TAG = "ThorCompanion";

  private CompanionScreen screen;
  private final CompanionMemory memory;

  CompanionPresentation(Context outerContext, Display display,
      CompanionScreen screen, CompanionMemory memory) {
    super(outerContext, display);
    this.screen = screen;
    this.memory = memory;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    Window w = getWindow();
    if (w != null) {
      // Never take key focus: the controller must keep driving the
      // game on the top screen. Touch on the bottom screen still works.
      w.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
          | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
          | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    View content;
    try {
      content = screen.onCreateView(getContext(), memory);
    } catch (Throwable t) {
      // A broken companion screen must never take the emulator down.
      Log.e(TAG, "Companion screen failed to build, using debug screen", t);
      screen = new DebugCompanionScreen();
      content = screen.onCreateView(getContext(), memory);
    }
    setContentView(content);
  }

  @Override
  protected void onStop() {
    try {
      screen.onDestroy();
    } catch (Throwable t) {
      Log.e(TAG, "Companion screen onDestroy failed", t);
    }
    super.onStop();
  }
}
