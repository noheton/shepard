/**
 * UI-2026-05-24-003 + UI-2026-05-24-004 — SectionIndexLanding +
 * UnauthorizedView for /me, /admin, /about.
 *
 * As admin:
 *   /me, /admin, /about land on a card grid (no blank panel) and the cards
 *   navigate to the corresponding URL hash.
 *
 * As alice (non-admin):
 *   /admin keeps the URL at /admin and shows an Unauthorized view (no
 *   silent bounce to /me).
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "./helpers/auth";

test.describe("Section index landing — admin viewer", () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, "admin", "admin-demo");
  });

  for (const section of [
    { path: "/me", title: "My profile", fragment: "profile" },
    { path: "/admin", title: "Administration", fragment: "feature-toggles" },
    { path: "/about", title: "About shepard", fragment: "version" },
  ]) {
    test(`${section.path} shows the landing card grid (no blank panel)`, async ({
      page,
    }) => {
      await page.goto(section.path);
      await page.waitForLoadState("networkidle");

      // The landing title should be visible — proves the SectionIndexLanding
      // rendered instead of an empty content panel.
      await expect(page.getByRole("heading", { name: section.title })).toBeVisible();

      // At least one landing card should be present + clickable.
      const card = page.locator(`[data-fragment="${section.fragment}"]`).first();
      await expect(card).toBeVisible();
      await card.click();

      // Clicking the card should set the URL hash; the landing should
      // disappear (replaced by the sub-pane content).
      await page.waitForURL(new RegExp(`${section.path}#${section.fragment}`));
      expect(page.url()).toContain(`#${section.fragment}`);
    });
  }
});

test.describe("Unauthorized handling — alice on /admin", () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, "alice", "alice-demo");
  });

  test("alice hitting /admin sees Unauthorized view (URL stable, no silent bounce)", async ({
    page,
  }) => {
    await page.goto("/admin");
    await page.waitForLoadState("networkidle");

    // URL must still be /admin — Option A keeps the link shareable + the
    // user has explicit feedback. (The pre-fix behaviour silently swapped
    // the URL to /me.)
    expect(new URL(page.url()).pathname).toBe("/admin");

    // A visible message must explain why content isn't shown — either the
    // Unauthorized view's heading or any visible text containing
    // "access" / "permission" / "restricted".
    const body = page.locator("body");
    await expect(body).toContainText(/access|permission|restricted/i);
  });
});
