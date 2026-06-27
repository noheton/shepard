/**
 * UIVERIFY-collections — Playwright viewport verification for the
 * collections list and collection detail pages.
 *
 * Validates at two viewports per `feedback_validate_user_viewport.md`:
 *   - 4K  (3840 × 2160) — operator's native resolution
 *   - FHD (1920 × 1080) — most common deployed resolution
 *
 * Screenshot artifacts land in:
 *   aidocs/agent-findings/screenshots/uiverify-collections/
 *
 * The collection-detail tests gate on `COLLECTION_APPID` so they can be
 * skipped in CI without a seeded instance. Set the env var to any valid
 * collection appId on the target instance to enable them.
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "./helpers/auth";
import path from "path";
import fs from "fs";

const DEMO_USER = process.env.DEMO_USER || "flodemo";
const DEMO_PASS = process.env.DEMO_PASSWORD || "flo-demo";
const COLLECTION_APPID = process.env.COLLECTION_APPID || "";

const OUT_DIR = path.resolve(
  __dirname,
  "../../aidocs/agent-findings/screenshots/uiverify-collections",
);

function ensureOutDir() {
  if (!fs.existsSync(OUT_DIR)) fs.mkdirSync(OUT_DIR, { recursive: true });
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

async function tolerantLogin(page: import("@playwright/test").Page) {
  await loginAs(page, DEMO_USER, DEMO_PASS).catch(() => {
    // Non-fatal: even an unauthenticated capture is useful for layout checks.
  });
}

// ---------------------------------------------------------------------------
// 4K viewport (3840 × 2160)
// ---------------------------------------------------------------------------

test.describe("UIVERIFY-collections — 4K (3840×2160)", () => {
  test.use({ viewport: { width: 3840, height: 2160 } });

  test.beforeEach(async ({ page }) => {
    await tolerantLogin(page);
  });

  test("collections list renders without crash at 4K", async ({ page }) => {
    ensureOutDir();
    await page.goto("/collections");
    await page.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => {});
    await page.waitForTimeout(1_000);

    // No unhandled backend error must appear.
    const body = page.locator("body");
    await expect(body).not.toContainText("ServiceUnavailableException");
    await expect(body).not.toContainText("NullPointerException");
    await expect(body).not.toContainText("500 Internal Server Error");

    await page.screenshot({
      path: path.join(OUT_DIR, "4k-collections-list.png"),
      fullPage: false,
    });
  });

  test("collections list heading visible at 4K", async ({ page }) => {
    await page.goto("/collections");
    await page.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => {});

    // The top-level heading or nav element for Collections must be visible.
    const heading = page
      .getByRole("heading", { name: /collections/i })
      .or(page.getByText("Collections").first());
    await expect(heading).toBeVisible({ timeout: 8_000 });
  });

  test("collections list has no horizontal overflow at 4K", async ({ page }) => {
    await page.goto("/collections");
    await page.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => {});
    await page.waitForTimeout(800);

    const scrollWidth: number = await page.evaluate(
      () => document.documentElement.scrollWidth,
    );
    expect(scrollWidth).toBeLessThanOrEqual(3840 + 20); // 20px tolerance for scrollbar
  });

  test("collection detail renders without crash at 4K", async ({ page }) => {
    test.skip(!COLLECTION_APPID, "COLLECTION_APPID not set — skipping detail page test");
    ensureOutDir();

    await page.goto(`/collections/${COLLECTION_APPID}`);
    await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
    await page.waitForTimeout(1_200);

    const body = page.locator("body");
    await expect(body).not.toContainText("ServiceUnavailableException");
    await expect(body).not.toContainText("500 Internal Server Error");

    await page.screenshot({
      path: path.join(OUT_DIR, "4k-collection-detail.png"),
      fullPage: false,
    });
  });

  test("collection detail has no horizontal overflow at 4K", async ({ page }) => {
    test.skip(!COLLECTION_APPID, "COLLECTION_APPID not set");

    await page.goto(`/collections/${COLLECTION_APPID}`);
    await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
    await page.waitForTimeout(800);

    const scrollWidth: number = await page.evaluate(
      () => document.documentElement.scrollWidth,
    );
    expect(scrollWidth).toBeLessThanOrEqual(3840 + 20);
  });
});

// ---------------------------------------------------------------------------
// FHD viewport (1920 × 1080)
// ---------------------------------------------------------------------------

test.describe("UIVERIFY-collections — FHD (1920×1080)", () => {
  test.use({ viewport: { width: 1920, height: 1080 } });

  test.beforeEach(async ({ page }) => {
    await tolerantLogin(page);
  });

  test("collections list renders without crash at 1920", async ({ page }) => {
    ensureOutDir();
    await page.goto("/collections");
    await page.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => {});
    await page.waitForTimeout(1_000);

    const body = page.locator("body");
    await expect(body).not.toContainText("ServiceUnavailableException");
    await expect(body).not.toContainText("NullPointerException");
    await expect(body).not.toContainText("500 Internal Server Error");

    await page.screenshot({
      path: path.join(OUT_DIR, "fhd-collections-list.png"),
      fullPage: false,
    });
  });

  test("collections table columns visible at 1920", async ({ page }) => {
    await page.goto("/collections");
    await page.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => {});

    await expect(page.getByText("Name")).toBeVisible({ timeout: 8_000 });
    await expect(page.getByText("Created by")).toBeVisible({ timeout: 8_000 });
  });

  test("create-collection button visible at 1920", async ({ page }) => {
    await page.goto("/collections");
    await page.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => {});

    await expect(
      page.getByRole("button", { name: /create new collection/i }),
    ).toBeVisible({ timeout: 8_000 });
  });

  test("collections list has no horizontal overflow at 1920", async ({ page }) => {
    await page.goto("/collections");
    await page.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => {});
    await page.waitForTimeout(800);

    const scrollWidth: number = await page.evaluate(
      () => document.documentElement.scrollWidth,
    );
    expect(scrollWidth).toBeLessThanOrEqual(1920 + 20);
  });

  test("collection detail renders without crash at 1920", async ({ page }) => {
    test.skip(!COLLECTION_APPID, "COLLECTION_APPID not set — skipping detail page test");
    ensureOutDir();

    await page.goto(`/collections/${COLLECTION_APPID}`);
    await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
    await page.waitForTimeout(1_200);

    const body = page.locator("body");
    await expect(body).not.toContainText("ServiceUnavailableException");
    await expect(body).not.toContainText("500 Internal Server Error");

    await page.screenshot({
      path: path.join(OUT_DIR, "fhd-collection-detail.png"),
      fullPage: false,
    });
  });

  test("collection detail has no horizontal overflow at 1920", async ({ page }) => {
    test.skip(!COLLECTION_APPID, "COLLECTION_APPID not set");

    await page.goto(`/collections/${COLLECTION_APPID}`);
    await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
    await page.waitForTimeout(800);

    const scrollWidth: number = await page.evaluate(
      () => document.documentElement.scrollWidth,
    );
    expect(scrollWidth).toBeLessThanOrEqual(1920 + 20);
  });
});
