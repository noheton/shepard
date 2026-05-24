/**
 * RDM Scrutinizer 2026-05-24 — live FAIR/DMP/publication walk.
 *
 * Targets 8 FAIR-relevant surfaces on https://shepard.nuclide.systems:
 *   1. Collection 42 (LUMEN) landing — license / export / citation
 *   2. DataObject detail tabs — pick one DO; capture each tab
 *   3. Permissions tab — access-rights enum? embargo?
 *   4. User profile — ORCID input? where does it surface on DOs?
 *   5. Publish/PID surface — UI presence check
 *   6. Export — RO-Crate or any download
 *   7. Lab Journal on a LUMEN DO — provenance human-readable?
 *   8. MFFD-Dropbox at scale (collection 661923) — license/access-rights at scale
 *
 * Layered as a DELTA over the prior source-only RDM doc (2026-05-21).
 * Read-only. No mutations.
 */
import { test, expect } from "@playwright/test";
import * as fs from "fs";
import * as path from "path";

const EVIDENCE = "/opt/shepard/aidocs/agent-findings/rdm-scrutinizer-2026-05-24-evidence";
const AUTH = "/opt/shepard/aidocs/agent-findings/ux-scrutinizer-workflows-2026-05-24-evidence/auth-state/alice.json";
const ADMIN_AUTH = "/opt/shepard/aidocs/agent-findings/ux-scrutinizer-workflows-2026-05-24-evidence/auth-state/admin.json";

fs.mkdirSync(EVIDENCE, { recursive: true });

test.use({ storageState: AUTH, viewport: { width: 1920, height: 1080 } });

// helper: capture page + JSON sidecar listing FAIR-relevant text on the surface
async function capture(page: import("@playwright/test").Page, slug: string, fairTerms: string[]) {
  const png = path.join(EVIDENCE, `${slug}.png`);
  const json = path.join(EVIDENCE, `${slug}.json`);
  await page.screenshot({ path: png, fullPage: true });
  const url = page.url();
  const title = await page.title();
  const bodyText = await page.locator("body").innerText().catch(() => "");
  const presence: Record<string, boolean> = {};
  for (const t of fairTerms) {
    presence[t] = bodyText.toLowerCase().includes(t.toLowerCase());
  }
  fs.writeFileSync(json, JSON.stringify({ url, title, presence, bodyTextLen: bodyText.length }, null, 2));
}

const FAIR_TERMS = [
  "license", "Lizenz", "SPDX", "CC-BY", "CC0",
  "access rights", "accessRights", "embargo", "Embargo",
  "ORCID", "orcid.org",
  "DOI", "PID", "ePIC", "DataCite", "Handle",
  "How to cite", "Cite this", "Citation", "Zitieren",
  "Export", "RO-Crate", "DataCite XML", "JSON-LD",
  "publish", "Publish", "publication", "Veröffentlichen",
  "funder", "grant", "funding",
  "creator", "rightsholder",
];

test("RDM-01 — Collection 42 LUMEN landing: FAIR fields visible?", async ({ page }) => {
  await page.goto("/collections/42");
  await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
  await capture(page, "01-collection-42-landing", FAIR_TERMS);
});

test("RDM-02 — Collection 42 sidebar metadata / tabs", async ({ page }) => {
  await page.goto("/collections/42");
  await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
  // open every tab we can find on the collection
  const tabs = await page.locator('[role="tab"], .v-tab').all();
  fs.writeFileSync(path.join(EVIDENCE, "02-collection-tabs-count.json"),
    JSON.stringify({ tabCount: tabs.length, labels: await Promise.all(tabs.map(t => t.innerText().catch(()=>"")))}, null, 2));
  for (let i = 0; i < tabs.length; i++) {
    try {
      await tabs[i].click({ timeout: 3000 });
      await page.waitForTimeout(800);
      await capture(page, `02-coll42-tab-${i}`, FAIR_TERMS);
    } catch { /* tab gone after click? skip */ }
  }
});

test("RDM-03 — DataObject detail TR-001 all tabs", async ({ page }) => {
  // Navigate via the collection to pick the first DO
  await page.goto("/collections/42");
  await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
  // First DO link inside the panel
  // Don't wait for visibility — the Vuetify list wrapper marks links hidden but href is set
  await page.waitForFunction(() => document.querySelectorAll('a[href*="/dataobjects/"]').length > 0, { timeout: 15_000 });
  const firstDoLink = page.locator('a[href*="/dataobjects/"]').first();
  const doHref = await firstDoLink.getAttribute("href");
  fs.writeFileSync(path.join(EVIDENCE, "03-do-href.json"), JSON.stringify({ doHref }, null, 2));
  await page.goto(doHref!, { waitUntil: "domcontentloaded", timeout: 15_000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
  await capture(page, "03-do-detail-landing", FAIR_TERMS);

  // Walk every tab on the DO
  const tabs = await page.locator('[role="tab"], .v-tab').all();
  const labels: string[] = [];
  for (let i = 0; i < tabs.length; i++) {
    try {
      const label = await tabs[i].innerText().catch(() => "");
      labels.push(label);
      await tabs[i].click({ timeout: 3000 });
      await page.waitForTimeout(800);
      await capture(page, `03-do-tab-${i}-${label.replace(/\W/g,"_").slice(0,20)}`, FAIR_TERMS);
    } catch { /* skip */ }
  }
  fs.writeFileSync(path.join(EVIDENCE, "03-do-tabs.json"), JSON.stringify({ count: tabs.length, labels }, null, 2));
});

test("RDM-04 — User profile: ORCID input present?", async ({ page }) => {
  await page.goto("/me");
  await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
  await capture(page, "04-user-profile-me", FAIR_TERMS.concat(["profile", "preferences"]));
  // try the /user surface if /me redirects elsewhere
  await page.goto("/user");
  await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
  await capture(page, "04-user-page", FAIR_TERMS);
});

test("RDM-05 — Publish / PID UI surface presence", async ({ page }) => {
  // Try the obvious paths
  const tries = ["/publish", "/admin/publish", "/admin/publications", "/publications", "/admin"];
  const results: Record<string, { status: number, finalUrl: string }> = {};
  for (const path_ of tries) {
    const resp = await page.goto(path_, { waitUntil: "domcontentloaded", timeout: 10_000 }).catch(() => null);
    results[path_] = { status: resp?.status() ?? 0, finalUrl: page.url() };
    await capture(page, `05-publish-try-${path_.replace(/\//g,"_")}`, FAIR_TERMS);
  }
  fs.writeFileSync(path.join(EVIDENCE, "05-publish-tries.json"), JSON.stringify(results, null, 2));
});

test("RDM-06 — Export affordance on Collection 42", async ({ page }) => {
  await page.goto("/collections/42");
  await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
  // Look for any "Export" / "Download" / "RO-Crate" affordance — buttons, menu items, links
  const exportCandidates = await page.locator('button, a, [role="menuitem"]')
    .filter({ hasText: /export|download|ro-crate|publish|cite/i }).all();
  const labels = await Promise.all(exportCandidates.map(c => c.innerText().catch(() => "")));
  fs.writeFileSync(path.join(EVIDENCE, "06-export-candidates.json"), JSON.stringify({ count: exportCandidates.length, labels }, null, 2));
  await capture(page, "06-collection-export-affordance-survey", FAIR_TERMS);
});

test("RDM-07 — Lab Journal: provenance human-readable?", async ({ page }) => {
  await page.goto("/collections/42");
  await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
  await page.waitForFunction(() => document.querySelectorAll('a[href*="/dataobjects/"]').length > 0, { timeout: 15_000 });
  const firstDoLink = page.locator('a[href*="/dataobjects/"]').first();
  const doHref = await firstDoLink.getAttribute("href");
  await page.goto(doHref!, { waitUntil: "domcontentloaded", timeout: 15_000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
  // Try clicking a Lab Journal / Notebook / Provenance tab
  const labCandidates = await page.locator('[role="tab"], .v-tab, button')
    .filter({ hasText: /lab journal|notebook|provenance|prov|activity|history/i }).all();
  for (let i = 0; i < labCandidates.length; i++) {
    try {
      const label = await labCandidates[i].innerText().catch(() => "");
      await labCandidates[i].click({ timeout: 3000 });
      await page.waitForTimeout(1000);
      await capture(page, `07-prov-tab-${i}-${label.replace(/\W/g,"_").slice(0,20)}`, FAIR_TERMS.concat(["AI","Claude","model","human","accepted","proposed"]));
    } catch { /* skip */ }
  }
  fs.writeFileSync(path.join(EVIDENCE, "07-prov-candidates.json"), JSON.stringify({ count: labCandidates.length }, null, 2));
});

test("RDM-08 — MFFD-Dropbox at scale: license/access-rights absence", async ({ page }) => {
  await page.goto("/collections/661923");
  await page.waitForLoadState("networkidle", { timeout: 20_000 }).catch(() => {});
  await capture(page, "08-mffd-collection-landing", FAIR_TERMS);
  // Pick a DO from the imported tree
  const firstDoLink = page.locator('a[href*="/dataobjects/"]').first();
  if (await firstDoLink.count() > 0) {
    const doHref = await firstDoLink.getAttribute("href");
    await page.goto(doHref!, { waitUntil: "domcontentloaded", timeout: 15_000 }).catch(() => {});
    await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
    await capture(page, "08-mffd-do-detail", FAIR_TERMS);
  }
});

test("RDM-09 — admin: license / accessRights config knob?", async ({ browser }) => {
  // Use admin auth for this one
  const ctx = await browser.newContext({ storageState: ADMIN_AUTH, viewport: { width: 1920, height: 1080 } });
  const page = await ctx.newPage();
  for (const p of ["/admin", "/configuration", "/admin/features", "/admin/semantic", "/admin/unhide"]) {
    await page.goto(p, { waitUntil: "domcontentloaded", timeout: 10_000 }).catch(() => {});
    await page.waitForTimeout(800);
    await capture(page, `09-admin-${p.replace(/\//g,"_")}`, FAIR_TERMS.concat(["default license","default-license","embargo default"]));
  }
  await ctx.close();
});
