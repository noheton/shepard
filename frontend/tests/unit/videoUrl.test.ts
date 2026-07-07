/**
 * MFFD-VIDEOREF-SCALE-1 — tests for the video URL helper that builds
 * a query-param-authenticated download URL for HTML5 `<video src>`.
 *
 * The browser cannot inject custom headers on `<video src>`, so the
 * JWT travels as `?access_token=`. `JWTFilter` reads the query param
 * when no Authorization header is present (RFC 6750 §2.3).
 */
import { describe, it, expect } from "vitest";
import { withAccessTokenQueryParam } from "~/utils/videoUrl";

describe("withAccessTokenQueryParam", () => {
  it("returns the URL unchanged when no token is given", () => {
    expect(withAccessTokenQueryParam("/v2/references/abc/content", null)).toBe(
      "/v2/references/abc/content",
    );
    expect(withAccessTokenQueryParam("/v2/references/abc/content", undefined)).toBe(
      "/v2/references/abc/content",
    );
    expect(withAccessTokenQueryParam("/v2/references/abc/content", "")).toBe(
      "/v2/references/abc/content",
    );
  });

  it("appends ?access_token to a relative URL with no existing query", () => {
    // APISIMP-VIDEO-TOMBSTONE-DELETE: unified download path
    const out = withAccessTokenQueryParam(
      "/v2/references/y/content",
      "tok123",
      "http://shep.local",
    );
    expect(out).toBe("/v2/references/y/content?access_token=tok123");
  });

  it("preserves existing query parameters", () => {
    const out = withAccessTokenQueryParam(
      "/v2/references/abc/content?inline=1",
      "tok123",
      "http://shep.local",
    );
    expect(out).toContain("inline=1");
    expect(out).toContain("access_token=tok123");
  });

  it("preserves absolute URLs", () => {
    const out = withAccessTokenQueryParam(
      "https://shep.example.com/v2/references/abc/content",
      "tok123",
      "http://shep.local",
    );
    expect(out).toBe(
      "https://shep.example.com/v2/references/abc/content?access_token=tok123",
    );
  });

  it("URL-encodes a token containing reserved characters", () => {
    const out = withAccessTokenQueryParam(
      "/v2/references/abc/content",
      "tok+ /=ABC",
      "http://shep.local",
    );
    // URLSearchParams encodes the value; verify decoded round-trip.
    const u = new URL(out, "http://shep.local");
    expect(u.searchParams.get("access_token")).toBe("tok+ /=ABC");
  });

  it("does not rewrite the origin when given a relative URL", () => {
    const out = withAccessTokenQueryParam(
      "/v2/path",
      "tok",
      "http://shep.example.com",
    );
    // Must NOT contain the resolution origin — only path + query.
    expect(out.startsWith("/")).toBe(true);
    expect(out).not.toContain("shep.example.com");
  });

  it("falls back to manual append on URL parse failure", () => {
    // An empty string is unparseable in the URL constructor without
    // a base; ensure the fallback runs cleanly.
    const out = withAccessTokenQueryParam("", "tok123", undefined);
    // Either way, the result must include the token; the contract is
    // "the consumer gets a URL it can request with the token attached."
    expect(out).toContain("access_token=tok123");
  });
});
