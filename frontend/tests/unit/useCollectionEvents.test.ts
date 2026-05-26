/**
 * Unit tests for the `parseBuffer` helper exported from useCollectionEvents.ts.
 *
 * parseBuffer is the pure parsing layer — no Nuxt runtime, no fetch, no auth.
 * We test it in isolation so that every edge case (split chunks, malformed JSON,
 * comment-only blocks, partial blocks) can be covered without spinning up a
 * composable or mocking network calls.
 */

import { describe, it, expect } from "vitest";
import { parseBuffer } from "../../composables/context/useCollectionEvents";

// ---------------------------------------------------------------------------
// Helper to build a minimal valid CollectionEventIO payload string
// ---------------------------------------------------------------------------

function makeEventPayload(eventType: string, extra: Record<string, unknown> = {}): string {
  return JSON.stringify({
    eventType,
    entityAppId: extra.entityAppId ?? "test-app-id",
    entityKind: extra.entityKind ?? "DataObject",
    collectionAppId: extra.collectionAppId ?? "coll-app-id",
    actorUsername: extra.actorUsername ?? "alice",
    timestamp: extra.timestamp ?? 1716739200000,
    ...extra,
  });
}

// ---------------------------------------------------------------------------
// Single complete event
// ---------------------------------------------------------------------------

describe("parseBuffer — single complete event", () => {
  it("parses a single data-only event block", () => {
    const payload = makeEventPayload("DATA_OBJECT_CREATED");
    const buffer = `data: ${payload}\n\n`;

    const { events, remaining } = parseBuffer(buffer);

    expect(events).toHaveLength(1);
    expect(events[0]!.eventType).toBe("DATA_OBJECT_CREATED");
    expect(events[0]!.entityAppId).toBe("test-app-id");
    expect(remaining).toBe("");
  });

  it("parses an event block that also has an event: line", () => {
    const payload = makeEventPayload("DATA_OBJECT_DELETED");
    // Server may send `event:` field before `data:`; parser must ignore it and still
    // extract the JSON from `data:`.
    const buffer = `event: DATA_OBJECT_DELETED\ndata: ${payload}\n\n`;

    const { events, remaining } = parseBuffer(buffer);

    expect(events).toHaveLength(1);
    expect(events[0]!.eventType).toBe("DATA_OBJECT_DELETED");
    expect(remaining).toBe("");
  });

  it("parses a HEARTBEAT event (minimal payload)", () => {
    const payload = JSON.stringify({ eventType: "HEARTBEAT", timestamp: 1716739200000 });
    const buffer = `data: ${payload}\n\n`;

    const { events, remaining } = parseBuffer(buffer);

    expect(events).toHaveLength(1);
    expect(events[0]!.eventType).toBe("HEARTBEAT");
    expect(remaining).toBe("");
  });
});

// ---------------------------------------------------------------------------
// Multiple events in one buffer
// ---------------------------------------------------------------------------

describe("parseBuffer — multiple events in one buffer", () => {
  it("parses two consecutive events", () => {
    const p1 = makeEventPayload("DATA_OBJECT_CREATED", { entityAppId: "do-1" });
    const p2 = makeEventPayload("DATA_OBJECT_DELETED", { entityAppId: "do-2" });
    const buffer = `data: ${p1}\n\ndata: ${p2}\n\n`;

    const { events, remaining } = parseBuffer(buffer);

    expect(events).toHaveLength(2);
    expect(events[0]!.entityAppId).toBe("do-1");
    expect(events[1]!.entityAppId).toBe("do-2");
    expect(remaining).toBe("");
  });
});

// ---------------------------------------------------------------------------
// Multi-chunk / incomplete buffer (split in mid-stream)
// ---------------------------------------------------------------------------

describe("parseBuffer — multi-chunk streaming", () => {
  it("returns remaining when buffer ends without a blank line", () => {
    const payload = makeEventPayload("DATA_OBJECT_CREATED");
    // Artificially split in the middle — no trailing \n\n yet
    const partial = `data: ${payload.slice(0, 20)}`;

    const { events, remaining } = parseBuffer(partial);

    expect(events).toHaveLength(0);
    expect(remaining).toBe(partial);
  });

  it("resumes correctly after receiving the rest of the chunk", () => {
    const payload = makeEventPayload("COLLECTION_UPDATED");
    const full = `data: ${payload}\n\n`;
    // Split at an arbitrary byte in the middle
    const splitAt = Math.floor(full.length / 2);
    const chunk1 = full.slice(0, splitAt);
    const chunk2 = full.slice(splitAt);

    const result1 = parseBuffer(chunk1);
    // First chunk incomplete — no events yet
    expect(result1.events).toHaveLength(0);

    // Feed remaining back in with the second chunk appended
    const result2 = parseBuffer(result1.remaining + chunk2);
    expect(result2.events).toHaveLength(1);
    expect(result2.events[0]!.eventType).toBe("COLLECTION_UPDATED");
    expect(result2.remaining).toBe("");
  });

  it("parses a complete event that follows an incomplete tail", () => {
    const p1 = makeEventPayload("DATA_OBJECT_CREATED", { entityAppId: "do-A" });
    const p2 = makeEventPayload("DATA_OBJECT_UPDATED", { entityAppId: "do-B" });

    // First call: one complete event + partial start of second
    const firstBuf = `data: ${p1}\n\ndata: ${p2.slice(0, 10)}`;
    const r1 = parseBuffer(firstBuf);
    expect(r1.events).toHaveLength(1);
    expect(r1.events[0]!.entityAppId).toBe("do-A");
    expect(r1.remaining).toContain("data: ");

    // Second call: feed remaining + rest of p2
    const secondBuf = r1.remaining + p2.slice(10) + "\n\n";
    const r2 = parseBuffer(secondBuf);
    expect(r2.events).toHaveLength(1);
    expect(r2.events[0]!.entityAppId).toBe("do-B");
    expect(r2.remaining).toBe("");
  });
});

// ---------------------------------------------------------------------------
// Comment-only blocks (SSE keep-alive `: heartbeat`)
// ---------------------------------------------------------------------------

describe("parseBuffer — comment-only blocks", () => {
  it("produces zero events for a comment-only block", () => {
    const buffer = ": heartbeat\n\n";

    const { events, remaining } = parseBuffer(buffer);

    expect(events).toHaveLength(0);
    expect(remaining).toBe("");
  });

  it("produces zero events for an empty block", () => {
    // Two blank lines in a row → one empty block
    const buffer = "\n\n";

    const { events, remaining } = parseBuffer(buffer);

    expect(events).toHaveLength(0);
    expect(remaining).toBe("");
  });

  it("skips comment block and then parses real event", () => {
    const payload = makeEventPayload("DATA_OBJECT_CREATED");
    const buffer = `: heartbeat\n\ndata: ${payload}\n\n`;

    const { events, remaining } = parseBuffer(buffer);

    expect(events).toHaveLength(1);
    expect(events[0]!.eventType).toBe("DATA_OBJECT_CREATED");
    expect(remaining).toBe("");
  });
});

// ---------------------------------------------------------------------------
// Malformed JSON
// ---------------------------------------------------------------------------

describe("parseBuffer — malformed JSON", () => {
  it("skips a block with malformed JSON without throwing", () => {
    const buffer = "data: {this is not valid json}\n\n";

    let result: ReturnType<typeof parseBuffer>;
    expect(() => {
      result = parseBuffer(buffer);
    }).not.toThrow();

    // @ts-expect-error result is assigned in the callback above
    expect(result.events).toHaveLength(0);
    // @ts-expect-error
    expect(result.remaining).toBe("");
  });

  it("skips malformed block and still parses subsequent valid block", () => {
    const payload = makeEventPayload("DATA_OBJECT_CREATED");
    const buffer = `data: {broken\n\ndata: ${payload}\n\n`;

    const { events, remaining } = parseBuffer(buffer);

    expect(events).toHaveLength(1);
    expect(events[0]!.eventType).toBe("DATA_OBJECT_CREATED");
    expect(remaining).toBe("");
  });
});

// ---------------------------------------------------------------------------
// Edge cases
// ---------------------------------------------------------------------------

describe("parseBuffer — edge cases", () => {
  it("handles an empty buffer", () => {
    const { events, remaining } = parseBuffer("");
    expect(events).toHaveLength(0);
    expect(remaining).toBe("");
  });

  it("handles buffer with only whitespace (no boundary)", () => {
    const { events, remaining } = parseBuffer("   ");
    expect(events).toHaveLength(0);
    expect(remaining).toBe("   ");
  });
});
