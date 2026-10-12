/* Libretro test core: GBA-like memory map + calls the companion
 * bridge JNI entry points through a real JVM, verifying that each
 * frame's snapshot holds the bytes written in the previous frame. */
#include <stdio.h>
#include <string.h>
#include <stdint.h>
#include <dlfcn.h>
#include <jni.h>
#include "libretro.h"

static retro_environment_t env_cb;
static retro_video_refresh_t video_cb;
static uint8_t ewram[256*1024], iwram[32*1024], io_a[16], io_b[16];
static uint16_t fb[16*16];
static unsigned frame; static int fails, checks;
static JNIEnv *jenv;
typedef jint (*set_t)(JNIEnv*, jclass, jintArray, jintArray);
typedef jlong (*snap_t)(JNIEnv*, jclass, jbyteArray, jbooleanArray);
typedef jstring (*stat_t)(JNIEnv*, jclass);
static set_t f_set; static snap_t f_snap; static stat_t f_stat;

#define CHECK(c, ...) do { checks++; if (!(c)) { fails++; fprintf(stderr, "FAIL f%u: ", frame); fprintf(stderr, __VA_ARGS__); fputc('\n', stderr);} } while(0)

static void start_jvm(void) {
   void *h = dlopen("/usr/lib/jvm/java-21-openjdk-amd64/lib/server/libjvm.so", RTLD_NOW|RTLD_GLOBAL);
   jint (*create)(JavaVM**, void**, void*) = h ? dlsym(h, "JNI_CreateJavaVM") : NULL;
   JavaVMInitArgs a = {0}; JavaVM *vm; a.version = JNI_VERSION_1_8;
   if (!create || create(&vm, (void**)&jenv, &a) != JNI_OK) { fprintf(stderr, "no JVM\n"); fails++; return; }
   f_set  = (set_t) dlsym(RTLD_DEFAULT, "Java_com_retroarch_browser_companion_CompanionBridge_nativeSetWatches");
   f_snap = (snap_t)dlsym(RTLD_DEFAULT, "Java_com_retroarch_browser_companion_CompanionBridge_nativeSnapshot");
   f_stat = (stat_t)dlsym(RTLD_DEFAULT, "Java_com_retroarch_browser_companion_CompanionBridge_nativeGetStatus");
   CHECK(f_set && f_snap && f_stat, "JNI symbols not found in RetroArch");
}

/* watches: EWRAM u32 counter, IWRAM byte via mirrored address,
 * range crossing two descriptors, unmapped range */
static jint W_ADDR[] = { 0x02000100, 0x03008000, 0x0400000C, 0x09000000 };
static jint W_LEN[]  = { 4, 1, 8, 4 };

static void bridge_check(void) {
   if (!f_snap) return;
   if (frame == 1) {
      jintArray A = (*jenv)->NewIntArray(jenv, 4), L = (*jenv)->NewIntArray(jenv, 4);
      (*jenv)->SetIntArrayRegion(jenv, A, 0, 4, W_ADDR);
      (*jenv)->SetIntArrayRegion(jenv, L, 0, 4, W_LEN);
      CHECK(f_set(jenv, NULL, A, L) == 4, "setWatches count");
      return;
   }
   if (frame < 3) return; /* frame 2 is the first copy after watches set */
   jbyteArray out = (*jenv)->NewByteArray(jenv, 17);
   jbooleanArray v = (*jenv)->NewBooleanArray(jenv, 4);
   jlong fr = f_snap(jenv, NULL, out, v);
   jbyte b[17]; jboolean vb[4];
   (*jenv)->GetByteArrayRegion(jenv, out, 0, 17, b);
   (*jenv)->GetBooleanArrayRegion(jenv, v, 0, 4, vb);
   unsigned prev = frame - 1; uint32_t c; memcpy(&c, b, 4);
   CHECK(fr > 0, "snapshot frame %lld", (long long)fr);
   CHECK(vb[0] && c == prev, "EWRAM counter got %u want %u (valid=%d)", c, prev, vb[0]);
   CHECK(vb[1] && (uint8_t)b[4] == (uint8_t)(prev*3), "IWRAM mirror got %u want %u", (uint8_t)b[4], (uint8_t)(prev*3));
   uint8_t want[8]; for (int i=0;i<8;i++) want[i]=(uint8_t)(prev+i);
   CHECK(vb[2] && !memcmp(b+5, want, 8), "cross-descriptor range mismatch");
   CHECK(!vb[3], "unmapped range should be invalid");
   if (frame == 10) {
      jstring s = f_stat(jenv, NULL);
      const char *cs = s ? (*jenv)->GetStringUTFChars(jenv, s, NULL) : NULL;
      fprintf(stderr, "status: %s\n", cs ? cs : "(null)");
      CHECK(cs && !strcmp(cs, "CompanionTest,testgame"), "status should name the ROM");
   }
   (*jenv)->DeleteLocalRef(jenv, out); (*jenv)->DeleteLocalRef(jenv, v);
}

RETRO_API void retro_run(void) {
   frame++;
   if (frame == 1) start_jvm();
   bridge_check();
   /* write this frame's values */
   memcpy(ewram + 0x100, &frame, 4);
   iwram[0] = (uint8_t)(frame*3);
   for (int i=0;i<8;i++) { uint8_t x = (uint8_t)(frame+i); if (i<4) io_a[12+i]=x; else io_b[i-4]=x; }
   video_cb(fb, 16, 16, 32);
   if (frame == 120) {
      fprintf(stderr, "E2E RESULT: %s (%d checks, %d failures)\n", fails ? "FAIL" : "PASS", checks, fails);
      env_cb(RETRO_ENVIRONMENT_SHUTDOWN, NULL);
   }
}

RETRO_API void retro_set_environment(retro_environment_t cb) { env_cb = cb; }
RETRO_API void retro_set_video_refresh(retro_video_refresh_t cb) { video_cb = cb; }
RETRO_API void retro_set_audio_sample(retro_audio_sample_t cb) {}
RETRO_API void retro_set_audio_sample_batch(retro_audio_sample_batch_t cb) {}
RETRO_API void retro_set_input_poll(retro_input_poll_t cb) {}
RETRO_API void retro_set_input_state(retro_input_state_t cb) {}
RETRO_API void retro_init(void) {}
RETRO_API void retro_deinit(void) {}
RETRO_API unsigned retro_api_version(void) { return RETRO_API_VERSION; }
RETRO_API void retro_get_system_info(struct retro_system_info *i) {
   memset(i,0,sizeof(*i)); i->library_name="CompanionTest"; i->library_version="1"; i->valid_extensions="gba"; }
RETRO_API void retro_get_system_av_info(struct retro_system_av_info *i) {
   memset(i,0,sizeof(*i)); i->geometry.base_width=i->geometry.max_width=16; i->geometry.base_height=i->geometry.max_height=16;
   i->timing.fps=60; i->timing.sample_rate=32768; }
RETRO_API void retro_set_controller_port_device(unsigned p, unsigned d) {}
RETRO_API void retro_reset(void) {}
RETRO_API size_t retro_serialize_size(void) { return 0; }
RETRO_API bool retro_serialize(void *d, size_t s) { return false; }
RETRO_API bool retro_unserialize(const void *d, size_t s) { return false; }
RETRO_API void retro_cheat_reset(void) {}
RETRO_API void retro_cheat_set(unsigned i, bool e, const char *c) {}
RETRO_API bool retro_load_game(const struct retro_game_info *g) {
   enum retro_pixel_format f = RETRO_PIXEL_FORMAT_RGB565; env_cb(RETRO_ENVIRONMENT_SET_PIXEL_FORMAT, &f);
   static struct retro_memory_descriptor d[4];
   memset(d,0,sizeof d);
   d[0].ptr=ewram; d[0].start=0x02000000; d[0].len=sizeof ewram;
   /* IWRAM 32KB mirrored across 0x03000000-0x03FFFFFF, like mGBA */
   d[1].ptr=iwram; d[1].start=0x03000000; d[1].select=0xFF000000; d[1].len=sizeof iwram;
   d[2].ptr=io_a; d[2].start=0x04000000; d[2].len=16;
   d[3].ptr=io_b; d[3].start=0x04000010; d[3].len=16;
   static struct retro_memory_map m; m.descriptors=d; m.num_descriptors=4;
   env_cb(RETRO_ENVIRONMENT_SET_MEMORY_MAPS, &m);
   return true; }
RETRO_API bool retro_load_game_special(unsigned t, const struct retro_game_info *g, size_t n) { return false; }
RETRO_API void retro_unload_game(void) {}
RETRO_API unsigned retro_get_region(void) { return RETRO_REGION_NTSC; }
RETRO_API void *retro_get_memory_data(unsigned id) { return id==RETRO_MEMORY_SYSTEM_RAM ? ewram : NULL; }
RETRO_API size_t retro_get_memory_size(unsigned id) { return id==RETRO_MEMORY_SYSTEM_RAM ? sizeof ewram : 0; }
