import { test, expect, type Page, type BrowserContext } from "@playwright/test";
import * as fs from "node:fs";
import * as path from "node:path";

const KC = process.env.KEYCLOAK_HOST || "https://shepard-auth.nuclide.systems";

// Local login — more tolerant of slow redirects than helpers/auth.ts.
// Critical: post-submit redirect chain can take >15s on cold cache.
async function loginLocal(page: Page, username: string, password: string): Promise<void> {
  await page.goto("/", { waitUntil: "domcontentloaded" }).catch(() => {});
  // already signed in?
  if (await page.getByText(/sign out/i).first().isVisible().catch(() => false)) return;

  for (let attempt = 0; attempt < 3; attempt++) {
    await page.goto("/auth/signIn", { waitUntil: "domcontentloaded", timeout: 30_000 }).catch(() => {});
    // bounced back to / if already signed
    if (await page.getByText(/sign out/i).first().isVisible().catch(() => false)) return;
    const btn = page.getByRole("button", { name: /sign in|login/i }).first();
    if (await btn.isVisible().catch(() => false)) await btn.click().catch(() => {});

    // Race: KC form or SSO bounce-back.
    try {
      await page.waitForURL(`${KC}/realms/**`, { timeout: 8_000 });
      await page.fill("#username", username);
      await page.fill("#password", password);
      await page.click('[type="submit"]');
    } catch {
      // already SSO'd
    }

    // Generous wait for SIGN OUT — up to 30s for the post-submit redirect chain.
    const ok = await page.waitForSelector("text=SIGN OUT", { timeout: 30_000 }).then(() => true).catch(() => false);
    if (ok) return;
    if (page.url().includes("/auth/signIn")) {
      await page.goto("/", { waitUntil: "domcontentloaded" }).catch(() => {});
    }
  }
  throw new Error("loginLocal failed after 3 retries");
}

const USERNAME = process.env.KC_USERNAME || "flo";
const PASSWORD = process.env.KC_PASSWORD || "flo-demo";
const SHOT_DIR = "/opt/shepard/aidocs/agent-findings/screenshots-ux-survey-2026-05-24";
const STORAGE_FILE = path.join(SHOT_DIR, "_storageState.json");

interface Timing {
  page: string;
  viewport: string;
  scale: string;
  domContentLoadedMs: number;
  visualMs?: number;
  loadEventMs?: number;
  errorCount: number;
  notes: string[];
}

const allTimings: Timing[] = [];

async function shoot(page: Page, name: string) {
  const target = path.join(SHOT_DIR, `${name}.png`);
  await page.screenshot({ path: target, fullPage: true }).catch((e) => {
    console.log(`shot fail ${name}: ${e.message}`);
  });
  return target;
}

async function timed(
  page: Page,
  label: { page: string; viewport: string; scale: string },
  gotoUrl: string,
  visualSelector?: string,
  visualTimeoutMs = 25_000,
): Promise<Timing> {
  const consoleErrors: string[] = [];
  const pageErrors: string[] = [];
  const onConsole = (msg: any) => {
    if (msg.type() === "error") consoleErrors.push(msg.text().slice(0, 200));
  };
  const onPageErr = (err: any) => pageErrors.push(err.message.slice(0, 200));
  page.on("console", onConsole);
  page.on("pageerror", onPageErr);

  const t0 = Date.now();
  await page.goto(gotoUrl, { waitUntil: "domcontentloaded", timeout: 60_000 }).catch(() => {});
  const dcl = Date.now() - t0;
  let visualMs: number | undefined;
  if (visualSelector) {
    await page.waitForSelector(visualSelector, { timeout: visualTimeoutMs }).catch(() => {});
    visualMs = Date.now() - t0;
  }
  await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
  const loadEventMs = Date.now() - t0;

  page.off("console", onConsole);
  page.off("pageerror", onPageErr);

  const timing: Timing = {
    page: label.page,
    viewport: label.viewport,
    scale: label.scale,
    domContentLoadedMs: dcl,
    visualMs,
    loadEventMs,
    errorCount: consoleErrors.length + pageErrors.length,
    notes: [...consoleErrors.slice(0, 6), ...pageErrors.slice(0, 4)],
  };
  allTimings.push(timing);
  console.log(`[${label.viewport}/${label.page}/${label.scale}] dcl=${dcl}ms visual=${visualMs}ms idle=${loadEventMs}ms errs=${timing.errorCount}`);
  return timing;
}

async function newViewportPage(browser: any, vp: { width: number; height: number }, useStorage = true) {
  const ctxOpts: any = { viewport: vp };
  if (useStorage && fs.existsSync(STORAGE_FILE)) ctxOpts.storageState = STORAGE_FILE;
  const ctx = await browser.newContext(ctxOpts);
  const page = await ctx.newPage();
  return { ctx, page };
}

test.describe.configure({ mode: "serial" });
test.setTimeout(180_000);

test.beforeAll(() => {
  fs.mkdirSync(SHOT_DIR, { recursive: true });
});

test.afterAll(() => {
  fs.writeFileSync(
    path.join(SHOT_DIR, "_timings.json"),
    JSON.stringify(allTimings, null, 2),
  );
});

test("00 — prime auth (login once, save storage)", async ({ browser }) => {
  const ctx = await browser.newContext({ viewport: { width: 1920, height: 1080 } });
  const page = await ctx.newPage();
  await loginLocal(page, USERNAME, PASSWORD);
  await ctx.storageState({ path: STORAGE_FILE });
  await ctx.close();
});

const VIEWPORTS = [
  { name: "4k", width: 3840, height: 2160 },
  { name: "fhd", width: 1920, height: 1080 },
] as const;

for (const vp of VIEWPORTS) {
  test(`${vp.name} — collections index (empty-ish/list)`, async ({ browser }) => {
    const { ctx, page } = await newViewportPage(browser, vp);
    await timed(page, { page: "collections-index", viewport: vp.name, scale: "list" }, "/collections", "h1");
    await shoot(page, `collections-index-${vp.name}`);
    // Count visible items, check pagination, sort
    const rowCount = await page.locator("[data-testid='collection-list-row'], table tbody tr").count().catch(() => 0);
    console.log(`  visible rows: ${rowCount}`);
    await ctx.close();
  });

  test(`${vp.name} — collection LUMEN (id=42, ~15 DOs)`, async ({ browser }) => {
    const { ctx, page } = await newViewportPage(browser, vp);
    await timed(page, { page: "collection-detail", viewport: vp.name, scale: "LUMEN-15" }, "/collections/42", "h2, .text-h5", 40_000);
    await shoot(page, `collection-LUMEN-${vp.name}-above-fold`);
    // Scroll into Data Objects panel
    const dop = page.locator("text=Data Objects").first();
    if (await dop.isVisible().catch(() => false)) {
      await dop.scrollIntoViewIfNeeded().catch(() => {});
      await page.waitForTimeout(800);
      await shoot(page, `collection-LUMEN-${vp.name}-dops`);
    }
    // Try expanding Lineage panel
    const lineage = page.locator("text=Dataset Lineage").first();
    if (await lineage.isVisible().catch(() => false)) {
      await lineage.click().catch(() => {});
      await page.waitForTimeout(2500);
      await shoot(page, `collection-LUMEN-${vp.name}-lineage`);
    }
    await ctx.close();
  });

  test(`${vp.name} — collection MFFD-big (search → first match)`, async ({ browser }) => {
    const { ctx, page } = await newViewportPage(browser, vp);
    await timed(page, { page: "collections-search", viewport: vp.name, scale: "search-MFFD" }, "/collections", "h1");
    // Use search field to filter for MFFD
    const search = page.locator('input[placeholder*="earch" i]').first();
    if (await search.isVisible().catch(() => false)) {
      await search.fill("MFFD");
      await page.waitForTimeout(1500);
      await shoot(page, `collections-search-MFFD-${vp.name}`);
    }

    // MFFD-Dropbox is collection id 661923, ~8514 DOs (confirmed via UI search)
    const candidates = [661923];
    for (const cid of candidates) {
      const t = await timed(
        page,
        { page: "collection-detail", viewport: vp.name, scale: `MFFD-${cid}` },
        `/collections/${cid}`,
        "h2, .text-h5",
        60_000,
      );
      await shoot(page, `collection-MFFD-${cid}-${vp.name}-above-fold`);
      const dop = page.locator("text=Data Objects").first();
      if (await dop.isVisible().catch(() => false)) {
        await dop.scrollIntoViewIfNeeded().catch(() => {});
        await page.waitForTimeout(2000);
        await shoot(page, `collection-MFFD-${cid}-${vp.name}-dops`);
        // try clicking sort or paging
        const next = page.locator('button:has-text("Next")').first();
        if (await next.isVisible().catch(() => false) && !(await next.isDisabled().catch(() => true))) {
          await next.click().catch(() => {});
          await page.waitForTimeout(2000);
          await shoot(page, `collection-MFFD-${cid}-${vp.name}-dops-page2`);
        }
      }
      console.log(`  MFFD ${cid}: dcl=${t.domContentLoadedMs}ms idle=${t.loadEventMs}ms errs=${t.errorCount}`);
    }
    await ctx.close();
  });

  test(`${vp.name} — containers index`, async ({ browser }) => {
    const { ctx, page } = await newViewportPage(browser, vp);
    await timed(page, { page: "containers-index", viewport: vp.name, scale: "list" }, "/containers", "h1, h2");
    await shoot(page, `containers-index-${vp.name}`);
    await ctx.close();
  });

  test(`${vp.name} — container detail: files`, async ({ browser }) => {
    const { ctx, page } = await newViewportPage(browser, vp);
    // MFFD-Dropbox-wiki-export is a file container — find any file-container by name from /containers
    await timed(page, { page: "containers-index", viewport: vp.name, scale: "browse" }, "/containers", "table");
    await page.waitForTimeout(2500);
    // Click the row whose "Container Type" cell says "File"
    const row = page.locator('table tbody tr').filter({ hasText: /\bFile\b/ }).first();
    if (await row.isVisible().catch(() => false)) {
      const t0 = Date.now();
      await row.click().catch(() => {});
      await page.waitForURL(/\/containers\/files\//, { timeout: 30_000 }).catch(() => {});
      await page.waitForTimeout(3000);
      const tEnd = Date.now() - t0;
      allTimings.push({ page: "container-files", viewport: vp.name, scale: "via-row-click", domContentLoadedMs: tEnd, errorCount: 0, notes: [`url=${page.url()}`] });
      console.log(`  clicked file row, took ${tEnd}ms, url=${page.url()}`);
      await shoot(page, `container-files-${vp.name}`);
    } else {
      console.log("  no file row");
      await shoot(page, `container-files-${vp.name}-NO-ROW`);
    }
    await ctx.close();
  });

  test(`${vp.name} — container detail: timeseries`, async ({ browser }) => {
    const { ctx, page } = await newViewportPage(browser, vp);
    await page.goto("/containers", { waitUntil: "domcontentloaded", timeout: 60_000 }).catch(() => {});
    await page.waitForTimeout(2500);
    const row = page.locator('table tbody tr').filter({ hasText: /\bTimeseries\b/ }).first();
    if (await row.isVisible().catch(() => false)) {
      const t0 = Date.now();
      await row.click().catch(() => {});
      await page.waitForURL(/\/containers\/timeseries\//, { timeout: 30_000 }).catch(() => {});
      await page.waitForTimeout(4000);
      const tEnd = Date.now() - t0;
      allTimings.push({ page: "container-timeseries", viewport: vp.name, scale: "via-row-click", domContentLoadedMs: tEnd, errorCount: 0, notes: [`url=${page.url()}`] });
      console.log(`  TS row click took ${tEnd}ms, url=${page.url()}`);
      await shoot(page, `container-timeseries-${vp.name}`);
    } else {
      await shoot(page, `container-timeseries-${vp.name}-NO-ROW`);
    }
    await ctx.close();
  });

  test(`${vp.name} — container detail: structured-data`, async ({ browser }) => {
    const { ctx, page } = await newViewportPage(browser, vp);
    await page.goto("/containers", { waitUntil: "domcontentloaded", timeout: 60_000 }).catch(() => {});
    await page.waitForTimeout(2500);
    const row = page.locator('table tbody tr').filter({ hasText: /Structured data/i }).first();
    if (await row.isVisible().catch(() => false)) {
      await row.click().catch(() => {});
      await page.waitForURL(/\/containers\/structureddata\//, { timeout: 30_000 }).catch(() => {});
      await page.waitForTimeout(3000);
      allTimings.push({ page: "container-sd", viewport: vp.name, scale: "via-row-click", domContentLoadedMs: 0, errorCount: 0, notes: [`url=${page.url()}`] });
      await shoot(page, `container-sd-${vp.name}`);
    } else {
      await shoot(page, `container-sd-${vp.name}-NO-ROW`);
    }
    await ctx.close();
  });

  test(`${vp.name} — container detail: spatial (placeholder?)`, async ({ browser }) => {
    const { ctx, page } = await newViewportPage(browser, vp);
    await page.goto("/containers", { waitUntil: "domcontentloaded", timeout: 60_000 }).catch(() => {});
    await page.waitForTimeout(2500);
    const row = page.locator('table tbody tr').filter({ hasText: /Spatial/i }).first();
    if (await row.isVisible().catch(() => false)) {
      await row.click().catch(() => {});
      await page.waitForURL(/\/containers\/spatialdata\//, { timeout: 30_000 }).catch(() => {});
      await page.waitForTimeout(2500);
      await shoot(page, `container-spatial-${vp.name}`);
    } else {
      // No spatial container exists — visit URL directly with bogus id to expose empty state
      await timed(page, { page: "container-spatial", viewport: vp.name, scale: "bogus" }, "/containers/spatialdata/1", "body");
      await shoot(page, `container-spatial-bogus-${vp.name}`);
    }
    await ctx.close();
  });

  test(`${vp.name} — error page: bogus collection id`, async ({ browser }) => {
    const { ctx, page } = await newViewportPage(browser, vp);
    await timed(page, { page: "error-bogus-collection", viewport: vp.name, scale: "n/a" }, "/collections/99999999", "body");
    await page.waitForTimeout(2500);
    await shoot(page, `collection-bogus-${vp.name}`);
    await ctx.close();
  });
}
