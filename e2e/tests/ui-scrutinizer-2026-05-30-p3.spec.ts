/**
 * Phase 3: long-form IDs to bypass the appId→numeric parseInt bug.
 */
import { test, Page } from "@playwright/test";
import * as fs from "node:fs";
import * as path from "node:path";
import { loginAs } from "./helpers/auth";

const OUT_DIR = "/opt/shepard/aidocs/agent-findings/screenshots/ui-scrutinizer-2026-05-30";
const MANIFEST = path.join(OUT_DIR, "_manifest_phase3.json");
fs.mkdirSync(OUT_DIR, { recursive: true });

const findings: any[] = [];

async function visit(page: Page, slug: string, route: string) {
  const consoleErrors: string[] = [];
  page.removeAllListeners("console");
  page.removeAllListeners("pageerror");
  page.on("console", (m) => {
    if (m.type() === "error") consoleErrors.push(m.text().slice(0, 240));
  });
  page.on("pageerror", (e) => consoleErrors.push("PAGEERROR: " + e.message.slice(0, 240)));

  let status: number | "n/a" = "n/a";
  try {
    const resp = await page.goto(route, { waitUntil: "domcontentloaded", timeout: 30_000 });
    status = resp?.status() ?? "n/a";
  } catch (e) {
    consoleErrors.push("GOTO_FAIL: " + String(e).slice(0, 240));
  }
  await page.waitForTimeout(4500);

  const html = await page.content().catch(() => "");
  const phs: string[] = [];
  for (const marker of [
    "PlaceholderImplStatus", "PlaceholderFragmentPane", "PlaceholderPageHeader", "PlaceholderRestDump",
    "placeholder-impl-status", "placeholder-fragment-pane", "placeholder-page-header", "placeholder-rest-dump",
  ]) if (html.includes(marker)) phs.push(marker);

  const shot = path.join(OUT_DIR, `${slug}.png`);
  await page.screenshot({ path: shot, fullPage: false }).catch(() => {});

  return { route, slug, screenshot: shot, status, placeholderHits: phs, consoleErrors };
}

test("p3 long-form-id deep-dive", async ({ page }) => {
  test.setTimeout(600_000);
  await page.setViewportSize({ width: 3840, height: 2160 });
  await loginAs(page, "alice", "alice-demo");

  // Use long-form IDs (parseInt-friendly) — LUMEN=2107, MFFD=1787
  findings.push(await visit(page, "collection-lumen-byid", `/collections/2107`));
  fs.writeFileSync(MANIFEST, JSON.stringify(findings, null, 2));

  findings.push(await visit(page, "collection-mffd-byid", `/collections/1787`));
  fs.writeFileSync(MANIFEST, JSON.stringify(findings, null, 2));

  // Drill into a LUMEN DO
  try {
    await page.goto(`/collections/2107`, { waitUntil: "domcontentloaded" });
    await page.waitForTimeout(5500);
    const doHref = await page.locator("a[href*='/dataobjects/']").first().getAttribute("href").catch(() => null);
    if (doHref) {
      findings.push(await visit(page, "dataobject-lumen", doHref));
      const frHref = await page.locator("a[href*='/filereferences/']").first().getAttribute("href").catch(() => null);
      if (frHref) findings.push(await visit(page, "filereference-lumen", frHref));
      const tsHref = await page.locator("a[href*='/timeseriesereferences/']").first().getAttribute("href").catch(() => null);
      if (tsHref) findings.push(await visit(page, "tsreference-lumen", tsHref));
      const sdHref = await page.locator("a[href*='/structureddatareferences/']").first().getAttribute("href").catch(() => null);
      if (sdHref) findings.push(await visit(page, "sdreference-lumen", sdHref));
    }
  } catch (e) { /* ignore */ }
  fs.writeFileSync(MANIFEST, JSON.stringify(findings, null, 2));

  // MFFD DO
  try {
    await page.goto(`/collections/1787`, { waitUntil: "domcontentloaded" });
    await page.waitForTimeout(5500);
    const doHref = await page.locator("a[href*='/dataobjects/']").first().getAttribute("href").catch(() => null);
    if (doHref) {
      findings.push(await visit(page, "dataobject-mffd", doHref));
      const frHref = await page.locator("a[href*='/filereferences/']").first().getAttribute("href").catch(() => null);
      if (frHref) findings.push(await visit(page, "filereference-mffd", frHref));
    }
  } catch (e) { /* ignore */ }
  fs.writeFileSync(MANIFEST, JSON.stringify(findings, null, 2));

  // Containers per kind
  try {
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
    }
  } catch (e) { /* ignore */ }
  fs.writeFileSync(MANIFEST, JSON.stringify(findings, null, 2));

  await page.context().clearCookies();
  findings.push(await visit(page, "signin", "/auth/signIn"));
  fs.writeFileSync(MANIFEST, JSON.stringify(findings, null, 2));
});
