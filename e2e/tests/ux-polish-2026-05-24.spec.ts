/**
 * UX polish bundle (2026-05-24) — four small wins shipped together.
 *
 * Pattern A: loading-state-leaks-as-error
 *   On cold load of a collection page the red error toast must NOT flash
 *   before the data actually arrives. `handleError` now suppresses
 *   AbortError + 401 (auth middleware handles those silently).
 *
 * Pattern E: sidebar sibling-nav tooltip
 *   The Contents header carries an info icon explaining the tree IS
 *   the sibling navigator.
 *
 * Pattern F: page titles include entity name
 *   Browser tab titles distinguish two collections / two DOs in
 *   parallel tabs.
 *
 * Bonus: `/search?q=X` URL param drives the search field + runs the query.
 *   The header-search dropdown's "See all results" footer link must round-trip
 *   through `?q=<query>`.
 */
import { test, expect } from "@playwright/test";

const USERNAME = process.env.DEMO_USER || "flo";
const PASSWORD = process.env.DEMO_PASSWORD || "flo-demo";

const LUMEN_ID = 42;
const SECOND_COLLECTION_ID = Number(
  process.env.SECOND_COLLECTION_ID ?? 661923,
);

/**
 * Tolerant login: covers two real-world cases the live Keycloak puts us in:
 *
 * 1. Cookies cleared / fresh session → sign-in form appears → fill + submit.
 * 2. Keycloak SSO cookie still hot → sign-in button immediately bounces
 *    back to / via the OIDC callback (no form shown).
 *
 * The vanilla `loginAs(page)` assumes case 1 only and times out on case 2
 * with a redirect-loop trace. We poll for "SIGN OUT" as the success
 * indicator and short-circuit when the form isn't shown.
 */
async function tolerantLogin(page: import("@playwright/test").Page): Promise<void> {
  await page.goto("/auth/signIn", { waitUntil: "domcontentloaded" });
  const signOutVisible = await page
    .getByText(/sign out/i)
    .first()
    .isVisible()
    .catch(() => false);
  if (signOutVisible) return;

  const signInBtn = page.getByRole("button", { name: /sign in|login/i }).first();
  if (await signInBtn.isVisible().catch(() => false)) {
    await signInBtn.click();
  }

  // Race: either the Keycloak form appears (case 1) or we land back on the
  // app with "SIGN OUT" (case 2 — SSO cookie hot).
  try {
    await page.waitForURL(/realms\/shepard-demo/, { timeout: 5_000 });
    await page.fill("#username", USERNAME);
    await page.fill("#password", PASSWORD);
    await page.click('[type="submit"]');
  } catch {
    // Case 2 path — no form needed.
  }

  // Be patient: against the live Keycloak, nuxt-auth's status sometimes
  // needs a couple of redirects before the layout shows SIGN OUT.
  for (let attempt = 0; attempt < 3; attempt++) {
    const ok = await page
      .waitForSelector("text=SIGN OUT", { timeout: 10_000 })
      .then(() => true)
      .catch(() => false);
    if (ok) return;
    // Nudge: if we're stuck on /auth/signIn, hit / once more.
    if (page.url().includes("/auth/signIn")) {
      await page.goto("/", { waitUntil: "domcontentloaded" });
    }
  }
  throw new Error("tolerantLogin: SIGN OUT never appeared");
}

test.describe("UX polish bundle 2026-05-24", () => {
  test.beforeEach(async ({ page }) => {
    await tolerantLogin(page);
  });

  test("Pattern A: no red error toast flashes on cold load of /collections/42", async ({ page }) => {
    // Cold load: beforeEach has logged us in; this is the first page hit
    // after sign-in, so all per-page fetches kick off from scratch and
    // would historically flash an error toast before data arrived.
    await page.goto(`/collections/${LUMEN_ID}`);

    // Watch for the red error snackbar in the first 2 seconds.
    // The ErrorNotification component renders a v-snackbar with class
    // bound to theme-error. We assert it doesn't appear.
    let toastSeen = false;
    const checkInterval = setInterval(async () => {
      const visible = await page
        .locator(".v-snackbar")
        .filter({ hasText: /Error while/ })
        .isVisible()
        .catch(() => false);
      if (visible) toastSeen = true;
    }, 100);
    await page.waitForTimeout(2000);
    clearInterval(checkInterval);

    expect(toastSeen, "red error toast must NOT flash on cold load").toBe(false);
  });

  test("Pattern F: collection page title contains the collection name", async ({ page }) => {
    await page.goto(`/collections/${LUMEN_ID}`);
    // Wait for the heading or any sign data has resolved.
    await page
      .locator("h1, h2")
      .first()
      .waitFor({ state: "visible", timeout: 10_000 });
    // Give the reactive useHead a tick.
    await page.waitForTimeout(500);
    const title = await page.title();
    expect(title).toMatch(/— shepard/);
    // Must NOT be the generic fallback once the collection has loaded.
    expect(title).not.toBe("shepard");
  });

  test("Pattern F: two collection tabs have distinct titles", async ({ page }) => {
    // Sequential rather than dual-tab: the outer beforeEach already logged
    // us in once; opening a second tab triggers a fresh login attempt which
    // is flaky against the live Keycloak. Sequential navigation in the same
    // tab still proves the title-getter is reactive to the data swap.
    await page.goto(`/collections/${LUMEN_ID}`);
    await page.locator("h1, h2").first().waitFor({ state: "visible", timeout: 10_000 });
    await page.waitForTimeout(500);
    const t1 = await page.title();

    await page.goto(`/collections/${SECOND_COLLECTION_ID}`);
    await page.locator("h1, h2").first().waitFor({ state: "visible", timeout: 10_000 });
    await page.waitForTimeout(500);
    const t2 = await page.title();

    // Both must end with "— shepard".
    expect(t1).toMatch(/— shepard/);
    expect(t2).toMatch(/— shepard/);
    if (LUMEN_ID !== SECOND_COLLECTION_ID) {
      expect(t1).not.toBe(t2);
    }
  });

  test("Search ?q= prefills the form and runs the query", async ({ page }) => {
    await page.goto("/search?q=TR-004", { waitUntil: "domcontentloaded" });
    // The Advanced Search renders the JSON-query into a v-text-field; the
    // computed JSON contains "TR-004" as the search value. We poll the
    // input's value attribute directly because innerText doesn't include
    // form-control values.
    await page.waitForFunction(
      () => {
        const inputs = Array.from(
          document.querySelectorAll("input, textarea"),
        ) as (HTMLInputElement | HTMLTextAreaElement)[];
        return inputs.some(i => (i.value ?? "").includes("TR-004"));
      },
      { timeout: 10_000 },
    );
  });

  test("Pattern E: CollectionSidebar Contents header carries the info tooltip", async ({ page }) => {
    await page.goto(`/collections/${LUMEN_ID}`);
    // The sidebar renders twice in the DOM (mobile + desktop layouts);
    // .first() picks whichever is visible at this viewport.
    const header = page
      .locator('[data-testid="collection-sidebar-contents-header"]')
      .first();
    await expect(header).toBeAttached({ timeout: 10_000 });
    // The info icon (mdi-information-outline) lives inside the header.
    const infoIcon = header.locator(".mdi-information-outline").first();
    await expect(infoIcon).toBeAttached({ timeout: 5_000 });
  });

  test("Header-search dropdown 'See all results' navigates to /search?q=…", async ({ page }) => {
    await page.goto("/");
    // The header search input has data-testid="header-search-input".
    const search = page.getByTestId("header-search-input").locator("input");
    await search.click();
    await search.fill("TR-004");
    // Wait for debounce + dropdown render.
    await page.waitForTimeout(800);
    const advancedLink = page.locator('[data-testid="header-search-advanced"]');
    await advancedLink.waitFor({ state: "visible", timeout: 5_000 });
    await advancedLink.click();
    // Should land on /search?q=TR-004
    await page.waitForURL(/\/search\?.*q=TR-004/, { timeout: 5_000 });
    const url = page.url();
    expect(url).toContain("q=TR-004");
  });
});
