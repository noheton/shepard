import { describe, it, expect, beforeEach } from "vitest";
import { useV1Deprecation } from "~/composables/context/useV1Deprecation";

/**
 * V1COMPAT.0 — covers the session-scoped deprecation-banner state.
 * State is module-scoped (so the banner can update from outside
 * setup()), so each test starts with a reset() to guarantee
 * isolation.
 */
describe("useV1Deprecation", () => {
  beforeEach(() => {
    useV1Deprecation().reset();
  });

  it("starts hidden with zero counter", () => {
    const { v1HitCount, visible, dismissed } = useV1Deprecation();
    expect(v1HitCount.value).toBe(0);
    expect(dismissed.value).toBe(false);
    expect(visible.value).toBe(false);
  });

  it("records hit when X-Shepard-Legacy header is present (Headers object)", () => {
    const { v1HitCount, visible, recordResponse } = useV1Deprecation();
    const headers = new Headers();
    headers.set("X-Shepard-Legacy", "true");
    recordResponse(headers);
    expect(v1HitCount.value).toBe(1);
    expect(visible.value).toBe(true);
  });

  it("records hit when X-Shepard-Legacy header is present (plain object)", () => {
    const { v1HitCount, visible, recordResponse } = useV1Deprecation();
    recordResponse({ "X-Shepard-Legacy": "true" });
    expect(v1HitCount.value).toBe(1);
    expect(visible.value).toBe(true);
  });

  it("plain-object lookup is case-insensitive", () => {
    const { v1HitCount, recordResponse } = useV1Deprecation();
    recordResponse({ "x-shepard-legacy": "true" });
    expect(v1HitCount.value).toBe(1);
  });

  it("ignores responses without the header", () => {
    const { v1HitCount, visible, recordResponse } = useV1Deprecation();
    recordResponse(new Headers());
    recordResponse({ "X-Shepard-Legacy": "false" });
    recordResponse({ "Content-Type": "application/json" });
    expect(v1HitCount.value).toBe(0);
    expect(visible.value).toBe(false);
  });

  it("ignores null / undefined headers gracefully", () => {
    const { v1HitCount, recordResponse } = useV1Deprecation();
    recordResponse(null);
    recordResponse(undefined);
    expect(v1HitCount.value).toBe(0);
  });

  it("increments counter on each hit", () => {
    const { v1HitCount, recordResponse } = useV1Deprecation();
    const headers = new Headers();
    headers.set("X-Shepard-Legacy", "true");
    recordResponse(headers);
    recordResponse(headers);
    recordResponse(headers);
    expect(v1HitCount.value).toBe(3);
  });

  it("dismiss hides the banner but keeps the counter", () => {
    const { v1HitCount, visible, dismissed, recordResponse, dismiss } = useV1Deprecation();
    recordResponse({ "X-Shepard-Legacy": "true" });
    recordResponse({ "X-Shepard-Legacy": "true" });
    dismiss();
    expect(visible.value).toBe(false);
    expect(dismissed.value).toBe(true);
    expect(v1HitCount.value).toBe(2);
  });

  it("dismissed state survives further hits within the session", () => {
    const { visible, recordResponse, dismiss } = useV1Deprecation();
    recordResponse({ "X-Shepard-Legacy": "true" });
    dismiss();
    recordResponse({ "X-Shepard-Legacy": "true" });
    expect(visible.value).toBe(false);
  });

  it("reset clears both counter and dismissal", () => {
    const { v1HitCount, visible, recordResponse, dismiss, reset } = useV1Deprecation();
    recordResponse({ "X-Shepard-Legacy": "true" });
    dismiss();
    reset();
    expect(v1HitCount.value).toBe(0);
    expect(visible.value).toBe(false);
  });

  it("handles array-valued headers (some HTTP runtimes normalise as arrays)", () => {
    const { v1HitCount, recordResponse } = useV1Deprecation();
    recordResponse({ "X-Shepard-Legacy": ["true"] });
    expect(v1HitCount.value).toBe(1);
  });

  it("treats 'TRUE' (uppercase) the same as 'true'", () => {
    const { v1HitCount, recordResponse } = useV1Deprecation();
    recordResponse({ "X-Shepard-Legacy": "TRUE" });
    expect(v1HitCount.value).toBe(1);
  });
});
