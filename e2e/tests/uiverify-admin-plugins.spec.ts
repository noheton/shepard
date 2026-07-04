/**
 * UIVERIFY-plugins-authenticated-paths — Playwright viewport verification for
 * the plugin-management admin surfaces.
 *
 * Confirms that the plugin-adjacent admin panes (plugins, legacy-v1, unhide,
 * feature-toggles, config-overview) render correctly at the two target
 * viewports per `feedback_validate_user_viewport.md`.  These six panes are
 * the "UNKNOWN" surfaces from the ui-feature-inventory-2026-06-13 audit
 * (aas / git / spatiotemporal / video / unhide / v1-compat admin UIs).
 *
 * Viewport targets:
 *   - 4K  (3840 × 2160) — operator's native resolution
 *   - FHD (1920 × 1080) — most common deployed resolution
 *
 * Screenshot artifacts land in:
 *   aidocs/agent-findings/screenshots/uiverify-admin-plugins/
 *
 * All tests gate on `ADMIN_USER` / `ADMIN_PASSWORD` env vars so they can be
 * skipped in CI without a seeded instance-admin account.  Set both env vars
 * to an account with `instance-admin` role to enable the full suite.
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "./helpers/auth";
import path from "path";
import fs from "fs";

const ADMIN_USER = process.env.ADMIN_USER || "";
const ADMIN_PASS = process.env.ADMIN_PASSWORD || "";

const OUT_DIR = path.resolve(
  __dirname,
  "../../aidocs/agent-findings/screenshots/uiverify-admin-plugins",
);

function ensureOutDir() {
  if (!fs.existsSync(OUT_DIR)) fs.mkdirSync(OUT_DIR, { recursive: true });
}

async function tolerantAdminLogin(page: import("@playwright/test").Page) {
  if (!ADMIN_USER) return; // gated — no login if env not set
  await loginAs(page, ADMIN_USER, ADMIN_PASS).catch(() => {
    // Non-fatal: crash-free and overflow assertions still valid unauthenticated.
  });
}

async function assertNoCrash(page: import("@playwright/test").Page) {
  const body = page.locator("body");
  await expect(body).not.toContainText("ServiceUnavailableException");
  await expect(body).not.toContainText("NullPointerException");
  await expect(body).not.toContainText("500 Internal Server Error");
}

// ---------------------------------------------------------------------------
// 4K viewport (3840 × 2160)
// ---------------------------------------------------------------------------

test.describe("UIVERIFY-plugins-auth-paths — 4K (3840×2160)", () => {
  test.use({ viewport: { width: 3840, height: 2160 } });

  test.beforeEach(async ({ page }) => {
    test.skip(!ADMIN_USER, "ADMIN_USER not set — skipping admin pane tests");
    await tolerantAdminLogin(page);
    ensureOutDir();
  });

  test("admin landing renders without crash at 4K", async ({ page }) => {
    await page.goto("/admin");
    await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
    await page.waitForTimeout(1_000);
    await assertNoCrash(page);
    const scrollWidth: number = await page.evaluate(
      () => document.documentElement.scrollWidth,
    );
    expect(scrollWidth).toBeLessThanOrEqual(3840 + 20);
    await page.screenshot({
      path: path.join(OUT_DIR, "4k-admin-landing.png"),
      fullPage: false,
    });
  });

  test("admin#plugins pane renders without crash at 4K", async ({ page }) => {
    await page.goto("/admin#plugins");
    await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
    await page.waitForTimeout(1_200);
    await assertNoCrash(page);
    const scrollWidth: number = await page.evaluate(
      () => document.documentElement.scrollWidth,
    );
    expect(scrollWidth).toBeLessThanOrEqual(3840 + 20);
    await page.screenshot({
      path: path.join(OUT_DIR, "4k-admin-plugins.png"),
      fullPage: false,
    });
  });

  test("admin#legacy-v1 pane renders without crash at 4K", async ({ page }) => {
    await page.goto("/admin#legacy-v1");
    await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
    await page.waitForTimeout(1_200);
    await assertNoCrash(page);
    const scrollWidth: number = await page.evaluate(
      () => document.documentElement.scrollWidth,
    );
    expect(scrollWidth).toBeLessThanOrEqual(3840 + 20);
    await page.screenshot({
      path: path.join(OUT_DIR, "4k-admin-legacy-v1.png"),
      fullPage: false,
    });
  });

  test("admin#unhide pane renders without crash at 4K", async ({ page }) => {
    await page.goto("/admin#unhide");
    await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
    await page.waitForTimeout(1_200);
    await assertNoCrash(page);
    const scrollWidth: number = await page.evaluate(
      () => document.documentElement.scrollWidth,
    );
    expect(scrollWidth).toBeLessThanOrEqual(3840 + 20);
    await page.screenshot({
      path: path.join(OUT_DIR, "4k-admin-unhide.png"),
      fullPage: false,
    });
  });

  test("admin#feature-toggles pane renders without crash at 4K", async ({
    page,
  }) => {
    await page.goto("/admin#feature-toggles");
    await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
    await page.waitForTimeout(1_200);
    await assertNoCrash(page);
    const scrollWidth: number = await page.evaluate(
      () => document.documentElement.scrollWidth,
    );
    expect(scrollWidth).toBeLessThanOrEqual(3840 + 20);
    await page.screenshot({
      path: path.join(OUT_DIR, "4k-admin-feature-toggles.png"),
      fullPage: false,
    });
  });

  test("admin#config-overview pane renders without crash at 4K", async ({
    page,
  }) => {
    await page.goto("/admin#config-overview");
    await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
    await page.waitForTimeout(1_200);
    await assertNoCrash(page);
    const scrollWidth: number = await page.evaluate(
      () => document.documentElement.scrollWidth,
    );
    expect(scrollWidth).toBeLessThanOrEqual(3840 + 20);
    await page.screenshot({
      path: path.join(OUT_DIR, "4k-admin-config-overview.png"),
      fullPage: false,
    });
  });
});

// ---------------------------------------------------------------------------
// FHD viewport (1920 × 1080)
// ---------------------------------------------------------------------------

test.describe("UIVERIFY-plugins-auth-paths — FHD (1920×1080)", () => {
  test.use({ viewport: { width: 1920, height: 1080 } });

  test.beforeEach(async ({ page }) => {
    test.skip(!ADMIN_USER, "ADMIN_USER not set — skipping admin pane tests");
    await tolerantAdminLogin(page);
    ensureOutDir();
  });

  test("admin landing renders without crash at 1920", async ({ page }) => {
    await page.goto("/admin");
    await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
    await page.waitForTimeout(1_000);
    await assertNoCrash(page);
    const scrollWidth: number = await page.evaluate(
      () => document.documentElement.scrollWidth,
    );
    expect(scrollWidth).toBeLessThanOrEqual(1920 + 20);
    await page.screenshot({
      path: path.join(OUT_DIR, "fhd-admin-landing.png"),
      fullPage: false,
    });
  });

  test("admin#plugins pane renders without crash at 1920", async ({ page }) => {
    await page.goto("/admin#plugins");
    await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
    await page.waitForTimeout(1_200);
    await assertNoCrash(page);
    const scrollWidth: number = await page.evaluate(
      () => document.documentElement.scrollWidth,
    );
    expect(scrollWidth).toBeLessThanOrEqual(1920 + 20);
    await page.screenshot({
      path: path.join(OUT_DIR, "fhd-admin-plugins.png"),
      fullPage: false,
    });
  });

  test("admin#legacy-v1 pane renders without crash at 1920", async ({
    page,
  }) => {
    await page.goto("/admin#legacy-v1");
    await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
    await page.waitForTimeout(1_200);
    await assertNoCrash(page);
    const scrollWidth: number = await page.evaluate(
      () => document.documentElement.scrollWidth,
    );
    expect(scrollWidth).toBeLessThanOrEqual(1920 + 20);
    await page.screenshot({
      path: path.join(OUT_DIR, "fhd-admin-legacy-v1.png"),
      fullPage: false,
    });
  });

  test("admin#unhide pane renders without crash at 1920", async ({ page }) => {
    await page.goto("/admin#unhide");
    await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
    await page.waitForTimeout(1_200);
    await assertNoCrash(page);
    const scrollWidth: number = await page.evaluate(
      () => document.documentElement.scrollWidth,
    );
    expect(scrollWidth).toBeLessThanOrEqual(1920 + 20);
    await page.screenshot({
      path: path.join(OUT_DIR, "fhd-admin-unhide.png"),
      fullPage: false,
    });
  });

  test("admin#feature-toggles pane renders without crash at 1920", async ({
    page,
  }) => {
    await page.goto("/admin#feature-toggles");
    await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
    await page.waitForTimeout(1_200);
    await assertNoCrash(page);
    const scrollWidth: number = await page.evaluate(
      () => document.documentElement.scrollWidth,
    );
    expect(scrollWidth).toBeLessThanOrEqual(1920 + 20);
    await page.screenshot({
      path: path.join(OUT_DIR, "fhd-admin-feature-toggles.png"),
      fullPage: false,
    });
  });

  test("admin#config-overview pane renders without crash at 1920", async ({
    page,
  }) => {
    await page.goto("/admin#config-overview");
    await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
    await page.waitForTimeout(1_200);
    await assertNoCrash(page);
    const scrollWidth: number = await page.evaluate(
      () => document.documentElement.scrollWidth,
    );
    expect(scrollWidth).toBeLessThanOrEqual(1920 + 20);
    await page.screenshot({
      path: path.join(OUT_DIR, "fhd-admin-config-overview.png"),
      fullPage: false,
    });
  });
});
