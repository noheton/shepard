/**
 * Phase 4: directly visit reference detail pages and container detail pages.
 */
import { test, Page } from "@playwright/test";
import * as fs from "node:fs";
import * as path from "node:path";
import { loginAs } from "./helpers/auth";

const OUT_DIR = "/opt/shepard/aidocs/agent-findings/screenshots/ui-scrutinizer-2026-05-30";
const MANIFEST = path.join(OUT_DIR, "_manifest_phase4.json");
const findings: any[] = [];

async function visit(page: Page, slug: string, route: string) {
  const consoleErrors: string[] = [];
  page.removeAllListeners("console");
  page.removeAllListeners("pageerror");
  page.on("console", (m) => { if (m.type() === "error") consoleErrors.push(m.text().slice(0, 240)); });
  page.on("pageerror", (e) => consoleErrors.push("PAGEERROR: " + e.message.slice(0, 240)));
  let status: number | "n/a" = "n/a";
  try {
    const resp = await page.goto(route, { waitUntil: "domcontentloaded", timeout: 30_000 });
    status = resp?.status() ?? "n/a";
  } catch (e) { consoleErrors.push("GOTO_FAIL: " + String(e).slice(0, 240)); }
  await page.waitForTimeout(4500);
  const html = await page.content().catch(() => "");
  const phs: string[] = [];
  for (const m of ["PlaceholderImplStatus","PlaceholderFragmentPane","PlaceholderPageHeader","PlaceholderRestDump",
    "placeholder-impl-status","placeholder-fragment-pane","placeholder-page-header","placeholder-rest-dump"])
    if (html.includes(m)) phs.push(m);
  const shot = path.join(OUT_DIR, `${slug}.png`);
  await page.screenshot({ path: shot, fullPage: false }).catch(() => {});
  return { route, slug, screenshot: shot, status, placeholderHits: phs, consoleErrors };
}

test("p4 ref+container deep dive", async ({ page }) => {
  test.setTimeout(600_000);
  await page.setViewportSize({ width: 3840, height: 2160 });
  await loginAs(page, "alice", "alice-demo");

  // DO 2112 has refs [2244 (TS), 2256 (FileBundle), 2261 (StructuredData)]
  findings.push(await visit(page, "tsref-tr001", `/collections/2107/dataobjects/2112/timeseriesereferences/2244`));
  fs.writeFileSync(MANIFEST, JSON.stringify(findings, null, 2));
  findings.push(await visit(page, "fileref-tr001", `/collections/2107/dataobjects/2112/filereferences/2256`));
  fs.writeFileSync(MANIFEST, JSON.stringify(findings, null, 2));
  findings.push(await visit(page, "sdref-tr001", `/collections/2107/dataobjects/2112/structureddatareferences/2261`));
  fs.writeFileSync(MANIFEST, JSON.stringify(findings, null, 2));

  // Container detail per kind by discovery from list
  await page.goto(`/containers`, { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(4000);
  for (const kind of ["files", "timeseries", "structureddata", "spatialdata", "hdf", "video"]) {
    const href = await page.locator(`a[href*='/containers/${kind}/']`).first().getAttribute("href").catch(() => null);
    if (href) {
      findings.push(await visit(page, `container-${kind}`, href));
      await page.goto(`/containers`, { waitUntil: "domcontentloaded" });
      await page.waitForTimeout(2000);
    } else {
      findings.push({ slug: `container-${kind}-none`, route: `/containers (no ${kind})`, status: "n/a", placeholderHits: [], consoleErrors: [`no ${kind} container link in list`] });
    }
    fs.writeFileSync(MANIFEST, JSON.stringify(findings, null, 2));
  }

  // Signin
  await page.context().clearCookies().catch(() => {});
  findings.push(await visit(page, "signin", "/auth/signIn"));
  fs.writeFileSync(MANIFEST, JSON.stringify(findings, null, 2));
});
