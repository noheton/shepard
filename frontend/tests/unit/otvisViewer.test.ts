/**
 * OTVIS-VIEWER — unit tests for the pure viewer helpers. Mirrors the
 * ThermographyCanvas.test.ts pattern: the Vue component wraps fetch + an
 * `<img>` blit that is impractical in the `node` test environment, so we
 * test the pure logic (OTvis detection, channel fallback, URL build).
 */
import { describe, it, expect } from "vitest";
import {
  isOtvisFilename,
  resolveChannel,
  buildRenderUrl,
  buildOtvisIndexBody,
  buildOtvisFrameBody,
  parseFramesIndex,
  channelsForKind,
  OTVIS_FRAME_SHAPE_IRI,
  type OtvisFrameInfo,
} from "~/utils/otvisViewer";

const lockInFrame: OtvisFrameInfo = {
  index: 0,
  kind: "lockin",
  channels: ["amplitude", "phase"],
  defaultChannel: "phase",
};
const rawFrame: OtvisFrameInfo = {
  index: 1,
  kind: "raw",
  channels: ["temperature"],
  defaultChannel: "temperature",
};

describe("isOtvisFilename", () => {
  it("matches .OTvis case-insensitively", () => {
    expect(isOtvisFilename("sample_S4_M13_L18_F4.OTvis")).toBe(true);
    expect(isOtvisFilename("scan.otvis")).toBe(true);
    expect(isOtvisFilename("SCAN.OTVIS")).toBe(true);
  });
  it("rejects non-OTvis names and nullish input", () => {
    expect(isOtvisFilename("scan.tif")).toBe(false);
    expect(isOtvisFilename("report.pdf")).toBe(false);
    expect(isOtvisFilename(null)).toBe(false);
    expect(isOtvisFilename(undefined)).toBe(false);
  });
});

describe("resolveChannel", () => {
  it("keeps a valid channel for the frame", () => {
    expect(resolveChannel(lockInFrame, "amplitude")).toBe("amplitude");
    expect(resolveChannel(lockInFrame, "phase")).toBe("phase");
  });
  it("falls back to defaultChannel when the requested channel is invalid", () => {
    // stale 'phase' selection moving onto a raw frame → temperature.
    expect(resolveChannel(rawFrame, "phase")).toBe("temperature");
    // unknown channel on a lock-in frame → phase (the NDT default).
    expect(resolveChannel(lockInFrame, "bogus")).toBe("phase");
  });
  it("returns the requested channel unchanged when frame is null", () => {
    expect(resolveChannel(null, "amplitude")).toBe("amplitude");
  });
});

describe("buildRenderUrl", () => {
  it("points at the generic render endpoint and strips a trailing slash", () => {
    expect(buildRenderUrl("https://api.example/")).toBe("https://api.example/v2/shapes/render");
    expect(buildRenderUrl("https://api.example")).toBe("https://api.example/v2/shapes/render");
  });
});

describe("buildOtvisIndexBody", () => {
  it("builds a file-rooted describe request (params.mode=index)", () => {
    expect(buildOtvisIndexBody("abc-123")).toEqual({
      shapeIri: OTVIS_FRAME_SHAPE_IRI,
      focusFileRefAppId: "abc-123",
      params: { mode: "index" },
    });
  });
});

describe("buildOtvisFrameBody", () => {
  it("builds a file-rooted frame request with frame + channel params", () => {
    expect(buildOtvisFrameBody("abc-123", 2, "phase")).toEqual({
      shapeIri: OTVIS_FRAME_SHAPE_IRI,
      focusFileRefAppId: "abc-123",
      params: { frame: "2", channel: "phase" },
    });
  });
});

describe("channelsForKind", () => {
  it("maps lock-in → amplitude+phase, everything else → temperature", () => {
    expect(channelsForKind("lockin")).toEqual(["amplitude", "phase"]);
    expect(channelsForKind("raw")).toEqual(["temperature"]);
  });
});

describe("parseFramesIndex", () => {
  it("reconstructs frame info from the render view-model bindings", () => {
    const frames = parseFramesIndex([
      { role: "0", channelSelector: "lockin", unit: "phase" },
      { role: "1", channelSelector: "raw", unit: "temperature" },
    ]);
    expect(frames).toEqual([
      { index: 0, kind: "lockin", channels: ["amplitude", "phase"], defaultChannel: "phase" },
      { index: 1, kind: "raw", channels: ["temperature"], defaultChannel: "temperature" },
    ]);
  });
  it("tolerates null/undefined bindings", () => {
    expect(parseFramesIndex(null)).toEqual([]);
    expect(parseFramesIndex(undefined)).toEqual([]);
  });
});
