/**
 * UX-WF-04 — Phase 4 / Admin & ops workflows.
 *
 * Workflows covered:
 *  - WF11 "Admin: see recent activity across all collections"
 *  - WF12 "Admin: disable a feature flag"
 *
 * Both use admin credentials.
 */
import { test } from "@playwright/test";
import { loginAs } from "./helpers/auth";
import { ClickTrail } from "./helpers/click-trail";

const OUT = "/opt/shepard/aidocs/agent-findings/ux-scrutinizer-workflows-2026-05-24-evidence";

test.describe.configure({ mode: "serial" });

test("UX-WF-11 — Admin: recent activity feed", async ({ page }) => {
  test.setTimeout(120_000);
  await loginAs(page, "admin", "admin-demo");
  const trail = new ClickTrail(page, "wf11-admin-activity-feed", OUT);

  await trail.goto("/", "Land on admin home");
  await page.waitForTimeout(5000);

  // Is there a global Activity feed CTA?
  const activityLink = page.locator('a:has-text("Activity"), a:has-text("Recent"), a[href*="activity"]').first();
  await trail.note(`Activity-link affordance count from home: ${await activityLink.count()}`);

  // Try /admin landing
  await trail.goto("/admin", "Open /admin landing");
  await page.waitForTimeout(5000);
  const adminBodyText = (await page.locator("body").innerText()).slice(0, 1500);
  await trail.note(`/admin body excerpt: ${adminBodyText}`);
  const adminPanels = await page
    .locator('a:visible[href*="/admin/"], .v-list-item:visible')
    .evaluateAll(els => els.slice(0, 30).map(e => ({
      text: (e as HTMLElement).innerText.trim().slice(0, 80),
      href: (e as HTMLAnchorElement).href || null,
    })));
  await trail.note(`/admin visible nav items: ${JSON.stringify(adminPanels).slice(0, 1500)}`);

  // Look for activity / audit / provenance link
  const auditLink = page.locator('a[href*="activity"], a[href*="audit"], a[href*="prov"]').first();
  await trail.note(`Audit/activity nav link count under /admin: ${await auditLink.count()}`);
  if (await auditLink.count() > 0) {
    await trail.step("Click activity/audit nav link", async () => {
      await auditLink.click();
      await page.waitForTimeout(1500);
    });
    await page.waitForTimeout(3000);
    await trail.note(`After activity click URL: ${page.url()}`);
    const rowCount = await page.locator('tr, .v-list-item').count();
    await trail.note(`Activity rows visible: ${rowCount}`);
  } else {
    await trail.note("No 'activity' / 'audit' nav link found in admin area — reconstruction required");
  }

  await trail.save({ persona: "admin", phase: 4, frequency: "medium" });
});

test("UX-WF-12 — Admin: disable a feature flag", async ({ page }) => {
  test.setTimeout(120_000);
  await loginAs(page, "admin", "admin-demo");
  const trail = new ClickTrail(page, "wf12-admin-disable-feature-flag", OUT);

  await trail.goto("/admin", "Open /admin landing");
  await page.waitForTimeout(5000);

  const featureLink = page.locator('a[href*="feature"], a:has-text("Feature")').first();
  await trail.note(`Feature-flag link count under /admin: ${await featureLink.count()}`);
  if (await featureLink.count() > 0) {
    await trail.step("Click feature-flags link", async () => {
      await featureLink.click();
      await page.waitForTimeout(1500);
    });
    await page.waitForTimeout(3000);
    await trail.note(`Feature-flags URL: ${page.url()}`);
    const switches = page.locator('input[type="checkbox"]:visible, .v-switch:visible');
    await trail.note(`Switch/checkbox toggles visible: ${await switches.count()}`);
    // DO NOT actually click — we don't mutate. Just measure discoverability.
    const flagNames = await page.locator('.v-list-item, tr, .v-card').evaluateAll(els =>
      els.slice(0, 20).map(e => (e as HTMLElement).innerText.trim().slice(0, 100)));
    await trail.note(`Visible feature-flag rows (first 20): ${JSON.stringify(flagNames).slice(0, 2000)}`);
  } else {
    // Try direct URL guess
    await trail.goto("/admin/features", "URL guess: /admin/features");
    await page.waitForTimeout(3000);
    await trail.note(`After /admin/features URL guess: ${page.url()}; title: ${await page.title()}`);
  }

  await trail.save({ persona: "instance-admin", phase: 4, frequency: "low" });
});
