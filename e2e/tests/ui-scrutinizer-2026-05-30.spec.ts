/**
 * UI Scrutinizer — full audit walk 2026-05-30.
 *
 * Visits every user-facing page at 4K viewport, captures a screenshot,
 * collects placeholder markers and console errors, writes a manifest
 * for the human-curated findings file.
 *
 * Auth: flodemo / flo-demo (realm role `user`, NOT instance-admin).
 */
import { test, expect, Page } from "@playwright/test";
import * as fs from "node:fs";
import * as path from "node:path";
import { loginAs } from "./helpers/auth";

const OUT_DIR = "/opt/shepard/aidocs/agent-findings/screenshots/ui-scrutinizer-2026-05-30";
const MANIFEST = path.join(OUT_DIR, "_manifest.json");
fs.mkdirSync(OUT_DIR, { recursive: true });

type Finding = {
  route: string;
  slug: string;
  screenshot: string;
  status: number | "n/a";
  title?: string;
  placeholderHits: string[];
  textPlaceholderHits: string[];
  consoleErrors: string[];
  loadingStuck: boolean;
  notes: string[];
};

const findings: Finding[] = [];

async function visit(page: Page, slug: string, route: string): Promise<Finding> {
  const consoleErrors: string[] = [];
  page.on("console", (m) => {
    if (m.type() === "error") consoleErrors.push(m.text().slice(0, 240));
  });
  page.on("pageerror", (e) => consoleErrors.push("PAGEERROR: " + e.message.slice(0, 240)));

  let status: number | "n/a" = "n/a";
  try {
    const resp = await page.goto(route, { waitUntil: "domcontentloaded", timeout: 25_000 });
    status = resp?.status() ?? "n/a";
  } catch (e) {
    consoleErrors.push("GOTO_FAIL: " + String(e).slice(0, 240));
  }
  // Allow Vue + data fetch to settle.
  await page.waitForTimeout(3500);

  // Look for placeholder kit markers in DOM.
  // PlaceholderImplStatus has "PlaceholderImplStatus" class or "Implementation status" copy.
  // PlaceholderFragmentPane uses "Fragment: " or "placeholder pane" copy.
  // PlaceholderPageHeader puts "Page placeholder" copy.
  // PlaceholderRestDump emits a "Raw REST response" or "rest dump" panel.
  const placeholderHits: string[] = [];
  const html = await page.content().catch(() => "");
  for (const marker of [
    "PlaceholderImplStatus",
    "PlaceholderFragmentPane",
    "PlaceholderPageHeader",
    "PlaceholderRestDump",
    "placeholder-impl-status",
    "placeholder-fragment-pane",
    "placeholder-page-header",
    "placeholder-rest-dump",
    "Implementation status",
    "Raw REST response",
    "Page placeholder",
    "Fragment:",
  ]) {
    if (html.includes(marker)) placeholderHits.push(marker);
  }

  // Text placeholders.
  const textPlaceholderHits: string[] = [];
  const bodyText = await page.locator("body").innerText().catch(() => "");
  for (const phrase of [
    "Coming soon",
    "Under construction",
    "Not implemented",
    "TBD",
    "Lorem ipsum",
    "Backend live but",
    "queued for",
    "placeholder",
  ]) {
    const re = new RegExp(phrase, "i");
    if (re.test(bodyText)) textPlaceholderHits.push(phrase);
  }

  // Stuck-spinner heuristic.
  const loadingStuck = await page
    .locator(".v-progress-circular, .v-skeleton-loader, [role='progressbar']")
    .first()
    .isVisible()
    .catch(() => false);

  const title = await page.title().catch(() => undefined);

  const screenshot = path.join(OUT_DIR, `${slug}.png`);
  await page
    .screenshot({ path: screenshot, fullPage: false })
    .catch((e) => consoleErrors.push("SHOT_FAIL: " + String(e).slice(0, 240)));

  const notes: string[] = [];
  return {
    route,
    slug,
    screenshot,
    status,
    title,
    placeholderHits,
    textPlaceholderHits,
    consoleErrors,
    loadingStuck,
    notes,
  };
}

test.describe.configure({ mode: "serial" });

test("audit walk 4K", async ({ page }) => {
  test.setTimeout(900_000);
  await page.setViewportSize({ width: 3840, height: 2160 });
  // Note: flodemo per brief, but the live shepard.nuclide.systems realm only has
  // alice/alice-demo provisioned (user role, no instance-admin). flodemo exists
  // in shepard-demo-realm.json but was not imported into the live KC.
  // We use alice — same `user` realm role, identical scrutinizer scope.
  await loginAs(page, "alice", "alice-demo");

  // LUMEN collection appId
  const LUMEN_COLL = "019e7243-f995-7914-be80-53e367aa5172";
  // The scene-graph appId from the brief.
  const SCENE_APP_ID = "019e79be-b880-7438-82df-4163625862b7";

  const pages: Array<[string, string]> = [
    ["home", "/"],
    ["me", "/me"],
    ["me-profile", "/me#profile"],
    ["me-apikeys", "/me#api-keys"],
    ["me-mcp", "/me#mcp"],
    ["me-subscriptions", "/me#subscriptions"],
    ["me-git", "/me#git-credentials"],
    ["me-ai", "/me#ai-settings"],
    ["me-semantic", "/me#semantic"],
    ["tools", "/tools"],
    ["collections-list", "/collections"],
    ["collection-lumen", `/collections/${LUMEN_COLL}`],
    ["scene-graph-play", `/scene-graphs/play/${SCENE_APP_ID}`],
    ["containers-list", "/containers"],
    ["semantic-landing", "/semantic"],
    ["semantic-sparql", "/semantic/sparql"],
    ["semantic-vocabularies", "/semantic/vocabularies"],
    ["shapes-validate", "/shapes/validate"],
    ["shapes-render", "/shapes/render"],
    ["snapshots-diff", "/snapshots/diff"],
    ["search", "/search?q=test"],
    ["help", "/help"],
    ["healthz", "/healthz"],
    ["about", "/about"],
    ["admin", "/admin"],
    ["admin-instance-registry", "/admin/instance-registry"],
    ["admin-provenance", "/admin/provenance"],
  ];

  for (const [slug, route] of pages) {
    console.log(`>>> ${slug}  ${route}`);
    const f = await visit(page, slug, route);
    findings.push(f);
    fs.writeFileSync(MANIFEST, JSON.stringify(findings, null, 2));
  }

  // Now drill into the LUMEN collection: find a DataObject with refs, render it.
  // Capture from collection page links rather than guessing IDs.
  try {
    await page.goto(`/collections/${LUMEN_COLL}`, { waitUntil: "domcontentloaded" });
    await page.waitForTimeout(4000);
    // Try to capture first DO link
    const doHref = await page
      .locator("a[href*='/dataobjects/']")
      .first()
      .getAttribute("href")
      .catch(() => null);
    if (doHref) {
      const f = await visit(page, "dataobject-first", doHref);
      f.notes.push(`first-DO link discovered from collection page: ${doHref}`);
      findings.push(f);
      fs.writeFileSync(MANIFEST, JSON.stringify(findings, null, 2));
      // Then dive into a FileReference on this DO if present.
      const frHref = await page
        .locator("a[href*='/filereferences/']")
        .first()
        .getAttribute("href")
        .catch(() => null);
      if (frHref) {
        const fr = await visit(page, "filereference-first", frHref);
        fr.notes.push(`first-FR link from DO page: ${frHref}`);
        findings.push(fr);
      }
      // And a TS reference
      const tsHref = await page
        .locator("a[href*='/timeseriesereferences/']")
        .first()
        .getAttribute("href")
        .catch(() => null);
      if (tsHref) {
        const ts = await visit(page, "timeseries-reference-first", tsHref);
        ts.notes.push(`first-TS link from DO page: ${tsHref}`);
        findings.push(ts);
      }
    } else {
      findings.push({
        route: `/collections/${LUMEN_COLL}`,
        slug: "do-discovery-fail",
        screenshot: "",
        status: "n/a",
        placeholderHits: [],
        textPlaceholderHits: [],
        consoleErrors: ["No /dataobjects/ link on LUMEN collection page"],
        loadingStuck: false,
        notes: [],
      });
    }
  } catch (e) {
    console.log("DO discovery failed: " + e);
  }
  fs.writeFileSync(MANIFEST, JSON.stringify(findings, null, 2));

  // Container detail per kind — discover from /containers
  try {
    await page.goto(`/containers`, { waitUntil: "domcontentloaded" });
    await page.waitForTimeout(3000);
    for (const kind of ["files", "timeseries", "structureddata", "spatialdata", "hdf", "video"]) {
      const href = await page
        .locator(`a[href*='/containers/${kind}/']`)
        .first()
        .getAttribute("href")
        .catch(() => null);
      if (href) {
        const f = await visit(page, `container-${kind}`, href);
        f.notes.push(`first-${kind}-container link`);
        findings.push(f);
      } else {
        findings.push({
          route: `/containers/${kind}`,
          slug: `container-${kind}-none`,
          screenshot: "",
          status: "n/a",
          placeholderHits: [],
          textPlaceholderHits: [],
          consoleErrors: [`no ${kind} container in listing`],
          loadingStuck: false,
          notes: [],
        });
      }
      // Need to re-visit /containers because kind tabs change URL
      await page.goto(`/containers`, { waitUntil: "domcontentloaded" });
      await page.waitForTimeout(1500);
    }
  } catch (e) {
    console.log("Container discovery failed: " + e);
  }

  fs.writeFileSync(MANIFEST, JSON.stringify(findings, null, 2));

  // Sign-in page (briefly — for completeness)
  await page.context().clearCookies();
  const fSignin = await visit(page, "signin", "/auth/signIn");
  findings.push(fSignin);
  fs.writeFileSync(MANIFEST, JSON.stringify(findings, null, 2));

  console.log(`>>> WALK COMPLETE. ${findings.length} finding rows.`);
});
