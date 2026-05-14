/**
 * Navigation + shell tests — verify all top-level routes render.
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "./helpers/auth";

const TOP_NAV_ROUTES = [
  { path: "/collections", label: "Collections" },
  { path: "/containers", label: "Containers" },
  { path: "/configuration", label: "Configuration" },
  { path: "/about", label: "About" },
];

test.describe("Navigation", () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, "alice", "alice-demo");
  });

  test("home page shows key nav items", async ({ page }) => {
    await page.goto("/");
    for (const { label } of TOP_NAV_ROUTES) {
      await expect(page.getByText(label).first()).toBeVisible();
    }
  });

  for (const { path, label } of TOP_NAV_ROUTES) {
    test(`${label} route loads without error`, async ({ page }) => {
      await page.goto(path);
      await page.waitForLoadState("networkidle");
      await expect(page.locator("body")).not.toContainText("500");
      await expect(page.locator("body")).not.toContainText(
        "ServiceUnavailableException",
      );
      // The heading / breadcrumb for this section should appear.
      await expect(page.getByText(label).first()).toBeVisible();
    });
  }

  test("home page has Go to Collections and Go to Containers links", async ({
    page,
  }) => {
    await page.goto("/");
    await expect(page.getByRole("link", { name: /go to collections/i })).toBeVisible();
    await expect(page.getByRole("link", { name: /go to containers/i })).toBeVisible();
  });

  test("clicking Go to Collections navigates to /collections", async ({
    page,
  }) => {
    await page.goto("/");
    await page.getByRole("link", { name: /go to collections/i }).click();
    await page.waitForURL(/\/collections/);
    expect(page.url()).toContain("/collections");
  });
});
