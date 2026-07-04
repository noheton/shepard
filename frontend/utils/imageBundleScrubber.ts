/**
 * MFFD-IMAGEBUNDLE-SCRUBBER-1 — pure helpers for the ImageBundle frame
 * scrubber. The scrubber is the unblocking UI for a researcher to
 * inspect ~38 TPS PNG frames per track (potentially 100s elsewhere)
 * without rendering every thumbnail at once. The strip itself is
 * virtualised via Vuetify's `v-virtual-scroll`; this helper computes
 * which **page** of the paginated backend endpoint needs to be
 * fetched for a given slider position so the scrubber jumps cleanly
 * to a frame without blocking on the full file list.
 *
 * Cardinality bound: a single bundle group of 313,538 frames would
 * be unusable as a strip anyway — the right shape is one bundle per
 * track (38 frames), 8,251 bundles per Collection, served on demand
 * by parent DataObject navigation. The paginate-by-page helper still
 * earns its keep on the long-tail bundles where N > 200.
 */

export interface FramePagePlan {
  /** 0-based page index to GET from the backend. */
  page: number;
  /** Offset of `frameIndex` within the page's `items[]`. */
  offsetInPage: number;
  /** Server-side page size to request. */
  pageSize: number;
}

/**
 * Compute the page (and offset within that page) the scrubber must
 * fetch to display frame `frameIndex`.
 *
 * @param frameIndex 0-based global frame index across the entire bundle
 * @param totalFrames total number of frames in the bundle (≥ 0)
 * @param pageSize    server-side page size to request
 *                    (clamped to [1, 1000] for safety)
 */
export function planPageForFrame(
  frameIndex: number,
  totalFrames: number,
  pageSize = 200,
): FramePagePlan {
  const size = Math.max(1, Math.min(1000, pageSize));
  if (totalFrames <= 0 || frameIndex < 0) {
    return { page: 0, offsetInPage: 0, pageSize: size };
  }
  // Clamp the requested frame index to the bundle length so a stale
  // slider position doesn't fetch a page past the end.
  const idx = Math.min(frameIndex, totalFrames - 1);
  const page = Math.floor(idx / size);
  const offsetInPage = idx - page * size;
  return { page, offsetInPage, pageSize: size };
}

/**
 * Decide whether a new page must be fetched given the page already
 * cached on the client. Returns true when the cached page does not
 * contain the requested frame.
 */
export function needsRefetch(
  cachedPage: number | null,
  desired: FramePagePlan,
): boolean {
  return cachedPage === null || cachedPage !== desired.page;
}

/**
 * Format a frame index as the slider's accessibility label. Reads
 * "frame N of TOTAL".
 */
export function frameLabel(frameIndex: number, totalFrames: number): string {
  if (totalFrames <= 0) return "frame 0 of 0";
  const clamped = Math.max(0, Math.min(frameIndex, totalFrames - 1));
  return `frame ${clamped + 1} of ${totalFrames}`;
}
