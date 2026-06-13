/**
 * UI 1920×1080 pass — 2026-06-13.
 * Live instance, viewport 1920×1080 deviceScaleFactor 1, auth flo/flo-demo.
 * Measures per-page overflow (horizontal scrollbar / clipped content), fold
 * problems (critical actions below 1080), dialog sizing, table fit, sidebar+main
 * width math. Emits a JSON metrics blob + full-page AND viewport-clip screenshots.
 *
 * Usage: node scripts/ui-1920-walk-2026-06-13.mjs [phaseName ...]
 * Screenshots → aidocs/agent-findings/screenshots/ui-1920-2026-06-13/
 * Metrics     → /tmp/ui-1920-metrics.json
 */
import { chromium } from "playwright";
import fs from "node:fs";

const BASE = "https://shepard.nuclide.systems";
const KC = "https://shepard-auth.nuclide.systems";
const SHOTS = "/opt/shepard/.claude/worktrees/agent-ae46d59e0d095c2a3/aidocs/agent-findings/screenshots/ui-1920-2026-06-13";
const STATE = "/tmp/ux-walk-2026-06-12-state.json";
const METRICS = "/tmp/ui-1920-metrics.json";
const VW = 1920, VH = 1080;

// Seeded entities (from prior walk + BT-KVS from prompt).
const LUMEN = "019eb019-d49b-7131-b2d2-3f3107d36a4f";
const TR004 = "019eb019-d8e9-7991-96e7-75ca1ab3d6be";
const TR004_TSREF = "019eb01a-4bba-74e9-8a16-7562c1994bca";
const TS_CONTAINER_ID = "364364";
const BTKVS = "019ebcec-37d2-7728-83a5-2ace13a89045";
const COUPON_COLL = "019eb02b-0ff0-77d0-adee-a572e5d8f2b7";
const COUPON_DO = "019eb02b-10b1-78d8-89fd-140edb6da79a";

const metrics = [];
const consoleErrors = [];
const log = (...a) => console.log("[1920]", ...a);

async function login(page) {
  await page.goto(BASE + "/", { waitUntil: "domcontentloaded" }).catch(() => {});
  if (await page.getByText(/sign out/i).first().isVisible().catch(() => false)) return;
  for (let i = 0; i < 3; i++) {
    await page.goto(BASE + "/auth/signIn", { waitUntil: "domcontentloaded" });
    if (await page.getByText(/sign out/i).first().isVisible().catch(() => false)) return;
    const btn = page.getByRole("button", { name: /sign in|login/i }).first();
    if (await btn.isVisible().catch(() => false)) await btn.click().catch(() => {});
    try {
      await page.waitForURL(`${KC}/realms/shepard-demo/**`, { timeout: 6000 });
      await page.fill("#username", "flo");
      await page.fill("#password", "flo-demo");
      await page.click('[type="submit"]');
    } catch { /* SSO hot */ }
    const ok = await page.waitForSelector("text=SIGN OUT", { timeout: 30000 }).then(() => true).catch(() => false);
    if (ok) return;
    await page.waitForTimeout(1500);
  }
  throw new Error("login failed");
}

/** Measure overflow + layout facts at 1920×1080. */
async function measure(page, name) {
  await page.waitForTimeout(400);
  const m = await page.evaluate(({ vw, vh }) => {
    const de = document.documentElement;
    const body = document.body;
    const scrollW = Math.max(de.scrollWidth, body.scrollWidth);
    const clientW = de.clientWidth;
    const horizOverflow = scrollW - clientW;
    // find elements wider than viewport (the overflow culprits)
    const culprits = [];
    const all = document.querySelectorAll("body *");
    for (const el of all) {
      const r = el.getBoundingClientRect();
      if (r.width > vw + 2 && r.right > clientW + 2) {
        const cls = (el.className && el.className.toString) ? el.className.toString().slice(0, 60) : "";
        culprits.push(`${el.tagName.toLowerCase()}.${cls} w=${Math.round(r.width)} right=${Math.round(r.right)}`);
        if (culprits.length >= 6) break;
      }
    }
    // sidebar + main width math
    const nav = document.querySelector(".v-navigation-drawer");
    const main = document.querySelector(".v-main");
    const sidebarW = nav ? Math.round(nav.getBoundingClientRect().width) : null;
    const mainW = main ? Math.round(main.getBoundingClientRect().width) : null;
    // open dialog facts
    const dlg = document.querySelector(".v-overlay--active .v-card, .v-dialog--active .v-card, .v-overlay__content");
    let dialog = null;
    if (dlg) {
      const r = dlg.getBoundingClientRect();
      dialog = { w: Math.round(r.width), h: Math.round(r.height), top: Math.round(r.top), bottom: Math.round(r.bottom), overflowsBottom: r.bottom > vh, overflowsRight: r.right > vw };
    }
    // widest table
    let widestTable = null;
    document.querySelectorAll("table, .v-table__wrapper").forEach((t) => {
      const w = Math.round(t.scrollWidth);
      if (!widestTable || w > widestTable.w) widestTable = { w, overflows: w > t.clientWidth + 2 };
    });
    return { scrollW, clientW, horizOverflow, culprits, sidebarW, mainW, dialog, widestTable, docHeight: de.scrollHeight };
  }, { vw: VW, vh: VH }).catch(() => ({ error: "eval-failed" }));
  m.page = name;
  m.url = page.url();
  metrics.push(m);
  log(name, "overflow=" + (m.horizOverflow ?? "?") + "px", "sidebar=" + m.sidebarW, "main=" + m.mainW,
      m.dialog ? `dialog ${m.dialog.w}x${m.dialog.h} botOvf=${m.dialog.overflowsBottom}` : "",
      m.culprits && m.culprits.length ? "CULPRITS:" + JSON.stringify(m.culprits) : "");
  return m;
}

async function shot(page, name) {
  await measure(page, name);
  await page.screenshot({ path: `${SHOTS}/${name}-fold.png`, fullPage: false, clip: { x: 0, y: 0, width: VW, height: VH } }).catch(() => {});
  await page.screenshot({ path: `${SHOTS}/${name}-full.png`, fullPage: true }).catch(() => {});
  log("shot", name, "→", page.url());
}

const phases = {
  async home(page) {
    await page.goto(BASE + "/", { waitUntil: "networkidle" }).catch(() => {});
    await shot(page, "01-home");
  },
  async collections(page) {
    await page.goto(BASE + "/collections", { waitUntil: "networkidle" }).catch(() => {});
    await page.waitForTimeout(1200);
    await shot(page, "02-collections-list");
  },
  async lumen(page) {
    await page.goto(`${BASE}/collections/${LUMEN}`, { waitUntil: "networkidle" }).catch(() => {});
    await page.waitForTimeout(2500);
    await shot(page, "03-lumen-collection");
  },
  async btkvs(page) {
    await page.goto(`${BASE}/collections/${BTKVS}`, { waitUntil: "networkidle" }).catch(() => {});
    await page.waitForTimeout(2500);
    await shot(page, "04-btkvs-collection");
  },
  async dataobject(page) {
    await page.goto(`${BASE}/collections/${LUMEN}/dataobjects/${TR004}`, { waitUntil: "networkidle" }).catch(() => {});
    await page.waitForTimeout(3000);
    await shot(page, "05-tr004-do-detail");
    // ActionMenuButton: "View as… / Record a…"
    const am = page.getByRole("button", { name: /view as|record a|actions|tools/i }).first();
    if (await am.isVisible().catch(() => false)) {
      await am.click().catch(() => {});
      await page.waitForTimeout(700);
      await shot(page, "06-tr004-actionmenu-open");
      await page.keyboard.press("Escape").catch(() => {});
    } else {
      log("no ActionMenu/Tools button on TR-004");
      await shot(page, "06-tr004-no-actionmenu");
    }
    // scroll to references panels
    await page.mouse.wheel(0, 1400);
    await page.waitForTimeout(800);
    await shot(page, "07-tr004-do-scrolled");
  },
  async annotationDialog(page) {
    await page.goto(`${BASE}/collections/${LUMEN}/dataobjects/${TR004}`, { waitUntil: "networkidle" }).catch(() => {});
    await page.waitForTimeout(2500);
    // find an Annotate/Add annotation affordance
    const ann = page.getByRole("button", { name: /annotat/i }).first();
    if (await ann.isVisible().catch(() => false)) {
      await ann.click().catch(() => {});
      await page.waitForTimeout(900);
      await shot(page, "08-annotation-dialog");
      await page.keyboard.press("Escape").catch(() => {});
    } else {
      // try scrolling to the annotations pane then its add button
      await page.mouse.wheel(0, 1800);
      await page.waitForTimeout(600);
      const ann2 = page.getByRole("button", { name: /annotat|add/i }).first();
      if (await ann2.isVisible().catch(() => false)) {
        await ann2.click().catch(() => {});
        await page.waitForTimeout(900);
        await shot(page, "08-annotation-dialog");
        await page.keyboard.press("Escape").catch(() => {});
      } else {
        log("no annotation button found");
        await shot(page, "08-annotation-not-found");
      }
    }
  },
  async globalSearch(page) {
    await page.goto(BASE + "/", { waitUntil: "networkidle" }).catch(() => {});
    await page.waitForTimeout(1000);
    const search = page.locator('input[type="text"], input[placeholder*="earch" i]').first();
    if (await search.isVisible().catch(() => false)) {
      await search.click().catch(() => {});
      await search.fill("TR").catch(() => {});
      await page.waitForTimeout(1500);
      await shot(page, "09-global-search-dropdown");
      await page.keyboard.press("Escape").catch(() => {});
    } else {
      log("no global search input found");
      await shot(page, "09-search-not-found");
    }
  },
  async tsContainer(page) {
    await page.goto(`${BASE}/containers/timeseries/${TS_CONTAINER_ID}`, { waitUntil: "networkidle" }).catch(() => {});
    await page.waitForTimeout(4000);
    await shot(page, "10-ts-container");
    await page.mouse.wheel(0, 1600);
    await page.waitForTimeout(800);
    await shot(page, "11-ts-container-scrolled");
  },
  async tools(page) {
    await page.goto(BASE + "/tools", { waitUntil: "networkidle" }).catch(() => {});
    await page.waitForTimeout(1200);
    await shot(page, "12-tools-landing");
    await page.goto(BASE + "/semantic/sparql", { waitUntil: "networkidle" }).catch(() => {});
    await page.waitForTimeout(1500);
    await shot(page, "13-tools-sparql");
    await page.goto(BASE + "/shapes/render", { waitUntil: "networkidle" }).catch(() => {});
    await page.waitForTimeout(1800);
    await shot(page, "14-tools-shapes-render");
    await page.goto(BASE + "/tools/form-preview", { waitUntil: "networkidle" }).catch(() => {});
    await page.waitForTimeout(1500);
    await shot(page, "15-tools-form-preview");
    await page.goto(BASE + "/scene-graphs", { waitUntil: "networkidle" }).catch(() => {});
    await page.waitForTimeout(1500);
    await shot(page, "16-tools-scene-graphs");
  },
  async admin(page) {
    await page.goto(BASE + "/admin", { waitUntil: "networkidle" }).catch(() => {});
    await page.waitForTimeout(1500);
    await shot(page, "17-admin-hub");
  },
  async me(page) {
    await page.goto(BASE + "/me", { waitUntil: "networkidle" }).catch(() => {});
    await page.waitForTimeout(1500);
    await shot(page, "18-me-profile");
  },
};

(async () => {
  const which = process.argv.slice(2);
  const browser = await chromium.launch();
  const ctx = await browser.newContext({
    viewport: { width: VW, height: VH },
    deviceScaleFactor: 1,
    storageState: fs.existsSync(STATE) ? STATE : undefined,
  });
  const page = await ctx.newPage();
  page.on("console", (m) => { if (m.type() === "error") consoleErrors.push(`${page.url()} :: ${m.text().slice(0, 200)}`); });
  page.on("response", (r) => { if (r.status() >= 400 && !r.url().includes("_nuxt")) log("HTTP", r.status(), r.request().method(), r.url().slice(0, 140)); });

  await login(page);
  await ctx.storageState({ path: STATE });
  log("logged in @", VW + "x" + VH);

  for (const name of (which.length ? which : Object.keys(phases))) {
    log("===", name, "===");
    try { await phases[name](page); } catch (e) { log("PHASE FAIL", name, e.message); }
  }

  fs.writeFileSync(METRICS, JSON.stringify({ viewport: { VW, VH }, metrics, consoleErrors: consoleErrors.slice(0, 30) }, null, 2));
  log("metrics written", METRICS);
  await browser.close();
})();
