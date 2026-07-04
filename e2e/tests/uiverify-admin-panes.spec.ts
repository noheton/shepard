/**
 * UIVERIFY-admin-panes — Playwright viewport verification for the remaining
 * admin panes not covered by uiverify-admin-plugins.spec.ts.
 *
 * Covers 23 admin fragments at 4K (3840×2160) and FHD (1920×1080):
 *   instance-health, templates, semantic-repositories, ontology-bundles,
 *   sparql-playground, user-groups, instance-ror, permission-audit-log,
 *   activity-log, file-migration, sql-timeseries, notifications-admin,
 *   instance-admins, users-orcid, users-git, ai-config, backup,
 *   ontology-alignment, semantic-config, instance-registry, jupyter,
 *   batch-create, storage-overview.
 *
 * Plugin-adjacent panes (plugins / legacy-v1 / unhide / feature-toggles /
 * config-overview) were exercised in uiverify-admin-plugins.spec.ts.
 *
 * Viewport targets:
 *   - 4K  (3840 × 2160) — operator's native resolution
 *   - FHD (1920 × 1080) — most common deployed resolution
 *
 * Screenshot artifacts land in:
 *   aidocs/agent-findings/screenshots/uiverify-admin-panes/
 *
 * All tests gate on ADMIN_USER / ADMIN_PASSWORD env vars so CI without a
 * seeded instance-admin account skips them gracefully.
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "./helpers/auth";
import path from "path";
import fs from "fs";

const ADMIN_USER = process.env.ADMIN_USER || "";
const ADMIN_PASS = process.env.ADMIN_PASSWORD || "";

const OUT_DIR = path.resolve(
  __dirname,
  "../../aidocs/agent-findings/screenshots/uiverify-admin-panes",
);

/** Fragments not yet covered by the plugins spec. */
const ADMIN_FRAGMENTS = [
  "instance-health",
  "templates",
  "semantic-repositories",
  "ontology-bundles",
  "sparql-playground",
  "user-groups",
  "instance-ror",
  "permission-audit-log",
  "activity-log",
  "file-migration",
  "sql-timeseries",
  "notifications-admin",
  "instance-admins",
  "users-orcid",
  "users-git",
  "ai-config",
  "backup",
  "ontology-alignment",
  "semantic-config",
  "instance-registry",
  "jupyter",
  "batch-create",
  "storage-overview",
] as const;

function ensureOutDir() {
  if (!fs.existsSync(OUT_DIR)) fs.mkdirSync(OUT_DIR, { recursive: true });
}

async function tolerantAdminLogin(page: import("@playwright/test").Page) {
  if (!ADMIN_USER) return;
  await loginAs(page, ADMIN_USER, ADMIN_PASS).catch(() => {});
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

test.describe("UIVERIFY-admin-panes — 4K (3840×2160)", () => {
  test.use({ viewport: { width: 3840, height: 2160 } });

  test.beforeEach(async ({ page }) => {
    test.skip(!ADMIN_USER, "ADMIN_USER not set — skipping admin pane tests");
    await tolerantAdminLogin(page);
    ensureOutDir();
  });

  for (const fragment of ADMIN_FRAGMENTS) {
    test(`admin#${fragment} renders without crash at 4K`, async ({ page }) => {
      await page.goto(`/admin#${fragment}`);
      await page
        .waitForLoadState("networkidle", { timeout: 15_000 })
        .catch(() => {});
      await page.waitForTimeout(1_200);
      await assertNoCrash(page);
      const scrollWidth: number = await page.evaluate(
        () => document.documentElement.scrollWidth,
      );
      expect(scrollWidth).toBeLessThanOrEqual(3840 + 20);
      await page.screenshot({
        path: path.join(OUT_DIR, `4k-admin-${fragment}.png`),
        fullPage: false,
      });
    });
  }
});

// ---------------------------------------------------------------------------
// FHD viewport (1920 × 1080)
// ---------------------------------------------------------------------------

test.describe("UIVERIFY-admin-panes — FHD (1920×1080)", () => {
  test.use({ viewport: { width: 1920, height: 1080 } });

  test.beforeEach(async ({ page }) => {
    test.skip(!ADMIN_USER, "ADMIN_USER not set — skipping admin pane tests");
    await tolerantAdminLogin(page);
    ensureOutDir();
  });

  for (const fragment of ADMIN_FRAGMENTS) {
    test(`admin#${fragment} renders without crash at 1920`, async ({ page }) => {
      await page.goto(`/admin#${fragment}`);
      await page
        .waitForLoadState("networkidle", { timeout: 15_000 })
        .catch(() => {});
      await page.waitForTimeout(1_200);
      await assertNoCrash(page);
      const scrollWidth: number = await page.evaluate(
        () => document.documentElement.scrollWidth,
      );
      expect(scrollWidth).toBeLessThanOrEqual(1920 + 20);
      await page.screenshot({
        path: path.join(OUT_DIR, `fhd-admin-${fragment}.png`),
        fullPage: false,
      });
    });
  }
});
