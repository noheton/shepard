/**
 * Collections page tests — requires authenticated session.
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "./helpers/auth";

test.describe("Collections", () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, "alice", "alice-demo");
  });

  test("collections page loads after login", async ({ page }) => {
    await page.goto("/collections");
    await page.waitForLoadState("networkidle");
    await expect(page.locator("body")).not.toContainText("ServiceUnavailableException");
    await expect(page.locator("body")).not.toContainText("AuthenticationException");
    await expect(page.locator("body")).not.toContainText("500");
  });

  test("collections table is visible", async ({ page }) => {
    await page.goto("/collections");
    await page.waitForLoadState("networkidle");
    // Header row with expected columns.
    await expect(page.getByText("Name")).toBeVisible();
    await expect(page.getByText("Created by")).toBeVisible();
  });

  test("create-new-collection button is present", async ({ page }) => {
    await page.goto("/collections");
    await expect(
      page.getByRole("button", { name: /create new collection/i }),
    ).toBeVisible();
  });

  test("can create a collection via the two-step wizard", async ({ page }) => {
    await page.goto("/collections");
    const dialog = page.locator(".v-overlay__content").filter({ hasText: "Create Collection" });

    // Open create dialog.
    await page.getByRole("button", { name: /create new collection/i }).click();
    await expect(dialog).toBeVisible({ timeout: 5_000 });

    const testName = `e2e-test-${Date.now()}`;

    // Step 1 — fill the name (first input in the overlay).
    await dialog.locator("input").first().fill(testName);
    await dialog.getByRole("button", { name: "Next", exact: true }).click();

    // Step 2 — confirm with "Create".
    await dialog.getByRole("button", { name: "Create", exact: true }).click();

    // The new row should appear in the table.
    await expect(page.getByText(testName)).toBeVisible({ timeout: 10_000 });
  });
});
