/**
 * UI-011e — `# DOs` column on /collections must show a real count.
 *
 * Regression test for the bug surfaced by the UX Scrutinizer
 * (`aidocs/agent-findings/ux-scrutinizer-workflows-2026-05-24.md` §"# DOs = 0"):
 * every collection card showed `0` because the search-backed list endpoint
 * (`SearchDAO.findCollections`) used `Neighborhood.ESSENTIAL`, which only
 * walks `:Permission` / `:User` and so never hydrates DataObjects on the
 * returned `Collection` entities. `CollectionIO.dataObjectIds[]` therefore
 * came back as `[]` for every row, and the frontend's `# DOs` cell
 * (`{{ (rowProps.item.dataObjectIds || []).length }}`) rendered `0`.
 *
 * The fix flipped `emitCollectionReturnPart` to `Neighborhood.EVERYTHING`
 * (depth 1) — the same neighborhood `CollectionDAO.findAllCollectionsByShepardId`
 * already uses on the non-search v2 listing path, which had always returned
 * correct counts in production.
 *
 * Acceptance: the LUMEN row and the MFFD-Dropbox row must each show a
 * positive integer in the `# DOs` column.
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "./helpers/auth";

test.describe("UI-011e — # DOs column shows a real count", () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, "alice", "alice-demo");
  });

  test("LUMEN row's # DOs cell shows a non-zero count", async ({ page }) => {
    // The collections list is paginated and ordered by createdAt desc, so the
    // LUMEN showcase (created on first deploy) is rarely on page 1. Use the
    // in-page search filter via the URL `searchText` query param — this is
    // wired by `CollectionSearchField.vue` and translates to the
    // `SearchApi.searchCollections` query the fix actually touched. Avoid the
    // global header search (it navigates to /advanced-search, which is a
    // different surface).
    await page.goto("/collections?searchText=LUMEN");
    await page.waitForLoadState("networkidle");

    const lumenRow = page.locator("tr", {
      has: page.locator("td", { hasText: /LUMEN/i }).first(),
    }).first();

    await expect(lumenRow).toBeVisible({ timeout: 10_000 });

    const doCount = lumenRow.locator('[data-testid="collection-row-do-count"]');
    await expect(doCount).toBeVisible();
    const text = (await doCount.textContent())?.trim() ?? "";

    // Parse the leading integer (the cell may suffix with a "large" chip).
    const match = text.match(/^(\d+)/);
    expect(match, `expected "# DOs" to start with an integer, got "${text}"`).not.toBeNull();
    const count = match ? Number.parseInt(match[1], 10) : 0;
    expect(count, `LUMEN must have at least 1 DataObject — got ${count}`).toBeGreaterThan(0);
  });

  test("MFFD-Dropbox row's # DOs cell shows a non-zero count", async ({ page }) => {
    await page.goto("/collections?searchText=MFFD");
    await page.waitForLoadState("networkidle");

    const mffdRow = page.locator("tr", {
      has: page.locator("td", { hasText: /MFFD-Dropbox/i }).first(),
    }).first();

    // Tolerate environments where the MFFD-Dropbox collection isn't seeded:
    // skip rather than fail. The LUMEN test above still proves the fix works.
    if ((await mffdRow.count()) === 0) {
      test.skip(true, "MFFD-Dropbox collection not present on this instance.");
      return;
    }

    await expect(mffdRow).toBeVisible();
    const doCount = mffdRow.locator('[data-testid="collection-row-do-count"]');
    await expect(doCount).toBeVisible();
    const text = (await doCount.textContent())?.trim() ?? "";
    const match = text.match(/^(\d+)/);
    expect(match, `expected "# DOs" to start with an integer, got "${text}"`).not.toBeNull();
    const count = match ? Number.parseInt(match[1], 10) : 0;
    // MFFD-Dropbox at last verified state had ~8500 DOs; assert at least 100
    // so a half-imported instance still passes but an empty cell fails loudly.
    expect(count, `MFFD-Dropbox must show a sizeable count — got ${count}`).toBeGreaterThan(100);
  });
});
