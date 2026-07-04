/**
 * UIVERIFY-references-all — Playwright viewport verification for the
 * DataObject data-references panel and per-kind reference detail pages.
 *
 * Covers the unified references table (list), kind filter chips, the create
 * dialog, and per-kind detail pages (file / file-bundle / timeseries /
 * structured-data) at two target viewports:
 *   - 4K  (3840 × 2160) — operator's native resolution
 *   - FHD (1920 × 1080) — most common deployed resolution
 *
 * Screenshot artifacts land in:
 *   aidocs/agent-findings/screenshots/uiverify-references-all/
 *
 * Environment variables (all optional — missing vars skip the relevant tests):
 *   COLLECTION_APPID    — appId of a collection containing the DataObject
 *   DO_APPID            — appId of the DataObject under test
 *   TS_REF_ID           — numeric id of a TimeseriesReference on that DO
 *   FILE_REF_ID         — numeric id of a FileReference (legacy bundle) on that DO
 *   SD_REF_ID           — numeric id of a StructuredDataReference on that DO
 *   COLLECTION_ID       — numeric id of the collection (for legacy detail URLs)
 *   DO_NUM_ID           — numeric id of the DataObject (for legacy detail URLs)
 *   DEMO_USER / DEMO_PASSWORD — credentials for login; defaults to flodemo/flo-demo
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "./helpers/auth";
import path from "path";
import fs from "fs";

const DEMO_USER = process.env.DEMO_USER || "flodemo";
const DEMO_PASS = process.env.DEMO_PASSWORD || "flo-demo";

const COLLECTION_APPID = process.env.COLLECTION_APPID || "";
const DO_APPID = process.env.DO_APPID || "";
const COLLECTION_ID = process.env.COLLECTION_ID || "";
const DO_NUM_ID = process.env.DO_NUM_ID || "";
const TS_REF_ID = process.env.TS_REF_ID || "";
const FILE_REF_ID = process.env.FILE_REF_ID || "";
const SD_REF_ID = process.env.SD_REF_ID || "";

const OUT_DIR = path.resolve(
  __dirname,
  "../../aidocs/agent-findings/screenshots/uiverify-references-all",
);

function ensureOutDir() {
  if (!fs.existsSync(OUT_DIR)) fs.mkdirSync(OUT_DIR, { recursive: true });
}

async function tolerantLogin(page: import("@playwright/test").Page) {
  await loginAs(page, DEMO_USER, DEMO_PASS).catch(() => {
    // Non-fatal: even unauthenticated captures are useful for layout checks.
  });
}

async function assertNoCrash(page: import("@playwright/test").Page) {
  const body = page.locator("body");
  await expect(body).not.toContainText("ServiceUnavailableException");
  await expect(body).not.toContainText("NullPointerException");
  await expect(body).not.toContainText("500 Internal Server Error");
}

const hasDoEnv = () => Boolean(COLLECTION_APPID && DO_APPID);
const hasLegacyEnv = () => Boolean(COLLECTION_ID && DO_NUM_ID);

/** Navigate to the DataObject detail page (v2 appId route). */
function doDetailUrl() {
  return `/collections/${COLLECTION_APPID}/dataobjects/${DO_APPID}`;
}

// Reference kinds rendered as filter chips in the unified table.
const REFERENCE_KIND_CHIPS = [
  "TimeSeries",
  "Structured Data",
  "File Bundle",
  "File",
  "Notebook",
  "Git",
  "Video",
  "Spatial",
] as const;

// ---------------------------------------------------------------------------
// 4K viewport (3840 × 2160)
// ---------------------------------------------------------------------------

test.describe("UIVERIFY-references-all — 4K (3840×2160)", () => {
  test.use({ viewport: { width: 3840, height: 2160 } });

  test.beforeEach(async ({ page }) => {
    await tolerantLogin(page);
    ensureOutDir();
  });

  // --- List view ------------------------------------------------------------

  test("data-references panel renders without crash at 4K", async ({ page }) => {
    test.skip(!hasDoEnv(), "COLLECTION_APPID or DO_APPID not set");

    await page.goto(doDetailUrl());
    await page.waitForLoadState("networkidle", { timeout: 20_000 }).catch(() => {});
    await page.waitForTimeout(1_500);

    await assertNoCrash(page);

    // The unified table section or its empty-state must be present.
    const refSection = page
      .getByText("Data References")
      .or(page.getByText("References"))
      .or(page.getByText("No data yet"))
      .first();
    await expect(refSection).toBeVisible({ timeout: 10_000 });

    await page.screenshot({
      path: path.join(OUT_DIR, "4k-references-panel.png"),
      fullPage: false,
    });
  });

  test("references panel has no horizontal overflow at 4K", async ({ page }) => {
    test.skip(!hasDoEnv(), "COLLECTION_APPID or DO_APPID not set");

    await page.goto(doDetailUrl());
    await page.waitForLoadState("networkidle", { timeout: 20_000 }).catch(() => {});
    await page.waitForTimeout(800);

    const scrollWidth: number = await page.evaluate(
      () => document.documentElement.scrollWidth,
    );
    expect(scrollWidth).toBeLessThanOrEqual(3840 + 20);
  });

  test("all reference kind filter chips render at 4K", async ({ page }) => {
    test.skip(!hasDoEnv(), "COLLECTION_APPID or DO_APPID not set");

    await page.goto(doDetailUrl());
    await page.waitForLoadState("networkidle", { timeout: 20_000 }).catch(() => {});
    await page.waitForTimeout(1_200);

    // Verify the kind chip row is present. At least the "All" chip must show.
    const allChip = page
      .getByRole("button", { name: /^All \(/ })
      .or(page.locator(".v-chip").filter({ hasText: /^All/ }))
      .first();
    await expect(allChip).toBeVisible({ timeout: 8_000 });

    // Each kind chip should appear in the chip filter row (count may be 0).
    for (const kind of REFERENCE_KIND_CHIPS) {
      const chip = page
        .locator(".v-chip")
        .filter({ hasText: new RegExp(`^${kind}`) })
        .first();
      await expect(chip).toBeVisible({ timeout: 5_000 });
    }

    await page.screenshot({
      path: path.join(OUT_DIR, "4k-references-kind-chips.png"),
      fullPage: false,
    });
  });

  // --- Create dialog --------------------------------------------------------

  test("add-data-reference dialog opens without crash at 4K", async ({ page }) => {
    test.skip(!hasDoEnv(), "COLLECTION_APPID or DO_APPID not set");

    await page.goto(doDetailUrl());
    await page.waitForLoadState("networkidle", { timeout: 20_000 }).catch(() => {});
    await page.waitForTimeout(1_200);

    // The "Add data reference" button is only visible when the user is allowed
    // to edit the collection. It may be hidden for read-only demo accounts.
    const addBtn = page.getByRole("button", { name: /add data reference/i });
    const btnVisible = await addBtn.isVisible().catch(() => false);
    if (!btnVisible) {
      test.skip(
        true,
        "Add data reference button not visible — user may lack write permission",
      );
    }

    await addBtn.click();
    // Give the dialog a moment to mount.
    await page.waitForTimeout(800);

    await assertNoCrash(page);

    // The dialog or its overlay should be present.
    const dialog = page
      .locator(".v-dialog--active, [role='dialog']")
      .or(page.getByText("Add Data Reference"))
      .or(page.getByText("Create data reference"))
      .first();
    await expect(dialog).toBeVisible({ timeout: 8_000 });

    await page.screenshot({
      path: path.join(OUT_DIR, "4k-create-reference-dialog.png"),
      fullPage: false,
    });

    // Dismiss the dialog cleanly.
    await page.keyboard.press("Escape");
    await page.waitForTimeout(400);
  });

  // --- TimeSeries reference detail ------------------------------------------

  test("timeseries reference detail renders without crash at 4K", async ({ page }) => {
    test.skip(
      !hasLegacyEnv() || !TS_REF_ID,
      "COLLECTION_ID, DO_NUM_ID, or TS_REF_ID not set",
    );

    await page.goto(
      `/collections/${COLLECTION_ID}/dataobjects/${DO_NUM_ID}/timeseriesereferences/${TS_REF_ID}`,
    );
    await page.waitForLoadState("networkidle", { timeout: 20_000 }).catch(() => {});
    await page.waitForTimeout(1_500);

    await assertNoCrash(page);

    const scrollWidth: number = await page.evaluate(
      () => document.documentElement.scrollWidth,
    );
    expect(scrollWidth).toBeLessThanOrEqual(3840 + 20);

    await page.screenshot({
      path: path.join(OUT_DIR, "4k-ts-reference-detail.png"),
      fullPage: false,
    });
  });

  // --- File / bundle reference detail ---------------------------------------

  test("file reference detail renders without crash at 4K", async ({ page }) => {
    test.skip(
      !hasLegacyEnv() || !FILE_REF_ID,
      "COLLECTION_ID, DO_NUM_ID, or FILE_REF_ID not set",
    );

    await page.goto(
      `/collections/${COLLECTION_ID}/dataobjects/${DO_NUM_ID}/filereferences/${FILE_REF_ID}`,
    );
    await page.waitForLoadState("networkidle", { timeout: 20_000 }).catch(() => {});
    await page.waitForTimeout(1_500);

    await assertNoCrash(page);

    const scrollWidth: number = await page.evaluate(
      () => document.documentElement.scrollWidth,
    );
    expect(scrollWidth).toBeLessThanOrEqual(3840 + 20);

    await page.screenshot({
      path: path.join(OUT_DIR, "4k-file-reference-detail.png"),
      fullPage: false,
    });
  });

  // --- Structured-data reference detail -------------------------------------

  test("structured-data reference detail renders without crash at 4K", async ({
    page,
  }) => {
    test.skip(
      !hasLegacyEnv() || !SD_REF_ID,
      "COLLECTION_ID, DO_NUM_ID, or SD_REF_ID not set",
    );

    await page.goto(
      `/collections/${COLLECTION_ID}/dataobjects/${DO_NUM_ID}/structureddatareferences/${SD_REF_ID}`,
    );
    await page.waitForLoadState("networkidle", { timeout: 20_000 }).catch(() => {});
    await page.waitForTimeout(1_500);

    await assertNoCrash(page);

    const scrollWidth: number = await page.evaluate(
      () => document.documentElement.scrollWidth,
    );
    expect(scrollWidth).toBeLessThanOrEqual(3840 + 20);

    await page.screenshot({
      path: path.join(OUT_DIR, "4k-structured-data-reference-detail.png"),
      fullPage: false,
    });
  });
});

// ---------------------------------------------------------------------------
// FHD viewport (1920 × 1080)
// ---------------------------------------------------------------------------

test.describe("UIVERIFY-references-all — FHD (1920×1080)", () => {
  test.use({ viewport: { width: 1920, height: 1080 } });

  test.beforeEach(async ({ page }) => {
    await tolerantLogin(page);
    ensureOutDir();
  });

  // --- List view ------------------------------------------------------------

  test("data-references panel renders without crash at 1920", async ({ page }) => {
    test.skip(!hasDoEnv(), "COLLECTION_APPID or DO_APPID not set");

    await page.goto(doDetailUrl());
    await page.waitForLoadState("networkidle", { timeout: 20_000 }).catch(() => {});
    await page.waitForTimeout(1_500);

    await assertNoCrash(page);

    const refSection = page
      .getByText("Data References")
      .or(page.getByText("References"))
      .or(page.getByText("No data yet"))
      .first();
    await expect(refSection).toBeVisible({ timeout: 10_000 });

    await page.screenshot({
      path: path.join(OUT_DIR, "fhd-references-panel.png"),
      fullPage: false,
    });
  });

  test("references panel has no horizontal overflow at 1920", async ({ page }) => {
    test.skip(!hasDoEnv(), "COLLECTION_APPID or DO_APPID not set");

    await page.goto(doDetailUrl());
    await page.waitForLoadState("networkidle", { timeout: 20_000 }).catch(() => {});
    await page.waitForTimeout(800);

    const scrollWidth: number = await page.evaluate(
      () => document.documentElement.scrollWidth,
    );
    expect(scrollWidth).toBeLessThanOrEqual(1920 + 20);
  });

  test("all reference kind filter chips render at 1920", async ({ page }) => {
    test.skip(!hasDoEnv(), "COLLECTION_APPID or DO_APPID not set");

    await page.goto(doDetailUrl());
    await page.waitForLoadState("networkidle", { timeout: 20_000 }).catch(() => {});
    await page.waitForTimeout(1_200);

    const allChip = page
      .getByRole("button", { name: /^All \(/ })
      .or(page.locator(".v-chip").filter({ hasText: /^All/ }))
      .first();
    await expect(allChip).toBeVisible({ timeout: 8_000 });

    for (const kind of REFERENCE_KIND_CHIPS) {
      const chip = page
        .locator(".v-chip")
        .filter({ hasText: new RegExp(`^${kind}`) })
        .first();
      await expect(chip).toBeVisible({ timeout: 5_000 });
    }

    await page.screenshot({
      path: path.join(OUT_DIR, "fhd-references-kind-chips.png"),
      fullPage: false,
    });
  });

  // --- Create dialog --------------------------------------------------------

  test("add-data-reference dialog opens without crash at 1920", async ({ page }) => {
    test.skip(!hasDoEnv(), "COLLECTION_APPID or DO_APPID not set");

    await page.goto(doDetailUrl());
    await page.waitForLoadState("networkidle", { timeout: 20_000 }).catch(() => {});
    await page.waitForTimeout(1_200);

    const addBtn = page.getByRole("button", { name: /add data reference/i });
    const btnVisible = await addBtn.isVisible().catch(() => false);
    if (!btnVisible) {
      test.skip(
        true,
        "Add data reference button not visible — user may lack write permission",
      );
    }

    await addBtn.click();
    await page.waitForTimeout(800);

    await assertNoCrash(page);

    const dialog = page
      .locator(".v-dialog--active, [role='dialog']")
      .or(page.getByText("Add Data Reference"))
      .or(page.getByText("Create data reference"))
      .first();
    await expect(dialog).toBeVisible({ timeout: 8_000 });

    await page.screenshot({
      path: path.join(OUT_DIR, "fhd-create-reference-dialog.png"),
      fullPage: false,
    });

    await page.keyboard.press("Escape");
    await page.waitForTimeout(400);
  });

  // --- Edit dialog (FileBundleReference rename) ----------------------------

  test("file-bundle rename dialog chip renders without crash at 1920", async ({
    page,
  }) => {
    test.skip(!hasDoEnv(), "COLLECTION_APPID or DO_APPID not set");

    await page.goto(doDetailUrl());
    await page.waitForLoadState("networkidle", { timeout: 20_000 }).catch(() => {});
    await page.waitForTimeout(1_200);

    // Filter to "File Bundle" kind chip to find rows of that kind.
    const bundleChip = page
      .locator(".v-chip")
      .filter({ hasText: /^File Bundle/ })
      .first();
    const chipVisible = await bundleChip.isVisible().catch(() => false);
    if (!chipVisible) {
      test.skip(true, "File Bundle chip not found — no bundles on this DataObject");
    }

    await bundleChip.click();
    await page.waitForTimeout(600);

    // Look for the rename (pencil) action button.
    const editBtn = page
      .locator("[aria-label='Rename file bundle reference']")
      .or(page.locator("button[data-testid*='edit']"))
      .first();
    const editVisible = await editBtn.isVisible().catch(() => false);
    if (!editVisible) {
      test.skip(true, "File Bundle rename button not visible");
    }

    await editBtn.click();
    await page.waitForTimeout(600);

    await assertNoCrash(page);

    await page.screenshot({
      path: path.join(OUT_DIR, "fhd-file-bundle-rename-dialog.png"),
      fullPage: false,
    });

    await page.keyboard.press("Escape");
    await page.waitForTimeout(400);
  });

  // --- TimeSeries reference detail ------------------------------------------

  test("timeseries reference detail renders without crash at 1920", async ({ page }) => {
    test.skip(
      !hasLegacyEnv() || !TS_REF_ID,
      "COLLECTION_ID, DO_NUM_ID, or TS_REF_ID not set",
    );

    await page.goto(
      `/collections/${COLLECTION_ID}/dataobjects/${DO_NUM_ID}/timeseriesereferences/${TS_REF_ID}`,
    );
    await page.waitForLoadState("networkidle", { timeout: 20_000 }).catch(() => {});
    await page.waitForTimeout(1_500);

    await assertNoCrash(page);

    const scrollWidth: number = await page.evaluate(
      () => document.documentElement.scrollWidth,
    );
    expect(scrollWidth).toBeLessThanOrEqual(1920 + 20);

    await page.screenshot({
      path: path.join(OUT_DIR, "fhd-ts-reference-detail.png"),
      fullPage: false,
    });
  });

  // --- File reference detail ------------------------------------------------

  test("file reference detail renders without crash at 1920", async ({ page }) => {
    test.skip(
      !hasLegacyEnv() || !FILE_REF_ID,
      "COLLECTION_ID, DO_NUM_ID, or FILE_REF_ID not set",
    );

    await page.goto(
      `/collections/${COLLECTION_ID}/dataobjects/${DO_NUM_ID}/filereferences/${FILE_REF_ID}`,
    );
    await page.waitForLoadState("networkidle", { timeout: 20_000 }).catch(() => {});
    await page.waitForTimeout(1_500);

    await assertNoCrash(page);

    const scrollWidth: number = await page.evaluate(
      () => document.documentElement.scrollWidth,
    );
    expect(scrollWidth).toBeLessThanOrEqual(1920 + 20);

    await page.screenshot({
      path: path.join(OUT_DIR, "fhd-file-reference-detail.png"),
      fullPage: false,
    });
  });

  // --- Structured-data reference detail -------------------------------------

  test("structured-data reference detail renders without crash at 1920", async ({
    page,
  }) => {
    test.skip(
      !hasLegacyEnv() || !SD_REF_ID,
      "COLLECTION_ID, DO_NUM_ID, or SD_REF_ID not set",
    );

    await page.goto(
      `/collections/${COLLECTION_ID}/dataobjects/${DO_NUM_ID}/structureddatareferences/${SD_REF_ID}`,
    );
    await page.waitForLoadState("networkidle", { timeout: 20_000 }).catch(() => {});
    await page.waitForTimeout(1_500);

    await assertNoCrash(page);

    const scrollWidth: number = await page.evaluate(
      () => document.documentElement.scrollWidth,
    );
    expect(scrollWidth).toBeLessThanOrEqual(1920 + 20);

    await page.screenshot({
      path: path.join(OUT_DIR, "fhd-structured-data-reference-detail.png"),
      fullPage: false,
    });
  });
});
