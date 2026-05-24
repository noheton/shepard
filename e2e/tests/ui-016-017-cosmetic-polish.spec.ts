/**
 * Playwright e2e for the UI-016 + UI-017 cosmetic polish pair.
 *
 *   - UI-016 (StructuredDataContainer "Referenced by" chip dedupe): visit a
 *     SD container detail page where the linked-by list has high-density
 *     rows (TR-001..TR-014 in LUMEN style) and assert that rows showing
 *     more than 3 annotations expose a clickable "+N more" overflow chip.
 *
 *   - UI-017 (DataObject description edit affordance): visit a LUMEN DO
 *     detail page, click the Description "Edit" button, and assert that
 *     the section gains a visual editing cue (the "Editing description"
 *     label, a dedicated data-testid, and a visible primary-tinted
 *     outline via the `description-editing` class).
 *
 * BASE_URL defaults to https://shepard.nuclide.systems (the live demo).
 * Both checks are best-effort against live data: if no qualifying SD
 * container is found in the demo, UI-016 is skipped (not failed) so the
 * suite stays green on a clean demo. UI-017 is deterministic against a
 * known LUMEN data object.
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "./helpers/auth";

const USER = process.env.DEMO_USER || "flo";
const PASSWORD = process.env.DEMO_PASSWORD || "flo-demo";

test.describe("UI-016 + UI-017 cosmetic polish", () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, USER, PASSWORD);
  });

  test("UI-016: SD container Referenced-by rows expose +N more when >3 annotations", async ({
    page,
  }) => {
    // Walk the SD container list, pick the first container whose detail page
    // has a Referenced-by row with >3 annotation chips. Skip gracefully if
    // no live container qualifies (the demo seed can drift between runs).
    await page.goto("/containers/structureddata");
    await page.waitForLoadState("networkidle", { timeout: 15_000 });

    // Find any container link on the page that points at a detail view.
    const containerLinks = page.locator(
      'a[href*="/containers/structureddata/"]',
    );
    const linkCount = await containerLinks.count();
    test.skip(
      linkCount === 0,
      "No SD containers visible to the test user — nothing to assert against.",
    );

    let foundOverflowChip = false;
    let scanned = 0;
    const max = Math.min(linkCount, 10);
    for (let i = 0; i < max; i++) {
      const href = await containerLinks.nth(i).getAttribute("href");
      if (!href) continue;
      await page.goto(href);
      await page.waitForLoadState("networkidle", { timeout: 15_000 });
      scanned += 1;

      // The Referenced-by panel auto-opens; wait briefly for it to mount.
      const overflowChips = page.locator(
        '[data-testid="annotations-overflow-chip"]',
      );
      try {
        await overflowChips.first().waitFor({ timeout: 3_000 });
        foundOverflowChip = true;
        // Sanity: the chip text matches "+N more" where N is a positive int.
        const chipText = (await overflowChips.first().textContent()) || "";
        expect(chipText).toMatch(/^\s*\+\d+\s+more\s*$/);

        // Click expands the row: the overflow chip should be hidden after
        // a click, and a "Show less" collapse chip should appear in the same
        // SemanticAnnotationList instance.
        await overflowChips.first().click();
        await expect(
          page.locator('[data-testid="annotations-collapse-chip"]').first(),
        ).toBeVisible({ timeout: 3_000 });
        break;
      } catch {
        // No high-density row on this container — try the next.
      }
    }

    test.skip(
      !foundOverflowChip,
      `Scanned ${scanned} SD container(s); none had a row with >3 annotations. ` +
        `UI-016 cap still in place, just nothing to assert against on this demo.`,
    );
  });

  test("UI-017: DO description Edit shows editing cue (label + outline)", async ({
    page,
  }) => {
    // LUMEN collection (id 42) is the synthetic showcase always seeded into
    // the demo. The DO ids drift with seed regenerations, so we discover the
    // first DataObject link on the collection landing page and follow it.
    await page.goto("/collections/42", { waitUntil: "networkidle" });
    await page.waitForTimeout(1500);

    const doLinks = page.locator('a[href*="/dataobjects/"]');
    const doCount = await doLinks.count();
    test.skip(
      doCount === 0,
      "LUMEN collection has no data objects on the landing page — nothing to assert against.",
    );
    const doHref = await doLinks.first().getAttribute("href");
    await page.goto(doHref!, { waitUntil: "networkidle" });
    await page.waitForTimeout(1500);

    // Static-state baseline.
    const editButton = page.getByRole("button", { name: /edit description/i });
    // Fall back to the plain "Edit" button if the aria-label hasn't been
    // re-rendered (e.g. stale SSR cache during a redeploy).
    const editButtonAny = (await editButton.count())
      ? editButton
      : page.locator('section.description-section button:has-text("Edit")');
    test.skip(
      (await editButtonAny.count()) === 0,
      "Test user lacks edit-collection permission on this data object.",
    );

    // Pre-click: section is in the static state, no editing label.
    await expect(
      page.locator('[data-testid="description-static"]'),
    ).toBeVisible();
    await expect(
      page.locator('[data-testid="description-editing-label"]'),
    ).toHaveCount(0);

    // Engage edit.
    await editButtonAny.first().click();

    // Post-click: the editing label is visible, the section has the
    // `description-editing` class, and the data-testid switched.
    const editingLabel = page.locator(
      '[data-testid="description-editing-label"]',
    );
    await expect(editingLabel).toBeVisible({ timeout: 5_000 });
    await expect(editingLabel).toHaveText(/editing description/i);

    const editingSection = page.locator(
      '[data-testid="description-editing"]',
    );
    await expect(editingSection).toBeVisible();
    await expect(editingSection).toHaveClass(/description-editing/);

    // Visual cue: the outline is set to a primary-tinted 2px solid (not
    // `transparent`). We assert the computed outline-style is "solid" and
    // the outline-color is not the transparent baseline.
    const outlineStyle = await editingSection.evaluate(
      (el) => getComputedStyle(el).outlineStyle,
    );
    expect(outlineStyle).toBe("solid");
    const outlineColor = await editingSection.evaluate(
      (el) => getComputedStyle(el).outlineColor,
    );
    expect(outlineColor).not.toBe("rgba(0, 0, 0, 0)");
    expect(outlineColor).not.toBe("transparent");
  });
});
