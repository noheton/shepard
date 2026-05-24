/**
 * UI-2026-05-24-018 — N+1 hypothesis re-check on deep MFFD DataObject detail.
 *
 * Context: ux-auditor flagged a HYPOTHESIS that CollectionLineageGraph.vue and
 * DataObjectProvGraph.vue might fire per-DataObject fetches when rendering a
 * deep DO. The first walk couldn't confirm because BUG #139 (commit 5913ca20)
 * blocked the panel from rendering at 4K. This spec re-checks now that the
 * panel renders.
 *
 * Method:
 *   1. Auth as alice
 *   2. Visit a deep MFFD DO at 1920×1080 (BUG #139 fix was for layout; the
 *      panel renders there as well)
 *   3. Wait for both graphs to load
 *   4. Count GETs against /v2/dataobjects, /v2/collections/{appId}/data-objects,
 *      /v2/dataobjects/*\/references/*, and any repeat-shape patterns
 *
 * Verdict rule:
 *   - If we see N similar requests for N nodes → CONFIRMED N+1
 *   - If we see ≤3 batch/paginated requests → CLOSED (negative evidence)
 *
 * This spec writes evidence to
 * /opt/shepard/aidocs/agent-findings/ui-018-019-evidence-2026-05-24/.
 * It is intentionally non-failing — captures evidence regardless of UI state.
 */
import { test, type Page, type ConsoleMessage, type Request, type Response } from "@playwright/test";
import * as fs from "fs";
import * as path from "path";

const KC = process.env.KEYCLOAK_HOST || "https://shepard-auth.nuclide.systems";
const REALM = "shepard-demo";
const OUT = "/opt/shepard/aidocs/agent-findings/ui-018-019-evidence-2026-05-24";

const MFFD_COLL = 661923;
// Pick a known-deep MFFD DO. 661958 is observed mid-depth from prior walks.
const MFFD_DO = 661958;
// LUMEN TR-007 = id 51 (LUMEN DOs start at id 45 = TR-001; verified via
// screenshot in the evidence dir). DO has predecessor + successor relationships.
const LUMEN_COLL = 42;
const LUMEN_DO = 51;

fs.mkdirSync(OUT, { recursive: true });

type RequestRecord = {
  url: string;
  method: string;
  status?: number;
  resourceType: string;
  time: number;
};

async function loginAs(page: Page, username: string, password: string) {
  await page.goto("/auth/signIn");
  await page
    .getByRole("button", { name: /sign in|login/i })
    .first()
    .click();
  await page.waitForURL(`${KC}/realms/${REALM}/**`, { timeout: 20_000 });
  await page.fill("#username", username);
  await page.fill("#password", password);
  await page.click('[type="submit"]');
  await page.waitForURL(/shepard\.nuclide\.systems(?!.*error)/, {
    timeout: 20_000,
  });
}

async function probeDoPage(
  page: Page,
  label: string,
  collectionId: number,
  dataObjectId: number | null,
): Promise<{ verdict: "CONFIRMED" | "CLOSED"; report: Record<string, unknown> }> {
  const requests: RequestRecord[] = [];
  const consoleErrors: string[] = [];
  const t0 = Date.now();

  page.on("request", (req: Request) => {
    requests.push({
      url: req.url(),
      method: req.method(),
      resourceType: req.resourceType(),
      time: Date.now() - t0,
    });
  });
  page.on("response", (resp: Response) => {
    // Attach status back to the matching request entry
    const url = resp.url();
    const method = resp.request().method();
    // Find the most recent record with same URL+method without status
    for (let i = requests.length - 1; i >= 0; i--) {
      if (requests[i].url === url && requests[i].method === method && requests[i].status === undefined) {
        requests[i].status = resp.status();
        break;
      }
    }
  });
  page.on("console", (msg: ConsoleMessage) => {
    if (msg.type() === "error") consoleErrors.push(msg.text());
  });
  page.on("pageerror", (e: Error) => consoleErrors.push(`PAGEERROR: ${e.message}`));

  // Reset counters before measuring the page (login is done by caller)
  requests.length = 0;
  const probeStart = Date.now();

  const targetUrl = dataObjectId !== null
    ? `/collections/${collectionId}/dataobjects/${dataObjectId}`
    : `/collections/${collectionId}`;
  await page.goto(targetUrl, {
    waitUntil: "networkidle",
    timeout: 30_000,
  });

  // Give graph components a moment to mount and fire their fetches
  await page.waitForTimeout(2500);

  // Both DataObjectProvGraph and CollectionLineageGraph live inside collapsed
  // ExpansionPanelItems. Scroll then open every accordion to trigger lazy fetches.
  await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
  await page.waitForTimeout(1500);

  // Try to open all expansion panels by clicking their headers
  const panelTitles = await page.locator(".v-expansion-panel-title").all();
  for (const t of panelTitles) {
    try {
      await t.click({ timeout: 1500 });
      await page.waitForTimeout(300);
    } catch {
      // ignore — some panels may already be open or disabled
    }
  }

  // Let any post-expand fetches drain
  await page.waitForTimeout(3000);
  await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
  await page.waitForTimeout(1500);
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.waitForTimeout(1500);

  const probeDuration = Date.now() - probeStart;

  // Capture screenshots for evidence
  await page.screenshot({
    path: path.join(OUT, `ui-018-${label}-full.png`),
    fullPage: true,
  });
  await page.screenshot({
    path: path.join(OUT, `ui-018-${label}-viewport.png`),
    fullPage: false,
  });

  // ─── Analyze request patterns ────────────────────────────────────────────
  // Treat as "API" any XHR/fetch request OR anything matching known REST shapes,
  // regardless of host. We saw the page is reached at shepard.nuclide.systems
  // but the API host can differ (rev-proxy or backendApiUrl override).
  const apiReqs = requests.filter(r => {
    if (r.resourceType === "xhr" || r.resourceType === "fetch") return true;
    if (/\/v2\/|\/shepard\/api\//.test(r.url)) return true;
    return false;
  });

  // Group by URL "shape" — strip IDs to find repeat patterns
  const shape = (u: string) =>
    u
      .replace(/\d{4,}/g, ":id")
      .replace(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/g, ":uuid")
      .replace(/\?.*$/, "");

  const byShape = new Map<string, RequestRecord[]>();
  for (const r of apiReqs) {
    const s = shape(r.url);
    if (!byShape.has(s)) byShape.set(s, []);
    byShape.get(s)!.push(r);
  }

  // Sort shapes by frequency (most likely N+1 candidates first)
  const shapeCounts = Array.from(byShape.entries())
    .map(([s, rs]) => ({ shape: s, count: rs.length, urls: rs.slice(0, 5).map(r => r.url) }))
    .sort((a, b) => b.count - a.count);

  // Specifically look for known N+1 suspects
  const dataObjectByIdReqs = apiReqs.filter(
    r => /\/v2\/dataobjects\/\d+(?:\/|\?|$)/.test(r.url) || /\/shepard\/api\/dataObjects\/\d+(?:\/|\?|$)/.test(r.url),
  );
  const refReqs = apiReqs.filter(r => /references|payload/.test(r.url));
  const listReqs = apiReqs.filter(r => /data-objects|dataObjects(?!\/)|dataobjects(?!\/)/.test(r.url) && !/\/\d+/.test(r.url.replace(/\?.*$/, "")));

  // ─── Verdict logic ───────────────────────────────────────────────────────
  // UI-018 was specifically about lineage/prov graphs. Those use
  // `useFetchAllDataObjects` which is paginated, so the suspect shapes
  // would be `/dataobjects/:id` or `/data-objects` repeated.
  const lineageProvSuspects = shapeCounts.filter(s =>
    s.count > 10 &&
    (s.shape.includes("/dataObjects") || s.shape.includes("/data-objects") || s.shape.includes("/dataobjects/")) &&
    s.shape.includes(":id"),
  );
  // Also record ANY shape >10× as a separate finding (unrelated N+1 candidates)
  const otherHighFreqShapes = shapeCounts.filter(s =>
    s.count > 10 &&
    !lineageProvSuspects.includes(s),
  );
  const verdict = lineageProvSuspects.length > 0 ? "CONFIRMED" : "CLOSED";

  const report = {
    test: "UI-2026-05-24-018",
    label,
    target: { collection: collectionId, dataObject: dataObjectId },
    viewport: { w: 1920, h: 1080 },
    probeDurationMs: probeDuration,
    totalRequests: requests.length,
    totalApiRequests: apiReqs.length,
    verdict,
    lineageProvSuspects,
    otherHighFreqShapes,
    top10ShapesByFrequency: shapeCounts.slice(0, 10),
    dataObjectByIdGetCount: dataObjectByIdReqs.length,
    referenceOrPayloadCount: refReqs.length,
    collectionListCount: listReqs.length,
    consoleErrors: consoleErrors.slice(0, 50),
    consoleErrorsTotal: consoleErrors.length,
    // Cap to first 200 URLs to keep the artifact manageable for collections
    // with thousands of DOs (MFFD-Dropbox: 8541 reqs would balloon the JSON).
    allApiUrls: apiReqs.slice(0, 200).map(r => ({ method: r.method, url: r.url, status: r.status, t: r.time })),
    allApiUrlsTruncated: apiReqs.length > 200,
  };

  fs.writeFileSync(
    path.join(OUT, `ui-018-${label}-network-report.json`),
    JSON.stringify(report, null, 2),
  );

  // Print summary so verdict is visible in test output
  console.log("─".repeat(70));
  console.log(`UI-018 [${label}] verdict: ${verdict}`);
  console.log(`  Total API requests during DO page load: ${apiReqs.length}`);
  console.log(`  DataObject-by-ID GETs: ${dataObjectByIdReqs.length}`);
  console.log(`  Reference/payload GETs: ${refReqs.length}`);
  console.log(`  Top 5 shapes:`);
  for (const s of shapeCounts.slice(0, 5)) {
    console.log(`    [${s.count}×] ${s.shape}`);
  }
  console.log("─".repeat(70));

  return { verdict, report };
}

test("UI-018 — N+1 hypothesis re-check across MFFD + LUMEN lineage DOs", async ({ page }) => {
  test.setTimeout(180_000);
  await page.setViewportSize({ width: 1920, height: 1080 });
  await loginAs(page, "alice", "alice-demo");

  // 1) Deep MFFD DataObject detail — triggers DataObjectProvGraph
  const mffd = await probeDoPage(page, "mffd-do-661958", MFFD_COLL, MFFD_DO);
  // 2) LUMEN DataObject with predecessors — also DataObjectProvGraph
  const lumen = await probeDoPage(page, "lumen-do-51", LUMEN_COLL, LUMEN_DO);
  // 3) LUMEN Collection landing — triggers CollectionLineageGraph (15+ TR nodes)
  const lumenColl = await probeDoPage(page, "lumen-coll-42", LUMEN_COLL, null);
  // 4) MFFD Collection landing — triggers CollectionLineageGraph
  const mffdColl = await probeDoPage(page, "mffd-coll-661923", MFFD_COLL, null);

  // Roll up to a single verdict file the report markdown can read
  const allVerdicts = {
    "mffd-do-661958": mffd.verdict,
    "lumen-do-51": lumen.verdict,
    "lumen-coll-42": lumenColl.verdict,
    "mffd-coll-661923": mffdColl.verdict,
  };
  const overall = Object.values(allVerdicts).some(v => v === "CONFIRMED") ? "CONFIRMED" : "CLOSED";
  const rollup = {
    test: "UI-2026-05-24-018",
    runAt: new Date().toISOString(),
    verdicts: allVerdicts,
    overall,
    reports: { mffd: mffd.report, lumen: lumen.report, lumenColl: lumenColl.report, mffdColl: mffdColl.report },
  };
  fs.writeFileSync(
    path.join(OUT, "ui-018-rollup.json"),
    JSON.stringify(rollup, null, 2),
  );
  console.log(`UI-018 OVERALL VERDICT: ${rollup.overall}`);
});
