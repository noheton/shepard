/**
 * UX-WF-02 — Phase 2 / Working with data — measure click trail for core jobs.
 *
 * Workflows covered:
 *  - WF3 "View TS chart for TR-004"
 *  - WF4 "Filter MFFD DOs by attribute / find Layup DOs"
 *  - WF5 "Bulk-annotate 3 DOs with the same annotation"
 *  - WF6 "Compare TR-004 to TR-005 side-by-side"
 *  - WF7 "Add Lab Journal entry to TR-004"
 *  - WF8 "Edit existing annotation on TR-004"
 *
 * No data mutations: forms are filled then Escape/Cancel before submit.
 */
import { test, type Page } from "@playwright/test";
import { loginAs } from "./helpers/auth";
import { ClickTrail } from "./helpers/click-trail";

const OUT = "/opt/shepard/aidocs/agent-findings/ux-scrutinizer-workflows-2026-05-24-evidence";
const LUMEN = 42;
const MFFD = 661923;

// Track LUMEN DO ids — TR-001 = 45, so TR-004 = 48 (per prior UI-018 evidence)
// We probe collections/42 first to confirm DO id mapping at runtime.

async function findDoIdByName(page: Page, name: string): Promise<number | null> {
  // Use Shepard API for a robust lookup
  const url = `/v2/collections/${LUMEN}/data-objects?pageNumber=0&pageSize=200`;
  const r = await page.evaluate(async (u) => {
    const resp = await fetch(u, { headers: { Accept: "application/json" } });
    if (!resp.ok) return null;
    return await resp.json();
  }, url);
  if (!r) return null;
  // r is paginated wrapper {content: [{appId, name, ...}]}
  const items: Array<Record<string, unknown>> = Array.isArray(r.content) ? r.content : (Array.isArray(r) ? r : []);
  const match = items.find(i => typeof i.name === "string" && (i.name as string).includes(name));
  return match ? Number(match.appId ?? match.id) : null;
}

// Each workflow test is independent — don't cascade-skip on one failure
test.describe.configure({ mode: "default" });

test("UX-WF-03 — View TS chart for TR-004", async ({ page }) => {
  test.setTimeout(180_000);
  await loginAs(page, "alice", "alice-demo");
  const trail = new ClickTrail(page, "wf03-view-ts-chart-tr004", OUT);

  await trail.goto(`/collections/${LUMEN}`, "Open LUMEN collection landing");
  // The page shows an error toast briefly during load; wait for real settle
  await page.waitForTimeout(8000);
  await trail.note(`After 8s settle, "Could not load" still visible? ${(await page.locator("body").innerText()).includes("Could not load")}`);

  // Find TR-004 link
  const tr004 = page.locator('a:has-text("TR-004"), tr:has-text("TR-004")').first();
  await trail.note(`TR-004 occurrences on landing: ${await tr004.count()}`);

  if (await tr004.count() > 0) {
    await trail.step("Click TR-004", async () => {
      await tr004.click();
      await page.waitForTimeout(1500);
    });
    await trail.note(`Landed on DO: ${page.url()}`);

    // Look for a timeseries reference link or "View chart" action
    const refsHeading = page.locator('text=/timeseries/i').first();
    await trail.note(`Timeseries-related visible elements: ${await refsHeading.count()}`);

    // Try clicking the first timeseries reference card / item
    const tsItem = page.locator('a:has-text("timeseries"), .v-list-item:has-text("timeseries"), button:has-text("Chart"), a[href*="timeseries"]').first();
    await trail.note(`TS item match count: ${await tsItem.count()}`);
    if (await tsItem.count() > 0) {
      await trail.step("Click first TS-related affordance", async () => {
        await tsItem.click();
        await page.waitForTimeout(1500);
      });
      await trail.note(`After TS click: ${page.url()}`);

      // Look for canvas / chart element
      const canvas = page.locator("canvas, svg.chart, .echarts, .apexcharts").first();
      await trail.note(`Chart canvas/svg count after navigation: ${await canvas.count()}`);

      // If we landed on a TS reference page with a "select channels to chart" UI,
      // try the most prominent button
      const chartButton = page.locator('button:has-text("Chart"), button:has-text("Plot"), button:has-text("Visualize")').first();
      if (await chartButton.count() > 0) {
        await trail.step("Click 'Chart/Plot' button", async () => {
          await chartButton.click();
          await page.waitForTimeout(3000);
        });
        await trail.note(`Chart canvas count after click: ${await page.locator("canvas, svg.chart, .echarts, .apexcharts").count()}`);
      }
    } else {
      await trail.note("No timeseries affordance found directly on DO page — must dig deeper");
    }
  } else {
    await trail.note("TR-004 not visible on collection landing — workflow blocked at click 0");
  }

  await trail.save({ persona: "researcher", phase: 2, frequency: "very-high" });
});

test("UX-WF-04 — Find MFFD Layup DOs (filter by name pattern)", async ({ page }) => {
  test.setTimeout(120_000);
  await loginAs(page, "alice", "alice-demo");
  const trail = new ClickTrail(page, "wf04-find-mffd-layup-dos", OUT);

  await trail.goto(`/collections/${MFFD}`, "Open MFFD-Dropbox collection landing");
  // MFFD has 8514 DOs; allow generous settle window
  await page.waitForTimeout(10_000);

  // Capture initial state — how many DOs visible?
  const initialRows = await page.locator('tr, .v-list-item, [data-test*="dataobject"]').count();
  await trail.note(`Initial visible row-ish elements: ${initialRows}`);

  // Find a filter / search input within the DataObjects panel
  const filterInput = page.locator('input[placeholder*="filter" i], input[placeholder*="search" i], input[placeholder*="name" i]').first();
  await trail.note(`Filter-input candidates: ${await filterInput.count()}`);
  if (await filterInput.count() > 0) {
    await trail.step("Click filter input", async () => {
      await filterInput.click();
    });
    await trail.type("Type 'Layup'", async () => {
      await filterInput.fill("Layup");
      await page.waitForTimeout(2500);
    });
    const filteredRows = await page.locator('tr, .v-list-item').count();
    await trail.note(`Visible rows after 'Layup' filter: ${filteredRows}`);
    const visibleTexts = await page.locator('tr, .v-list-item').evaluateAll(els =>
      els.slice(0, 15).map(e => (e as HTMLElement).innerText.slice(0, 120)));
    await trail.note(`Sample of visible rows: ${JSON.stringify(visibleTexts).slice(0, 1500)}`);
  }

  // Test attribute-based filter via Advanced search instead
  await trail.goto("/search", "Open advanced search");
  await page.waitForTimeout(1500);
  // The advanced search needs a query type + JSON DSL — capture the affordance shape
  const queryTypeSelect = page.locator('text=/Query Type/i').first();
  await trail.note(`Advanced search has Query Type selector: ${await queryTypeSelect.count() > 0}`);
  const codeView = page.locator('text=/code view/i, text=/JSON/i').first();
  await trail.note(`Advanced search advertises "Use Code view" / JSON: ${await codeView.count() > 0}`);

  await trail.save({ persona: "researcher", phase: 2, frequency: "high" });
});

test("UX-WF-05 — Bulk-annotate 3 DOs with same annotation", async ({ page }) => {
  test.setTimeout(120_000);
  await loginAs(page, "alice", "alice-demo");
  const trail = new ClickTrail(page, "wf05-bulk-annotate-3-dos", OUT);

  await trail.goto(`/collections/${LUMEN}`, "Open LUMEN collection landing");
  await page.waitForTimeout(3000);

  // Look for multi-select checkboxes in the DataObjects table/list
  const checkboxes = page.locator('input[type="checkbox"]:visible');
  const cbCount = await checkboxes.count();
  await trail.note(`Visible checkboxes on collection landing: ${cbCount}`);

  // Look for any "Select all" or "Bulk action" affordance
  const bulkAction = page.locator('button:has-text("Bulk"), button:has-text("Select"), [aria-label*="select" i]');
  await trail.note(`Bulk/select affordance count: ${await bulkAction.count()}`);

  if (cbCount === 0) {
    await trail.note("FINDING: no checkbox affordance for multi-select on DataObjects → bulk-annotate requires N visits to N DO detail pages");
    // Demonstrate the cost by visiting 3 DOs and counting clicks per annotation
    const dos = ["TR-001", "TR-002", "TR-003"];
    let totalClicksForBulk = 0;
    for (const name of dos) {
      const link = page.locator(`a:has-text("${name}")`).first();
      if (await link.count() === 0) {
        await trail.note(`${name} not visible — would need pagination/expansion`);
        continue;
      }
      await trail.step(`Visit ${name} (start of single-annotation cycle)`, async () => {
        await link.click();
        await page.waitForTimeout(1500);
      });
      totalClicksForBulk++;
      // Look for "Add Annotation" button
      const addAnno = page.locator('button:has-text("Add Annotation"), button:has-text("Annotate"), button:has-text("Add annotation")').first();
      await trail.note(`${name}: 'Add Annotation' affordance count: ${await addAnno.count()}`);
      if (await addAnno.count() > 0) {
        await trail.step(`Click 'Add Annotation' on ${name}`, async () => {
          await addAnno.click();
          await page.waitForTimeout(1500);
        });
        totalClicksForBulk++;
        // Cancel out — we don't mutate
        await page.keyboard.press("Escape");
        await page.waitForTimeout(500);
      }
      // Back to collection
      await trail.step(`Navigate back to collection (${name} done)`, async () => {
        await page.goto(`/collections/${LUMEN}`);
        await page.waitForTimeout(1500);
      });
      totalClicksForBulk++;
    }
    await trail.note(`Total clicks to set up annotation on 3 DOs: ${totalClicksForBulk} (NOT counting the form-fill itself per DO)`);
  } else {
    // Multi-select exists — try to use it
    await trail.step("Check 3 row checkboxes", async () => {
      for (let i = 0; i < Math.min(3, cbCount); i++) {
        await checkboxes.nth(i).check();
        await page.waitForTimeout(200);
      }
    });
    // Look for a bulk-action toolbar that appears
    const bulkAnnotate = page.locator('button:has-text("Annotate"), button:has-text("Bulk annotate")').first();
    await trail.note(`After selecting 3: bulk-annotate button count: ${await bulkAnnotate.count()}`);
  }

  await trail.save({ persona: "data-steward", phase: 2, frequency: "medium" });
});

test("UX-WF-06 — Compare TR-004 to TR-005 side-by-side", async ({ page }) => {
  test.setTimeout(90_000);
  await loginAs(page, "alice", "alice-demo");
  const trail = new ClickTrail(page, "wf06-compare-tr004-tr005", OUT);

  await trail.goto(`/collections/${LUMEN}`, "Open LUMEN collection landing");
  await page.waitForTimeout(8000);

  // Look for any "Compare" affordance
  const compareBtn = page.locator('button:has-text("Compare"), a:has-text("Compare"), [aria-label*="compare" i]');
  await trail.note(`Compare-affordance count: ${await compareBtn.count()}`);

  // No compare → measure the cost of doing it linearly
  await trail.note("Assuming no native Compare → measuring linear cost: visit TR-004, take note, back, visit TR-005, mental diff");
  const tr004 = page.locator('a:has-text("TR-004")').first();
  if (await tr004.count() > 0) {
    await trail.step("Click TR-004", async () => {
      await tr004.click();
      await page.waitForTimeout(1500);
    });
    // Capture key content
    const tr004Body = (await page.locator("main, .v-main").innerText().catch(() => "")).slice(0, 500);
    await trail.note(`TR-004 main content excerpt: ${tr004Body}`);

    await trail.step("Browser back", async () => {
      await page.goBack();
      await page.waitForTimeout(1500);
    });

    const tr005 = page.locator('a:has-text("TR-005")').first();
    if (await tr005.count() > 0) {
      await trail.step("Click TR-005", async () => {
        await tr005.click();
        await page.waitForTimeout(1500);
      });
      const tr005Body = (await page.locator("main, .v-main").innerText().catch(() => "")).slice(0, 500);
      await trail.note(`TR-005 main content excerpt: ${tr005Body}`);
    }
  }

  await trail.save({ persona: "researcher-investigation", phase: 2, frequency: "high" });
});

test("UX-WF-07 — Add Lab Journal entry to TR-004", async ({ page }) => {
  test.setTimeout(90_000);
  await loginAs(page, "alice", "alice-demo");
  const trail = new ClickTrail(page, "wf07-add-labjournal-entry", OUT);

  await trail.goto(`/collections/${LUMEN}`, "Open LUMEN collection");
  await page.waitForTimeout(8000);
  const tr004 = page.locator('a:has-text("TR-004")').first();
  if (await tr004.count() > 0) {
    await trail.step("Click TR-004", async () => {
      await tr004.click();
      await page.waitForTimeout(1500);
    });
    // Find the Lab Journal panel / button
    const labJournalAffordance = page.locator('text=/lab.?journal/i, text=/notebook/i, button:has-text("Add Entry"), a:has-text("Lab Journal")');
    await trail.note(`Lab Journal affordance candidates: ${await labJournalAffordance.count()}`);

    // Try expanding any expansion panel that mentions lab journal
    const ljPanel = page.locator('.v-expansion-panel-title').filter({ hasText: /lab.?journal|notebook/i }).first();
    if (await ljPanel.count() > 0) {
      await trail.step("Expand Lab Journal panel", async () => {
        await ljPanel.click();
        await page.waitForTimeout(1500);
      });
      const addEntry = page.locator('button:has-text("Add"), button:has-text("New"), button:has-text("Create")').first();
      await trail.note(`Add-entry button candidates after expand: ${await addEntry.count()}`);
      if (await addEntry.count() > 0) {
        await trail.step("Click 'Add Entry' (or similar)", async () => {
          await addEntry.click();
          await page.waitForTimeout(2000);
        });
        // Dialog likely opens; do not submit
        await trail.note(`Dialog visible? ${await page.locator('.v-dialog:visible, [role="dialog"]:visible').count() > 0}`);
        await page.keyboard.press("Escape");
      }
    } else {
      await trail.note("No Lab Journal panel visible on DO detail at default state");
    }
  }

  await trail.save({ persona: "researcher", phase: 2, frequency: "medium" });
});

test("UX-WF-08 — Edit existing annotation on TR-004", async ({ page }) => {
  test.setTimeout(90_000);
  await loginAs(page, "alice", "alice-demo");
  const trail = new ClickTrail(page, "wf08-edit-annotation", OUT);

  await trail.goto(`/collections/${LUMEN}`, "Open LUMEN collection");
  await page.waitForTimeout(8000);
  const tr004 = page.locator('a:has-text("TR-004")').first();
  if (await tr004.count() > 0) {
    await trail.step("Click TR-004", async () => {
      await tr004.click();
      await page.waitForTimeout(1500);
    });
    // Find annotations area
    const annoPanel = page.locator('.v-expansion-panel-title').filter({ hasText: /annotation/i }).first();
    await trail.note(`Annotation panel count: ${await annoPanel.count()}`);
    if (await annoPanel.count() > 0) {
      await trail.step("Expand Annotations panel", async () => {
        await annoPanel.click();
        await page.waitForTimeout(1500);
      });
    }
    // Look for any pencil/edit icon button next to an annotation
    const editBtn = page.locator('button[aria-label*="edit" i], button:has(svg.mdi-pencil), [data-test*="edit-annotation"]');
    await trail.note(`Edit-button candidates: ${await editBtn.count()}`);
    // Or maybe click any annotation row to reveal an edit affordance?
    const annoRow = page.locator('.v-list-item, tr').filter({ hasText: /^\w+:\s/ }).first();
    await trail.note(`Annotation row candidate count (k:v pattern): ${await annoRow.count()}`);
    if (await annoRow.count() > 0) {
      await trail.step("Click first annotation row", async () => {
        await annoRow.click();
        await page.waitForTimeout(1000);
      });
      const editAfterClick = page.locator('button[aria-label*="edit" i], button:has(svg.mdi-pencil)');
      await trail.note(`Edit button count AFTER clicking row: ${await editAfterClick.count()}`);
    }
  }

  await trail.save({ persona: "data-steward", phase: 2, frequency: "high" });
});
