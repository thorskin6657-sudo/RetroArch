package android.view; public final class Choreographer { static final Choreographer I = new Choreographer();
 public interface FrameCallback { void doFrame(long t); }
 public java.util.List<FrameCallback> q = new java.util.ArrayList<>();
 public static Choreographer getInstance() { return I; }
 public void postFrameCallback(FrameCallback c) { q.add(c); } public void removeFrameCallback(FrameCallback c) { q.remove(c); }
 public void vsync() { java.util.List<FrameCallback> r = new java.util.ArrayList<>(q); q.clear(); for (FrameCallback c : r) c.doFrame(0); } }
