/**
 * UI Scrutinizer — phase 2: use *live* appIds for collection / DO / refs.
 */
import { test, Page } from "@playwright/test";
import * as fs from "node:fs";
import * as path from "node:path";
import { loginAs } from "./helpers/auth";

const OUT_DIR = "/opt/shepard/aidocs/agent-findings/screenshots/ui-scrutinizer-2026-05-30";
const MANIFEST = path.join(OUT_DIR, "_manifest_phase2.json");
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
  await page.waitForTimeout(4000);

  const html = await page.content().catch(() => "");
  const phs: string[] = [];
  for (const marker of [
    "PlaceholderImplStatus", "PlaceholderFragmentPane", "PlaceholderPageHeader", "PlaceholderRestDump",
    "placeholder-impl-status", "placeholder-fragment-pane", "placeholder-page-header", "placeholder-rest-dump",
    "Implementation status", "Raw REST response", "Page placeholder", "Backend live but", "queued for",
  ]) if (html.includes(marker)) phs.push(marker);

  const bodyText = await page.locator("body").innerText().catch(() => "");
  const stuck = await page.locator(".v-progress-circular, .v-skeleton-loader").first().isVisible().catch(() => false);

  const shot = path.join(OUT_DIR, `${slug}.png`);
  await page.screenshot({ path: shot, fullPage: false }).catch(() => {});

  return {
    route, slug, screenshot: shot, status, placeholderHits: phs,
    textPlaceholderHits: [], consoleErrors, loadingStuck: stuck,
    bodyExcerpt: bodyText.slice(0, 800),
  };
}

test.describe.configure({ mode: "serial" });

test("phase2 deep dives", async ({ page }) => {
  test.setTimeout(900_000);
  await page.setViewportSize({ width: 3840, height: 2160 });
  await loginAs(page, "alice", "alice-demo");

  const LUMEN = "019e6ffc-89a4-76b5-8dbb-15888646a904";
  const MFFD = "019e6ff9-2bf7-732c-aa1c-2b504302a1e4";

  findings.push(await visit(page, "collection-lumen-live", `/collections/${LUMEN}`));
  fs.writeFileSync(MANIFEST, JSON.stringify(findings, null, 2));

  findings.push(await visit(page, "collection-mffd-live", `/collections/${MFFD}`));
  fs.writeFileSync(MANIFEST, JSON.stringify(findings, null, 2));

  // Drill into first DO from LUMEN
  try {
    await page.goto(`/collections/${LUMEN}`, { waitUntil: "domcontentloaded" });
    await page.waitForTimeout(5000);
    const doHref = await page.locator("a[href*='/dataobjects/']").first().getAttribute("href").catch(() => null);
    if (doHref) {
      findings.push(await visit(page, "dataobject-lumen", doHref));
      // Try first reference link types
      const frHref = await page.locator("a[href*='/filereferences/']").first().getAttribute("href").catch(() => null);
      if (frHref) findings.push(await visit(page, "filereference-lumen", frHref));
      const tsHref = await page.locator("a[href*='/timeseriesereferences/']").first().getAttribute("href").catch(() => null);
      if (tsHref) findings.push(await visit(page, "tsreference-lumen", tsHref));
      const sdHref = await page.locator("a[href*='/structureddatareferences/']").first().getAttribute("href").catch(() => null);
      if (sdHref) findings.push(await visit(page, "sdreference-lumen", sdHref));
    } else {
      findings.push({ slug: "do-disco-fail-lumen", route: `/collections/${LUMEN}`, status: "n/a", placeholderHits: [], consoleErrors: ["no DO link"], loadingStuck: false });
    }
  } catch (e) {
    findings.push({ slug: "do-disco-exception", route: `/collections/${LUMEN}`, status: "n/a", placeholderHits: [], consoleErrors: [String(e).slice(0, 200)], loadingStuck: false });
  }
  fs.writeFileSync(MANIFEST, JSON.stringify(findings, null, 2));

  // Drill into MFFD too (often has scene-graphs)
  try {
    await page.goto(`/collections/${MFFD}`, { waitUntil: "domcontentloaded" });
    await page.waitForTimeout(5000);
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
        findings.push({ slug: `container-${kind}-none`, route: `/containers (no ${kind})`, status: "n/a", placeholderHits: [], consoleErrors: [`no ${kind} container in /containers list`], loadingStuck: false });
      }
    }
  } catch (e) { /* ignore */ }
  fs.writeFileSync(MANIFEST, JSON.stringify(findings, null, 2));

  // Sign-in page
  await page.context().clearCookies();
  findings.push(await visit(page, "signin", "/auth/signIn"));
  fs.writeFileSync(MANIFEST, JSON.stringify(findings, null, 2));

  console.log(`>>> PHASE2 COMPLETE. ${findings.length} rows.`);
});
