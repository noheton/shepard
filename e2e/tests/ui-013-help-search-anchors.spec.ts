/**
 * Playwright e2e for in-page help search + per-heading anchors — UI-2026-05-24-013.
 *
 * Coverage:
 *   - Search box filters sections on the current page (matching sections
 *     remain visible; non-matching ones collapse via `doc-section--hidden`).
 *   - Clearing the search restores all sections.
 *   - Clicking a heading anchor updates the URL hash and scrolls the heading
 *     into view.
 *
 * Backed by `frontend/components/context/help/HelpFrame.vue` + the
 * heading/anchor generation in `frontend/utils/helpMarkdown.ts`.
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "./helpers/auth";

const USER = process.env.DEMO_USER || "flo";
const PASSWORD = process.env.DEMO_PASSWORD || "flo-demo";

// The shared loginAs() helper defaults KEYCLOAK_HOST to a stale internal IP.
// Newer specs override via env at run time; document the convention here.
if (!process.env.KEYCLOAK_HOST) {
  process.env.KEYCLOAK_HOST = "https://shepard-auth.nuclide.systems";
}

test.describe("Help page search + anchors (UI-013)", () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, USER, PASSWORD);
  });

  test("search box filters sections on the current help page", async ({ page }) => {
    // The User guide has ≥9 H2 sections including "Collections", "DataObjects",
    // "Permissions", "References", etc. — solid target for filtering.
    await page.goto("/help?page=user-guide");

    // Wait for the rendered markdown to mount + sections to wrap.
    const content = page.getByTestId("help-content");
    await expect(content).toBeVisible();
    await expect(content.locator(".doc-section")).not.toHaveCount(0);

    const totalBefore = await content.locator(".doc-section").count();
    expect(totalBefore).toBeGreaterThanOrEqual(3);

    // Type a substring that should match at least one section.
    const input = page.getByTestId("help-search-input").locator("input");
    await input.click();
    await input.fill("collection");

    // Wait for the filter to apply — status text should appear.
    await expect(page.getByTestId("help-search-status")).toBeVisible();

    // Non-matching sections must carry the hidden class; matching ones must not.
    const visibleSections = content.locator(".doc-section:not(.doc-section--hidden)");
    const hiddenSections = content.locator(".doc-section.doc-section--hidden");
    const visCount = await visibleSections.count();
    const hidCount = await hiddenSections.count();
    expect(visCount).toBeGreaterThan(0);
    expect(visCount + hidCount).toBe(totalBefore);
    // "collection" should NOT match every section, so we expect at least one hide.
    expect(hidCount).toBeGreaterThan(0);

    // Clear search → all sections visible again.
    await input.fill("");
    const afterClearVisible = await content
      .locator(".doc-section:not(.doc-section--hidden)")
      .count();
    expect(afterClearVisible).toBe(totalBefore);
  });

  test("clicking a heading anchor updates the URL hash and scrolls", async ({ page }) => {
    await page.goto("/help?page=user-guide");

    const content = page.getByTestId("help-content");
    await expect(content).toBeVisible();

    // Pick the first heading anchor on the page (any H2/H3).
    const firstAnchor = content.locator(".doc-heading-anchor").first();
    await expect(firstAnchor).toHaveCount(1);
    const href = await firstAnchor.getAttribute("href");
    expect(href).toMatch(/^#[a-z0-9-]+$/);
    const slug = href!.slice(1);

    // Anchor links are revealed on heading hover — click via JS to avoid the
    // hover-coordinate dance.
    await firstAnchor.evaluate((el: HTMLElement) => el.click());

    // URL hash should update.
    await page.waitForFunction(
      (s: string) => window.location.hash === `#${s}`,
      slug,
      { timeout: 5_000 },
    );
    expect(page.url()).toContain(`#${slug}`);

    // The matching heading must exist and be the scroll target.
    const heading = page.locator(`#${slug}`);
    await expect(heading).toBeVisible();
  });

  test("loading /help?page=…#slug scrolls to the heading on cold load", async ({ page }) => {
    // First visit the page once to discover a real slug, then revisit cold.
    await page.goto("/help?page=user-guide");
    const firstAnchor = page
      .getByTestId("help-content")
      .locator(".doc-heading-anchor")
      .first();
    await expect(firstAnchor).toBeVisible({ timeout: 10_000 }).catch(() => {});
    const href = await firstAnchor.getAttribute("href");
    expect(href).toMatch(/^#[a-z0-9-]+$/);
    const slug = href!.slice(1);

    // Now load the page cold with the hash present.
    await page.goto(`/help?page=user-guide#${slug}`);
    const heading = page.locator(`#${slug}`);
    // Wait until the async markdown fetch resolves + heading is in DOM.
    await expect(heading).toBeVisible({ timeout: 10_000 });
  });
});
