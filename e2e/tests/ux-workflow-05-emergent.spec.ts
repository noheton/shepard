/**
 * UX-WF-05 — Emergent workflows surfaced during the depth-walk.
 *
 *  - WF13 "Sign-out and back in — full round-trip" (measures auth ceremony cost)
 *  - WF14 "Navigate sibling chain TR-004 → TR-005 → TR-006" (next/prev nav)
 *  - WF15 "Land on home and reach LUMEN MFFD MFFD-Dropbox in one click" (recent collections)
 */
import { test } from "@playwright/test";
import { loginAs } from "./helpers/auth";
import { ClickTrail } from "./helpers/click-trail";

const OUT = "/opt/shepard/aidocs/agent-findings/ux-scrutinizer-workflows-2026-05-24-evidence";
const LUMEN = 42;

test.describe.configure({ mode: "serial" });

test("UX-WF-13 — Navigate sibling chain TR-004 → TR-005 → TR-006", async ({ page }) => {
  test.setTimeout(180_000);
  await loginAs(page, "alice", "alice-demo");
  const trail = new ClickTrail(page, "wf13-sibling-chain-nav", OUT);

  await trail.goto(`/collections/${LUMEN}`, "Open LUMEN collection");
  await page.waitForTimeout(8000);

  // Click TR-004
  const tr004 = page.locator('a:has-text("TR-004")').first();
  if (await tr004.count() === 0) {
    await trail.note("TR-004 not visible at default state");
    await trail.save({ persona: "researcher-investigation", phase: 2, frequency: "high" });
    return;
  }
  await trail.step("Click TR-004", async () => {
    await tr004.click();
    await page.waitForTimeout(1500);
  });
  await page.waitForTimeout(3000);

  // Look for next/prev sibling nav (arrows, prev/next button, breadcrumb)
  const sibNav = page.locator('button[aria-label*="next" i], button[aria-label*="prev" i], a:has-text("Next"), a:has-text("Previous"), .v-pagination, [data-test*="sibling"]');
  await trail.note(`Sibling-nav affordance count: ${await sibNav.count()}`);

  // Find TR-005 reference anywhere on the page (successor link?)
  const tr005Anywhere = page.locator('a:has-text("TR-005")').first();
  await trail.note(`TR-005 link visible from TR-004 detail: ${await tr005Anywhere.count()}`);
  if (await tr005Anywhere.count() > 0) {
    await trail.step("Click TR-005 (from TR-004 detail)", async () => {
      await tr005Anywhere.click();
      await page.waitForTimeout(1500);
    });
    await page.waitForTimeout(2000);
    const tr006Anywhere = page.locator('a:has-text("TR-006")').first();
    await trail.note(`TR-006 link visible from TR-005 detail: ${await tr006Anywhere.count()}`);
    if (await tr006Anywhere.count() > 0) {
      await trail.step("Click TR-006 (from TR-005 detail)", async () => {
        await tr006Anywhere.click();
        await page.waitForTimeout(1500);
      });
    }
  } else {
    // Must go back to collection landing and click the sibling
    await trail.step("Browser back to LUMEN", async () => {
      await page.goBack();
      await page.waitForTimeout(1500);
    });
    await page.waitForTimeout(2000);
    const tr005 = page.locator('a:has-text("TR-005")').first();
    if (await tr005.count() > 0) {
      await trail.step("Click TR-005 from collection list", async () => {
        await tr005.click();
        await page.waitForTimeout(1500);
      });
      await page.waitForTimeout(2000);
      await trail.step("Browser back to LUMEN", async () => {
        await page.goBack();
        await page.waitForTimeout(1500);
      });
      const tr006 = page.locator('a:has-text("TR-006")').first();
      if (await tr006.count() > 0) {
        await trail.step("Click TR-006 from collection list", async () => {
          await tr006.click();
          await page.waitForTimeout(1500);
        });
      }
    }
  }

  await trail.save({ persona: "researcher-investigation", phase: 3, frequency: "high" });
});

test("UX-WF-14 — Reach LUMEN from home in 1 click (recent collections)", async ({ page }) => {
  test.setTimeout(60_000);
  await loginAs(page, "alice", "alice-demo");
  const trail = new ClickTrail(page, "wf14-home-to-lumen-one-click", OUT);

  await trail.goto("/", "Home");
  await page.waitForTimeout(8000);
  await trail.note(`"Could not load" visible? ${(await page.locator("body").innerText()).includes("Could not load")}`);

  // Look for a "Recent collections" surface that shows LUMEN directly
  const lumenLink = page.locator('a:has-text("LUMEN"), .v-card:has-text("LUMEN") a').first();
  await trail.note(`LUMEN link on home: ${await lumenLink.count()}`);
  if (await lumenLink.count() > 0) {
    await trail.step("Click LUMEN from home", async () => {
      await lumenLink.click();
      await page.waitForTimeout(1500);
    });
    await trail.note(`Landed at: ${page.url()}`);
  } else {
    await trail.note("No LUMEN link on home — must go via /collections (2 clicks min)");
  }
  await trail.save({ persona: "researcher-frequent-collection", phase: 1, frequency: "very-high" });
});

test("UX-WF-15 — Help discoverability for 'how do I export TR-004'", async ({ page }) => {
  test.setTimeout(90_000);
  await loginAs(page, "alice", "alice-demo");
  const trail = new ClickTrail(page, "wf15-help-export-discovery", OUT);

  await trail.goto("/", "Home");
  await page.waitForTimeout(5000);

  const helpLink = page.locator('a[href*="/help"], a:has-text("Help")').first();
  await trail.note(`Help link on home count: ${await helpLink.count()}`);
  if (await helpLink.count() > 0) {
    await trail.step("Click Help link", async () => {
      await helpLink.click();
      await page.waitForTimeout(1500);
    });
    await page.waitForTimeout(3000);
    await trail.note(`Help URL: ${page.url()}`);

    const helpBody = (await page.locator("main, .v-main").innerText().catch(() => "")).slice(0, 1500);
    await trail.note(`Help body excerpt: ${helpBody}`);

    // Search for "export" within Help
    const helpSearch = page.locator('input[type="search"]:visible, input[placeholder*="search" i]:visible').first();
    if (await helpSearch.count() > 0) {
      await trail.step("Click help search input", async () => {
        await helpSearch.click();
      });
      await trail.type("Type 'export'", async () => {
        await helpSearch.fill("export");
        await page.waitForTimeout(2000);
      });
      const helpResults = await page.evaluate(() => {
        const r: string[] = [];
        document.querySelectorAll(".v-list-item, .search-result, [role='option']").forEach(el => {
          const t = (el as HTMLElement).innerText.trim();
          if (t) r.push(t.slice(0, 200));
        });
        return r.slice(0, 20);
      });
      await trail.note(`Help-search results for 'export': ${JSON.stringify(helpResults).slice(0, 1500)}`);
    }
  }

  await trail.save({ persona: "researcher-new-user", phase: 5, frequency: "high" });
});
