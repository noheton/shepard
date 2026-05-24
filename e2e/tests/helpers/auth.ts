import type { Page, BrowserContext } from "@playwright/test";

// Default to the public Keycloak host; per-spec env overrides remain supported
// for local-only setups. Two recent UI-013 + UI-018 agents both flagged the
// previous internal-IP default as stale (their specs override per-file).
const KC = process.env.KEYCLOAK_HOST || "https://shepard-auth.nuclide.systems";
const REALM = "shepard-demo";

/**
 * Tolerant OIDC browser login. Returns when the app's home page is loaded
 * with the user signed in (SIGN OUT visible).
 *
 * Covers the two real-world cases the live shepard.nuclide.systems
 * Keycloak / NextAuth pair puts us in (E2E-AUTH-TOLERANT-LOGIN,
 * 2026-05-24):
 *
 *   1. Cookies cleared / fresh session → sign-in form appears →
 *      fill + submit, app redirects back.
 *   2. Keycloak SSO cookie still hot → Sign In button immediately
 *      bounces back to / via the OIDC callback (no form shown).
 *
 * The previous shape assumed case 1 only and timed out ~30% of the
 * time on case 2 (`waitForURL(KC)` never resolves because we never
 * leave the app origin). Three independent specs grew their own
 * `tolerantLogin` / `loginAsTolerant` overrides before this helper
 * was made tolerant; those overrides can now be deleted.
 *
 * Implementation notes:
 *   - Early-exit on "already signed in" (cheapest path, hot SSO).
 *   - Race between "Keycloak form appears" and "SIGN OUT visible
 *     in the app" — whichever wins, we don't deadlock.
 *   - Up to 3 retries for nuxt-auth's occasional sign-in
 *     redirect loop on partially-initialised sessions.
 */
export async function loginAs(
  page: Page,
  username: string,
  password: string,
): Promise<void> {
  // Cheapest path: are we already signed in? (e.g. shared storageState,
  // or a fresh page in a context that's already authed.)
  await page.goto("/", { waitUntil: "domcontentloaded" }).catch(() => {});
  if (
    await page
      .getByText(/sign out/i)
      .first()
      .isVisible()
      .catch(() => false)
  ) {
    return;
  }

  for (let attempt = 0; attempt < 3; attempt++) {
    await page.goto("/auth/signIn", { waitUntil: "domcontentloaded" });

    // Sometimes nuxt-auth bounces us straight to "/" with an active
    // session before the Sign In button is even rendered.
    if (
      await page
        .getByText(/sign out/i)
        .first()
        .isVisible()
        .catch(() => false)
    ) {
      return;
    }

    const signInBtn = page
      .getByRole("button", { name: /sign in|login/i })
      .first();
    if (await signInBtn.isVisible().catch(() => false)) {
      await signInBtn.click().catch(() => {});
    }

    // Race: either Keycloak form (case 1) or SSO bounce-back to app (case 2).
    try {
      await page.waitForURL(`${KC}/realms/${REALM}/**`, { timeout: 5_000 });
      // Case 1 — credentials form is up.
      await page.fill("#username", username);
      await page.fill("#password", password);
      await page.click('[type="submit"]');
    } catch {
      // Case 2 — Keycloak SSO short-circuited; no credentials needed.
    }

    // Wait for the app to settle with SIGN OUT visible. Try a couple
    // of times because nuxt-auth status sometimes needs a redirect or
    // two before the layout reflects the new session.
    for (let confirm = 0; confirm < 2; confirm++) {
      const ok = await page
        .waitForSelector("text=SIGN OUT", { timeout: 10_000 })
        .then(() => true)
        .catch(() => false);
      if (ok) return;
      // Nudge: if we're stuck on /auth/signIn, hit / once.
      if (page.url().includes("/auth/signIn")) {
        await page.goto("/", { waitUntil: "domcontentloaded" }).catch(() => {});
      }
    }
    // brief settle before next outer retry
    await page.waitForTimeout(1_500);
  }
  throw new Error(
    "loginAs: SIGN OUT never appeared after 3 tolerant retries (covered cookie-hot + cookie-cold paths)",
  );
}

/** Save auth state to a file so other tests can skip the login UI. */
export async function saveAuthState(
  context: BrowserContext,
  file: string,
): Promise<void> {
  await context.storageState({ path: file });
}
