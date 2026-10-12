#!/usr/bin/env python3
"""Build the JNI/Java harness; optionally run the core inside a Linux RetroArch."""
import argparse
import os
from pathlib import Path
import shlex
import subprocess

root = Path(__file__).resolve().parents[2]
out = root / "companion/tests/build"
out.mkdir(exist_ok=True)
jdk = Path(os.environ["JAVA_HOME"])
jni = Path(os.environ.get("COMPANION_JNI_INCLUDE", str(jdk / "include")))
cc = shlex.split(os.environ.get("CC", "gcc"))
platform = "win32" if os.name == "nt" else "linux"
includes = ["-I" + str(p) for p in (root, root / "libretro-common/include", jni, jni / platform)]

def run(args, **kwargs):
    print("+", " ".join(str(a) for a in args), flush=True)
    return subprocess.run(args, check=True, **kwargs)

parser = argparse.ArgumentParser()
parser.add_argument("--e2e", type=Path)
args = parser.parse_args()
run(cc + ["-shared", "-O2", "-pthread"] + ([] if os.name == "nt" else ["-fPIC"]) + includes +
    [str(root / p) for p in ("companion/tests/java/mock_runloop.c", "companion/companion_bridge.c",
                            "libretro-common/string/stdstring.c", "libretro-common/compat/compat_strl.c",
                            "libretro-common/encodings/encoding_utf.c")] +
    ["-o", str(out / "libretroarch-activity.so")])
sources = list((root / "companion/tests/java/stubs").rglob("*.java"))
sources += list((root / "pkg/android/companion-api/src/main/java").rglob("*.java"))
sources += list((root / "pkg/android/phoenix-common/src/com/retroarch/browser/companion").glob("*.java"))
sources.append(root / "companion/tests/java/CompanionTest.java")
run([str(jdk / "bin/javac"), "-d", str(out)] + [str(p) for p in sources])
run([str(jdk / "bin/java"), "-cp", str(out), "CompanionTest"], cwd=out)
if args.e2e:
    if os.name == "nt":
        raise SystemExit("The in-RetroArch runner requires Linux; JNI/Java tests support both platforms.")
    core = out / "companion_testcore.so"
    run(cc + ["-shared", "-fPIC", "-O2", "-D_GNU_SOURCE"] + includes + [str(root / "companion/tests/e2e_testcore.c"), "-ldl", "-o", str(core)])
    (out / "testgame.gba").write_bytes(b"companion-test-fixture")
    (out / "test.cfg").write_text('video_driver = "null"\naudio_driver = "null"\ninput_driver = "null"\nmenu_driver = "null"\nvideo_threaded = "false"\npause_nonactive = "false"\nload_dummy_on_core_shutdown = "false"\n')
    env = dict(os.environ, COMPANION_JVM_LIB=str(jdk / "lib/server/libjvm.so"))
    try:
        result = run([str(args.e2e.resolve()), "--verbose", "--config", str(out / "test.cfg"), "-L", str(core), str(out / "testgame.gba")],
                     env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=90)
    except subprocess.TimeoutExpired as error:
        print((error.stdout or b"").decode(errors="replace"), flush=True)
        raise
    print(result.stdout)
    if "E2E RESULT: PASS" not in result.stdout:
        raise SystemExit("In-RetroArch E2E test did not pass")
