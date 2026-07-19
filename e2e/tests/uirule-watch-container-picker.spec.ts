/**
 * UIRULE-NO-MANUAL-IDS — WatchedContainersPanel "Add watch" form @ 4K.
 *
 * Verifies the container field is a searchable picker (v-autocomplete), NOT a
 * raw appId paste field. Also a regression guard for WATCH-ENVELOPE-UNWRAP: the
 * /watches paged envelope must be unwrapped, else the panel crashes the moment
 * this form opens.
 *
 * Uses a self-owned fixture collection (Bearer API helper) so the editor-only
 * "Add watch" affordance is present; the fixture is soft-deleted in afterEach.
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "./helpers/auth";
import { createFixtureCollection } from "./helpers/api";

test.use({ viewport: { width: 3840, height: 2160 } });

test.describe("UIRULE watch-container picker @4K", () => {
  let cleanup: (() => Promise<void>) | null = null;

  test.beforeEach(async ({ page }) => {
    await loginAs(page, "alice", "alice-demo");
  });

  test.afterEach(async () => {
    if (cleanup) await cleanup().catch(() => {});
    cleanup = null;
  });

  test("Add-watch form exposes a searchable picker, not a raw appId field", async ({ page }) => {
    const fixture = await createFixtureCollection(page, `e2e-watch-picker-${Date.now()}`);
    cleanup = fixture.cleanup;

    // Let the owner's :Permissions edge seed (PERM-SEED-V1-CREATE, ~2-3 s) so the
    // parent page passes isAllowedToEdit=true.
    await page.waitForTimeout(5000);
    await page.goto(`/collections/${fixture.appId}`, { waitUntil: "domcontentloaded" });

    // "Watched containers" is a collapsed ExpansionPanelItem; expand it via its
    // header button (scoped so we don't match the sidebar tree).
    const panel = page.locator(".v-expansion-panel", {
      has: page.getByRole("button", { name: /watched containers/i }),
    });
    await panel.getByRole("button", { name: /watched containers/i }).click();

    // toBeVisible() polls through the expansion animation.
    const addWatch = panel.getByRole("button", { name: /add watch/i });
    await expect(addWatch).toBeVisible({ timeout: 10000 });
    await addWatch.click();

    // The DEFAULT field MUST be the searchable picker (v-autocomplete), not a
    // paste field. (Before WATCH-ENVELOPE-UNWRAP this click crashed the panel.)
    const picker = panel.locator('[data-testid="watch-container-autocomplete"]');
    await expect(picker).toBeVisible({ timeout: 8000 });

    // Paste is only the fallback behind the "advanced" toggle → picker is default.
    await expect(panel.locator('[data-testid="watch-advanced-toggle"]')).toContainText(/paste an appId/i);

    // Type-to-search must be accepted without a fatal render error.
    const input = picker.locator("input").first();
    await input.click();
    await input.fill("a");
    await expect(page.locator("body")).not.toContainText("Cannot read");

    await page.screenshot({ path: "screenshots/uirule-watch-container-picker-4k.png" });
  });
});
