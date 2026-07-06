/**
 * Server-side @Max on the `listChannels` pageSize endpoint parameter
 * (APISIMP-CHANNEL-PAGESZ-MAX / CHANNELS-PAGESIZE-500-FIX-2026-07-02).
 *
 * Values above this cause a 400 constraint-violation response; the channel
 * table reads empty and the Trace3D render sees no channels.  All frontend
 * fetch sites must use this constant so a single-point change keeps them
 * in sync with the backend cap.
 */
export const MAX_CHANNEL_PAGE_SIZE = 500;
