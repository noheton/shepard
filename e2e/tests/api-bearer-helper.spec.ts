/**
 * Smoke test for the Bearer-token API helper (tests/helpers/api.ts).
 *
 * Proves the two facts the helper exists to provide:
 *   1. the live nuxt-auth session token authorizes the v2 API (cookies alone → 401);
 *   2. createFixtureCollection creates a session-owned Collection and self-cleans
 *      (delete retries through the ~2 s owner-permission seed lag).
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "./helpers/auth";
import { sessionToken, createFixtureCollection } from "./helpers/api";

test.describe("e2e Bearer API helper", () => {
  test("session token authorizes the v2 API (cookies alone would 401)", async ({ page }) => {
    await loginAs(page, "alice", "alice-demo");

    // Cookies-only request is unauthorized on the Bearer-gated v2 surface…
    const noAuth = await page.request.get("/v2/collections?page=0&size=1");
    expect(noAuth.status()).toBe(401);

    // …the helper's session token authorizes it.
    const token = await sessionToken(page);
    expect(token.length).toBeGreaterThan(100);
    const withAuth = await page.request.get("/v2/collections?page=0&size=1", {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(withAuth.status()).toBe(200);
  });

  test("createFixtureCollection creates a session-owned collection and self-cleans", async ({ page }) => {
    await loginAs(page, "alice", "alice-demo");
    const { appId, cleanup } = await createFixtureCollection(page, `e2e-helper-smoke-${Date.now()}`);
    expect(appId).toMatch(/^[0-9a-f-]{36}$/);
    await cleanup(); // 204 after the permission-seed retry
  });
});
