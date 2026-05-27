/**
 * Trace3DChannelPicker — unit tests for tupleKey and channelFor helpers.
 *
 * Tests the pure key-derivation and lookup logic without mounting the component.
 */
import { describe, it, expect } from "vitest";

// ── inline the key helpers (mirrors component logic) ─────────────────────────

const norm = (s: string | null | undefined) => (s ?? "").trim();

function tupleKey(ch: {
  measurement?: string; device?: string; location?: string;
  symbolicName?: string; field?: string;
}): string {
  return `${norm(ch.measurement)}|${norm(ch.device)}|${norm(ch.location)}|${norm(ch.symbolicName)}|${norm(ch.field)}`;
}

interface ChannelV2 {
  shepardId: string;
  measurement?: string;
  device?: string;
  location?: string;
  symbolicName?: string;
  field?: string;
}

function channelFor(
  key: string | null,
  keyToChannel: Map<string, ChannelV2>,
): { measurement: string; device: string; location: string; symbolicName: string; field: string } | null {
  if (!key) return null;
  const ch = keyToChannel.get(key);
  if (!ch) return null;
  return {
    measurement:  ch.measurement  ?? "",
    device:       ch.device       ?? "",
    location:     ch.location     ?? "",
    symbolicName: ch.symbolicName ?? "",
    field:        ch.field        ?? "",
  };
}

// ── fixtures ──────────────────────────────────────────────────────────────────

const CHANNELS: ChannelV2[] = [
  { shepardId: "id-1", device: "KUKA-LBR", symbolicName: "FT", field: "force_x_N" },
  { shepardId: "id-2", device: "KUKA-LBR", symbolicName: "FT", field: "force_y_N" },
  { shepardId: "id-3", device: "KUKA-LBR", symbolicName: "FT", field: "force_z_N", location: "TCP" },
  { shepardId: "id-4", measurement: "temp", device: "AFP-Robot", field: "tcp_temp_C" },
];

const keyToChannel = new Map<string, ChannelV2>(
  CHANNELS.map(ch => [tupleKey(ch), ch]),
);

// ── tupleKey ──────────────────────────────────────────────────────────────────

describe("tupleKey", () => {
  it("produces a pipe-delimited 5-field key", () => {
    expect(tupleKey({ device: "KUKA-LBR", symbolicName: "FT", field: "force_x_N" }))
      .toBe("|KUKA-LBR||FT|force_x_N");
  });

  it("treats undefined and empty string identically", () => {
    expect(tupleKey({ measurement: undefined, device: "D", field: "f" }))
      .toBe(tupleKey({ measurement: "",        device: "D", field: "f" }));
  });

  it("trims whitespace before comparison", () => {
    expect(tupleKey({ device: "  KUKA-LBR  ", field: "force_x_N" }))
      .toBe(tupleKey({ device: "KUKA-LBR",     field: "force_x_N" }));
  });

  it("distinguishes channels with different location fields", () => {
    const withLoc    = tupleKey({ device: "KUKA-LBR", field: "force_z_N", location: "TCP" });
    const withoutLoc = tupleKey({ device: "KUKA-LBR", field: "force_z_N" });
    expect(withLoc).not.toBe(withoutLoc);
  });
});

// ── channelFor ────────────────────────────────────────────────────────────────

describe("channelFor", () => {
  it("returns a Channel5Tuple with empty-string fields for undefined properties", () => {
    const key = tupleKey(CHANNELS[0]!);
    const result = channelFor(key, keyToChannel);
    expect(result).not.toBeNull();
    expect(result!.measurement).toBe("");  // CHANNELS[0] has no measurement
    expect(result!.device).toBe("KUKA-LBR");
    expect(result!.field).toBe("force_x_N");
  });

  it("returns null for a null key", () => {
    expect(channelFor(null, keyToChannel)).toBeNull();
  });

  it("returns null for an unknown key", () => {
    expect(channelFor("nonexistent|key", keyToChannel)).toBeNull();
  });

  it("round-trips: channel → key → channelFor returns same tuple fields", () => {
    for (const ch of CHANNELS) {
      const key = tupleKey(ch);
      const result = channelFor(key, keyToChannel);
      expect(result).not.toBeNull();
      expect(result!.device).toBe(ch.device ?? "");
      expect(result!.field).toBe(ch.field ?? "");
      expect(result!.location).toBe(ch.location ?? "");
    }
  });

  it("channel with measurement field round-trips correctly", () => {
    const ch = CHANNELS[3]!; // { measurement: "temp", device: "AFP-Robot", field: "tcp_temp_C" }
    const key = tupleKey(ch);
    const result = channelFor(key, keyToChannel);
    expect(result!.measurement).toBe("temp");
    expect(result!.device).toBe("AFP-Robot");
  });
});
