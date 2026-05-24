/**
 * UX-WF-01 — Phase 1 / Discovery — measure click trail for first-contact workflows.
 *
 * Workflows covered in this spec:
 *  - WF1 "Anonymous visitor first contact"  → home page, first meaningful action
 *  - WF2 "Find TR-004 anomaly investigation" → header search, browse, URL guess
 *
 * Method: ClickTrail helper records every navigation + click, screenshots,
 * counts page transitions. Output:
 *   - <evidence>/wf01-discovery-*-trail.json
 *   - <evidence>/wf01-discovery-*-step-NN.png
 *
 * No data mutations. Observation only.
 */
import { test, type Page } from "@playwright/test";
import { loginAs } from "./helpers/auth";
import { ClickTrail } from "./helpers/click-trail";
import * as path from "path";

const OUT = "/opt/shepard/aidocs/agent-findings/ux-scrutinizer-workflows-2026-05-24-evidence";

test.describe.configure({ mode: "serial" });

test("UX-WF-01a — Anonymous first contact (Phase 1 Discovery)", async ({ browser }) => {
  test.setTimeout(60_000);
  // Use a fresh context with no storage state so we are truly anonymous
  const ctx = await browser.newContext({ storageState: undefined });
  const page = await ctx.newPage();

  const trail = new ClickTrail(page, "wf01a-anonymous-first-contact", OUT);

  await trail.goto("/", "Land on root URL as anonymous visitor");
  await trail.note(`Title: "${await page.title()}"`);

  // What CTAs are visible? Capture visible top-level affordances.
  const visibleCtas = await page
    .locator('button:visible, a:visible[href]')
    .evaluateAll((els: Element[]) => els.slice(0, 30).map(e => ({
      text: (e as HTMLElement).innerText.trim().slice(0, 80),
      href: (e as HTMLAnchorElement).href || null,
      tag: e.tagName,
    })));
  await trail.note(`Visible top-30 CTAs: ${JSON.stringify(visibleCtas).slice(0, 1000)}`);

  // Heuristic "first meaningful action" — try the most prominent CTA the user
  // would hit (Sign in or Explore). We click the first sign-in link to measure
  // depth-to-login. Record both whether ANY visible link points at /collections.
  const exploreLink = page.locator('a[href*="/collections"]:visible').first();
  if (await exploreLink.count() > 0) {
    await trail.step("Click first visible link to /collections", async () => {
      await exploreLink.click();
    });
    await trail.note(`Result URL: ${page.url()}`);
  } else {
    await trail.note("No anonymous /collections link visible on home — user must sign in first");
  }

  await trail.save({ persona: "anonymous-visitor", phase: 1 });
  await ctx.close();
});

test("UX-WF-01b — Find TR-004 via header search (Phase 1 Discovery)", async ({ page }) => {
  test.setTimeout(90_000);
  await loginAs(page, "alice", "alice-demo");
  const trail = new ClickTrail(page, "wf01b-find-tr004-search", OUT);

  await trail.goto("/", "Land on app home, logged in");

  // Try the header search affordance
  const searchInput = page.locator('input[type="text"]:visible, input[type="search"]:visible').first();
  if (await searchInput.count() > 0) {
    await trail.step("Click header search input", async () => {
      await searchInput.click();
    });
    await trail.type("Type 'TR-004' into search", async () => {
      await searchInput.fill("TR-004");
      await page.waitForTimeout(1500);
    });
    // Capture what the search reveals
    const visibleResults = await page.evaluate(() => {
      // Capture text of anything that looks like search results
      const txt: string[] = [];
      document.querySelectorAll(".v-list-item, .v-menu .v-list, [role='listbox']").forEach(el => {
        const t = (el as HTMLElement).innerText.trim();
        if (t.length > 0 && t.length < 500) txt.push(t.slice(0, 200));
      });
      return txt.slice(0, 20);
    });
    await trail.note(`Search dropdown results (top 20): ${JSON.stringify(visibleResults).slice(0, 1500)}`);
    await trail.step("Press Enter to submit search", async () => {
      await searchInput.press("Enter");
      await page.waitForTimeout(1500);
    });
    await trail.note(`After-submit URL: ${page.url()}`);
    // Capture result page content
    const bodyTxt = (await page.locator("body").innerText()).slice(0, 1500);
    await trail.note(`Result page first 1500 chars: ${bodyTxt}`);
  } else {
    await trail.note("No visible search input on home — search affordance missing");
  }

  await trail.save({ persona: "researcher-via-search", phase: 1 });
});

test("UX-WF-01c — Find TR-004 by browsing /collections (Phase 1 Discovery)", async ({ page }) => {
  test.setTimeout(120_000);
  await loginAs(page, "alice", "alice-demo");
  const trail = new ClickTrail(page, "wf01c-find-tr004-browse", OUT);

  await trail.goto("/collections", "Open collections list");
  // The page briefly shows a "Could not load" toast before the data settles.
  // Wait longer to measure the eventually-rendered state.
  await page.waitForTimeout(8000);
  await trail.note(`After 8s settle, "Could not load" still visible? ${(await page.locator("body").innerText()).includes("Could not load")}`);

  // Find the LUMEN collection link
  const lumenRow = page.locator('a:has-text("LUMEN"), tr:has-text("LUMEN"), .v-list-item:has-text("LUMEN")').first();
  await trail.note(`LUMEN row count: ${await lumenRow.count()}`);
  if (await lumenRow.count() > 0) {
    await trail.step("Click LUMEN collection row", async () => {
      await lumenRow.click();
      await page.waitForTimeout(1500);
    });
    await trail.note(`Arrived at: ${page.url()}`);

    // Find TR-004 in the collection
    const tr004 = page.locator('a:has-text("TR-004"), tr:has-text("TR-004"), .v-list-item:has-text("TR-004")').first();
    await trail.note(`TR-004 element count on collection page: ${await tr004.count()}`);
    if (await tr004.count() > 0) {
      await trail.step("Click TR-004 DataObject", async () => {
        await tr004.click();
        await page.waitForTimeout(1500);
      });
      await trail.note(`Final URL: ${page.url()}`);
    } else {
      await trail.note("TR-004 not visible on collection landing — pagination or accordion needed");
      // Try expanding any DataObjects panel
      const dobjPanel = page.locator('.v-expansion-panel-title:has-text("DataObject"), button:has-text("DataObjects")').first();
      if (await dobjPanel.count() > 0) {
        await trail.step("Expand DataObjects panel", async () => {
          await dobjPanel.click();
          await page.waitForTimeout(1500);
        });
        const tr004Again = page.locator('a:has-text("TR-004"), tr:has-text("TR-004"), .v-list-item:has-text("TR-004")').first();
        if (await tr004Again.count() > 0) {
          await trail.step("Click TR-004 after expanding panel", async () => {
            await tr004Again.click();
            await page.waitForTimeout(1500);
          });
          await trail.note(`Final URL: ${page.url()}`);
        }
      }
    }
  }

  await trail.save({ persona: "researcher-via-browse", phase: 1 });
});

test("UX-WF-01d — Find TR-004 by URL guess (Phase 1 Discovery)", async ({ page }) => {
  test.setTimeout(60_000);
  await loginAs(page, "alice", "alice-demo");
  const trail = new ClickTrail(page, "wf01d-find-tr004-url-guess", OUT);

  // Researcher might try /search?q=TR-004 or guess /collections/42
  await trail.goto("/search?q=TR-004", "Try /search?q= URL guess");
  await page.waitForTimeout(2000);
  const url1 = page.url();
  const bodyTxt1 = (await page.locator("body").innerText()).slice(0, 1000);
  await trail.note(`/search URL result: ${url1}; body excerpt: ${bodyTxt1.slice(0, 500)}`);

  await trail.goto("/collections/42", "Try /collections/42 (knows the id)");
  await page.waitForTimeout(1500);
  await trail.note(`Landed: ${page.url()}; title: ${await page.title()}`);

  await trail.save({ persona: "power-user-url-guess", phase: 1 });
});
