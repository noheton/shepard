/**
 * RDM-002 — ORCID input on `/me/profile`.
 *
 * The RDM Scrutinizer flagged that the user-profile page surfaced no
 * ORCID input, even though the backend has the field + ISO 7064 mod 11-2
 * validation. Previously the field lived inside an "Edit Profile" dialog;
 * a researcher who didn't think to click "Edit" never saw it. This was a
 * FAIR R1 (rich author metadata) gap that also blocked task #18's ORCID
 * badge from rendering for the typical user.
 *
 * Fix: promote the ORCID + display-name fields to inline editors on the
 * Profile pane (`ProfilePane.vue`) under a new "Identity" section, and
 * mirror the avatar's ORCID-badge overlay onto the header avatar button
 * (`HeaderBar.vue`).
 *
 * This spec verifies, against the live deployment, that:
 *   - the ORCID input is reachable in one navigation step (no Edit click)
 *   - a valid ORCID persists across reload
 *   - an invalid ORCID surfaces an inline error before save
 */
import { expect, test } from "@playwright/test";

const KC = process.env.KEYCLOAK_HOST || "https://shepard-auth.nuclide.systems";
const REALM = "shepard-demo";
const USER = process.env.E2E_USER || "alice";
const PASS = process.env.E2E_PASS || "alice-demo";

// Valid test ORCIDs (genuine mod 11-2 checksums — the backend validator
// rejects anything else, so the test ORCIDs MUST be real-checksum).
const VALID_ORCID = "0000-0002-1825-0097"; // canonical orcid.org example
const VALID_ORCID_ALT = "0000-0002-1694-233X"; // valid example with `X` check digit
const INVALID_ORCID = "abc";

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

async function clearOrcid(page: import("@playwright/test").Page) {
  // Set ORCID back to empty so subsequent test runs start from a known
  // state. Best-effort — if the field already exists and is editable we
  // wipe it via the inline form, otherwise we just continue.
  await page.goto("/me#profile");
  const input = page.getByTestId("profile-orcid-input").locator("input");
  if (await input.count()) {
    await input.first().fill("");
    const save = page.getByTestId("profile-identity-save");
    if (await save.isEnabled().catch(() => false)) {
      await save.click();
      await page.waitForTimeout(500); // small settle window for the PATCH
    }
  }
}

test.describe("RDM-002: ORCID inline input on /me/profile", () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, USER, PASS);
    await clearOrcid(page);
  });

  test.afterAll(async ({ browser }) => {
    // Leave the test user without an ORCID so the next test run is
    // deterministic; do this in a fresh context to avoid coupling to the
    // last test's page state.
    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    try {
      await loginAs(page, USER, PASS);
      await clearOrcid(page);
    } finally {
      await ctx.close();
    }
  });

  test("ORCID input is visible without clicking Edit", async ({ page }) => {
    await page.goto("/me#profile");
    // The "Identity" section + ORCID input must be in the DOM and
    // visible on first paint — no dialog open, no Edit click needed.
    const identity = page.getByTestId("profile-identity-section");
    await expect(identity).toBeVisible();
    const orcidInput = page.getByTestId("profile-orcid-input");
    await expect(orcidInput).toBeVisible();
    // Confirm the discoverability fix: no "Edit" affordance is required.
    // We don't ban an Edit button outright (a future plugin might add
    // one) but we ensure no modal dialog is intercepting the field.
    await expect(page.locator(".v-overlay__content")).toHaveCount(0);
  });

  test("typing a valid ORCID, saving, and reloading persists the value", async ({
    page,
  }) => {
    await page.goto("/me#profile");
    const orcidInput = page.getByTestId("profile-orcid-input").locator("input");
    await orcidInput.fill(VALID_ORCID);
    await page.getByTestId("profile-identity-save").click();
    // Wait for the PATCH round-trip to land; the "currently set" hint
    // appears only after the server confirms the new ORCID.
    await expect(page.getByTestId("profile-orcid-current")).toContainText(
      VALID_ORCID,
      { timeout: 10_000 },
    );

    // Reload and re-assert from server state.
    await page.reload();
    await page.waitForURL(/\/me/);
    const orcidInputAfter = page
      .getByTestId("profile-orcid-input")
      .locator("input");
    await expect(orcidInputAfter).toHaveValue(VALID_ORCID, { timeout: 10_000 });
  });

  test("typing an invalid ORCID surfaces an inline error and blocks save", async ({
    page,
  }) => {
    await page.goto("/me#profile");
    const orcidInput = page.getByTestId("profile-orcid-input").locator("input");
    await orcidInput.fill(INVALID_ORCID);
    // Vuetify renders error-messages inside a .v-messages slot under
    // the field; assert one of them carries the "ORCID" word.
    const orcidField = page.getByTestId("profile-orcid-input");
    await expect(orcidField).toContainText(/ORCID/i, { timeout: 5_000 });
    // The Save button must be disabled while the input is invalid.
    const save = page.getByTestId("profile-identity-save");
    await expect(save).toBeDisabled();
  });

  test("the alternate X-checksum ORCID is accepted", async ({ page }) => {
    await page.goto("/me#profile");
    const orcidInput = page.getByTestId("profile-orcid-input").locator("input");
    await orcidInput.fill(VALID_ORCID_ALT);
    const save = page.getByTestId("profile-identity-save");
    await expect(save).toBeEnabled({ timeout: 5_000 });
    await save.click();
    await expect(page.getByTestId("profile-orcid-current")).toContainText(
      VALID_ORCID_ALT,
      { timeout: 10_000 },
    );
  });

  test("once an ORCID is set, the header avatar shows the ORCID badge", async ({
    page,
  }) => {
    await page.goto("/me#profile");
    const orcidInput = page.getByTestId("profile-orcid-input").locator("input");
    await orcidInput.fill(VALID_ORCID);
    await page.getByTestId("profile-identity-save").click();
    await expect(page.getByTestId("profile-orcid-current")).toContainText(
      VALID_ORCID,
      { timeout: 10_000 },
    );
    // Navigate somewhere else so the header is the only ORCID badge in
    // view (the profile page's own avatar carries one too).
    await page.goto("/collections");
    // The header avatar button must exist; the ORCID badge overlay must
    // render because `showOrcidBadge` defaults to true on first set.
    const headerAvatar = page.getByTestId("header-user-avatar-btn");
    await expect(headerAvatar).toBeVisible();
    const badge = page.getByTestId("header-user-orcid-badge");
    await expect(badge).toBeVisible({ timeout: 10_000 });
  });
});
