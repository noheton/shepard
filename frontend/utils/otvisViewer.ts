/**
 * OTVIS-VIEWER — pure helpers for the Edevis OTvis frame viewer.
 *
 * The Vue component (`DataObjectOtvisViewer.vue`) handles fetch + canvas;
 * Vitest runs in a `node` environment where neither `fetch` nor an `<img>`
 * are practical to mount. These pure helpers carry the testable logic:
 * which singleton FileReferences are OTvis archives, and how the per-frame
 * heatmap URL is built — so a regression in the path shape or the channel
 * fallback is caught in unit tests.
 */

/** Minimal shape of a frame descriptor returned by the frames index. */
export interface OtvisFrameInfo {
  index: number;
  kind: string;
  channels: string[];
  defaultChannel: string;
}

/** True when a filename names an Edevis `.OTvis` archive (case-insensitive). */
export function isOtvisFilename(filename: string | null | undefined): boolean {
  return (filename ?? "").toLowerCase().endsWith(".otvis");
}

/**
 * Pick the channel to render for a frame. If the requested channel is valid
 * for the frame it is kept; otherwise the frame's `defaultChannel` is used.
 * Raw frames carry only `temperature`, so a stale `phase` selection from a
 * previous lock-in frame falls back cleanly.
 */
export function resolveChannel(
  frame: OtvisFrameInfo | null | undefined,
  requested: string,
): string {
  if (!frame) return requested;
  return frame.channels.includes(requested) ? requested : frame.defaultChannel;
}

/**
 * Build the per-frame heatmap PNG URL. The base is the v2 API root; the
 * appId + channel are URL-encoded so unusual reference names / channels do
 * not break the path. Mirrors the backend route exactly:
 * `GET /v2/thermography/otvis/{appId}/frames/{n}?channel=...`
 */
export function buildOtvisFrameUrl(
  v2Base: string,
  fileReferenceAppId: string,
  frameIndex: number,
  channel: string,
): string {
  const base = v2Base.replace(/\/$/, "");
  return (
    `${base}/v2/thermography/otvis/${encodeURIComponent(fileReferenceAppId)}` +
    `/frames/${frameIndex}?channel=${encodeURIComponent(channel)}`
  );
}
