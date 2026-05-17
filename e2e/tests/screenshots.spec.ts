/**
 * Screenshot capture spec — captures before/after images for the UI changelog.
 *
 * Run with:   SCREENSHOTS=1 npx playwright test tests/screenshots.spec.ts
 * Outputs to: e2e/screenshots/<slug>.png
 *
 * No auth required for the pages captured here. Auth-gated screenshots
 * are deferred until the CI Keycloak fixture is wired (aidocs/ops/85 §5).
 */
import { test, expect } from "@playwright/test";
import * as fs from "fs";
import * as path from "path";

// Only run when explicitly requested so normal CI isn't slowed.
const RUN = process.env.SCREENSHOTS === "1";

const SCREENSHOT_DIR = path.join(__dirname, "..", "screenshots");

function ensureDir() {
  if (!fs.existsSync(SCREENSHOT_DIR)) {
    fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
  }
}

async function capture(page: import("@playwright/test").Page, slug: string) {
  ensureDir();
  const file = path.join(SCREENSHOT_DIR, `${slug}.png`);
  await page.screenshot({ path: file, fullPage: false });
  console.log(`Screenshot saved: ${slug}.png`);
}

test.describe("UI screenshots (public pages)", () => {
  test.skip(!RUN, "Set SCREENSHOTS=1 to run screenshot captures");

  test("home page", async ({ page }) => {
    await page.goto("/");
    await page.waitForLoadState("networkidle");
    await capture(page, "home");
  });

  test("about page", async ({ page }) => {
    await page.goto("/about");
    await page.waitForLoadState("networkidle");
    await capture(page, "about");
  });

  test("sign-in page", async ({ page }) => {
    await page.goto("/auth/signIn");
    await page.waitForLoadState("networkidle");
    await capture(page, "auth-sign-in");
  });
});
