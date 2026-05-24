/**
 * UI Scrutinizer Part 2 — DO detail + container views (phases 3-6).
 * The part-1 spec couldn't get DO/container IDs via page.request (wrong API host).
 * This time we mine IDs by scraping the rendered sidebar links / clicking through.
 */
import { test, expect, type Page, type ConsoleMessage, type Request, type Response } from "@playwright/test";
import * as fs from "fs";
import * as path from "path";

const KC = process.env.KEYCLOAK_HOST || "https://shepard-auth.nuclide.systems";
const REALM = "shepard-demo";
const OUT = "/opt/shepard/aidocs/agent-findings/ui-scrutinizer-2026-05-24-evidence";
const LUMEN_COLL = 42;
const MFFD_COLL = 661923;

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
    if (s >= 400) netErrors.push({ url: resp.url(), status: s, statusText: resp.statusText() });
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
  const settle = opts.settleMs ?? 3000;
  await page.setViewportSize({ width: viewport.w, height: viewport.h });
  const { errors, warnings, netErrors, netFailed } = attachListeners(page);
  const t0 = Date.now();
  let finalUrl = url;
  let title: string | undefined;
  try {
    await page.goto(url, { waitUntil: "domcontentloaded", timeout: 30_000 });
    await page.waitForTimeout(settle);
    finalUrl = page.url();
    title = await page.title().catch(() => undefined);
  } catch (e) {
    errors.push(`NAV_FAIL: ${(e as Error).message}`);
  }
  const duration = Date.now() - t0;
  try {
    await page.screenshot({
      path: path.join(OUT, `${slug}-${viewport.w}.png`),
      fullPage: true,
      timeout: 20_000,
    });
  } catch (e) {
    errors.push(`SCREENSHOT_FAIL: ${(e as Error).message}`);
  }
  if (viewport.w >= 3840) {
    try {
      await page.screenshot({
        path: path.join(OUT, `${slug}-${viewport.w}-viewport.png`),
        fullPage: false,
        timeout: 20_000,
      });
    } catch {}
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

test("ui-scrutinizer part2 — DO/container/forms", async ({ browser }) => {
  test.setTimeout(40 * 60_000);

  const ctx = await browser.newContext({
    viewport: { width: 1920, height: 1080 },
    ignoreHTTPSErrors: true,
  });
  const page = await ctx.newPage();
  await loginAs(page, "alice", "alice-demo");

  const VP_HD = { w: 1920, h: 1080 };
  const VP_4K = { w: 3840, h: 2160 };

  // ------------------------------------------------------------------
  // Mine an MFFD DO id by intercepting the API call from the live page
  // ------------------------------------------------------------------
  const mffdDoIds: string[] = [];
  page.on("response", async (r) => {
    const u = r.url();
    if (u.match(/dataObjects/) && r.status() === 200) {
      try {
        const j = await r.json();
        if (Array.isArray(j)) {
          for (const o of j) {
            if (o && (o.id || o.appId)) {
              mffdDoIds.push(`${o.id || ""}|${o.appId || ""}|${o.name || ""}`);
            }
          }
        }
      } catch {}
    }
  });

  await page.goto(`/collections/${MFFD_COLL}`);
  await page.waitForTimeout(7000);
  // also expand the sidebar a bit
  try {
    const buttons = page.locator("button[aria-expanded='false']");
    const n = Math.min(await buttons.count(), 5);
    for (let i = 0; i < n; i++) {
      try {
        await buttons.nth(i).click({ timeout: 1500 });
        await page.waitForTimeout(500);
      } catch {}
    }
  } catch {}
  await page.waitForTimeout(2500);
  fs.writeFileSync(path.join(OUT, "mffd-do-ids-observed.json"),
    JSON.stringify(Array.from(new Set(mffdDoIds)).slice(0, 50), null, 2));

  // Also try scraping link hrefs that look like DO IDs
  const mffdLinks = await page.$$eval('a[href*="/dataobjects/"]', (as) =>
    as.map((a) => (a as HTMLAnchorElement).href).slice(0, 30),
  );
  fs.writeFileSync(path.join(OUT, "mffd-do-links.json"), JSON.stringify(mffdLinks, null, 2));

  // Pick one
  let mffdDoNumericId: string | undefined;
  for (const link of mffdLinks) {
    const m = link.match(/dataobjects\/(\d+)/);
    if (m) { mffdDoNumericId = m[1]; break; }
  }

  // Do the same for LUMEN
  const lumenDoIds: string[] = [];
  const lumenLis = page.on("response", async (r) => {
    const u = r.url();
    if (u.match(/collections\/[^/]+\/dataObjects/) && r.status() === 200) {
      try {
        const j = await r.json();
        if (Array.isArray(j)) {
          for (const o of j) {
            if (o && (o.id || o.appId)) {
              lumenDoIds.push(`${o.id || ""}|${o.appId || ""}|${o.name || ""}`);
            }
          }
        }
      } catch {}
    }
  });

  await page.goto(`/collections/${LUMEN_COLL}`);
  await page.waitForTimeout(7000);
  try {
    const buttons = page.locator("button[aria-expanded='false']");
    const n = Math.min(await buttons.count(), 8);
    for (let i = 0; i < n; i++) {
      try {
        await buttons.nth(i).click({ timeout: 1500 });
        await page.waitForTimeout(400);
      } catch {}
    }
  } catch {}
  await page.waitForTimeout(2000);
  const lumenLinks = await page.$$eval('a[href*="/dataobjects/"]', (as) =>
    as.map((a) => (a as HTMLAnchorElement).href).slice(0, 30),
  );
  fs.writeFileSync(path.join(OUT, "lumen-do-links.json"), JSON.stringify(lumenLinks, null, 2));

  let lumenDoNumericId: string | undefined;
  for (const link of lumenLinks) {
    const m = link.match(/dataobjects\/(\d+)/);
    if (m) { lumenDoNumericId = m[1]; break; }
  }

  fs.writeFileSync(path.join(OUT, "discovered-ids.json"), JSON.stringify({
    lumenDoNumericId,
    mffdDoNumericId,
  }, null, 2));

  // ------------------------------------------------------------------
  // Phase 3: DataObject detail
  // ------------------------------------------------------------------
  if (lumenDoNumericId) {
    await visit(page, `/collections/${LUMEN_COLL}/dataobjects/${lumenDoNumericId}`,
      "p3-01-lumen-do-detail", VP_HD,
      { settleMs: 6000, notes: `LUMEN DO ${lumenDoNumericId}` });
    await visit(page, `/collections/${LUMEN_COLL}/dataobjects/${lumenDoNumericId}`,
      "p3-01-lumen-do-detail", VP_4K,
      { settleMs: 6000 });
  }
  if (mffdDoNumericId) {
    await visit(page, `/collections/${MFFD_COLL}/dataobjects/${mffdDoNumericId}`,
      "p3-02-mffd-do-detail", VP_HD,
      { settleMs: 6000, notes: `MFFD DO ${mffdDoNumericId} — BUG #139 candidate` });
    await visit(page, `/collections/${MFFD_COLL}/dataobjects/${mffdDoNumericId}`,
      "p3-02-mffd-do-detail", VP_4K,
      { settleMs: 6000 });
  }

  // ------------------------------------------------------------------
  // Phase 4: Container views — discover containers via API host
  // ------------------------------------------------------------------
  let tsId: string | number | undefined;
  let fileId: string | number | undefined;
  let sdId: string | number | undefined;
  try {
    const r = await page.request.get("https://shepard-api.nuclide.systems/shepard/api/timeseriesContainers?page=0&size=5");
    if (r.ok()) {
      const arr = await r.json();
      if (Array.isArray(arr) && arr.length > 0) tsId = arr[0].id || arr[0].oid;
      fs.writeFileSync(path.join(OUT, "ts-containers.json"), JSON.stringify(arr, null, 2));
    } else {
      fs.writeFileSync(path.join(OUT, "ts-containers.json"), JSON.stringify({ status: r.status() }));
    }
  } catch (e) {
    fs.writeFileSync(path.join(OUT, "ts-containers-err.json"), JSON.stringify({ err: (e as Error).message }));
  }
  try {
    const r = await page.request.get("https://shepard-api.nuclide.systems/shepard/api/fileContainers?page=0&size=5");
    if (r.ok()) {
      const arr = await r.json();
      if (Array.isArray(arr) && arr.length > 0) fileId = arr[0].id || arr[0].oid;
      fs.writeFileSync(path.join(OUT, "file-containers.json"), JSON.stringify(arr, null, 2));
    } else {
      fs.writeFileSync(path.join(OUT, "file-containers.json"), JSON.stringify({ status: r.status() }));
    }
  } catch (e) {
    fs.writeFileSync(path.join(OUT, "file-containers-err.json"), JSON.stringify({ err: (e as Error).message }));
  }
  try {
    const r = await page.request.get("https://shepard-api.nuclide.systems/shepard/api/structuredDataContainers?page=0&size=5");
    if (r.ok()) {
      const arr = await r.json();
      if (Array.isArray(arr) && arr.length > 0) sdId = arr[0].id || arr[0].oid;
      fs.writeFileSync(path.join(OUT, "sd-containers.json"), JSON.stringify(arr, null, 2));
    } else {
      fs.writeFileSync(path.join(OUT, "sd-containers.json"), JSON.stringify({ status: r.status() }));
    }
  } catch (e) {
    fs.writeFileSync(path.join(OUT, "sd-containers-err.json"), JSON.stringify({ err: (e as Error).message }));
  }
  fs.writeFileSync(path.join(OUT, "container-ids-found.json"),
    JSON.stringify({ tsId, fileId, sdId }, null, 2));

  if (tsId) {
    await visit(page, `/containers/timeseries/${tsId}`, "p4-01-ts-container", VP_HD,
      { settleMs: 6000, notes: `TS container ${tsId}` });
  }
  if (fileId) {
    await visit(page, `/containers/files/${fileId}`, "p4-02-file-container", VP_HD,
      { settleMs: 6000, notes: `File container ${fileId}` });
  }
  if (sdId) {
    await visit(page, `/containers/structureddata/${sdId}`, "p4-03-sd-container", VP_HD,
      { settleMs: 6000, notes: `SD container ${sdId}` });
  }

  // ------------------------------------------------------------------
  // Phase 5 part 2 — explore the LUMEN DO panels in detail
  // ------------------------------------------------------------------
  if (lumenDoNumericId) {
    await page.setViewportSize({ width: 1920, height: 1080 });
    await page.goto(`/collections/${LUMEN_COLL}/dataobjects/${lumenDoNumericId}`);
    await page.waitForTimeout(6000);
    await page.screenshot({ path: path.join(OUT, "p5-04-lumen-do-full-1920.png"), fullPage: true });

    // try opening tabs / accordions if present
    const tabs = page.locator('[role="tab"]');
    const ntabs = await tabs.count();
    for (let i = 0; i < Math.min(ntabs, 6); i++) {
      try {
        await tabs.nth(i).click({ timeout: 2000 });
        await page.waitForTimeout(1500);
        await page.screenshot({
          path: path.join(OUT, `p5-05-lumen-do-tab-${i}.png`),
          fullPage: true,
        });
      } catch {}
    }
  }

  // ------------------------------------------------------------------
  // Phase 6 — Create-from-template / Add-DO modal (open + close, no submit)
  // ------------------------------------------------------------------
  await page.goto(`/collections/${LUMEN_COLL}`);
  await page.waitForTimeout(4000);
  // try FAB / button
  const candidates = [
    'button[aria-label*="add" i]',
    'button[aria-label*="create" i]',
    'button[aria-label*="new" i]',
    'button:has-text("Add")',
    'button:has-text("Create")',
    'button:has-text("New")',
    'button:has(i.mdi-plus)',
    '.v-fab button',
  ];
  for (const sel of candidates) {
    try {
      const btn = page.locator(sel).first();
      if (await btn.count() > 0 && await btn.isVisible({ timeout: 1000 })) {
        await btn.click({ timeout: 3000 });
        await page.waitForTimeout(1500);
        await page.screenshot({
          path: path.join(OUT, `p6-01-create-dialog-${sel.replace(/[^a-z0-9]/gi, "_")}.png`),
          fullPage: false,
        });
        await page.keyboard.press("Escape");
        await page.waitForTimeout(500);
        break;
      }
    } catch {}
  }

  // Admin (alice is NOT admin; document the redirect explicitly)
  await visit(page, "/admin", "p6-02-admin-as-alice", VP_HD,
    { notes: "alice is non-admin; should see UNAUTHORIZED, but currently silently bounces to /me" });

  // Logout as alice; login as admin
  await page.goto("/");
  await page.waitForTimeout(1500);
  try {
    await page.getByText("SIGN OUT").click({ timeout: 5000 });
    await page.waitForTimeout(2500);
  } catch {}
  try {
    await loginAs(page, "admin", "admin-demo");
  } catch (e) {
    fs.writeFileSync(path.join(OUT, "admin-login-err.json"), JSON.stringify({ err: (e as Error).message }));
  }

  // Admin pages as admin
  await visit(page, "/admin", "p6-03-admin-as-admin", VP_HD,
    { settleMs: 4000, notes: "Admin landing as admin user" });
  await visit(page, "/configuration", "p6-04-configuration-as-admin", VP_HD,
    { settleMs: 4000, notes: "Configuration page as admin user" });

  // ------------------------------------------------------------------
  // Phase 5: Lineage / provenance graphs at LUMEN scale
  // ------------------------------------------------------------------
  if (lumenDoNumericId) {
    await visit(page, `/collections/${LUMEN_COLL}/dataobjects/${lumenDoNumericId}`,
      "p5-06-lumen-do-as-admin", VP_HD, { settleMs: 6000 });
  }

  expect(true).toBe(true);
});
