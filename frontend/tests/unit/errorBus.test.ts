/**
 * UX Pattern A (2026-05-24) — handleError must suppress two classes of
 * false-alarm errors that were lighting up the red toast on cold-load:
 *   1. AbortError / "Failed to fetch" (route changed mid-flight)
 *   2. 401 Unauthorized (auth middleware handles + redirects)
 *
 * Real errors (4xx other than 401, 5xx, string-typed) MUST still emit.
 *
 * #118 (2026-05-26) — handleError must also deduplicate identical errors
 * emitted by parallel API calls within a 1000 ms window, preventing toast
 * stacking on pages with multiple simultaneous fetches.
 */
import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import type { ErrorType } from "~/utils/errorBus";

let handleError: typeof import("~/utils/errorBus").handleError;
let onError: typeof import("~/utils/errorBus").onError;
let _resetDedupStateForTests: typeof import("~/utils/errorBus")._resetDedupStateForTests;
let humanizeIdError: typeof import("~/utils/errorBus").humanizeIdError;

beforeEach(async () => {
  vi.resetModules();
  const mod = await import("~/utils/errorBus");
  handleError = mod.handleError;
  onError = mod.onError;
  _resetDedupStateForTests = mod._resetDedupStateForTests;
  _resetDedupStateForTests();
  humanizeIdError = mod.humanizeIdError;
});

afterEach(() => {
  vi.useRealTimers();
});

function buildResponseError(status: number, bodyText = ""): Error {
  // Replicate the shape isResponseError() checks for: `response` field with
  // a Response-like object exposing status + body reader.
  const encoder = new TextEncoder();
  const bytes = encoder.encode(bodyText);
  const body = {
    getReader: () => ({
      read: () =>
        Promise.resolve({
          value: bytes.length > 0 ? bytes : undefined,
          done: false,
        }),
    }),
  };
  const err = new Error(`HTTP ${status}`);
  (err as unknown as { response: unknown }).response = {
    status,
    statusText: status === 401 ? "Unauthorized" : "Error",
    body,
  };
  return err;
}

// ---------------------------------------------------------------------------
// humanizeIdError unit tests (UX-WALK-2026-05-29-04)
// ---------------------------------------------------------------------------

function makeError(exception: string, message: string, status = 404): ErrorType {
  return { status, exception, message };
}

describe("humanizeIdError — ID ERROR transformation", () => {
  it('maps "Collection with id N is null or deleted" to a friendly collection message', async () => {
    const input = makeError("ID ERROR", "Collection with id 7 is null or deleted");
    const result = humanizeIdError(input);
    expect(result.exception).toBe("");
    expect(result.message).toContain("collection");
    expect(result.message).not.toMatch(/\bid \d+\b/);
  });

  it('maps "DataObject with id N is null or deleted" to a friendly data object message', async () => {
    const input = makeError("ID ERROR", "DataObject with id 3 is null or deleted");
    const result = humanizeIdError(input);
    expect(result.exception).toBe("");
    expect(result.message).toContain("data object");
    expect(result.message).not.toMatch(/\bid \d+\b/);
  });

  it("maps an unknown entity ID ERROR to the generic friendly message", async () => {
    const input = makeError("ID ERROR", "Foo with id 99 is null or deleted");
    const result = humanizeIdError(input);
    expect(result.exception).toBe("");
    expect(result.message).toContain("isn't available");
    expect(result.message).not.toMatch(/\bid \d+\b/);
  });

  it("passes through errors whose exception is not ID ERROR unchanged", async () => {
    const input = makeError("Not Found", "Collection with id 7 is null or deleted");
    const result = humanizeIdError(input);
    expect(result).toEqual(input);
  });

  it("passes through ordinary errors unchanged", async () => {
    const input = makeError("Internal Server Error", "Something went wrong", 500);
    const result = humanizeIdError(input);
    expect(result).toEqual(input);
  });

  it("preserves the original status code", async () => {
    const input = makeError("ID ERROR", "Collection with id 1 is null or deleted", 404);
    const result = humanizeIdError(input);
    expect(result.status).toBe(404);
  });
});

describe("handleError — suppression rules", () => {
  it("suppresses AbortError (DOMException)", async () => {
    const seen: unknown[] = [];
    onError(e => seen.push(e));
    const abort = new DOMException("aborted", "AbortError");
    handleError(abort, "fetching collection");
    await new Promise(r => setTimeout(r, 10));
    expect(seen).toEqual([]);
  });

  it('suppresses TypeError "Failed to fetch" (route change mid-flight)', async () => {
    const seen: unknown[] = [];
    onError(e => seen.push(e));
    handleError(new TypeError("Failed to fetch"), "fetching dataobject");
    await new Promise(r => setTimeout(r, 10));
    expect(seen).toEqual([]);
  });

  it("suppresses 401 Unauthorized (auth middleware handles it)", async () => {
    const seen: unknown[] = [];
    onError(e => seen.push(e));
    handleError(buildResponseError(401, ""), "fetching collection");
    await new Promise(r => setTimeout(r, 10));
    expect(seen).toEqual([]);
  });

  it("still emits for genuine 404 (NOT suppressed)", async () => {
    const seen: unknown[] = [];
    onError(e => seen.push(e));
    handleError(buildResponseError(404, '{"message":"gone"}'), "fetching dataobject");
    await new Promise(r => setTimeout(r, 50));
    expect(seen.length).toBe(1);
  });

  it("still emits for genuine 500 (NOT suppressed)", async () => {
    const seen: unknown[] = [];
    onError(e => seen.push(e));
    handleError(buildResponseError(500, '{"message":"oops"}'), "saving collection");
    await new Promise(r => setTimeout(r, 50));
    expect(seen.length).toBe(1);
  });

  it("still emits for string-typed errors (caller intent)", async () => {
    const seen: unknown[] = [];
    onError(e => seen.push(e));
    handleError("Validation failed", "creating reference");
    await new Promise(r => setTimeout(r, 10));
    expect(seen.length).toBe(1);
  });

  it("still emits for plain Error instances", async () => {
    const seen: unknown[] = [];
    onError(e => seen.push(e));
    handleError(new Error("Something broke"), "saving");
    await new Promise(r => setTimeout(r, 10));
    expect(seen.length).toBe(1);
  });
});

describe("handleError — deduplication within 1000 ms (#118)", () => {
  it("suppresses duplicate string error within 1000 ms", async () => {
    vi.useFakeTimers();
    const seen: unknown[] = [];
    onError(e => seen.push(e));

    handleError("Validation failed", "creating reference");
    handleError("Validation failed", "creating reference");
    handleError("Validation failed", "creating reference");

    await Promise.resolve();
    expect(seen).toHaveLength(1);
  });

  it("allows the same string error after 1000 ms", async () => {
    vi.useFakeTimers();
    const seen: unknown[] = [];
    onError(e => seen.push(e));

    handleError("Validation failed", "creating reference");
    expect(seen).toHaveLength(1);

    vi.advanceTimersByTime(1001);
    handleError("Validation failed", "creating reference");
    expect(seen).toHaveLength(2);
  });

  it("does NOT deduplicate string errors with different messages", async () => {
    const seen: unknown[] = [];
    onError(e => seen.push(e));

    handleError("Error A", "creating reference");
    handleError("Error B", "creating reference");

    await Promise.resolve();
    expect(seen).toHaveLength(2);
  });

  it("does NOT deduplicate string errors with different situations", async () => {
    const seen: unknown[] = [];
    onError(e => seen.push(e));

    handleError("Validation failed", "creating reference");
    handleError("Validation failed", "deleting reference");

    await Promise.resolve();
    expect(seen).toHaveLength(2);
  });

  it("suppresses duplicate plain Error within 1000 ms", async () => {
    vi.useFakeTimers();
    const seen: unknown[] = [];
    onError(e => seen.push(e));

    handleError(new Error("network failure"), "fetching collection");
    handleError(new Error("network failure"), "fetching collection");

    await Promise.resolve();
    expect(seen).toHaveLength(1);
  });

  it("allows plain Error after 1000 ms window", async () => {
    vi.useFakeTimers();
    const seen: unknown[] = [];
    onError(e => seen.push(e));

    handleError(new Error("network failure"), "fetching collection");
    vi.advanceTimersByTime(1001);
    handleError(new Error("network failure"), "fetching collection");

    await Promise.resolve();
    expect(seen).toHaveLength(2);
  });

  it("suppresses duplicate ResponseError with same status within 1000 ms (pre-parse)", async () => {
    // No fake timers here — using real async to let parseResponseError settle.
    let parseCount = 0;
    const err500a = buildResponseError(500, '{"message":"oops"}');
    const err500b = buildResponseError(500, '{"message":"oops"}');

    // Patch the body reader to track how many times parsing is initiated.
    for (const err of [err500a, err500b]) {
      const originalGetReader = (
        err as unknown as { response: { body: { getReader: () => unknown } } }
      ).response.body.getReader;
      (err as unknown as { response: { body: { getReader: () => unknown } } })
        .response.body.getReader = () => {
        parseCount++;
        return originalGetReader();
      };
    }

    const seen: unknown[] = [];
    onError(e => seen.push(e));

    handleError(err500a, "fetching data objects");
    handleError(err500b, "fetching data objects");

    // Let microtask queue drain (parseResponseError is Promise-based).
    await new Promise(r => { setTimeout(r, 50); });

    // Only one parse should have been initiated (second was suppressed pre-parse).
    expect(parseCount).toBe(1);
    expect(seen).toHaveLength(1);
  }, 10000);

  it("allows distinct HTTP status codes through even within 1000 ms", async () => {
    // No fake timers — real async settle for parseResponseError.
    const seen: unknown[] = [];
    onError(e => seen.push(e));

    handleError(buildResponseError(500, '{"message":"server error"}'), "fetching data");
    handleError(buildResponseError(403, '{"message":"forbidden"}'), "fetching data");

    await new Promise(r => { setTimeout(r, 50); });
    expect(seen).toHaveLength(2);
  }, 10000);
});
