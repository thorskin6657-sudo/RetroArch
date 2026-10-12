/* Thor companion bridge
 *
 * Gives an in-process second-screen UI (the AYN Thor bottom screen)
 * direct, per-frame access to core memory, without the UDP network
 * command round trip.
 *
 * The UI registers the address ranges it cares about ("watches").
 * Right after each retro_run() the runloop copies those ranges out of
 * the core's memory map into a snapshot buffer. The UI copies the
 * snapshot out over JNI whenever it draws. Addresses are the same
 * ones READ_CORE_MEMORY uses (the core's memory map), so existing
 * companion modules keep their address tables unchanged.
 */

#ifndef COMPANION_BRIDGE_H__
#define COMPANION_BRIDGE_H__

#include <retro_common_api.h>

RETRO_BEGIN_DECLS

/* Called on the runloop thread right after retro_run(). */
void companion_bridge_on_frame(void);

/* Called when content is unloaded. */
void companion_bridge_on_content_unloaded(void);

RETRO_END_DECLS

#endif
