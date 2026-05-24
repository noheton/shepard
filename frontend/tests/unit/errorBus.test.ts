/**
 * UX Pattern A (2026-05-24) — handleError must suppress two classes of
 * false-alarm errors that were lighting up the red toast on cold-load:
 *   1. AbortError / "Failed to fetch" (route changed mid-flight)
 *   2. 401 Unauthorized (auth middleware handles + redirects)
 *
 * Real errors (4xx other than 401, 5xx, string-typed) MUST still emit.
 */
import { describe, it, expect, beforeEach, vi } from "vitest";

let handleError: typeof import("~/utils/errorBus").handleError;
let onError: typeof import("~/utils/errorBus").onError;

beforeEach(async () => {
  vi.resetModules();
  const mod = await import("~/utils/errorBus");
  handleError = mod.handleError;
  onError = mod.onError;
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
