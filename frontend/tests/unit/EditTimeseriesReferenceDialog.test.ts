/**
 * REF-EDIT-1 — unit tests for TimeseriesReference edit-dialog helpers and
 * template-driven channel-selection prefill.
 *
 * Pattern: pure-helper tests (matches
 * `CreateDataReferenceDialog.fileNamingPrefill.test.ts`).
 * Mounting the full Nuxt + Vuetify component tree is out of scope — the existing
 * harness does not wire `@vue/test-utils`. The tests exercise:
 *   1. `extractChannelSelectionHint` — the parse helper for the
 *      `urn:shepard:reference:channelSelection` annotation.
 *   2. `findAnnotationByPredicate` (channel-selection predicate variant).
 *   3. A simulated dialog open-handler flow (channel prefill guard logic).
 *   4. The ns↔datetime-local conversion math used by the dialog.
 */
import { describe, it, expect } from "vitest";
import type { SemanticAnnotation } from "@dlr-shepard/backend-client";
import {
  REFERENCE_PREDICATE,
  extractChannelSelectionHint,
  findAnnotationByPredicate,
} from "~/composables/references/referenceTemplatePrefill";

// ── Helpers ──────────────────────────────────────────────────────────────────

function mkAnn(
  propertyIRI: string,
  valueName: string | null,
): SemanticAnnotation {
  return {
    propertyName: "",
    propertyIRI,
    valueName: valueName ?? undefined,
    valueIRI: undefined,
  } as unknown as SemanticAnnotation;
}

/** Mirrors the ns→datetime-local logic in the dialog (local-timezone aware). */
function nsToDatetimeLocal(ns: number): string {
  const ms = Math.floor(ns / 1_000_000);
  const d = new Date(ms);
  const pad = (n: number) => String(n).padStart(2, "0");
  return (
    `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}` +
    `T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
  );
}

/** Mirrors the datetime-local→ns logic in the dialog. */
function datetimeLocalToNs(str: string): number {
  return new Date(str).getTime() * 1_000_000;
}

/** Channel key — same algorithm as the dialog. */
function channelKey(ts: {
  measurement?: string;
  device?: string;
  location?: string;
  symbolicName?: string;
  field?: string;
}): string {
  return [ts.measurement, ts.device, ts.location, ts.symbolicName, ts.field].join("|");
}

/**
 * Simulates the dialog's channel-prefill guard (applied once on open):
 * - If timeseries already has channels → leave unchanged.
 * - If no annotation matching the predicate → leave unchanged.
 * - Otherwise → set channels (and optionally window) from the hint.
 */
function simulateChannelPrefillOnOpen(opts: {
  currentChannels: Array<{ measurement: string; device: string; location: string; symbolicName: string; field: string }>;
  annotations: SemanticAnnotation[] | null;
  nowNs?: number;
}): {
  channels: Array<{ measurement?: string; device?: string; location?: string; symbolicName?: string; field?: string }>;
  start?: number;
  end?: number;
  prefilled: boolean;
} {
  const { currentChannels, annotations, nowNs = Date.now() * 1_000_000 } = opts;
  if (currentChannels.length > 0) {
    return { channels: currentChannels, prefilled: false };
  }
  const ann = findAnnotationByPredicate(annotations, REFERENCE_PREDICATE.CHANNEL_SELECTION);
  const hint = extractChannelSelectionHint(ann);
  if (!hint) {
    return { channels: currentChannels, prefilled: false };
  }
  const durationNs = hint.windowDurationNs ?? 30_000_000_000;
  return {
    channels: hint.channels,
    start: nowNs - durationNs,
    end: nowNs,
    prefilled: true,
  };
}

// ── Tests: extractChannelSelectionHint ──────────────────────────────────────

describe("extractChannelSelectionHint", () => {
  it("returns null for null annotation", () => {
    expect(extractChannelSelectionHint(null)).toBeNull();
  });

  it("returns null when valueName is empty or whitespace", () => {
    expect(extractChannelSelectionHint(mkAnn(REFERENCE_PREDICATE.CHANNEL_SELECTION, ""))).toBeNull();
    expect(extractChannelSelectionHint(mkAnn(REFERENCE_PREDICATE.CHANNEL_SELECTION, "   "))).toBeNull();
  });

  it("returns null when valueName is not valid JSON", () => {
    expect(extractChannelSelectionHint(mkAnn(REFERENCE_PREDICATE.CHANNEL_SELECTION, "not-json"))).toBeNull();
  });

  it("returns null when channels array is empty", () => {
    const ann = mkAnn(REFERENCE_PREDICATE.CHANNEL_SELECTION, JSON.stringify({ channels: [] }));
    expect(extractChannelSelectionHint(ann)).toBeNull();
  });

  it("parses a valid hint with one channel", () => {
    const hint = {
      channels: [
        { measurement: "vibration", device: "turbopump", location: "LOC-A", symbolicName: "V1", field: "rms" },
      ],
    };
    const ann = mkAnn(REFERENCE_PREDICATE.CHANNEL_SELECTION, JSON.stringify(hint));
    const result = extractChannelSelectionHint(ann);
    expect(result).not.toBeNull();
    expect(result!.channels).toHaveLength(1);
    expect(result!.channels[0]!.measurement).toBe("vibration");
    expect(result!.channels[0]!.field).toBe("rms");
    expect(result!.windowDurationNs).toBeUndefined();
  });

  it("parses windowDurationNs when present", () => {
    const hint = {
      channels: [
        { measurement: "pressure", device: "sensor-1", location: "L1", symbolicName: "P1", field: "bar" },
      ],
      windowDurationNs: 10_000_000_000,
    };
    const ann = mkAnn(REFERENCE_PREDICATE.CHANNEL_SELECTION, JSON.stringify(hint));
    const result = extractChannelSelectionHint(ann);
    expect(result!.windowDurationNs).toBe(10_000_000_000);
  });

  it("accepts multiple channels in the hint", () => {
    const hint = {
      channels: [
        { measurement: "m1", device: "d1", location: "l1", symbolicName: "s1", field: "f1" },
        { measurement: "m2", device: "d2", location: "l2", symbolicName: "s2", field: "f2" },
      ],
    };
    const ann = mkAnn(REFERENCE_PREDICATE.CHANNEL_SELECTION, JSON.stringify(hint));
    const result = extractChannelSelectionHint(ann);
    expect(result!.channels).toHaveLength(2);
  });

  it("ignores null / primitive entries in channels array", () => {
    const hint = {
      channels: [
        null,
        { measurement: "m", device: "d", location: "l", symbolicName: "s", field: "f" },
        42,
        "string-entry",
      ],
    };
    const ann = mkAnn(REFERENCE_PREDICATE.CHANNEL_SELECTION, JSON.stringify(hint));
    const result = extractChannelSelectionHint(ann);
    expect(result).not.toBeNull();
    // Only the one valid object channel survives filtering.
    expect(result!.channels).toHaveLength(1);
  });
});

// ── Tests: ns ↔ datetime-local conversion ────────────────────────────────────

describe("ns ↔ datetime-local round-trip (dialog math)", () => {
  it("round-trips a known ns value within ±1 s", () => {
    const originalNs = 1_748_435_696_000_000_000; // 2026-05-28T12:34:56Z
    const localStr = nsToDatetimeLocal(originalNs);
    const roundTrippedNs = datetimeLocalToNs(localStr);
    // datetime-local has second precision → allow ±1 s.
    expect(Math.abs(roundTrippedNs - originalNs)).toBeLessThan(1_000_000_001);
  });

  it("start < end after round-trip for a 30 s window", () => {
    const startNs = 1_748_435_000_000_000_000;
    const endNs   = startNs + 30_000_000_000;
    expect(datetimeLocalToNs(nsToDatetimeLocal(startNs))).toBeLessThan(
      datetimeLocalToNs(nsToDatetimeLocal(endNs)),
    );
  });
});

// ── Tests: channel key helper ────────────────────────────────────────────────

describe("channelKey (dialog deduplication logic)", () => {
  it("produces distinct keys for channels that differ in one field", () => {
    const a = { measurement: "m", device: "d", location: "l", symbolicName: "s", field: "f1" };
    const b = { measurement: "m", device: "d", location: "l", symbolicName: "s", field: "f2" };
    expect(channelKey(a)).not.toBe(channelKey(b));
  });

  it("produces the same key for identical 5-tuples", () => {
    const a = { measurement: "vib", device: "acc", location: "LOC", symbolicName: "V", field: "rms" };
    expect(channelKey(a)).toBe(channelKey({ ...a }));
  });
});

// ── Tests: simulateChannelPrefillOnOpen ──────────────────────────────────────

describe("simulateChannelPrefillOnOpen — full dialog flow", () => {
  const fixedNowNs = 2_000_000_000_000_000_000;

  it("prefills channels when annotation exists and no channels are pre-selected", () => {
    const annotations = [
      mkAnn(
        REFERENCE_PREDICATE.CHANNEL_SELECTION,
        JSON.stringify({
          channels: [
            { measurement: "vibration", device: "turbopump", location: "LOC-A", symbolicName: "V1", field: "rms" },
          ],
          windowDurationNs: 20_000_000_000,
        }),
      ),
    ];
    const result = simulateChannelPrefillOnOpen({
      currentChannels: [],
      annotations,
      nowNs: fixedNowNs,
    });
    expect(result.prefilled).toBe(true);
    expect(result.channels).toHaveLength(1);
    expect(result.channels[0]!.measurement).toBe("vibration");
    expect(result.end).toBe(fixedNowNs);
    expect(result.start).toBe(fixedNowNs - 20_000_000_000);
  });

  it("does NOT prefill when channels are already selected (user-typed guard)", () => {
    const existing = [
      { measurement: "m", device: "d", location: "l", symbolicName: "s", field: "f" },
    ];
    const annotations = [
      mkAnn(
        REFERENCE_PREDICATE.CHANNEL_SELECTION,
        JSON.stringify({
          channels: [{ measurement: "other", device: "d2", location: "l2", symbolicName: "s2", field: "f2" }],
        }),
      ),
    ];
    const result = simulateChannelPrefillOnOpen({
      currentChannels: existing,
      annotations,
      nowNs: fixedNowNs,
    });
    expect(result.prefilled).toBe(false);
    expect(result.channels).toBe(existing);
  });

  it("does NOT prefill when no channelSelection annotation is present", () => {
    const annotations = [mkAnn("urn:shepard:other:thing", "foo")];
    const result = simulateChannelPrefillOnOpen({
      currentChannels: [],
      annotations,
      nowNs: fixedNowNs,
    });
    expect(result.prefilled).toBe(false);
    expect(result.channels).toHaveLength(0);
  });

  it("uses 30 s default window when windowDurationNs is absent", () => {
    const annotations = [
      mkAnn(
        REFERENCE_PREDICATE.CHANNEL_SELECTION,
        JSON.stringify({
          channels: [{ measurement: "m", device: "d", location: "l", symbolicName: "s", field: "f" }],
        }),
      ),
    ];
    const result = simulateChannelPrefillOnOpen({
      currentChannels: [],
      annotations,
      nowNs: fixedNowNs,
    });
    expect(result.prefilled).toBe(true);
    expect(result.end! - result.start!).toBe(30_000_000_000);
  });

  it("does NOT prefill when annotations list is empty", () => {
    const result = simulateChannelPrefillOnOpen({
      currentChannels: [],
      annotations: [],
      nowNs: fixedNowNs,
    });
    expect(result.prefilled).toBe(false);
  });

  it("does NOT prefill when annotations is null", () => {
    const result = simulateChannelPrefillOnOpen({
      currentChannels: [],
      annotations: null,
      nowNs: fixedNowNs,
    });
    expect(result.prefilled).toBe(false);
  });
});
