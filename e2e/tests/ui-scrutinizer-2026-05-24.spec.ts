/**
 * UI Scrutinizer — systematic live-shepard walk, 2026-05-24
 *
 * One-shot exploratory spec. Walks the 6 phases from the scrutinizer prompt:
 *   1) Landing + nav
 *   2) Collection-level (LUMEN 42, MFFD-Dropbox 661923)
 *   3) DataObject detail (LUMEN + MFFD deep)
 *   4) Container views (TS / Files / SD)
 *   5) Cross-cutting (search, advanced mode, help, lineage/prov)
 *   6) Forms + writes (read-only inspection; no mutations beyond observing modal shape)
 *
 * For each visit:
 *  - capture full-page screenshot @ 1920x1080
 *  - capture full-page screenshot @ 3840x2160 (4K) for layout-sensitive pages
 *  - record console errors + warnings
 *  - record network 4xx / 5xx
 *  - dump a JSON sidecar with all of the above
 *
 * Output goes to /opt/shepard/aidocs/agent-findings/ui-scrutinizer-2026-05-24-evidence/
 *
 * This spec is INTENTIONALLY non-failing — it captures evidence regardless of UI state.
 * The findings narrative goes in the matching .md file.
 */
import { test, expect, type Page, type ConsoleMessage, type Request, type Response } from "@playwright/test";
import * as fs from "fs";
import * as path from "path";

const KC = process.env.KEYCLOAK_HOST || "https://shepard-auth.nuclide.systems";
const REALM = "shepard-demo";
const OUT = "/opt/shepard/aidocs/agent-findings/ui-scrutinizer-2026-05-24-evidence";

const LUMEN_COLL = 42;
const MFFD_COLL = 661923;

fs.mkdirSync(OUT, { recursive: true });

type Capture = {
  url: string;
  finalUrl?: string;
  title?: string;
  viewport: { w: number; h: number };
  consoleErrors: string[];
  consoleWarnings: string[];
  networkErrors: { url: string; status: number; statusText: string }[];
  networkFailed: { url: string; failure: string }[];
  durationMs: number;
  notes?: string;
};

function attachListeners(page: Page) {
  const errors: string[] = [];
  const warnings: string[] = [];
  const netErrors: { url: string; status: number; statusText: string }[] = [];
  const netFailed: { url: string; failure: string }[] = [];

  page.on("console", (msg: ConsoleMessage) => {
    if (msg.type() === "error") errors.push(msg.text());
    if (msg.type() === "warning") warnings.push(msg.text());
  });
  page.on("pageerror", (e: Error) => errors.push(`PAGEERROR: ${e.message}`));
  page.on("response", (resp: Response) => {
    const s = resp.status();
    if (s >= 400) {
      const u = resp.url();
      // skip noisy keycloak well-known fetches if 404; record others
      if (!u.includes(".well-known") || s >= 500) {
        netErrors.push({ url: u, status: s, statusText: resp.statusText() });
      }
    }
  });
  page.on("requestfailed", (req: Request) => {
    netFailed.push({ url: req.url(), failure: req.failure()?.errorText || "" });
  });
  return { errors, warnings, netErrors, netFailed };
}

async function loginAs(page: Page, username: string, password: string) {
  await page.goto("/auth/signIn");
  await page.getByRole("button", { name: /sign in|login/i }).first().click();
  await page.waitForURL(`${KC}/realms/${REALM}/**`, { timeout: 15_000 });
  await page.fill("#username", username);
  await page.fill("#password", password);
  await page.click('[type="submit"]');
  await page.waitForURL(/shepard\.nuclide\.systems(?!.*error)/, { timeout: 20_000 });
  await page.waitForSelector("text=SIGN OUT", { timeout: 15_000 });
}

async function visit(
  page: Page,
  url: string,
  slug: string,
  viewport: { w: number; h: number },
  opts: { settleMs?: number; notes?: string } = {},
): Promise<Capture> {
  const settle = opts.settleMs ?? 2500;
  await page.setViewportSize({ width: viewport.w, height: viewport.h });
  const { errors, warnings, netErrors, netFailed } = attachListeners(page);
  const t0 = Date.now();
  let finalUrl = url;
  let title: string | undefined;
  try {
    await page.goto(url, { waitUntil: "domcontentloaded", timeout: 25_000 });
    // give SPA a chance to render
    await page.waitForTimeout(settle);
    finalUrl = page.url();
    title = await page.title().catch(() => undefined);
  } catch (e) {
    errors.push(`NAV_FAIL: ${(e as Error).message}`);
  }
  const duration = Date.now() - t0;
  const screenshotPath = path.join(OUT, `${slug}-${viewport.w}.png`);
  try {
    await page.screenshot({ path: screenshotPath, fullPage: true, timeout: 15_000 });
  } catch (e) {
    errors.push(`SCREENSHOT_FAIL: ${(e as Error).message}`);
  }
  // also viewport-only at 4K so we see what fits in one screen
  if (viewport.w >= 3840) {
    const vpPath = path.join(OUT, `${slug}-${viewport.w}-viewport.png`);
    try {
      await page.screenshot({ path: vpPath, fullPage: false, timeout: 15_000 });
    } catch (e) {
      errors.push(`SCREENSHOT_VP_FAIL: ${(e as Error).message}`);
    }
  }
  const cap: Capture = {
    url,
    finalUrl,
    title,
    viewport,
    consoleErrors: errors,
    consoleWarnings: warnings,
    networkErrors: netErrors,
    networkFailed: netFailed,
    durationMs: duration,
    notes: opts.notes,
  };
  fs.writeFileSync(
    path.join(OUT, `${slug}-${viewport.w}.json`),
    JSON.stringify(cap, null, 2),
  );
  return cap;
}

test.describe.configure({ mode: "serial" });

test.describe("UI Scrutinizer 2026-05-24", () => {
  test.setTimeout(45 * 60_000);

  test("walk", async ({ browser }) => {
    const ctx = await browser.newContext({
      viewport: { width: 1920, height: 1080 },
      ignoreHTTPSErrors: true,
    });
    const page = await ctx.newPage();
    await loginAs(page, "alice", "alice-demo");

    const VP_HD = { w: 1920, h: 1080 };
    const VP_4K = { w: 3840, h: 2160 };

    const log: Record<string, Capture> = {};

    // ============================================================
    // PHASE 1: Landing + nav
    // ============================================================
    log["p1-home"] = await visit(page, "/", "p1-01-home", VP_HD,
      { notes: "Authenticated home / personal digest" });
    log["p1-home-4k"] = await visit(page, "/", "p1-01-home", VP_4K, { settleMs: 3000 });
    log["p1-collections"] = await visit(page, "/collections", "p1-02-collections-list", VP_HD,
      { settleMs: 3500, notes: "Top-level collections list" });
    log["p1-collections-4k"] = await visit(page, "/collections", "p1-02-collections-list", VP_4K,
      { settleMs: 3500 });
    log["p1-containers"] = await visit(page, "/containers", "p1-03-containers-list", VP_HD,
      { settleMs: 3500, notes: "Containers top-level" });
    log["p1-search"] = await visit(page, "/search", "p1-04-search", VP_HD,
      { notes: "Advanced search page" });
    log["p1-help"] = await visit(page, "/help", "p1-05-help", VP_HD,
      { notes: "In-app help" });
    log["p1-about"] = await visit(page, "/about", "p1-06-about", VP_HD);
    log["p1-me"] = await visit(page, "/me", "p1-07-me", VP_HD,
      { notes: "Profile / settings" });
    log["p1-admin"] = await visit(page, "/admin", "p1-08-admin", VP_HD,
      { notes: "Admin page (alice may be unauthorized)" });
    log["p1-config"] = await visit(page, "/configuration", "p1-09-configuration", VP_HD);

    // ============================================================
    // PHASE 2: Collection-level
    // ============================================================
    log["p2-lumen"] = await visit(page, `/collections/${LUMEN_COLL}`, "p2-01-lumen-landing", VP_HD,
      { settleMs: 4000, notes: "LUMEN collection landing (synthetic showcase)" });
    log["p2-lumen-4k"] = await visit(page, `/collections/${LUMEN_COLL}`, "p2-01-lumen-landing", VP_4K,
      { settleMs: 4000 });
    log["p2-mffd"] = await visit(page, `/collections/${MFFD_COLL}`, "p2-02-mffd-landing", VP_HD,
      { settleMs: 5000, notes: "MFFD-Dropbox collection landing (scale stress)" });
    log["p2-mffd-4k"] = await visit(page, `/collections/${MFFD_COLL}`, "p2-02-mffd-landing", VP_4K,
      { settleMs: 5000 });

    // ============================================================
    // PHASE 3: DataObject detail
    // Need to discover one LUMEN DO id and one MFFD deep DO id by walking the sidebar.
    // Use the /v2 API via page.request to find IDs without scraping HTML.
    // ============================================================

    // LUMEN first DO via API
    let lumenDoId: number | undefined;
    let lumenDoAppId: string | undefined;
    let mffdDoId: number | undefined;
    let mffdDoAppId: string | undefined;
    try {
      // Cookie-based session already established
      const r = await page.request.get(
        `https://shepard.nuclide.systems/shepard/api/collections/${LUMEN_COLL}/dataObjects`,
      );
      if (r.ok()) {
        const arr = await r.json();
        if (Array.isArray(arr) && arr.length > 0) {
          lumenDoId = arr[0].id || arr[0].oid;
          lumenDoAppId = arr[0].appId;
          fs.writeFileSync(path.join(OUT, "lumen-do-sample.json"), JSON.stringify(arr.slice(0, 3), null, 2));
        }
      }
    } catch {}
    try {
      const r = await page.request.get(
        `https://shepard.nuclide.systems/shepard/api/collections/${MFFD_COLL}/dataObjects`,
      );
      if (r.ok()) {
        const arr = await r.json();
        if (Array.isArray(arr) && arr.length > 0) {
          mffdDoId = arr[0].id || arr[0].oid;
          mffdDoAppId = arr[0].appId;
          fs.writeFileSync(path.join(OUT, "mffd-do-sample.json"), JSON.stringify(arr.slice(0, 3), null, 2));
        }
      }
    } catch {}

    fs.writeFileSync(path.join(OUT, "do-ids.json"), JSON.stringify({ lumenDoId, lumenDoAppId, mffdDoId, mffdDoAppId }, null, 2));

    if (lumenDoId) {
      log["p3-lumen-do"] = await visit(
        page,
        `/collections/${LUMEN_COLL}/dataobjects/${lumenDoId}`,
        "p3-01-lumen-do-detail",
        VP_HD,
        { settleMs: 5000, notes: `LUMEN DO ${lumenDoId} detail` },
      );
      log["p3-lumen-do-4k"] = await visit(
        page,
        `/collections/${LUMEN_COLL}/dataobjects/${lumenDoId}`,
        "p3-01-lumen-do-detail",
        VP_4K,
        { settleMs: 5000 },
      );
    }
    if (mffdDoId) {
      log["p3-mffd-do"] = await visit(
        page,
        `/collections/${MFFD_COLL}/dataobjects/${mffdDoId}`,
        "p3-02-mffd-do-detail",
        VP_HD,
        { settleMs: 5000, notes: `MFFD DO ${mffdDoId} detail — BUG #139 candidate` },
      );
      log["p3-mffd-do-4k"] = await visit(
        page,
        `/collections/${MFFD_COLL}/dataobjects/${mffdDoId}`,
        "p3-02-mffd-do-detail",
        VP_4K,
        { settleMs: 5000 },
      );
    }

    // Find a deep MFFD DO (one with no children that is far down the chain)
    let mffdDeepId: number | undefined;
    try {
      const r = await page.request.get(
        `https://shepard.nuclide.systems/shepard/api/collections/${MFFD_COLL}/dataObjects?perPage=200`,
      );
      if (r.ok()) {
        const arr = await r.json();
        if (Array.isArray(arr) && arr.length > 0) {
          // pick one near the end (likely deeper / leaf)
          mffdDeepId = arr[arr.length - 1].id || arr[arr.length - 1].oid;
          fs.writeFileSync(path.join(OUT, "mffd-deep-do-sample.json"), JSON.stringify(arr[arr.length - 1], null, 2));
        }
      }
    } catch {}
    if (mffdDeepId && mffdDeepId !== mffdDoId) {
      log["p3-mffd-deep"] = await visit(
        page,
        `/collections/${MFFD_COLL}/dataobjects/${mffdDeepId}`,
        "p3-03-mffd-deep-do",
        VP_HD,
        { settleMs: 5000, notes: `MFFD deep DO ${mffdDeepId} detail` },
      );
    }

    // ============================================================
    // PHASE 4: Container views — find one TS / SD / File container by walking LUMEN refs
    // ============================================================
    let tsContainerId: number | undefined;
    let fileContainerId: number | undefined;
    let sdContainerId: number | undefined;
    try {
      const r = await page.request.get(`https://shepard.nuclide.systems/shepard/api/timeseriesContainers`);
      if (r.ok()) {
        const arr = await r.json();
        if (Array.isArray(arr) && arr.length > 0) tsContainerId = arr[0].id;
        fs.writeFileSync(path.join(OUT, "ts-containers-sample.json"), JSON.stringify(arr.slice(0, 5), null, 2));
      }
    } catch {}
    try {
      const r = await page.request.get(`https://shepard.nuclide.systems/shepard/api/fileContainers`);
      if (r.ok()) {
        const arr = await r.json();
        if (Array.isArray(arr) && arr.length > 0) fileContainerId = arr[0].id;
        fs.writeFileSync(path.join(OUT, "file-containers-sample.json"), JSON.stringify(arr.slice(0, 5), null, 2));
      }
    } catch {}
    try {
      const r = await page.request.get(`https://shepard.nuclide.systems/shepard/api/structuredDataContainers`);
      if (r.ok()) {
        const arr = await r.json();
        if (Array.isArray(arr) && arr.length > 0) sdContainerId = arr[0].id;
        fs.writeFileSync(path.join(OUT, "sd-containers-sample.json"), JSON.stringify(arr.slice(0, 5), null, 2));
      }
    } catch {}
    fs.writeFileSync(path.join(OUT, "container-ids.json"), JSON.stringify({ tsContainerId, fileContainerId, sdContainerId }, null, 2));

    if (tsContainerId) {
      log["p4-ts"] = await visit(
        page,
        `/containers/timeseries/${tsContainerId}`,
        "p4-01-ts-container",
        VP_HD,
        { settleMs: 5000, notes: `TS container ${tsContainerId}` },
      );
    }
    if (fileContainerId) {
      log["p4-file"] = await visit(
        page,
        `/containers/files/${fileContainerId}`,
        "p4-02-file-container",
        VP_HD,
        { settleMs: 5000, notes: `File container ${fileContainerId}` },
      );
    }
    if (sdContainerId) {
      log["p4-sd"] = await visit(
        page,
        `/containers/structureddata/${sdContainerId}`,
        "p4-03-sd-container",
        VP_HD,
        { settleMs: 5000, notes: `SD container ${sdContainerId}` },
      );
    }

    // ============================================================
    // PHASE 5: Cross-cutting — search interaction + advanced mode toggle observation
    // ============================================================

    // Header search
    await page.setViewportSize({ width: 1920, height: 1080 });
    await page.goto("/");
    await page.waitForTimeout(2000);
    const searchListeners = attachListeners(page);
    try {
      // type into header search to see what shows up
      const searchBox = page.locator('input[type="search"], input[placeholder*="earch" i]').first();
      if (await searchBox.count() > 0) {
        await searchBox.fill("TR-004");
        await page.waitForTimeout(2500);
        await page.screenshot({ path: path.join(OUT, "p5-01-header-search-tr004-1920.png"), fullPage: false });
      }
    } catch (e) {
      searchListeners.errors.push(`SEARCH_INTERACT: ${(e as Error).message}`);
    }
    fs.writeFileSync(path.join(OUT, "p5-01-header-search.json"), JSON.stringify({
      consoleErrors: searchListeners.errors,
      consoleWarnings: searchListeners.warnings,
      networkErrors: searchListeners.netErrors,
    }, null, 2));

    // Advanced mode toggle (if reachable from collection landing)
    await page.goto(`/collections/${LUMEN_COLL}`);
    await page.waitForTimeout(4000);
    const beforeAdv = path.join(OUT, "p5-02-lumen-basic-mode.png");
    await page.screenshot({ path: beforeAdv, fullPage: true });
    try {
      const toggle = page.getByRole("button", { name: /advanced|basic/i }).first();
      if (await toggle.count() > 0) {
        await toggle.click();
        await page.waitForTimeout(2500);
        await page.screenshot({ path: path.join(OUT, "p5-03-lumen-advanced-mode.png"), fullPage: true });
      }
    } catch {}

    // Visit a LUMEN DO that we know has rich references — pick from sample if available
    if (lumenDoId) {
      await page.goto(`/collections/${LUMEN_COLL}/dataobjects/${lumenDoId}`);
      await page.waitForTimeout(6000);
      await page.screenshot({ path: path.join(OUT, "p5-04-lumen-do-full-detail.png"), fullPage: true });
    }

    // ============================================================
    // PHASE 6: Forms inspection — open Create dialogs without submitting
    // ============================================================
    await page.goto(`/collections/${LUMEN_COLL}`);
    await page.waitForTimeout(3000);
    try {
      // try to find a "+" / "Add" / "Create" button
      const addBtn = page
        .locator("button:has(i), button:has(svg)")
        .filter({ hasText: /add|create|\+/i })
        .first();
      if (await addBtn.count() > 0) {
        await addBtn.click({ timeout: 5000 });
        await page.waitForTimeout(1500);
        await page.screenshot({ path: path.join(OUT, "p6-01-create-dialog.png"), fullPage: false });
        // close it without submitting
        await page.keyboard.press("Escape");
      }
    } catch {}

    // Write final aggregate
    fs.writeFileSync(
      path.join(OUT, "00-walk-summary.json"),
      JSON.stringify(
        {
          startedAt: new Date().toISOString(),
          lumenColl: LUMEN_COLL,
          mffdColl: MFFD_COLL,
          lumenDoId,
          mffdDoId,
          mffdDeepId,
          tsContainerId,
          fileContainerId,
          sdContainerId,
          captures: Object.fromEntries(
            Object.entries(log).map(([k, v]) => [
              k,
              {
                url: v.url,
                finalUrl: v.finalUrl,
                title: v.title,
                viewport: v.viewport,
                durationMs: v.durationMs,
                consoleErrorCount: v.consoleErrors.length,
                consoleWarningCount: v.consoleWarnings.length,
                networkErrorCount: v.networkErrors.length,
                notes: v.notes,
              },
            ]),
          ),
        },
        null,
        2,
      ),
    );

    expect(true).toBe(true);
  });
});
