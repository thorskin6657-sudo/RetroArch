/* Fake RetroArch runtime around companion_bridge.c so the Java side
 * can be exercised in a plain JVM. A "frame" = test calls nativeStep(). */
#include <string.h>
#include <stdint.h>
#include <jni.h>
#include <retro_endianness.h>
#include "runloop.h"
#include "core_info.h"
#include "paths.h"
#include "companion/companion_bridge.h"

static runloop_state_t st;
static rarch_memory_descriptor_t descs[2];
static uint8_t ewram[256*1024], iwram[32*1024];
static char base[64] = "Oracle of Seasons (USA)";
static core_info_t ci;
static unsigned mode;
static uint8_t fallback_ram[0x8000];
static void *test_memory_data(unsigned id)
{
   return id == RETRO_MEMORY_SYSTEM_RAM && mode != 5 ? fallback_ram : NULL;
}
static size_t test_memory_size(unsigned id)
{
   return id == RETRO_MEMORY_SYSTEM_RAM ? (mode == 2 ? 0x8000 : 0x2000) : 0;
}

runloop_state_t *runloop_state_get_ptr(void) { return &st; }
bool core_info_get_current_core(core_info_t **c) {
   ci.system_id = mode == 1 ? "game_boy" : mode == 2 ? "game_boy_color"
      : mode == 3 || mode == 5 ? "nintendo_64" : mode == 4 ? "unknown" : "game_boy_advance";
   *c = &ci; return true;
}
const char *path_get(enum rarch_path_type t) { return base; }

static void setup(void) {
   memset(descs, 0, sizeof descs);
   descs[0].core.ptr = ewram; descs[0].core.start = 0x02000000; descs[0].core.len = sizeof ewram;
   descs[1].core.ptr = iwram; descs[1].core.start = 0x03000000; descs[1].core.len = sizeof iwram;
   st.system.mmaps.descriptors = descs; st.system.mmaps.num_descriptors = 2;
}

JNIEXPORT void JNICALL Java_CompanionTest_nativeStep(JNIEnv *e, jclass c, jint frameValue) {
   int i;
   if (!mode && !st.system.mmaps.descriptors) setup();
   /* game writes: counter at 0x02000100, a byte table at 0x02000200.. */
   memcpy(ewram + 0x100, &frameValue, 4);
   for (i = 0; i < 64; i++) ewram[0x200 + i] = (uint8_t)(frameValue + i);
   iwram[0x10] = (uint8_t)(frameValue * 7);
   for (i = 0; i < (mode == 2 ? 0x8000 : 0x2000); i++)
      fallback_ram[i ^ (mode == 3 && is_little_endian() ? 3 : 0)]
         = (uint8_t)(frameValue + i);
   companion_bridge_on_frame();
}
JNIEXPORT void JNICALL Java_CompanionTest_nativeMode(JNIEnv *e, jclass c, jint value) {
   companion_bridge_on_content_unloaded();
   mode = (unsigned)value;
   st.current_core_type = mode == 7 ? CORE_TYPE_DUMMY : CORE_TYPE_PLAIN;
   st.system.mmaps.descriptors = NULL;
   st.system.mmaps.num_descriptors = 0;
   st.system.info.library_name = mode == 1 || mode == 2 ? "Gambatte"
      : mode == 3 || mode == 5 ? "Mupen64Plus-Next" : "TestUnknown";
   st.current_core.retro_get_memory_data = test_memory_data;
   st.current_core.retro_get_memory_size = test_memory_size;
   if (mode == 6) setup();
}
JNIEXPORT void JNICALL Java_CompanionTest_nativeUnload(JNIEnv *e, jclass c) { companion_bridge_on_content_unloaded(); }
JNIEXPORT void JNICALL Java_CompanionTest_nativeSetRom(JNIEnv *e, jclass c, jstring s) {
   const char *u = (*e)->GetStringUTFChars(e, s, NULL); strncpy(base, u, sizeof base - 1); (*e)->ReleaseStringUTFChars(e, s, u); }
const char *path_basename(const char *p) { const char *s = strrchr(p, '/'); return s ? s + 1 : p; }
