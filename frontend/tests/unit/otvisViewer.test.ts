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
  buildOtvisFrameUrl,
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

describe("buildOtvisFrameUrl", () => {
  it("builds the exact backend route", () => {
    expect(buildOtvisFrameUrl("https://api.example/", "abc-123", 2, "phase")).toBe(
      "https://api.example/v2/thermography/otvis/abc-123/frames/2?channel=phase",
    );
  });
  it("strips a trailing slash and URL-encodes appId + channel", () => {
    const url = buildOtvisFrameUrl("https://api.example", "a/b c", 0, "amp&plitude");
    expect(url).toBe(
      "https://api.example/v2/thermography/otvis/a%2Fb%20c/frames/0?channel=amp%26plitude",
    );
  });
});
