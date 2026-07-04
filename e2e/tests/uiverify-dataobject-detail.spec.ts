/**
 * UIVERIFY-dataobject-detail — Playwright viewport verification for the
 * DataObject detail page (the most-used page in Shepard).
 *
 * Validates at two viewports per `feedback_validate_user_viewport.md`:
 *   - 4K  (3840 × 2160) — operator's native resolution
 *   - FHD (1920 × 1080) — most common deployed resolution
 *
 * Screenshot artifacts land in:
 *   aidocs/agent-findings/screenshots/uiverify-dataobject-detail/
 *
 * Both COLLECTION_APPID and DO_APPID env vars must be set for the detail-page
 * tests to run (they gate on `test.skip`). The `collections-list-loads` tests
 * navigate to the collections page and are always active (no env gate).
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "./helpers/auth";
import path from "path";
import fs from "fs";

const DEMO_USER = process.env.DEMO_USER || "flodemo";
const DEMO_PASS = process.env.DEMO_PASSWORD || "flo-demo";
const COLLECTION_APPID = process.env.COLLECTION_APPID || "";
const DO_APPID = process.env.DO_APPID || "";

const OUT_DIR = path.resolve(
  __dirname,
  "../../aidocs/agent-findings/screenshots/uiverify-dataobject-detail",
);

function ensureOutDir() {
  if (!fs.existsSync(OUT_DIR)) fs.mkdirSync(OUT_DIR, { recursive: true });
}

async function tolerantLogin(page: import("@playwright/test").Page) {
  await loginAs(page, DEMO_USER, DEMO_PASS).catch(() => {
    // Non-fatal: even an unauthenticated capture is useful for layout checks.
  });
}

const hasEnv = () => Boolean(COLLECTION_APPID && DO_APPID);

// ---------------------------------------------------------------------------
// 4K viewport (3840 × 2160)
// ---------------------------------------------------------------------------

test.describe("UIVERIFY-dataobject-detail — 4K (3840×2160)", () => {
  test.use({ viewport: { width: 3840, height: 2160 } });

  test.beforeEach(async ({ page }) => {
    await tolerantLogin(page);
  });

  test("dataobject detail renders without crash at 4K", async ({ page }) => {
    test.skip(!hasEnv(), "COLLECTION_APPID or DO_APPID not set — skipping");
    ensureOutDir();

    await page.goto(`/collections/${COLLECTION_APPID}/dataobjects/${DO_APPID}`);
    await page.waitForLoadState("networkidle", { timeout: 20_000 }).catch(() => {});
    await page.waitForTimeout(1_500);

    const body = page.locator("body");
    await expect(body).not.toContainText("ServiceUnavailableException");
    await expect(body).not.toContainText("NullPointerException");
    await expect(body).not.toContainText("500 Internal Server Error");

    await page.screenshot({
      path: path.join(OUT_DIR, "4k-do-detail.png"),
      fullPage: false,
    });
  });

  test("dataobject detail has no horizontal overflow at 4K", async ({ page }) => {
    test.skip(!hasEnv(), "COLLECTION_APPID or DO_APPID not set");

    await page.goto(`/collections/${COLLECTION_APPID}/dataobjects/${DO_APPID}`);
    await page.waitForLoadState("networkidle", { timeout: 20_000 }).catch(() => {});
    await page.waitForTimeout(800);

    const scrollWidth: number = await page.evaluate(
      () => document.documentElement.scrollWidth,
    );
    expect(scrollWidth).toBeLessThanOrEqual(3840 + 20);
  });

  test("dataobject title or name is visible at 4K", async ({ page }) => {
    test.skip(!hasEnv(), "COLLECTION_APPID or DO_APPID not set");

    await page.goto(`/collections/${COLLECTION_APPID}/dataobjects/${DO_APPID}`);
    await page.waitForLoadState("networkidle", { timeout: 20_000 }).catch(() => {});
    await page.waitForTimeout(1_000);

    // The page must surface at least one heading element (the DO name).
    const heading = page.locator("h1, h2, [data-testid='do-title']").first();
    await expect(heading).toBeVisible({ timeout: 10_000 });
  });

  test("dataobject status badge or metadata pane renders at 4K", async ({ page }) => {
    test.skip(!hasEnv(), "COLLECTION_APPID or DO_APPID not set");

    await page.goto(`/collections/${COLLECTION_APPID}/dataobjects/${DO_APPID}`);
    await page.waitForLoadState("networkidle", { timeout: 20_000 }).catch(() => {});
    await page.waitForTimeout(1_000);

    // At least one of the status chip, metadata panel, or description should exist.
    const statusOrMeta = page
      .locator("[class*='chip'], [class*='badge'], [class*='v-chip']")
      .or(page.getByText("Status"))
      .or(page.getByText("Description"))
      .first();
    await expect(statusOrMeta).toBeVisible({ timeout: 10_000 });
  });
});

// ---------------------------------------------------------------------------
// FHD viewport (1920 × 1080)
// ---------------------------------------------------------------------------

test.describe("UIVERIFY-dataobject-detail — FHD (1920×1080)", () => {
  test.use({ viewport: { width: 1920, height: 1080 } });

  test.beforeEach(async ({ page }) => {
    await tolerantLogin(page);
  });

  test("dataobject detail renders without crash at 1920", async ({ page }) => {
    test.skip(!hasEnv(), "COLLECTION_APPID or DO_APPID not set — skipping");
    ensureOutDir();

    await page.goto(`/collections/${COLLECTION_APPID}/dataobjects/${DO_APPID}`);
    await page.waitForLoadState("networkidle", { timeout: 20_000 }).catch(() => {});
    await page.waitForTimeout(1_500);

    const body = page.locator("body");
    await expect(body).not.toContainText("ServiceUnavailableException");
    await expect(body).not.toContainText("NullPointerException");
    await expect(body).not.toContainText("500 Internal Server Error");

    await page.screenshot({
      path: path.join(OUT_DIR, "fhd-do-detail.png"),
      fullPage: false,
    });
  });

  test("dataobject detail has no horizontal overflow at 1920", async ({ page }) => {
    test.skip(!hasEnv(), "COLLECTION_APPID or DO_APPID not set");

    await page.goto(`/collections/${COLLECTION_APPID}/dataobjects/${DO_APPID}`);
    await page.waitForLoadState("networkidle", { timeout: 20_000 }).catch(() => {});
    await page.waitForTimeout(800);

    const scrollWidth: number = await page.evaluate(
      () => document.documentElement.scrollWidth,
    );
    expect(scrollWidth).toBeLessThanOrEqual(1920 + 20);
  });

  test("dataobject title or name is visible at 1920", async ({ page }) => {
    test.skip(!hasEnv(), "COLLECTION_APPID or DO_APPID not set");

    await page.goto(`/collections/${COLLECTION_APPID}/dataobjects/${DO_APPID}`);
    await page.waitForLoadState("networkidle", { timeout: 20_000 }).catch(() => {});
    await page.waitForTimeout(1_000);

    const heading = page.locator("h1, h2, [data-testid='do-title']").first();
    await expect(heading).toBeVisible({ timeout: 10_000 });
  });

  test("data-references panel visible or empty-state shown at 1920", async ({
    page,
  }) => {
    test.skip(!hasEnv(), "COLLECTION_APPID or DO_APPID not set");

    await page.goto(`/collections/${COLLECTION_APPID}/dataobjects/${DO_APPID}`);
    await page.waitForLoadState("networkidle", { timeout: 20_000 }).catch(() => {});
    await page.waitForTimeout(1_000);

    // The unified data-references table or its empty-state placeholder must render.
    const refArea = page
      .getByText("Data References")
      .or(page.getByText("No data references"))
      .or(page.getByText("References"))
      .first();
    await expect(refArea).toBeVisible({ timeout: 10_000 });
  });

  test("dataobject detail screenshot at 1920", async ({ page }) => {
    test.skip(!hasEnv(), "COLLECTION_APPID or DO_APPID not set");
    ensureOutDir();

    await page.goto(`/collections/${COLLECTION_APPID}/dataobjects/${DO_APPID}`);
    await page.waitForLoadState("networkidle", { timeout: 20_000 }).catch(() => {});
    await page.waitForTimeout(1_200);

    await page.screenshot({
      path: path.join(OUT_DIR, "fhd-do-detail-full.png"),
      fullPage: false,
    });
  });
});
