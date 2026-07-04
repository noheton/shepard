/**
 * UIVERIFY-tools-cluster — Playwright viewport verification for the Tools
 * cluster pages: tools landing, SPARQL playground, shapes render, shapes
 * validate, and snapshot diff.
 *
 * All five pages are confirmed real implementations (fire-237 audit):
 *   - /tools                   — Tools landing grid (tiles: sparql, shapes-render …)
 *   - /semantic/sparql         — SPARQL playground (235-line editor + results table)
 *   - /shapes/render           — Shape render playground (1138-line renderer)
 *   - /shapes/validate         — SHACL validation playground
 *   - /snapshots/diff          — Snapshot diff tool
 *
 * None require a specific data appId env var — all show a usable empty state
 * (pickers, query textarea, snapshot selectors) without pre-seeded data.
 *
 * Viewport targets (per feedback_validate_user_viewport.md):
 *   - 4K  (3840 × 2160) — operator's native resolution
 *   - FHD (1920 × 1080) — most common deployed resolution
 *
 * Screenshot artifacts land in:
 *   aidocs/agent-findings/screenshots/uiverify-tools-cluster/
 *
 * Tests login with DEMO_USER/DEMO_PASSWORD (tolerant — non-fatal if auth
 * fails; layout checks are still valid on an unauth'd page load).
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "./helpers/auth";
import path from "path";
import fs from "fs";

const DEMO_USER = process.env.DEMO_USER || "flodemo";
const DEMO_PASS = process.env.DEMO_PASSWORD || "flo-demo";

const OUT_DIR = path.resolve(
  __dirname,
  "../../aidocs/agent-findings/screenshots/uiverify-tools-cluster",
);

function ensureOutDir() {
  if (!fs.existsSync(OUT_DIR)) fs.mkdirSync(OUT_DIR, { recursive: true });
}

async function tolerantLogin(page: import("@playwright/test").Page) {
  await loginAs(page, DEMO_USER, DEMO_PASS).catch(() => {
    // Non-fatal: even an unauthenticated layout check is useful.
  });
}

/** The five tools-cluster pages we verify. */
const TOOLS_PAGES = [
  {
    route: "/tools",
    expectedTitle: "Tools | shepard",
    slug: "tools-landing",
    visibleHeading: "Tools",
  },
  {
    route: "/semantic/sparql",
    expectedTitle: "SPARQL playground | shepard",
    slug: "sparql",
    visibleHeading: null,
  },
  {
    route: "/shapes/render",
    expectedTitle: "Shape render playground | shepard",
    slug: "shapes-render",
    visibleHeading: null,
  },
  {
    route: "/shapes/validate",
    expectedTitle: "SHACL playground | shepard",
    slug: "shapes-validate",
    visibleHeading: null,
  },
  {
    route: "/snapshots/diff",
    expectedTitle: "Snapshot diff | shepard",
    slug: "snapshots-diff",
    visibleHeading: null,
  },
] as const;

// ---------------------------------------------------------------------------
// 4K viewport (3840 × 2160)
// ---------------------------------------------------------------------------

test.describe("UIVERIFY-tools-cluster — 4K (3840×2160)", () => {
  test.use({ viewport: { width: 3840, height: 2160 } });

  test.beforeEach(async ({ page }) => {
    await tolerantLogin(page);
  });

  for (const tool of TOOLS_PAGES) {
    test(`${tool.slug} renders without crash at 4K`, async ({ page }) => {
      ensureOutDir();

      await page.goto(tool.route);
      await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
      await page.waitForTimeout(1_000);

      const body = page.locator("body");
      await expect(body).not.toContainText("ServiceUnavailableException");
      await expect(body).not.toContainText("NullPointerException");
      await expect(body).not.toContainText("500 Internal Server Error");

      await page.screenshot({
        path: path.join(OUT_DIR, `4k-${tool.slug}.png`),
        fullPage: false,
      });
    });

    test(`${tool.slug} page title correct at 4K`, async ({ page }) => {
      await page.goto(tool.route);
      await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});

      const title = await page.title();
      expect(title).toBe(tool.expectedTitle);
    });

    test(`${tool.slug} has no full-page horizontal overflow at 4K`, async ({
      page,
    }) => {
      await page.goto(tool.route);
      await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
      await page.waitForTimeout(800);

      const scrollWidth: number = await page.evaluate(
        () => document.documentElement.scrollWidth,
      );
      expect(scrollWidth).toBeLessThanOrEqual(3840 + 20);
    });
  }

  test("tools landing shows 'Tools' h1 heading at 4K", async ({ page }) => {
    await page.goto("/tools");
    await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});

    const heading = page.locator("h1").filter({ hasText: "Tools" });
    await expect(heading).toBeVisible({ timeout: 10_000 });
  });
});

// ---------------------------------------------------------------------------
// FHD viewport (1920 × 1080)
// ---------------------------------------------------------------------------

test.describe("UIVERIFY-tools-cluster — FHD (1920×1080)", () => {
  test.use({ viewport: { width: 1920, height: 1080 } });

  test.beforeEach(async ({ page }) => {
    await tolerantLogin(page);
  });

  for (const tool of TOOLS_PAGES) {
    test(`${tool.slug} renders without crash at 1920`, async ({ page }) => {
      ensureOutDir();

      await page.goto(tool.route);
      await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
      await page.waitForTimeout(1_000);

      const body = page.locator("body");
      await expect(body).not.toContainText("ServiceUnavailableException");
      await expect(body).not.toContainText("NullPointerException");
      await expect(body).not.toContainText("500 Internal Server Error");

      await page.screenshot({
        path: path.join(OUT_DIR, `fhd-${tool.slug}.png`),
        fullPage: false,
      });
    });

    test(`${tool.slug} page title correct at 1920`, async ({ page }) => {
      await page.goto(tool.route);
      await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});

      const title = await page.title();
      expect(title).toBe(tool.expectedTitle);
    });

    test(`${tool.slug} has no full-page horizontal overflow at 1920`, async ({
      page,
    }) => {
      await page.goto(tool.route);
      await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
      await page.waitForTimeout(800);

      const scrollWidth: number = await page.evaluate(
        () => document.documentElement.scrollWidth,
      );
      expect(scrollWidth).toBeLessThanOrEqual(1920 + 20);
    });
  }

  test("tools landing shows 'Tools' h1 heading at 1920", async ({ page }) => {
    await page.goto("/tools");
    await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});

    const heading = page.locator("h1").filter({ hasText: "Tools" });
    await expect(heading).toBeVisible({ timeout: 10_000 });
  });
});
