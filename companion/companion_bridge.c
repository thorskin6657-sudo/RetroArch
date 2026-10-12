/* Thor companion bridge. See companion_bridge.h. */

#include <string.h>
#include <stdlib.h>
#include <pthread.h>
#include <stdint.h>
#include <retro_endianness.h>

#include <jni.h>

#include <file/file_path.h>
#include <string/stdstring.h>

#include "companion_bridge.h"
#include "../runloop.h"
#include "../core_info.h"
#include "../content.h"
#include "../paths.h"

#define CB_MAX_WATCHES      64
#define CB_MAX_WATCH_LEN    0x10000      /* 64 KiB per range */
#define CB_MAX_TOTAL_BYTES  0x80000      /* 512 KiB across all ranges */
#define CB_STATUS_EVERY     30           /* refresh status string every N frames */

typedef struct
{
   uint32_t address;
   uint32_t length;
   uint32_t offset;  /* into snapshot buffer */
} cb_watch_t;

typedef struct
{
   pthread_mutex_t lock;

   cb_watch_t watches[CB_MAX_WATCHES];
   unsigned   num_watches;
   uint32_t   total_bytes;

   uint8_t   *snapshot;
   uint8_t    valid[CB_MAX_WATCHES]; /* 1 if the whole range resolved this frame */

   int64_t    frame;                  /* increments every copied frame */
   unsigned   frames_since_status;
   char       status[512];            /* GET_STATUS-compatible body */
   int        content_loaded;
} cb_state_t;

static cb_state_t cb_st = {
   PTHREAD_MUTEX_INITIALIZER
};

typedef struct
{
   const uint8_t *data;
   size_t size;
   uint32_t base;
   unsigned byte_xor;
} cb_ram_t;

static int cb_system_ram(runloop_state_t *st, cb_ram_t *ram)
{
   core_info_t *info = NULL;
   const char *system;
   const char *name = st->system.info.library_name;
   int gb, n64;

   core_info_get_current_core(&info);
   system = info ? info->system_id : NULL;
   gb = (system && (!strcmp(system, "game_boy")
         || !strcmp(system, "game_boy_color")
         || !strcmp(system, "gb") || !strcmp(system, "gbc")))
      || (name && (!strcmp(name, "Gambatte") || !strcmp(name, "SameBoy")));
   /* Mupen exposes host-endian uint32_t RDRAM, not CPU byte order. */
   n64 = name && (strstr(name, "Mupen64Plus") != NULL);
   if ((!gb && !n64) || !st->current_core.retro_get_memory_data
         || !st->current_core.retro_get_memory_size)
      return 0;

   ram->data = (const uint8_t*)st->current_core.retro_get_memory_data(
         RETRO_MEMORY_SYSTEM_RAM);
   ram->size = st->current_core.retro_get_memory_size(RETRO_MEMORY_SYSTEM_RAM);
   ram->base = gb ? 0xC000u : 0x80000000u;
   ram->byte_xor = n64 && is_little_endian() ? 3 : 0;
   if (!ram->data || !ram->size || ram->size > (gb ? 0x8000u : 0x800000u)
         || (n64 && (ram->size & 3)))
      return 0;
   return 1;
}

static int cb_copy_system_ram(const cb_ram_t *ram,
      uint32_t address, uint32_t len, uint8_t *dst)
{
   size_t offset, i;
   if (address < ram->base)
      return 0;
   offset = (size_t)(address - ram->base);
   if (offset > ram->size || len > ram->size - offset)
      return 0;
   if (!ram->byte_xor)
      memcpy(dst, ram->data + offset, len);
   else
      for (i = 0; i < len; i++)
         dst[i] = ram->data[(offset + i) ^ ram->byte_xor];
   return 1;
}

/* Same descriptor matching RetroArch's READ_CORE_MEMORY uses
 * (command.c), so addresses mean exactly the same thing. */
static const rarch_memory_descriptor_t *cb_find_descriptor(
      const rarch_memory_map_t *mmap, unsigned address, size_t *offset)
{
   const rarch_memory_descriptor_t *desc = mmap->descriptors;
   const rarch_memory_descriptor_t *end  = desc + mmap->num_descriptors;

   for (; desc < end; desc++)
   {
      if (desc->core.select == 0)
      {
         if (     address >= desc->core.start
               && address <  desc->core.start + desc->core.len)
         {
            *offset = address - desc->core.start;
            return desc;
         }
      }
      else if (((desc->core.start ^ address) & desc->core.select) == 0)
      {
         unsigned desc_offset = address - (unsigned)desc->core.start;
         unsigned mask        = (unsigned)desc->core.disconnect;

         while (mask)
         {
            const unsigned tmp = (mask - 1) & ~mask;
            desc_offset = (desc_offset & tmp) | ((desc_offset >> 1) & ~tmp);
            mask        = (mask & (mask - 1)) >> 1;
         }

         *offset = desc_offset;
         if (desc_offset < desc->core.len)
            return desc;
      }
   }

   return NULL;
}

/* Copies [address, address+len) into dst, crossing descriptors if
 * needed. Returns 1 if every byte resolved. */
static int cb_copy_range(const rarch_memory_map_t *mmap,
      uint32_t address, uint32_t len, uint8_t *dst)
{
   while (len > 0)
   {
      size_t   offset;
      uint32_t chunk;
      const rarch_memory_descriptor_t *desc =
         cb_find_descriptor(mmap, address, &offset);

      if (!desc || !desc->core.ptr || offset >= desc->core.len)
         return 0;

      chunk = (uint32_t)(desc->core.len - offset);
      if (chunk > len)
         chunk = len;

      memcpy(dst,
            (const uint8_t*)desc->core.ptr + desc->core.offset + offset,
            chunk);

      dst     += chunk;
      address += chunk;
      len     -= chunk;
   }
   return 1;
}

static void cb_refresh_status(runloop_state_t *runloop_st)
{
   size_t      _len      = 0;
   core_info_t *core_info = NULL;
   const char  *base     = path_get(RARCH_PATH_BASENAME);

   core_info_get_current_core(&core_info);

   if (core_info && core_info->system_id)
      strlcpy_append(cb_st.status, sizeof(cb_st.status), &_len,
            core_info->system_id);
   else if (runloop_st->system.info.library_name)
      strlcpy_append(cb_st.status, sizeof(cb_st.status), &_len,
            runloop_st->system.info.library_name);
   else
      strlcpy_append(cb_st.status, sizeof(cb_st.status), &_len, "UNKNOWN");

   strlcpy_append(cb_st.status, sizeof(cb_st.status), &_len, ",");
   strlcpy_append(cb_st.status, sizeof(cb_st.status), &_len,
         (base && *base) ? path_basename(base) : "UNKNOWN");

   if (_len == 0)
      cb_st.status[0] = '\0';
}

void companion_bridge_on_frame(void)
{
   unsigned i;
   runloop_state_t *runloop_st;
   const rarch_memory_map_t *mmap;
   cb_ram_t ram;
   int have_ram;

   /* Never stall emulation: if the UI thread is mid-copy, skip
    * this frame's snapshot. The UI just sees the previous frame. */
   if (pthread_mutex_trylock(&cb_st.lock) != 0)
      return;

   runloop_st = runloop_state_get_ptr();

   if (!cb_st.content_loaded || cb_st.frames_since_status >= CB_STATUS_EVERY)
   {
      cb_refresh_status(runloop_st);
      cb_st.frames_since_status = 0;
   }
   cb_st.frames_since_status++;
   cb_st.content_loaded = 1;

   mmap = &runloop_st->system.mmaps;
   have_ram = mmap->num_descriptors == 0 && cb_system_ram(runloop_st, &ram);

   for (i = 0; i < cb_st.num_watches; i++)
   {
      const cb_watch_t *w = &cb_st.watches[i];
      cb_st.valid[i] = (mmap->num_descriptors > 0)
         ? (uint8_t)cb_copy_range(mmap, w->address, w->length,
               cb_st.snapshot + w->offset)
         : (have_ram ? (uint8_t)cb_copy_system_ram(&ram,
               w->address, w->length, cb_st.snapshot + w->offset) : 0);
   }

   cb_st.frame++;
   pthread_mutex_unlock(&cb_st.lock);
}

void companion_bridge_on_content_unloaded(void)
{
   pthread_mutex_lock(&cb_st.lock);
   cb_st.content_loaded = 0;
   cb_st.status[0]      = '\0';
   memset(cb_st.valid, 0, sizeof(cb_st.valid));
   pthread_mutex_unlock(&cb_st.lock);
}

/* ---------------------------------------------------------------- */
/* JNI: com.retroarch.browser.companion.CompanionBridge             */
/* ---------------------------------------------------------------- */

/* Replaces the watch list. Returns the number of watches accepted
 * (ranges past the caps are dropped). */
JNIEXPORT jint JNICALL
Java_com_retroarch_browser_companion_CompanionBridge_nativeSetWatches(
      JNIEnv *env, jclass clazz, jintArray j_addrs, jintArray j_lens)
{
   jint     n, i;
   jint    *addrs, *lens;
   unsigned count = 0;
   uint32_t total = 0;
   uint8_t *buf;
   cb_watch_t tmp[CB_MAX_WATCHES];

   if (!j_addrs || !j_lens)
      return 0;

   n = (*env)->GetArrayLength(env, j_addrs);
   if ((*env)->GetArrayLength(env, j_lens) < n)
      n = (*env)->GetArrayLength(env, j_lens);
   if (n > CB_MAX_WATCHES)
      n = CB_MAX_WATCHES;

   addrs = (*env)->GetIntArrayElements(env, j_addrs, NULL);
   lens  = (*env)->GetIntArrayElements(env, j_lens,  NULL);

   for (i = 0; i < n; i++)
   {
      uint32_t len = (uint32_t)lens[i];
      if (len == 0 || len > CB_MAX_WATCH_LEN)
         continue;
      if (total + len > CB_MAX_TOTAL_BYTES)
         break;
      tmp[count].address = (uint32_t)addrs[i];
      tmp[count].length  = len;
      tmp[count].offset  = total;
      total += len;
      count++;
   }

   (*env)->ReleaseIntArrayElements(env, j_addrs, addrs, JNI_ABORT);
   (*env)->ReleaseIntArrayElements(env, j_lens,  lens,  JNI_ABORT);

   buf = total ? (uint8_t*)calloc(1, total) : NULL;
   if (total && !buf)
      return 0;

   pthread_mutex_lock(&cb_st.lock);
   free(cb_st.snapshot);
   cb_st.snapshot    = buf;
   cb_st.total_bytes = total;
   cb_st.num_watches = count;
   memcpy(cb_st.watches, tmp, sizeof(cb_watch_t) * count);
   memset(cb_st.valid, 0, sizeof(cb_st.valid));
   pthread_mutex_unlock(&cb_st.lock);

   return (jint)count;
}

/* Copies the latest snapshot into out (packed in watch order) and
 * the per-watch valid flags into valid_out. Returns the frame number
 * of the snapshot, or -1 if no content is loaded. */
JNIEXPORT jlong JNICALL
Java_com_retroarch_browser_companion_CompanionBridge_nativeSnapshot(
      JNIEnv *env, jclass clazz, jbyteArray out, jbooleanArray valid_out)
{
   jlong frame;
   unsigned i, nvalid;

   pthread_mutex_lock(&cb_st.lock);

   if (!cb_st.content_loaded)
   {
      pthread_mutex_unlock(&cb_st.lock);
      return -1;
   }

   if (out && cb_st.snapshot)
   {
      jsize cap = (*env)->GetArrayLength(env, out);
      jsize n   = (jsize)cb_st.total_bytes < cap
         ? (jsize)cb_st.total_bytes : cap;
      (*env)->SetByteArrayRegion(env, out, 0, n,
            (const jbyte*)cb_st.snapshot);
   }

   if (valid_out)
   {
      jboolean flags[CB_MAX_WATCHES];
      nvalid = (unsigned)(*env)->GetArrayLength(env, valid_out);
      if (nvalid > cb_st.num_watches)
         nvalid = cb_st.num_watches;
      for (i = 0; i < nvalid; i++)
         flags[i] = cb_st.valid[i] ? JNI_TRUE : JNI_FALSE;
      if (nvalid)
         (*env)->SetBooleanArrayRegion(env, valid_out, 0, (jsize)nvalid, flags);
   }

   frame = (jlong)cb_st.frame;
   pthread_mutex_unlock(&cb_st.lock);
   return frame;
}

/* "system_id,rom_basename" for the running content, or null. */
JNIEXPORT jstring JNICALL
Java_com_retroarch_browser_companion_CompanionBridge_nativeGetStatus(
      JNIEnv *env, jclass clazz)
{
   char copy[sizeof(cb_st.status)];

   pthread_mutex_lock(&cb_st.lock);
   if (!cb_st.content_loaded || !cb_st.status[0])
   {
      pthread_mutex_unlock(&cb_st.lock);
      return NULL;
   }
   strlcpy(copy, cb_st.status, sizeof(copy));
   pthread_mutex_unlock(&cb_st.lock);

   return (*env)->NewStringUTF(env, copy);
}
