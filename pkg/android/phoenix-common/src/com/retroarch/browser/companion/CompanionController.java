package com.retroarch.browser.companion;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Choreographer;
import android.view.Display;
import android.view.WindowManager;

/**
 * Puts the companion screen on the second display (the AYN Thor's
 * bottom screen) automatically: it appears when a game starts, follows
 * game changes, and goes away when the game closes. On by default;
 * nothing to toggle.
 *
 * Owned by RetroActivityFuture: start() in onStart, stop() in onStop.
 */
public final class CompanionController implements DisplayManager.DisplayListener, Choreographer.FrameCallback {

  private static final String TAG = "ThorCompanion";

  /** Shared preference (in the app's default prefs) to turn the feature off. On by default. */
  public static final String PREF_ENABLED = "thor_companion_enabled";
  /** Launch intent extra: pass COMPANION=false to skip it for one launch. */
  public static final String EXTRA_ENABLED = "COMPANION";
  /** Manifest meta-data naming the CompanionScreen class to show. */
  public static final String META_SCREEN = "com.retroarch.companion.SCREEN";

  // Content can briefly report nothing while it reloads; wait this many
  // vsyncs before closing the bottom screen.
  private static final int HIDE_AFTER_POLLS = 45;

  private final Activity activity;
  private final DisplayManager displayManager;
  private final CompanionBridge bridge = CompanionBridge.get();
  private final Handler handler = new Handler(Looper.getMainLooper());

  private boolean started = false;
  private CompanionPresentation presentation;
  private String shownFor;     // game the current screen was built for
  private int pollsWithoutGame = 0;
  private int retryShowIn = 0;  // vsyncs until we look for a display again

  public CompanionController(Activity activity) {
    this.activity = activity;
    this.displayManager = (DisplayManager) activity.getSystemService(Context.DISPLAY_SERVICE);
  }

  public static boolean isEnabled(Activity activity) {
    if (activity.getIntent() != null && activity.getIntent().hasExtra(EXTRA_ENABLED))
      return activity.getIntent().getBooleanExtra(EXTRA_ENABLED, true);
    SharedPreferences prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(activity);
    return prefs.getBoolean(PREF_ENABLED, true);
  }

  public void start() {
    if (started || !isEnabled(activity))
      return;
    started = true;
    displayManager.registerDisplayListener(this, handler);
    Choreographer.getInstance().postFrameCallback(this);
  }

  public void stop() {
    if (!started)
      return;
    started = false;
    Choreographer.getInstance().removeFrameCallback(this);
    displayManager.unregisterDisplayListener(this);
    hide();
  }

  // Once per vsync while the activity is visible.
  @Override
  public void doFrame(long frameTimeNanos) {
    if (!started)
      return;

    try {
      bridge.poll();
      update();
    } catch (Throwable t) {
      Log.e(TAG, "Companion update failed", t);
    }

    Choreographer.getInstance().postFrameCallback(this);
  }

  private void update() {
    String game = bridge.getStatus();

    if (game == null) {
      if (presentation != null && ++pollsWithoutGame >= HIDE_AFTER_POLLS)
        hide();
      return;
    }
    pollsWithoutGame = 0;

    // The system dismisses the window itself if its display goes away.
    if (presentation != null && !presentation.isShowing())
      hide();

    if (presentation != null && game.equals(shownFor))
      return;

    // Looking up displays is an IPC call: don't do it every vsync
    // while there's no second screen to use.
    if (presentation == null && retryShowIn > 0) {
      retryShowIn--;
      return;
    }

    // New game, or not showing yet: (re)build the screen for it.
    hide();
    Display target = findSecondDisplay();
    if (target != null)
      show(target, game);
    if (presentation == null)
      retryShowIn = 60;
  }

  private Display findSecondDisplay() {
    int own = currentDisplayId();

    Display[] candidates = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION);
    for (Display d : candidates)
      if (d.getDisplayId() != own)
        return d;

    // Some handhelds don't flag the second panel as a presentation
    // display; fall back to any other display that's on.
    for (Display d : displayManager.getDisplays())
      if (d.getDisplayId() != own && d.getState() != Display.STATE_OFF)
        return d;

    return null;
  }

  private int currentDisplayId() {
    if (android.os.Build.VERSION.SDK_INT >= 30) {
      Display d = activity.getDisplay();
      if (d != null)
        return d.getDisplayId();
    }
    return activity.getWindowManager().getDefaultDisplay().getDisplayId();
  }

  private void show(Display display, String game) {
    CompanionPresentation p = new CompanionPresentation(activity, display, createScreen(), bridge);
    try {
      p.show();
      presentation = p;
      shownFor = game;
      Log.i(TAG, "Companion screen on display " + display.getDisplayId() + " for " + game);
    } catch (WindowManager.InvalidDisplayException | WindowManager.BadTokenException e) {
      Log.w(TAG, "Couldn't show companion screen on display " + display.getDisplayId(), e);
    }
  }

  private void hide() {
    if (presentation != null) {
      try {
        presentation.dismiss();
      } catch (Throwable ignored) {
      }
      presentation = null;
    }
    shownFor = null;
    pollsWithoutGame = 0;
  }

  private CompanionScreen createScreen() {
    try {
      ApplicationInfo info = activity.getPackageManager()
          .getApplicationInfo(activity.getPackageName(), PackageManager.GET_META_DATA);
      Bundle meta = info.metaData;
      String cls = meta != null ? meta.getString(META_SCREEN) : null;
      if (cls != null && !cls.isEmpty())
        return (CompanionScreen) Class.forName(cls).getDeclaredConstructor().newInstance();
    } catch (Throwable t) {
      Log.e(TAG, "Couldn't load companion screen, using debug screen", t);
    }
    return new DebugCompanionScreen();
  }

  // DisplayManager.DisplayListener: react when the bottom screen is
  // turned off/on or the second display otherwise comes and goes.
  @Override
  public void onDisplayAdded(int displayId) {
    retryShowIn = 0; // look for it on the next vsync
  }

  @Override
  public void onDisplayRemoved(int displayId) {
    if (presentation != null && presentation.getDisplay().getDisplayId() == displayId)
      hide();
    retryShowIn = 0;
  }

  @Override
  public void onDisplayChanged(int displayId) {
    // Presentation dismisses itself if its display goes away.
  }
}
