# RetroArch Thor: built-in bottom screen companion

A RetroArch fork for the AYN Thor. While a game runs, a companion
screen opens on the bottom display by itself and reads the game's RAM
directly, every frame. There is no network command setting to turn on,
no second app to launch, and no UDP polling.

## What happens on the device

1. You start a game in RetroArch Thor (top screen).
2. The bottom screen opens the companion for that game.
3. It updates in step with the game, every emulated frame.
4. Switch games and it rebuilds for the new one. Close the game and it goes away.

It never takes controller focus, so your buttons keep driving the game.
Touch on the bottom screen still works.

To switch it off: set the `thor_companion_enabled` preference to false,
or launch with the intent extra `COMPANION=false` (handy from a frontend).

## How it works

```
retro_run()  ->  companion_bridge_on_frame()     (C, runloop thread)
                 copies only the watched RAM ranges
                 into a snapshot, using the same
                 address map as READ_CORE_MEMORY
                        |
                        |  JNI: nativeSnapshot()  (once per vsync)
                        v
CompanionBridge  ->  CompanionMemory.read(addr, len)
CompanionController  ->  Presentation on the second display
                         hosting a CompanionScreen
```

* `companion/companion_bridge.c`: native snapshot, never blocks emulation
  (if the UI is mid-copy it skips that frame).
* `pkg/android/phoenix-common/src/com/retroarch/browser/companion/`:
  bridge, controller (auto show/hide), presentation, built-in debug screen.
* `pkg/android/companion-api/`: the two interfaces a companion library
  compiles against (`CompanionMemory`, `CompanionScreen`).
* `pkg/android/companion-api/thorhub-adapter/`: Kotlin files to drop into
  Thor Game Hub.

Reading memory: the first read of a new range returns null and the
range is copied from the next frame on. Nearby reads are merged into one
range; ranges nobody reads for 5 seconds are dropped. `watch()` pins a
range so even the first read has data.

## Build the APK

**Easiest (no Android Studio):** push this branch to your GitHub fork,
open Actions, run "Android Thor build", download the APK artifact and
sideload it. It installs as "RetroArch Thor" next to regular RetroArch.

**Locally:** with the Android SDK and NDK 29 installed,

```
cd pkg/android/phoenix
./gradlew assembleThorDebug
```

Without a companion library bundled, the bottom screen shows the
built-in debug screen (game name, frame rate, live hex of work RAM). Use
it to confirm the memory path works on the Thor first.

## Plug in Thor Game Hub

1. Make the hub's companion UI an Android **library** module
   (`com.android.library`; leave plugin versions off, the RetroArch build
   supplies AGP).
2. Add `compileOnly project(':companion-api')` to its dependencies.
3. Copy the two files from `pkg/android/companion-api/thorhub-adapter/`
   into it, fill in your root composable in `ThorHubCompanionScreen`,
   and add its `<meta-data>` line to the library manifest.
4. Give your game modules `DirectRetroArchClient` in place of the UDP
   client. Same `getStatus()` text and `readMemory()` shape, so
   `GameRegistry` detection and the address tables don't change.
5. Build with `-PthorHubDir=/path/to/hub/library-module`, or fill in the
   two inputs when running the GitHub Action.

## Tests run on this branch

* Linux build of RetroArch with the bridge enabled, plus a test libretro
  core exposing a GBA-style memory map, calling the JNI entry points
  through a real JVM inside RetroArch: 593 checks, 0 failures (per-frame
  values, mirrored addresses, ranges spanning two regions, unmapped
  ranges, game name).
* Java bridge and controller against the real native code with
  stand-in Android classes: 42 checks, 0 failures (auto-watch, merging,
  expiry, pause detection, game switch, unload, display add/remove,
  focus flags, fallback on a broken screen, off switches).
* Not yet run: a Gradle/NDK APK build and on-device testing.
