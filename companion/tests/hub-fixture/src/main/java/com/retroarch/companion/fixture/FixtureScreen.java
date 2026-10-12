package com.retroarch.companion.fixture;

import android.content.Context;
import android.view.View;
import android.widget.TextView;
import com.retroarch.browser.companion.CompanionMemory;
import com.retroarch.browser.companion.CompanionScreen;

/** Build fixture for compileOnly API and manifest discovery. */
public final class FixtureScreen implements CompanionScreen {
  public View onCreateView(Context context, CompanionMemory memory) {
    TextView view = new TextView(context);
    view.setText("Thor library build fixture");
    return view;
  }
  public void onDestroy() {}
}
