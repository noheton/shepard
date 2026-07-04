/**
 * UX walk 2026-06-12 — shapes-for-displays + general usability audit.
 * Live instance, 4K viewport (3840×2160), auth flo/flo-demo.
 *
 * Usage: node scripts/ux-walk-2026-06-12.mjs [phase1|phase2|...]
 * Screenshots → /opt/shepard/aidocs/agent-findings/screenshots/ux-2026-06-12/
 */
import { chromium } from "playwright";
import fs from "node:fs";

const BASE = "https://shepard.nuclide.systems";
const KC = "https://shepard-auth.nuclide.systems";
const SHOTS = "/opt/shepard/aidocs/agent-findings/screenshots/ux-2026-06-12";
const STATE = "/tmp/ux-walk-2026-06-12-state.json";

const LUMEN = "019eb019-d49b-7131-b2d2-3f3107d36a4f";
const TR004 = "019eb019-d8e9-7991-96e7-75ca1ab3d6be";
const TR004_TSREF = "019eb01a-4bba-74e9-8a16-7562c1994bca";
const TS_CONTAINER_ID = "364364";
const TS_CONTAINER_APPID = "019eb019-e072-7f29-8f7d-6f7621547ea0";
const COUPON_COLL = "019eb02b-0ff0-77d0-adee-a572e5d8f2b7";
const COUPON_DO = "019eb02b-10b1-78d8-89fd-140edb6da79a";
const RENDERMEDIA_COLL = "019eb028-35d3-7eb0-8f5a-e331fe46688d";
const RENDERMEDIA_DO = "019eb028-3657-7d3c-b79d-3e8e853d887f";

const consoleErrors = [];
const log = (...a) => console.log("[walk]", ...a);

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

async function shot(page, name) {
  await page.waitForTimeout(400);
  await page.screenshot({ path: `${SHOTS}/${name}.png`, fullPage: false });
  log("shot", name, "→", page.url());
}

async function dumpButtons(page, label) {
  const btns = await page.locator("button:visible, a.v-btn:visible").allInnerTexts();
  log(`[${label}] visible buttons:`, JSON.stringify(btns.map(b => b.trim()).filter(Boolean)));
}

const phases = {
  // ── Phase 1: core navigation ────────────────────────────────────────────
  async phase1(page) {
    await page.goto(BASE + "/", { waitUntil: "networkidle" }).catch(() => {});
    await shot(page, "01-landing-4k");
    await page.goto(BASE + "/collections", { waitUntil: "networkidle" }).catch(() => {});
    await shot(page, "02-collections-list-4k");
    await page.goto(`${BASE}/collections/${LUMEN}`, { waitUntil: "networkidle" }).catch(() => {});
    await page.waitForTimeout(2500);
    await shot(page, "03-lumen-collection-4k");
    await page.goto(`${BASE}/collections/${LUMEN}/dataobjects/${TR004}`, { waitUntil: "networkidle" }).catch(() => {});
    await page.waitForTimeout(3000);
    await shot(page, "04-tr004-do-detail-4k");
    await dumpButtons(page, "tr004");
    // scroll down to see references panels
    await page.mouse.wheel(0, 1600);
    await page.waitForTimeout(800);
    await shot(page, "05-tr004-do-detail-scrolled-4k");
  },

  // ── Phase 2: tools menu on DO + shapes flow entry ───────────────────────
  async phase2(page) {
    await page.goto(`${BASE}/collections/${LUMEN}/dataobjects/${TR004}`, { waitUntil: "networkidle" }).catch(() => {});
    await page.waitForTimeout(2500);
    // find Tools menu trigger
    const tools = page.getByRole("button", { name: /tools/i }).first();
    if (await tools.isVisible().catch(() => false)) {
      await tools.click();
      await page.waitForTimeout(600);
      await shot(page, "06-tr004-tools-menu-4k");
      const items = await page.locator(".v-list-item:visible").allInnerTexts();
      log("tools menu items:", JSON.stringify(items.map(t => t.replace(/\s+/g, " ").trim())));
      await page.keyboard.press("Escape");
    } else {
      log("NO Tools button on TR-004 detail page");
      await shot(page, "06-tr004-no-tools-btn-4k");
    }
    // coupon-valid DO (has attached template) — check Tools menu gating
    await page.goto(`${BASE}/collections/${COUPON_COLL}/dataobjects/${COUPON_DO}`, { waitUntil: "networkidle" }).catch(() => {});
    await page.waitForTimeout(2500);
    const tools2 = page.getByRole("button", { name: /tools/i }).first();
    if (await tools2.isVisible().catch(() => false)) {
      await tools2.click();
      await page.waitForTimeout(600);
      await shot(page, "07-coupon-tools-menu-4k");
      const items = await page.locator(".v-list-item:visible").allInnerTexts();
      log("coupon tools menu items:", JSON.stringify(items.map(t => t.replace(/\s+/g, " ").trim())));
      await page.keyboard.press("Escape");
    } else {
      log("NO Tools button on coupon-valid DO");
      await shot(page, "07-coupon-no-tools-btn-4k");
    }
  },

  // ── Phase 3: TS reference detail → Visualize in 3D dialog ──────────────
  async phase3(page) {
    const url = `${BASE}/collections/${LUMEN}/dataobjects/${TR004}/timeseriesereferences/${TR004_TSREF}`;
    await page.goto(url, { waitUntil: "networkidle" }).catch(() => {});
    await page.waitForTimeout(3500);
    await shot(page, "08-tr004-tsref-detail-4k");
    await dumpButtons(page, "tsref");
    const viz = page.getByRole("button", { name: /visualize|3d/i }).first();
    if (await viz.isVisible().catch(() => false)) {
      await viz.click();
      await page.waitForTimeout(1200);
      await shot(page, "09-visualize-dialog-4k");
      const dialogText = await page.locator(".v-dialog:visible").innerText().catch(() => "");
      log("dialog text:", JSON.stringify(dialogText.slice(0, 1500)));
      // try opening trace3d via the dialog's primary button
      const open = page.locator(".v-dialog:visible").getByRole("button", { name: /open|render|trace/i }).first();
      if (await open.isVisible().catch(() => false)) {
        const enabled = await open.isEnabled().catch(() => false);
        log("dialog open btn enabled:", enabled);
        if (enabled) {
          await open.click();
          await page.waitForTimeout(4000);
          await shot(page, "10-shapes-render-from-ref-4k");
          log("after open url:", page.url());
          await page.mouse.wheel(0, 1200);
          await page.waitForTimeout(500);
          await shot(page, "10b-shapes-render-from-ref-scrolled-4k");
        }
      }
    } else {
      log("NO visualize button on TS ref page");
    }
  },

  // ── Phase 4: bare /shapes/render + /tools + templates admin ─────────────
  async phase4(page) {
    await page.goto(BASE + "/shapes/render", { waitUntil: "networkidle" }).catch(() => {});
    await page.waitForTimeout(1500);
    await shot(page, "11-shapes-render-bare-4k");
    await page.goto(BASE + "/tools", { waitUntil: "networkidle" }).catch(() => {});
    await page.waitForTimeout(1200);
    await shot(page, "12-tools-landing-4k");
    const toolTiles = await page.locator(".v-card:visible .v-card-title, .v-list-item-title:visible").allInnerTexts().catch(() => []);
    log("tools landing entries:", JSON.stringify(toolTiles.map(t => t.trim()).filter(Boolean).slice(0, 40)));
    await page.goto(BASE + "/admin/templates", { waitUntil: "networkidle" }).catch(() => {});
    await page.waitForTimeout(1200);
    await shot(page, "13-admin-templates-route-4k");
    log("admin/templates landed on:", page.url());
    const notFound = await page.getByText(/not found|404/i).first().isVisible().catch(() => false);
    log("admin/templates shows not-found?", notFound);
    await page.goto(BASE + "/admin", { waitUntil: "networkidle" }).catch(() => {});
    await page.waitForTimeout(1500);
    await shot(page, "14-admin-hub-4k");
  },

  // ── Phase 5: TS container + chart ───────────────────────────────────────
  async phase5(page) {
    await page.goto(`${BASE}/containers/timeseries/${TS_CONTAINER_ID}`, { waitUntil: "networkidle" }).catch(() => {});
    await page.waitForTimeout(4000);
    await shot(page, "15-ts-container-4k");
    await dumpButtons(page, "ts-container");
    await page.mouse.wheel(0, 1800);
    await page.waitForTimeout(800);
    await shot(page, "15b-ts-container-scrolled-4k");
  },

  // ── Phase 6: render-media showcase (the dedicated trace3d demo) ─────────
  async phase6(page) {
    await page.goto(`${BASE}/collections/${RENDERMEDIA_COLL}/dataobjects/${RENDERMEDIA_DO}`, { waitUntil: "networkidle" }).catch(() => {});
    await page.waitForTimeout(2500);
    await shot(page, "16-rendermedia-do-4k");
    await dumpButtons(page, "rendermedia-do");
  },
};

(async () => {
  const which = process.argv.slice(2);
  const browser = await chromium.launch();
  const ctx = await browser.newContext({
    viewport: { width: 3840, height: 2160 },
    storageState: fs.existsSync(STATE) ? STATE : undefined,
  });
  const page = await ctx.newPage();
  page.on("console", m => { if (m.type() === "error") consoleErrors.push(`${page.url()} :: ${m.text().slice(0, 300)}`); });
  page.on("response", r => { if (r.status() >= 400 && !r.url().includes("_nuxt")) log("HTTP", r.status(), r.request().method(), r.url().slice(0, 160)); });

  await login(page);
  await ctx.storageState({ path: STATE });
  log("logged in");

  for (const name of (which.length ? which : Object.keys(phases))) {
    log("=== ", name, " ===");
    try { await phases[name](page); } catch (e) { log("PHASE FAIL", name, e.message); }
  }

  log("console errors:", JSON.stringify(consoleErrors.slice(0, 30), null, 1));
  await browser.close();
})();
