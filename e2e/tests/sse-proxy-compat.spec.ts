/**
 * P22 — SSE proxy-compatibility smoke test (Playwright side)
 *
 * Verifies that `GET /v2/collections/{id}/events` returns a valid SSE response
 * through the full reverse-proxy stack (Zoraxy → Caddy → Quarkus), specifically:
 *
 *   1. The response status is 200 (not 502 Bad Gateway / 504 Gateway Timeout)
 *   2. The Content-Type header is `text/event-stream`
 *   3. At least one SSE event line is received within 15 seconds
 *
 * Why page.request rather than native EventSource:
 *   The SSE endpoint requires an Authorization header.  Native EventSource cannot
 *   send custom headers (browser limitation).  The endpoint's own Javadoc says to
 *   use fetch + streaming body instead.  Playwright's page.request.fetch() does
 *   support custom headers and returns the response body as a stream.
 *
 * Known proxy issue (P22 findings):
 *   The Caddy `handle /v2/*` catch-all (Caddyfile line 43-45) lacks
 *   `flush_interval -1`, which means Caddy may buffer the SSE stream.  If test 3
 *   fails intermittently while tests 1 and 2 pass, the Caddyfile fix in
 *   aidocs/ops/p22-sse-proxy-compat-findings.md is the next step.
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "./helpers/auth";

// Collection appId to use for SSE smoke — uses an env var so CI and operators
// can supply a known-good collection without hardcoding.  Falls back to the
// LUMEN showcase collection appId that the seed script creates deterministically.
const COLLECTION_ID =
  process.env.SSE_SMOKE_COLLECTION_ID ||
  process.env.SMOKE_COLLECTION_ID ||
  ""; // empty = skip the test that needs a real ID

const BASE_URL = process.env.BASE_URL || "https://shepard.nuclide.systems";
const API_URL = BASE_URL.replace(/\/$/, "");

test.describe("P22 — SSE proxy-compatibility", () => {
  // Retrieve an API token from the authenticated browser session so we can
  // make authenticated requests against the backend from within the test.
  let bearerToken: string | null = null;

  test.beforeEach(async ({ page }) => {
    await loginAs(page, "alice", "alice-demo");
    // Extract the token from the browser's local/session storage so we can
    // reuse it for direct API calls.  NextAuth stores it under "next-auth.session-token"
    // or similar; we probe both the localStorage and cookies.
    bearerToken = await page.evaluate(() => {
      // Try to find a token stored by useFetchNotifications / the Nuxt auth layer.
      for (const key of Object.keys(localStorage)) {
        if (key.toLowerCase().includes("token") || key.toLowerCase().includes("auth")) {
          return localStorage.getItem(key);
        }
      }
      return null;
    });
  });

  test("SSE endpoint returns 200 (not 502/504) through reverse proxy", async ({
    page,
  }) => {
    test.skip(!COLLECTION_ID, "SSE_SMOKE_COLLECTION_ID not set — skipping live proxy check");

    // Use a short timeout GET to check the response code.  The stream will not
    // close on its own; we set a short abort timeout and check the response
    // status before the body arrives.
    const response = await page.request.fetch(
      `${API_URL}/v2/collections/${COLLECTION_ID}/events`,
      {
        method: "GET",
        headers: {
          Accept: "text/event-stream",
          ...(bearerToken ? { Authorization: `Bearer ${bearerToken}` } : {}),
        },
        // Playwright fetch timeout in ms — short enough to not hang the test
        // but long enough to receive the response headers + first event.
        timeout: 15_000,
        // Do not wait for the body to close (it never will for SSE).
        // We check the headers and first chunk.
      },
    ).catch((err) => {
      // If the request fails entirely (connection refused, DNS error) that is
      // also a failure — re-throw so the test fails with a useful message.
      throw new Error(`Request to SSE endpoint failed: ${err.message}`);
    });

    // Status must be 200 — not 502 (Bad Gateway) or 504 (Gateway Timeout)
    expect(
      response.status(),
      `Expected 200 from SSE endpoint, got ${response.status()} — proxy may be misconfigured`,
    ).toBe(200);
  });

  test("SSE endpoint returns Content-Type: text/event-stream through reverse proxy", async ({
    page,
  }) => {
    test.skip(!COLLECTION_ID, "SSE_SMOKE_COLLECTION_ID not set — skipping live proxy check");

    const response = await page.request.fetch(
      `${API_URL}/v2/collections/${COLLECTION_ID}/events`,
      {
        method: "GET",
        headers: {
          Accept: "text/event-stream",
          ...(bearerToken ? { Authorization: `Bearer ${bearerToken}` } : {}),
        },
        timeout: 15_000,
      },
    );

    const contentType = response.headers()["content-type"] ?? "";
    expect(
      contentType,
      "Response Content-Type must be text/event-stream — a buffering proxy may have swallowed the header",
    ).toContain("text/event-stream");
  });

  test("SSE endpoint emits at least one event within 15 seconds (proxy flush check)", async ({
    page,
  }) => {
    test.skip(!COLLECTION_ID, "SSE_SMOKE_COLLECTION_ID not set — skipping live proxy check");

    // Read the response body as a stream and look for any SSE event line.
    // CollectionEventsRest emits an immediate HEARTBEAT on connect, so we
    // expect to see a line starting with "data:" or ":heartbeat" or "event:"
    // within the timeout window.
    //
    // NOTE: If this test fails while tests 1 and 2 pass, the cause is almost
    // certainly Caddy buffering the stream.  The fix is described in
    // aidocs/ops/p22-sse-proxy-compat-findings.md.
    const response = await page.request.fetch(
      `${API_URL}/v2/collections/${COLLECTION_ID}/events`,
      {
        method: "GET",
        headers: {
          Accept: "text/event-stream",
          ...(bearerToken ? { Authorization: `Bearer ${bearerToken}` } : {}),
        },
        timeout: 15_000,
      },
    );

    expect(response.status()).toBe(200);

    // body() buffers until the connection closes; for an SSE stream that is
    // until max-time.  We rely on Playwright's test timeout (30 s) as the
    // outer bound.  If Caddy is buffering, body() will return empty — that
    // is the test failure we want to surface.
    const body = await response.text().catch(() => "");

    const hasEvent =
      body.includes("data:") ||
      body.includes(":heartbeat") ||
      body.includes("event:");

    expect(
      hasEvent,
      [
        "No SSE events received in 15 s.",
        "If tests 1 and 2 passed but this test fails, Caddy is buffering the /v2/* route.",
        "Fix: add a dedicated 'handle /v2/collections/*/events' block with 'flush_interval -1'",
        "See: aidocs/ops/p22-sse-proxy-compat-findings.md",
      ].join("\n"),
    ).toBe(true);
  });

  test("SSE endpoint rejects unauthenticated request with 401", async ({
    page,
  }) => {
    test.skip(!COLLECTION_ID, "SSE_SMOKE_COLLECTION_ID not set — skipping live proxy check");

    // Fire without auth — must get 401, not 200 or 502.
    const response = await page.request.fetch(
      `${API_URL}/v2/collections/${COLLECTION_ID}/events`,
      {
        method: "GET",
        headers: { Accept: "text/event-stream" },
        timeout: 10_000,
        failOnStatusCode: false,
      },
    );

    expect(
      response.status(),
      "Unauthenticated SSE request must return 401 (endpoint is not public)",
    ).toBe(401);
  });
});
