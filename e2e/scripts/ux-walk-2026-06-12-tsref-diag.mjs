import { chromium } from "playwright";
import fs from "node:fs";
const STATE = "/tmp/ux-walk-2026-06-12-state.json";
const BASE = "https://shepard.nuclide.systems";
const urls = [
  `${BASE}/collections/019eb019-d49b-7131-b2d2-3f3107d36a4f/dataobjects/019eb019-d8e9-7991-96e7-75ca1ab3d6be/timeseriesereferences/019eb01a-4bba-74e9-8a16-7562c1994bca`,
  `${BASE}/collections/364325/dataobjects/364336/timeseriesereferences/365047`,
];
const browser = await chromium.launch();
const ctx = await browser.newContext({ viewport: { width: 3840, height: 2160 }, storageState: STATE });
const page = await ctx.newPage();
page.on("response", r => { const u = r.url(); if (!u.includes("_nuxt") && (u.includes("api") || u.includes("/v2/") || u.includes("shepard"))) console.log(r.status(), r.request().method(), u.slice(0, 180)); });
page.on("console", m => { if (m.type() === "error") console.log("CONSOLE:", m.text().slice(0, 250)); });
for (const [i, u] of urls.entries()) {
  console.log("=== NAV", u);
  await page.goto(u, { waitUntil: "networkidle" }).catch(e => console.log("nav err", e.message));
  await page.waitForTimeout(8000);
  await page.screenshot({ path: `/opt/shepard/aidocs/agent-findings/screenshots/ux-2026-06-12/08${i === 0 ? "" : "b-numericid"}-tsref-diag.png` });
  const spinner = await page.locator(".v-progress-circular:visible").count();
  console.log("spinners visible:", spinner);
  const btns = await page.locator("button:visible").allInnerTexts();
  console.log("buttons:", JSON.stringify(btns.map(b=>b.trim()).filter(Boolean)));
}
await browser.close();
