/**
 * OTVIS-VIEWER — pure helpers for the Edevis OTvis frame viewer.
 *
 * The Vue component (`DataObjectOtvisViewer.vue`) handles fetch + canvas;
 * Vitest runs in a `node` environment where neither `fetch` nor an `<img>`
 * are practical to mount. These pure helpers carry the testable logic:
 * which singleton FileReferences are OTvis archives, the channel fallback,
 * and how the render request to `POST /v2/shapes/render` is shaped.
 *
 * V2CONV-A7-THERMO: the bespoke `GET /v2/thermography/otvis/*` REST is gone —
 * viewing flows through the generic `POST /v2/shapes/render` (file-rooted:
 * shapeIri + focusFileRefAppId in the body, frame/channel in `params`; never
 * a path/URL). The frames catalogue is `params.mode=index` (Accept: JSON); a
 * frame heatmap is `params.frame`+`params.channel` (Accept: image/png).
 */

/** The SHACL shape IRI the thermography OTvis frame renderer claims. */
export const OTVIS_FRAME_SHAPE_IRI =
  "http://semantics.dlr.de/shepard-ui/thermography/transform#OtvisFrameShape";

/** Minimal shape of a frame descriptor returned by the frames index. */
export interface OtvisFrameInfo {
  index: number;
  kind: string;
  channels: string[];
  defaultChannel: string;
}

/** Request body for `POST /v2/shapes/render` (file-rooted OTvis dispatch). */
export interface OtvisRenderBody {
  shapeIri: string;
  focusFileRefAppId: string;
  params: Record<string, string>;
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

/** Build the `POST /v2/shapes/render` URL. */
export function buildRenderUrl(v2Base: string): string {
  return `${v2Base.replace(/\/$/, "")}/v2/shapes/render`;
}

/**
 * Body for the OTvis frames-catalogue describe call (`params.mode=index`,
 * Accept: application/json) — replaces the former
 * `GET /v2/thermography/otvis/{appId}/frames`.
 */
export function buildOtvisIndexBody(fileReferenceAppId: string): OtvisRenderBody {
  return {
    shapeIri: OTVIS_FRAME_SHAPE_IRI,
    focusFileRefAppId: fileReferenceAppId,
    params: { mode: "index" },
  };
}

/**
 * Body for one frame's heatmap PNG (Accept: image/png) — replaces the former
 * `GET /v2/thermography/otvis/{appId}/frames/{n}?channel=...`.
 */
export function buildOtvisFrameBody(
  fileReferenceAppId: string,
  frameIndex: number,
  channel: string,
): OtvisRenderBody {
  return {
    shapeIri: OTVIS_FRAME_SHAPE_IRI,
    focusFileRefAppId: fileReferenceAppId,
    params: { frame: String(frameIndex), channel },
  };
}

/**
 * Derive the available channels for a frame kind — mirrors the backend
 * `OtvisFrameRenderService.buildIndex`: lock-in frames carry amplitude+phase,
 * raw frames carry temperature.
 */
export function channelsForKind(kind: string): string[] {
  return kind === "lockin" ? ["amplitude", "phase"] : ["temperature"];
}

/** One channel-binding entry from the `POST /v2/shapes/render` JSON view-model. */
interface RenderBinding {
  role?: string | null;
  channelSelector?: string | null;
  unit?: string | null;
}

/**
 * Parse the `POST /v2/shapes/render` (params.mode=index) JSON view-model into
 * the viewer's frame-index shape. The OtvisFrameRenderer encodes one binding
 * per frame: role=frame index, channelSelector=kind, unit=defaultChannel; the
 * channel list is reconstructed from the kind (see `channelsForKind`).
 */
export function parseFramesIndex(bindings: RenderBinding[] | null | undefined): OtvisFrameInfo[] {
  if (!bindings) return [];
  return bindings.map(b => {
    const kind = b.channelSelector ?? "raw";
    const channels = channelsForKind(kind);
    return {
      index: Number.parseInt(b.role ?? "0", 10) || 0,
      kind,
      channels,
      defaultChannel: b.unit ?? channels[0] ?? "temperature",
    };
  });
}
