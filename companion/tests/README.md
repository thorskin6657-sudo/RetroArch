Tests for the Thor companion bridge (desktop, no Android SDK needed).

e2e_testcore.c: libretro core with a GBA-style memory map that calls the
bridge's JNI functions through a real JVM inside a Linux RetroArch build
(`make HAVE_COMPANION_BRIDGE=1 COMPANION_JNI_INC="-I$JDK/include -I$JDK/include/linux"`,
linked with -rdynamic). Prints `E2E RESULT: PASS`.

java/: CompanionBridge + CompanionController against the real native
bridge (mock_runloop.c stands in for RetroArch) and stand-in Android
classes in stubs/. Build mock_runloop.c + ../companion_bridge.c into
libretroarch-activity.so, javac everything, run `java CompanionTest`.
