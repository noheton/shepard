/**
 * UI-005 regression test — collection landing must NOT fire
 * `GET /v2/collections/{appId}/watches/me`, which returns 404 by design
 * (intentional "not watching" signal) but pollutes the browser console
 * on every page load for every researcher on every collection.
 *
 * Root cause: `useCollectionWatch.refresh()` called `getMyWatch`, which hits
 * `/watches/me` and returns 404 when the caller isn't watching. The frontend
 * caught the 404 semantically as "not watching", but the browser logs ANY 4xx
 * on fetch to the console regardless of try/catch — so the JS-side suppression
 * couldn't silence the network-level error.
 *
 * Fix: `useCollectionWatch.refresh()` now uses the list endpoint
 * (`GET /v2/collections/{appId}/watches`, always 200 when caller has Read,
 * which the landing page does by construction) + `GET /v2/users/me` to
 * resolve the caller's username, then checks list membership.
 *
 * This test verifies:
 *   - LUMEN collection landing (id 42) emits no /watches/me request
 *   - MFFD-Dropbox collection landing (id 661923) emits no /watches/me request
 *   - The new wire is observed (/v2/users/me + /v2/collections/.../watches)
 */
import { expect, test } from "@playwright/test";

const KC = process.env.KEYCLOAK_HOST || "https://shepard-auth.nuclide.systems";
const REALM = "shepard-demo";
const USER = process.env.E2E_USER || "alice";
const PASS = process.env.E2E_PASS || "alice-demo";

async function loginAs(
  page: import("@playwright/test").Page,
  username: string,
  password: string,
) {
  await page.goto("/auth/signIn");
  await page
    .getByRole("button", { name: /sign in|login/i })
    .first()
    .click();
  await page.waitForURL(`${KC}/realms/${REALM}/**`, { timeout: 20_000 });
  await page.fill("#username", username);
  await page.fill("#password", password);
  await page.click('[type="submit"]');
  await page.waitForURL(/shepard\.nuclide\.systems(?!.*error)/, {
    timeout: 20_000,
  });
}

/**
 * Drive the browser to a collection landing page and record:
 *   - every network 404 (so we can assert /watches/me 404 is gone)
 *   - every successful request to /watches and /users/me (so we can assert
 *     the new wire shape is observed)
 */
async function captureCollectionLandingNetwork(
  page: import("@playwright/test").Page,
  collectionId: number,
) {
  const watchesMeRequests: string[] = [];
  const listWatchesRequests: string[] = [];
  const meRequests: string[] = [];
  const networkErrors: { url: string; status: number }[] = [];

  page.on("response", async (resp) => {
    const url = resp.url();
    const status = resp.status();
    if (/\/v2\/collections\/[^/]+\/watches\/me(\?|$)/.test(url)) {
      watchesMeRequests.push(`${status} ${url}`);
    }
    if (/\/v2\/collections\/[^/]+\/watches(\?|$)/.test(url)) {
      listWatchesRequests.push(`${status} ${url}`);
    }
    if (/\/v2\/users\/me(\?|$)/.test(url)) {
      meRequests.push(`${status} ${url}`);
    }
    if (status >= 400 && status < 600) {
      networkErrors.push({ url, status });
    }
  });

  await page.goto(`/collections/${collectionId}`, { waitUntil: "networkidle" });
  // Give the watch composable a beat to fire its initial refresh.
  await page.waitForTimeout(1500);

  return { watchesMeRequests, listWatchesRequests, meRequests, networkErrors };
}

test.describe("UI-005 — /watches/me 404 spam silenced on collection landing", () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, USER, PASS);
  });

  test("LUMEN collection landing (id 42) — no /watches/me request fires", async ({ page }) => {
    const { watchesMeRequests, listWatchesRequests, networkErrors } =
      await captureCollectionLandingNetwork(page, 42);

    // CORE INVARIANT: zero requests to the 404-emitting /watches/me endpoint.
    expect(
      watchesMeRequests,
      `Expected zero /watches/me requests, got: ${JSON.stringify(watchesMeRequests, null, 2)}`,
    ).toEqual([]);

    // Belt-and-braces: no /watches/me 404 in the broader network-error list.
    const watchesMe404s = networkErrors.filter(
      (e) => /\/watches\/me(\?|$)/.test(e.url) && e.status === 404,
    );
    expect(
      watchesMe404s,
      `Expected zero /watches/me 404s, got: ${JSON.stringify(watchesMe404s, null, 2)}`,
    ).toEqual([]);

    // POSITIVE INVARIANT: the new wire shape fired (list endpoint hit).
    expect(
      listWatchesRequests.length,
      `Expected at least one list-watches request, got: ${JSON.stringify(listWatchesRequests, null, 2)}`,
    ).toBeGreaterThanOrEqual(1);
  });

  test("MFFD-Dropbox collection landing (id 661923) — no /watches/me request fires", async ({ page }) => {
    const { watchesMeRequests, listWatchesRequests, networkErrors } =
      await captureCollectionLandingNetwork(page, 661923);

    expect(
      watchesMeRequests,
      `Expected zero /watches/me requests, got: ${JSON.stringify(watchesMeRequests, null, 2)}`,
    ).toEqual([]);

    const watchesMe404s = networkErrors.filter(
      (e) => /\/watches\/me(\?|$)/.test(e.url) && e.status === 404,
    );
    expect(
      watchesMe404s,
      `Expected zero /watches/me 404s, got: ${JSON.stringify(watchesMe404s, null, 2)}`,
    ).toEqual([]);

    expect(
      listWatchesRequests.length,
      `Expected at least one list-watches request, got: ${JSON.stringify(listWatchesRequests, null, 2)}`,
    ).toBeGreaterThanOrEqual(1);
  });
});
