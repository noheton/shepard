/**
 * MFFD-RENDER-MATERIAL-BATCH-TRACE (slice 3 UI) — unit tests
 *
 * Tests cover:
 *   - isMaterialBatchDo detection from annotation list
 *   - render response parsing (OK / MISSING / mixed)
 *   - navigation link construction (in-collection vs cross-collection)
 *
 * We test the pure logic units that mirror the inline functions in
 * MaterialBatchTracePane.vue and the computed in the DataObject detail page.
 */

import { describe, it, expect } from "vitest";
import { collectionsPath, dataObjectsPathFragment } from "~/utils/constants";

// ── isMaterialBatchDo detection ───────────────────────────────────────────────

/** Mirrors the `isMaterialBatchDo` computed in the DataObject detail page. */
function isMaterialBatchDo(
  annotations: Array<{ propertyIRI?: string }>,
): boolean {
  return annotations.some(a => a.propertyIRI === "urn:shepard:mffd:batch-id");
}

describe("isMaterialBatchDo detection", () => {
  it("returns false for an empty annotation list", () => {
    expect(isMaterialBatchDo([])).toBe(false);
  });

  it("returns false when no annotation matches the batch-id predicate", () => {
    expect(
      isMaterialBatchDo([
        { propertyIRI: "urn:shepard:mffd:process-type" },
        { propertyIRI: "urn:shepard:mffd:material-batch" },
      ]),
    ).toBe(false);
  });

  it("returns true when at least one annotation has the batch-id predicate", () => {
    expect(
      isMaterialBatchDo([
        { propertyIRI: "urn:shepard:mffd:material-class" },
        { propertyIRI: "urn:shepard:mffd:batch-id" },
      ]),
    ).toBe(true);
  });

  it("returns true with only the batch-id annotation", () => {
    expect(
      isMaterialBatchDo([{ propertyIRI: "urn:shepard:mffd:batch-id" }]),
    ).toBe(true);
  });

  it("is not confused by a partial IRI match", () => {
    expect(
      isMaterialBatchDo([
        { propertyIRI: "urn:shepard:mffd:batch-identifier" },
        { propertyIRI: "urn:shepard:mffd:batch-id-extra" },
      ]),
    ).toBe(false);
  });
});

// ── render response parsing ───────────────────────────────────────────────────

interface ChannelBinding {
  status: string;
  resolved?: { channelRef?: string } | null;
}

/** Mirrors the consumer-extraction logic from MaterialBatchTracePane.vue. */
function parseConsumers(
  bindings: ChannelBinding[],
): { appIds: string[]; isEmpty: boolean } {
  const okAppIds = bindings
    .filter(b => b.status === "OK" && b.resolved?.channelRef)
    .map(b => b.resolved!.channelRef!);

  if (okAppIds.length === 0) {
    return { appIds: [], isEmpty: true };
  }
  return { appIds: okAppIds, isEmpty: false };
}

describe("parseConsumers — render response parsing", () => {
  it("returns empty when channelBindings is empty", () => {
    expect(parseConsumers([])).toEqual({ appIds: [], isEmpty: true });
  });

  it("returns empty when binding is MISSING", () => {
    expect(
      parseConsumers([{ status: "MISSING", resolved: null }]),
    ).toEqual({ appIds: [], isEmpty: true });
  });

  it("returns appIds from OK bindings", () => {
    const bindings: ChannelBinding[] = [
      { status: "OK", resolved: { channelRef: "018f0000-0000-7000-0000-000000000001" } },
      { status: "OK", resolved: { channelRef: "018f0000-0000-7000-0000-000000000002" } },
    ];
    expect(parseConsumers(bindings)).toEqual({
      appIds: [
        "018f0000-0000-7000-0000-000000000001",
        "018f0000-0000-7000-0000-000000000002",
      ],
      isEmpty: false,
    });
  });

  it("ignores MISSING bindings when other OK bindings exist", () => {
    const bindings: ChannelBinding[] = [
      { status: "OK", resolved: { channelRef: "018f0000-0000-7000-0000-000000000003" } },
      { status: "MISSING", resolved: null },
    ];
    expect(parseConsumers(bindings)).toEqual({
      appIds: ["018f0000-0000-7000-0000-000000000003"],
      isEmpty: false,
    });
  });

  it("skips OK bindings that have no channelRef", () => {
    const bindings: ChannelBinding[] = [
      { status: "OK", resolved: { channelRef: undefined } },
      { status: "OK", resolved: null },
    ];
    expect(parseConsumers(bindings)).toEqual({ appIds: [], isEmpty: true });
  });
});

// ── navigation link construction ─────────────────────────────────────────────

/** Mirrors the NuxtLink :to computation in MaterialBatchTracePane.vue. */
function navigationHref(collectionAppId: string, consumerAppId: string): string {
  return `${collectionsPath}${collectionAppId}${dataObjectsPathFragment}${consumerAppId}`;
}

describe("navigationHref — in-collection consumer links", () => {
  const COLL = "018e0000-0000-7000-0000-000000000010";
  const DO   = "018e0000-0000-7000-0000-000000000099";

  it("builds the correct /collections/{coll}/dataobjects/{do} path", () => {
    expect(navigationHref(COLL, DO)).toBe(
      `/collections/${COLL}/dataobjects/${DO}`,
    );
  });

  it("uses the appId paths (not numeric ids) per the V2-LINKS rule", () => {
    const href = navigationHref(COLL, DO);
    // Must not contain any bare numeric id segment
    expect(href).not.toMatch(/\/\d+\//);
  });
});
