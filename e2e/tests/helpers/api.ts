/**
 * Bearer-token API helper for the e2e suite.
 *
 * The Shepard v2 API is Bearer-authenticated: Playwright's `page.request`
 * sends only cookies (→ 401 on `/v2/...`), so specs that need to resolve or
 * create entities via the API must attach a Bearer token.
 *
 * `sessionToken(page)` extracts the **live nuxt-auth session access token** of
 * the currently logged-in page (via the `/api/auth/session` endpoint the
 * frontend exposes). Because it is the exact token the browser session uses,
 * it resolves to the SAME backend `:User` — so API-created fixtures are owned
 * by (and therefore editable in) the browser session. Prefer this over a
 * standalone Keycloak password grant, whose token can resolve to a different
 * principal/audience and leave fixtures un-editable in the browser.
 *
 * Call AFTER `loginAs(page, …)`.
 */
import type { Page } from "@playwright/test";

/** The live session access token for the logged-in page. Throws if absent. */
export async function sessionToken(page: Page): Promise<string> {
  const res = await page.request.get("/api/auth/session");
  if (!res.ok()) {
    throw new Error(`/api/auth/session -> ${res.status()} (is the page logged in?)`);
  }
  const body = await res.json().catch(() => ({} as Record<string, unknown>));
  const token = (body as { accessToken?: string }).accessToken;
  if (!token) {
    throw new Error(`no accessToken in nuxt-auth session (keys: ${Object.keys(body || {}).join(",")})`);
  }
  return token;
}

/** Authorization + JSON headers for a v2 API call, bound to the page session. */
export async function authHeaders(page: Page): Promise<Record<string, string>> {
  return { Authorization: `Bearer ${await sessionToken(page)}`, "Content-Type": "application/json" };
}

/**
 * Create a throwaway Collection owned by the current session user and return
 * its appId plus a `cleanup()` that soft-deletes it.
 *
 * `cleanup()` retries the DELETE: the owner's `:Permissions` edge is seeded
 * asynchronously (~2 s) after create (the PERM-SEED-V1-CREATE lag), so an
 * immediate delete 403s. Call `cleanup()` in an `afterEach`/`finally`.
 */
export async function createFixtureCollection(
  page: Page,
  name: string,
): Promise<{ appId: string; cleanup: () => Promise<void> }> {
  const token = await sessionToken(page);
  const headers = { Authorization: `Bearer ${token}`, "Content-Type": "application/json" };
  const res = await page.request.post("/v2/collections", {
    headers,
    data: { name, description: "e2e fixture — safe to delete" },
  });
  if (!res.ok()) throw new Error(`create fixture collection -> ${res.status()} ${await res.text()}`);
  const appId = (await res.json()).appId as string;

  const cleanup = async () => {
    for (let i = 0; i < 5; i++) {
      const d = await page.request.delete(`/v2/collections/${appId}`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (d.status() === 204) return;
      await page.waitForTimeout(2000); // wait out the permission-seed lag
    }
  };
  return { appId, cleanup };
}
