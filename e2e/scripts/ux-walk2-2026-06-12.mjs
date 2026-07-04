import { chromium } from "playwright";
const STATE = "/tmp/ux-walk-2026-06-12-state.json";
const BASE = "https://shepard.nuclide.systems";
const SHOTS = "/opt/shepard/aidocs/agent-findings/screenshots/ux-2026-06-12";
const DEV = "lumen-testbench-p3-lampoldshausen-daq", LOC = "P3-Lampoldshausen", M = "hotfire";
const ch = (s) => ({ measurement: M, device: DEV, location: LOC, symbolicName: s, field: s });
const roles = { x: ch("acc_gimbal_x"), y: ch("acc_gimbal_y"), z: ch("thrust_kn"), value: ch("turbopump_vibration_rms_g") };
const browser = await chromium.launch();
const ctx = await browser.newContext({ viewport: { width: 3840, height: 2160 }, storageState: STATE });
const page = await ctx.newPage();
const errs = [];
page.on("console", m => { if (m.type() === "error") errs.push(m.text().slice(0, 200)); });
page.on("response", r => { if (r.status() >= 400 && !r.url().includes("_nuxt") && !r.url().includes("avatar")) console.log("HTTP", r.status(), r.request().method(), r.url().slice(0, 170)); });
const shot = async (n) => { await page.waitForTimeout(500); await page.screenshot({ path: `${SHOTS}/${n}.png` }); console.log("shot", n, page.url().slice(0, 140)); };

// A) Simulated dialog → render URL (what openTrace3D() builds)
const q = new URLSearchParams({
  containerId: "364364",
  startNs: "1721206800000000000",
  endNs: "1721206829990000000",
  colormap: "inferno",
  roles: Buffer.from(JSON.stringify(roles)).toString("base64"),
});
await page.goto(`${BASE}/shapes/render?${q}`, { waitUntil: "networkidle" }).catch(() => {});
await page.waitForTimeout(7000);
await shot("17-shapes-render-from-dialog-4k");
await page.mouse.wheel(0, 1000); await page.waitForTimeout(500);
await shot("17b-shapes-render-from-dialog-scrolled-4k");

// B) coupon-valid "Render view" in-context route (template attached = DATAOBJECT_RECIPE)
await page.goto(`${BASE}/shapes/render?focusShepardId=019eb02b-10b1-78d8-89fd-140edb6da79a&scope=data-object&templateAppId=019eb02b-1055-79d2-b880-7721b8057257`, { waitUntil: "networkidle" }).catch(() => {});
await page.waitForTimeout(5000);
await shot("18-shapes-render-coupon-dataobject-recipe-4k");

// C) bare playground: pick VIEW_RECIPE template via autocomplete + render-media DO
await page.goto(`${BASE}/shapes/render`, { waitUntil: "networkidle" }).catch(() => {});
await page.waitForTimeout(2000);
const tpl = page.getByLabel(/Template \(VIEW_RECIPE\)/i).first();
if (await tpl.isVisible().catch(() => false)) {
  await tpl.click(); await page.waitForTimeout(1200);
  const opts = await page.locator(".v-overlay .v-list-item:visible").allInnerTexts();
  console.log("template options:", JSON.stringify(opts.map(t => t.replace(/\s+/g," ").trim())));
  await shot("19-template-autocomplete-open-4k");
  const opt = page.locator(".v-overlay .v-list-item:visible", { hasText: "feat-render-media-trace3d" }).first();
  if (await opt.isVisible().catch(() => false)) await opt.click();
  await page.waitForTimeout(600);
}
await shot("19b-template-picked-4k");
console.log("console errors:", JSON.stringify([...new Set(errs)].slice(0, 15), null, 1));
await browser.close();
