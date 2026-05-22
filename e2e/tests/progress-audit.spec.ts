/**
 * Read-only UX progress-indicator audit (task #136).
 *
 * Walks every long-running UI flow in the Shepard frontend, captures
 * screenshots at the suspected gap moment, and records evidence about
 * the feedback that exists (or is missing) during each operation.
 *
 * Output:
 *  - screenshots in /opt/shepard/aidocs/agent-findings/screenshots/ux-progress-sweep/
 *  - JSON evidence in /opt/shepard/aidocs/agent-findings/screenshots/ux-progress-sweep/evidence.json
 *
 * This spec does NOT make assertions that "fix" anything; it just
 * records measurements. The findings doc is authored separately.
 */
import { test, expect, Page } from "@playwright/test";
import { loginAs } from "./helpers/auth";
import * as fs from "fs";
import * as path from "path";

const OUT_DIR =
  "/opt/shepard/aidocs/agent-findings/screenshots/ux-progress-sweep";

const VIEWPORTS = {
  "4k": { width: 3840, height: 2160 },
  "1920": { width: 1920, height: 1080 },
  "1440": { width: 1440, height: 900 },
};

// Known IDs from project_mffd_api_keys.md + API probe above.
const MFFD_DROPBOX_ID = 515365;          // ~8k+ DOs
const LUMEN_COLLECTION_ID = 42;          // 17 DOs
const LUMEN_TR004_DO_ID = 48;            // TR-004 anomaly run
const LUMEN_PUBLICATIONS_DO_ID = 707;    // has structured-data ref

const evidence: Record<string, unknown>[] = [];

function record(entry: Record<string, unknown>) {
  evidence.push({ ts: new Date().toISOString(), ...entry });
}

async function snap(page: Page, slug: string, label: string) {
  const fp = path.join(OUT_DIR, `${slug}--${label}.png`);
  fs.mkdirSync(OUT_DIR, { recursive: true });
  await page
    .screenshot({ path: fp, fullPage: false, timeout: 5000 })
    .catch((e) => record({ slug, label, screenshotError: String(e) }));
  return fp;
}

/** Inspect ARIA progress affordances visible right now. */
async function ariaInventory(page: Page): Promise<Record<string, number>> {
  return await page.evaluate(() => {
    const count = (sel: string) => document.querySelectorAll(sel).length;
    return {
      ariaBusy: count('[aria-busy="true"]'),
      roleProgressbar: count('[role="progressbar"]'),
      roleStatus: count('[role="status"]'),
      ariaLive: count('[aria-live]'),
      vProgressLinear: count('.v-progress-linear'),
      vProgressCircular: count('.v-progress-circular'),
      vSkeletonLoader: count('.v-skeleton-loader'),
      vOverlay: count('.v-overlay--active'),
    };
  });
}

/** Time how long a "spinner of faith" condition lasts. */
async function timeUntil(
  page: Page,
  fn: () => Promise<boolean>,
  timeoutMs = 30_000
): Promise<number> {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    try {
      if (await fn()) return Date.now() - start;
    } catch {
      // ignore
    }
    await page.waitForTimeout(150);
  }
  return -1;
}

test.describe.configure({ mode: "serial" });

test.describe("UX progress indicator audit", () => {
  test.setTimeout(180_000);

  test("login + warm storage state", async ({ page, context }) => {
    await page.setViewportSize(VIEWPORTS["4k"]);
    const user = process.env.SHEPARD_E2E_USER || "admin";
    const pass = process.env.SHEPARD_E2E_PASS || "admin-demo";

    // --- F00: login redirect flow ---
    const t0 = Date.now();
    await page.goto("/auth/signIn");
    await snap(page, "F00-login", "01-signin-page");
    const signInBtn = page.getByRole("button", { name: /sign in|login/i }).first();
    await signInBtn.click();
    // Time-to-Keycloak-page
    await page.waitForURL(/realms\/shepard-demo/, { timeout: 15_000 });
    await snap(page, "F00-login", "02-kc-form");
    await page.fill("#username", user);
    await page.fill("#password", pass);
    const submitClickedAt = Date.now();
    await page.click('[type="submit"]');
    // Time-to-back-on-app
    await page.waitForURL(
      /shepard\.nuclide\.systems\/(?!.*error)/,
      { timeout: 30_000 }
    );
    // Time-to-app-ready (SIGN OUT visible)
    await page.waitForSelector("text=SIGN OUT", { timeout: 15_000 });
    const totalMs = Date.now() - t0;
    const submitToAppMs = Date.now() - submitClickedAt;
    await snap(page, "F00-login", "03-logged-in");
    const aria = await ariaInventory(page);
    record({
      flow: "F00-login",
      totalMs,
      submitToAppMs,
      aria,
      note:
        "OIDC bounce: from form-submit to home-page-ready. Look for a spinner during the round-trip in screenshot 02-kc-form -> 03-logged-in.",
    });

    await context.storageState({ path: "/tmp/auth-state.json" });
  });

  test("F01: collection page initial load (MFFD-Dropbox, 8k+ DOs)", async ({
    browser,
  }) => {
    const ctx = await browser.newContext({
      storageState: "/tmp/auth-state.json",
      viewport: VIEWPORTS["4k"],
    });
    const page = await ctx.newPage();

    const responseSizes: { url: string; size: number; ms: number }[] = [];
    const reqStart: Record<string, number> = {};
    page.on("request", (r) => {
      if (r.url().includes("/shepard/api/") || r.url().includes("/v2/"))
        reqStart[r.url()] = Date.now();
    });
    page.on("response", async (r) => {
      const u = r.url();
      if (u.includes("/shepard/api/") || u.includes("/v2/")) {
        const ms = Date.now() - (reqStart[u] || Date.now());
        let size = 0;
        try {
          size = (await r.body()).length;
        } catch {
          /* */
        }
        responseSizes.push({ url: u, size, ms });
      }
    });

    const t0 = Date.now();
    await page.goto(`/collections/${MFFD_DROPBOX_ID}`);
    await snap(page, "F01-collection-load", "01-immediate");

    // Quick mid-load snapshot
    await page.waitForTimeout(500);
    await snap(page, "F01-collection-load", "02-500ms");
    const aria500 = await ariaInventory(page);

    await page.waitForTimeout(2000);
    await snap(page, "F01-collection-load", "03-2500ms");
    const aria2500 = await ariaInventory(page);

    // Try to wait for the data-objects table heading.
    const tableReadyMs = await timeUntil(page, async () => {
      const rows = await page.locator(".v-data-table tr, .v-list-item").count();
      return rows > 5;
    }, 30_000);

    await snap(page, "F01-collection-load", "04-table-ready");
    const ariaFinal = await ariaInventory(page);
    const totalMs = Date.now() - t0;

    record({
      flow: "F01-collection-load-mffd",
      collectionId: MFFD_DROPBOX_ID,
      totalMs,
      tableReadyMs,
      aria500,
      aria2500,
      ariaFinal,
      apiCallCount: responseSizes.length,
      apiTotalBytes: responseSizes.reduce((a, b) => a + b.size, 0),
      slowestApi: responseSizes.sort((a, b) => b.ms - a.ms).slice(0, 5),
    });

    await ctx.close();
  });

  test("F02: sidebar tree at scale", async ({ browser }) => {
    const ctx = await browser.newContext({
      storageState: "/tmp/auth-state.json",
      viewport: VIEWPORTS["4k"],
    });
    const page = await ctx.newPage();

    await page.goto(`/collections/${MFFD_DROPBOX_ID}`);
    await page.waitForLoadState("networkidle", { timeout: 30_000 }).catch(() => {});
    await snap(page, "F02-sidebar", "01-initial");

    // Look for sidebar expand triggers.
    const sidebar = page.locator(
      'aside, [class*="sidebar"], [class*="Sidebar"], .v-navigation-drawer'
    );
    const sidebarCount = await sidebar.count();

    // Count visible items in sidebar.
    const itemCount = await page
      .locator(".v-list-item, .v-treeview-node")
      .count();

    // Try expanding any closed group node.
    let expandLatencyMs = -1;
    const expandable = page.locator(
      '.v-list-group__activator, [aria-expanded="false"]'
    );
    const expandableCount = await expandable.count();
    if (expandableCount > 0) {
      const beforeAria = await ariaInventory(page);
      const t0 = Date.now();
      await expandable.first().click();
      await page.waitForTimeout(300);
      const afterAria = await ariaInventory(page);
      expandLatencyMs = Date.now() - t0;
      await snap(page, "F02-sidebar", "02-after-expand");
      record({
        flow: "F02-sidebar-expand",
        beforeAria,
        afterAria,
        expandLatencyMs,
      });
    }

    record({
      flow: "F02-sidebar-tree",
      sidebarCount,
      itemCount,
      expandableCount,
    });

    await ctx.close();
  });

  test("F03: collection lineage graph render", async ({ browser }) => {
    const ctx = await browser.newContext({
      storageState: "/tmp/auth-state.json",
      viewport: VIEWPORTS["4k"],
    });
    const page = await ctx.newPage();

    // Capture network requests while lineage renders.
    const lineageRequests: { url: string; ms: number }[] = [];
    const reqT: Record<string, number> = {};
    page.on("request", (r) => {
      if (r.url().includes("/shepard/api/") || r.url().includes("/v2/"))
        reqT[r.url()] = Date.now();
    });
    page.on("response", (r) => {
      const u = r.url();
      if (u.includes("/shepard/api/") || u.includes("/v2/")) {
        const ms = Date.now() - (reqT[u] || Date.now());
        lineageRequests.push({ url: u, ms });
      }
    });

    // LUMEN: small (17 DOs). Lineage is an expansion panel near the bottom.
    await page.goto(`/collections/${LUMEN_COLLECTION_ID}`);
    await page.waitForLoadState("networkidle", { timeout: 20_000 }).catch(() => {});
    await snap(page, "F03-lineage-small", "01-page");

    // Scroll the "Dataset Lineage" panel into view and click it.
    const lineageHeader = page.getByText(/Dataset Lineage/i).first();
    await lineageHeader.scrollIntoViewIfNeeded().catch(() => {});
    await snap(page, "F03-lineage-small", "02-scrolled");
    const reqsBefore = lineageRequests.length;
    const t0 = Date.now();
    await lineageHeader.click({ timeout: 5000 }).catch(() => {});
    await page.waitForTimeout(80);
    await snap(page, "F03-lineage-small", "03-clicked-80ms");
    const aria80 = await ariaInventory(page);
    await page.waitForTimeout(420);
    await snap(page, "F03-lineage-small", "04-500ms");
    const aria500 = await ariaInventory(page);

    // ECharts renders into a <canvas> inside the lineage panel.
    const renderReadyMs = await timeUntil(page, async () => {
      const canvases = page.locator("canvas");
      const n = await canvases.count();
      if (n === 0) return false;
      // Wait for one to be > 100px wide (real chart, not a sparkline).
      for (let i = 0; i < n; i++) {
        const box = await canvases.nth(i).boundingBox().catch(() => null);
        if (box && box.width > 200 && box.height > 200) return true;
      }
      return false;
    }, 30_000);
    await snap(page, "F03-lineage-small", "05-rendered");
    const ariaFinal = await ariaInventory(page);

    record({
      flow: "F03-lineage-small",
      clickedToReadyMs: renderReadyMs,
      aria80,
      aria500,
      ariaFinal,
      apiCallsAfterClick: lineageRequests.length - reqsBefore,
      slowestAfterClick: lineageRequests
        .slice(reqsBefore)
        .sort((a, b) => b.ms - a.ms)
        .slice(0, 5),
    });

    // Now MFFD scale. Same expansion-panel approach.
    const reqsBeforeBig = lineageRequests.length;
    await page.goto(`/collections/${MFFD_DROPBOX_ID}`);
    await page.waitForLoadState("networkidle", { timeout: 30_000 }).catch(() => {});
    const lineageHeader2 = page.getByText(/Dataset Lineage/i).first();
    await lineageHeader2.scrollIntoViewIfNeeded().catch(() => {});
    await snap(page, "F03-lineage-large", "01-scrolled");
    const tBig0 = Date.now();
    await lineageHeader2.click({ timeout: 5000 }).catch(() => {});
    await page.waitForTimeout(80);
    await snap(page, "F03-lineage-large", "02-clicked-80ms");
    await page.waitForTimeout(420);
    await snap(page, "F03-lineage-large", "03-500ms");
    await page.waitForTimeout(2000);
    await snap(page, "F03-lineage-large", "04-2500ms");
    const ariaBig = await ariaInventory(page);
    const renderReadyMsBig = await timeUntil(page, async () => {
      const canvases = page.locator("canvas");
      const n = await canvases.count();
      for (let i = 0; i < n; i++) {
        const box = await canvases.nth(i).boundingBox().catch(() => null);
        if (box && box.width > 200 && box.height > 200) return true;
      }
      return false;
    }, 90_000);
    await snap(page, "F03-lineage-large", "05-rendered-or-timeout");
    record({
      flow: "F03-lineage-large",
      clickedToReadyMs: renderReadyMsBig,
      ariaDuring: ariaBig,
      apiCallsAfterClick: lineageRequests.length - reqsBeforeBig,
      slowestAfterClick: lineageRequests
        .slice(reqsBeforeBig)
        .sort((a, b) => b.ms - a.ms)
        .slice(0, 8),
    });

    await ctx.close();
  });

  test("F04: DataObject detail page + TR-004 prov graph", async ({
    browser,
  }) => {
    const ctx = await browser.newContext({
      storageState: "/tmp/auth-state.json",
      viewport: VIEWPORTS["4k"],
    });
    const page = await ctx.newPage();

    const t0 = Date.now();
    await page.goto(
      `/collections/${LUMEN_COLLECTION_ID}/dataobjects/${LUMEN_TR004_DO_ID}`
    );
    await snap(page, "F04-do-detail", "01-immediate");
    await page.waitForTimeout(500);
    const aria500 = await ariaInventory(page);
    await snap(page, "F04-do-detail", "02-500ms");

    const readyMs = await timeUntil(page, async () => {
      const txt = await page.locator("body").innerText();
      return /TR-004/i.test(txt);
    }, 30_000);
    await snap(page, "F04-do-detail", "03-ready");
    const ariaFinal = await ariaInventory(page);
    record({
      flow: "F04-do-detail",
      totalMs: Date.now() - t0,
      contentReadyMs: readyMs,
      aria500,
      ariaFinal,
    });

    await ctx.close();
  });

  test("F05: timeseries container page + chart load", async ({ browser }) => {
    const ctx = await browser.newContext({
      storageState: "/tmp/auth-state.json",
      viewport: VIEWPORTS["4k"],
    });
    const page = await ctx.newPage();

    // First find a TS container. From the API a DataObject lists timeseriesReferenceCount.
    // For the LUMEN dataset TR-001..TR-015 all have TS references.
    // Use the DataObject detail page approach: navigate to TR-001 (id 45).
    await page.goto(`/collections/${LUMEN_COLLECTION_ID}/dataobjects/45`);
    await page.waitForLoadState("networkidle", { timeout: 30_000 }).catch(() => {});
    await snap(page, "F05-ts", "01-do-page");

    // Click any "Open timeseries" / "View chart" / link.
    const tsLink = page.getByRole("link", { name: /timeseries|chart/i });
    if ((await tsLink.count()) > 0) {
      const t0 = Date.now();
      await tsLink.first().click().catch(() => {});
      await snap(page, "F05-ts", "02-after-click");
      await page.waitForTimeout(500);
      await snap(page, "F05-ts", "03-500ms");
      const aria = await ariaInventory(page);
      const chartMs = await timeUntil(page, async () => {
        return (await page.locator("canvas, svg.chart").count()) > 0;
      }, 30_000);
      await snap(page, "F05-ts", "04-chart");
      record({
        flow: "F05-ts-chart",
        clickedToChartMs: chartMs,
        aria,
      });
    } else {
      record({ flow: "F05-ts-chart", note: "No TS link visible on DO page" });
    }

    await ctx.close();
  });

  test("F06: collection search / filter responsiveness", async ({
    browser,
  }) => {
    const ctx = await browser.newContext({
      storageState: "/tmp/auth-state.json",
      viewport: VIEWPORTS["4k"],
    });
    const page = await ctx.newPage();

    await page.goto(`/collections/${MFFD_DROPBOX_ID}`);
    await page.waitForLoadState("networkidle", { timeout: 30_000 }).catch(() => {});

    // Hunt for a search input.
    const searchBox = page
      .locator(
        'input[type="search"], input[aria-label*="earch" i], input[placeholder*="earch" i]'
      )
      .first();
    if ((await searchBox.count()) > 0) {
      await searchBox.click();
      await snap(page, "F06-search", "01-focused");
      const t0 = Date.now();
      await searchBox.type("frame", { delay: 80 });
      // Capture mid-typing
      await snap(page, "F06-search", "02-mid-type");
      const aria = await ariaInventory(page);
      // Wait for result count to settle.
      await page.waitForTimeout(1500);
      await snap(page, "F06-search", "03-1500ms");
      const ariaFinal = await ariaInventory(page);
      record({
        flow: "F06-search-debounce",
        midTypeAria: aria,
        finalAria: ariaFinal,
      });
    } else {
      record({ flow: "F06-search-debounce", note: "No search box found" });
    }

    await ctx.close();
  });

  test("F07: navigate around without explicit progress", async ({
    browser,
  }) => {
    const ctx = await browser.newContext({
      storageState: "/tmp/auth-state.json",
      viewport: VIEWPORTS["4k"],
    });
    const page = await ctx.newPage();
    await page.goto("/");
    await snap(page, "F07-nav", "01-home");

    // Navigate to /collections
    const t0 = Date.now();
    await page.goto("/collections");
    await snap(page, "F07-nav", "02-collections-immediate");
    await page.waitForTimeout(300);
    const aria = await ariaInventory(page);
    await snap(page, "F07-nav", "03-collections-300ms");
    await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
    await snap(page, "F07-nav", "04-collections-idle");
    const ariaFinal = await ariaInventory(page);
    record({
      flow: "F07-nav-collections-index",
      transitionMs: Date.now() - t0,
      aria,
      ariaFinal,
    });

    // Navigate to /containers/timeseries (overview page)
    await page.goto("/containers/timeseries");
    await snap(page, "F07-nav", "05-ts-index");
    await page.waitForTimeout(300);
    await snap(page, "F07-nav", "06-ts-index-300ms");
    const ariaTs = await ariaInventory(page);
    record({ flow: "F07-nav-ts-index", aria: ariaTs });

    await ctx.close();
  });

  test("F08: viewport variants for worst offenders", async ({ browser }) => {
    for (const vp of ["1920", "1440"] as const) {
      const ctx = await browser.newContext({
        storageState: "/tmp/auth-state.json",
        viewport: VIEWPORTS[vp],
      });
      const page = await ctx.newPage();
      await page.goto(`/collections/${MFFD_DROPBOX_ID}`);
      await page.waitForTimeout(800);
      await snap(page, `F08-vp-${vp}`, "collection-mffd");
      await page.goto(`/collections/${LUMEN_COLLECTION_ID}/dataobjects/${LUMEN_TR004_DO_ID}`);
      await page.waitForTimeout(800);
      await snap(page, `F08-vp-${vp}`, "tr004-detail");
      await ctx.close();
    }
    record({ flow: "F08-viewport-variants", note: "captured 1920+1440 of worst offenders" });
  });

  test.afterAll(async () => {
    fs.writeFileSync(
      path.join(OUT_DIR, "evidence.json"),
      JSON.stringify(evidence, null, 2)
    );
    // eslint-disable-next-line no-console
    console.log(`\n[audit] ${evidence.length} flow records written to ${OUT_DIR}/evidence.json`);
  });
});
