package android.widget; public class TextView extends android.view.View { public CharSequence text = ""; public TextView(android.content.Context c) { super(c); }
 public void setTypeface(android.graphics.Typeface t) {} public void setTextSize(float s) {} public void setTextColor(int c) {} public void setGravity(int g) {}
 public void setText(CharSequence s) { text = s.toString(); } }
