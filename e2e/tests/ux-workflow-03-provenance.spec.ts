/**
 * UX-WF-03 — Phase 3 / Provenance & lineage workflows.
 *
 * Workflows covered:
 *  - WF9  "From TR-006 trace back to TR-004 anomaly"
 *  - WF10 "For TR-004 show me all attached files"
 *
 * No data mutations.
 */
import { test } from "@playwright/test";
import { loginAs } from "./helpers/auth";
import { ClickTrail } from "./helpers/click-trail";

const OUT = "/opt/shepard/aidocs/agent-findings/ux-scrutinizer-workflows-2026-05-24-evidence";
const LUMEN = 42;

test.describe.configure({ mode: "default" });

test("UX-WF-09 — Trace TR-006 back to TR-004 anomaly via provenance", async ({ page }) => {
  test.setTimeout(180_000);
  await loginAs(page, "alice", "alice-demo");
  const trail = new ClickTrail(page, "wf09-trace-tr006-to-tr004", OUT);

  await trail.goto(`/collections/${LUMEN}`, "Open LUMEN collection landing");
  await page.waitForTimeout(8000);
  await trail.note(`"Could not load" visible? ${(await page.locator("body").innerText()).includes("Could not load")}`);

  // Click TR-006 (post-repair re-test)
  const tr006 = page.locator('a:has-text("TR-006")').first();
  await trail.note(`TR-006 link count: ${await tr006.count()}`);
  if (await tr006.count() > 0) {
    await trail.step("Click TR-006 (post-repair re-test)", async () => {
      await tr006.click();
      await page.waitForTimeout(1500);
    });
    await page.waitForTimeout(3000);

    // Look for a lineage / provenance graph component
    const lineageSection = page.locator('text=/lineage|provenance|predecessor|successor|prov.?graph|ancestor|chain/i');
    await trail.note(`Lineage/provenance text occurrences: ${await lineageSection.count()}`);

    // Try expanding any provenance panel
    const provPanel = page.locator('.v-expansion-panel-title').filter({ hasText: /lineage|provenance|predecessor|prov/i }).first();
    if (await provPanel.count() > 0) {
      await trail.step("Expand provenance/lineage panel", async () => {
        await provPanel.click();
        await page.waitForTimeout(3000);
      });
      // Look for a graph (SVG) or list of predecessor nodes
      const graphSvg = page.locator('svg.lineage, .vue-flow, svg g.node, .provenance-graph svg');
      await trail.note(`Graph SVG count: ${await graphSvg.count()}`);
      // Look for TR-005 (immediate predecessor) and TR-004 (root anomaly) text
      await trail.note(`TR-005 appears in DOM: ${(await page.content()).includes("TR-005")}`);
      await trail.note(`TR-004 appears in DOM: ${(await page.content()).includes("TR-004")}`);

      // Try clicking a TR-005 node if present
      const tr005Node = page.locator('text="TR-005", a:has-text("TR-005")').first();
      if (await tr005Node.count() > 0) {
        await trail.step("Click TR-005 node in lineage graph", async () => {
          await tr005Node.click();
          await page.waitForTimeout(1500);
        });
        await page.waitForTimeout(3000);
        const tr004Now = page.locator('a:has-text("TR-004"), text="TR-004"').first();
        await trail.note(`TR-004 visible at TR-005 detail: ${await tr004Now.count()}`);
        if (await tr004Now.count() > 0) {
          await trail.step("Click TR-004 (root anomaly)", async () => {
            await tr004Now.click();
            await page.waitForTimeout(1500);
          });
        }
      }
    } else {
      await trail.note("No lineage/provenance panel found at default DO detail layout");
    }
  } else {
    await trail.note("TR-006 not visible on collection landing — workflow blocked");
  }

  await trail.save({ persona: "researcher-investigation", phase: 3, frequency: "high" });
});

test("UX-WF-10 — For TR-004 show all attached files", async ({ page }) => {
  test.setTimeout(120_000);
  await loginAs(page, "alice", "alice-demo");
  const trail = new ClickTrail(page, "wf10-tr004-attached-files", OUT);

  await trail.goto(`/collections/${LUMEN}`, "Open LUMEN collection");
  await page.waitForTimeout(8000);
  const tr004 = page.locator('a:has-text("TR-004")').first();
  await trail.note(`TR-004 link count: ${await tr004.count()}`);
  if (await tr004.count() > 0) {
    await trail.step("Click TR-004", async () => {
      await tr004.click();
      await page.waitForTimeout(1500);
    });
    await page.waitForTimeout(3000);

    // Look for File References panel/section
    const filePanel = page.locator('.v-expansion-panel-title, h2, h3').filter({ hasText: /file/i });
    await trail.note(`File-related section/panel headings: ${await filePanel.count()}`);
    const fileText = await filePanel.evaluateAll(els =>
      els.slice(0, 10).map(e => (e as HTMLElement).innerText.slice(0, 80)));
    await trail.note(`File-related panel headings text: ${JSON.stringify(fileText)}`);

    // Try expanding the first one
    const fileExpansionTitle = page.locator('.v-expansion-panel-title').filter({ hasText: /file/i }).first();
    if (await fileExpansionTitle.count() > 0) {
      await trail.step("Expand File-References panel", async () => {
        await fileExpansionTitle.click();
        await page.waitForTimeout(2000);
      });
      // Count visible file entries
      const fileRows = page.locator('.v-expansion-panel-text [data-test*="file"], .v-list-item, tr').filter({ hasText: /\.(pdf|csv|jpg|png|h5|hdf|rdk|txt|zip|tar|stl|step)$/i });
      await trail.note(`Visible file rows: ${await fileRows.count()}`);
      const allText = (await page.locator('.v-expansion-panel-text').first().innerText().catch(() => "")).slice(0, 800);
      await trail.note(`Expanded file-panel content excerpt: ${allText}`);
    } else {
      await trail.note("No file-references panel found");
    }

    // Look for thumbnails / previews
    const thumbs = page.locator('img[src*="thumbnail"], img[src*="preview"], .thumbnail');
    await trail.note(`Thumbnail count on DO page: ${await thumbs.count()}`);
  }

  await trail.save({ persona: "researcher", phase: 3, frequency: "very-high" });
});
