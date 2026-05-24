/**
 * Playwright e2e for the header-search dropdown — UI-2026-05-24-002.
 *
 * Coverage:
 *   - Typing a known DataObject name produces a dropdown with at least one
 *     dataobject row that matches.
 *   - Typing nonsense yields the empty-state.
 *   - Empty focus fires no spurious calls (debounce gate).
 *
 * Uses the existing `loginAs` helper. The LUMEN demo collection seeded by
 * `examples/lumen-showcase/seed.py` is the data source; TR-004 is present.
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "./helpers/auth";

const USER = process.env.DEMO_USER || "flo";
const PASSWORD = process.env.DEMO_PASSWORD || "flo-demo";

test.describe("Header search dropdown (UI-002)", () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, USER, PASSWORD);
  });

  test("typing a known DataObject name produces a matching dataobject row", async ({ page }) => {
    await page.goto("/");
    const input = page.getByTestId("header-search-input").locator("input");
    await input.click();
    await input.fill("TR-004");

    // Dropdown should open. Wait for at least one dataobject result.
    const doRow = page
      .getByTestId("header-search-result-dataobject")
      .filter({ hasText: "TR-004" })
      .first();
    await expect(doRow).toBeVisible({ timeout: 8_000 });

    // The advanced-search footer link should also be present.
    await expect(page.getByTestId("header-search-advanced")).toBeVisible();
  });

  test("typing a string with no matches shows the empty state", async ({ page }) => {
    await page.goto("/");
    const input = page.getByTestId("header-search-input").locator("input");
    await input.click();
    await input.fill("homarrxyzdoesnotexist");

    // Empty state appears once the search has completed with zero hits.
    const empty = page.getByTestId("header-search-empty");
    await expect(empty).toBeVisible({ timeout: 8_000 });
    await expect(empty).toContainText("No matches");
  });

  test("focusing the empty input does not fire a search request", async ({ page }) => {
    // Capture network calls to /search and /searchContainers.
    const searchCalls: string[] = [];
    page.on("request", req => {
      const url = req.url();
      if (
        url.includes("/shepard/api/search") &&
        req.method() === "POST"
      ) {
        searchCalls.push(url);
      }
    });

    await page.goto("/");
    const input = page.getByTestId("header-search-input").locator("input");
    await input.click();
    // Wait past the debounce window — should not have fired anything.
    await page.waitForTimeout(800);
    expect(searchCalls.length).toBe(0);

    // Dropdown should NOT be open for empty input.
    const dropdown = page.getByTestId("header-search-dropdown");
    // v-menu renders into a portal; check open-state via the result list visibility.
    const anyResult = page.getByTestId("header-search-result-collection").first();
    await expect(anyResult).not.toBeVisible();
  });

  test("clicking a result navigates to its detail route", async ({ page }) => {
    await page.goto("/");
    const input = page.getByTestId("header-search-input").locator("input");
    await input.click();
    await input.fill("TR-004");

    const doRow = page
      .getByTestId("header-search-result-dataobject")
      .filter({ hasText: "TR-004" })
      .first();
    await expect(doRow).toBeVisible({ timeout: 8_000 });
    await doRow.click();

    // Should land on a dataobject detail route.
    await page.waitForURL(/\/collections\/\d+\/dataobjects\/\d+/, { timeout: 10_000 });
  });
});
