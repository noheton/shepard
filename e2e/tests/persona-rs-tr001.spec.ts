import { test } from "@playwright/test";
import { loginAs } from "./helpers/auth";

/**
 * Follow-up walk for the Reluctant Senior persona — TR-001 direct,
 * AddAnnotationDialog open attempt, Collection-level Lineage panel.
 * See: aidocs/agent-findings/persona-reluctant-senior-2026-05-24.md
 */

test("TR-001 detail page direct", async ({ page }) => {
  await loginAs(page, "alice", "alice-demo");
  await page.goto("/collections/42", { waitUntil: "networkidle" }).catch(() => {});
  await page.waitForTimeout(1500);
  const link = page.getByRole("link", { name: /TR-001/i }).first();
  if (await link.isVisible().catch(() => false)) {
    await link.click();
    await page.waitForLoadState("networkidle").catch(() => {});
    await page.waitForTimeout(2500);
    await page.screenshot({ path: "screenshots/persona-reluctant-2026-05-24/09-tr001-detail.png", fullPage: true });
    console.log(`[persona] arrived at: ${page.url()}`);
  } else {
    const side = page.locator("aside, nav").getByText("TR-001").first();
    if (await side.isVisible().catch(() => false)) {
      await side.click();
      await page.waitForLoadState("networkidle").catch(() => {});
      await page.waitForTimeout(2500);
      await page.screenshot({ path: "screenshots/persona-reluctant-2026-05-24/09-tr001-detail.png", fullPage: true });
      console.log(`[persona] sidebar arrived at: ${page.url()}`);
    } else {
      console.log(`[persona] could not find TR-001 link`);
    }
  }
});

test("Open AddAnnotationDialog cold", async ({ page }) => {
  await loginAs(page, "alice", "alice-demo");
  await page.goto("/collections/42", { waitUntil: "networkidle" }).catch(() => {});
  await page.waitForTimeout(2000);
  const annoAdd = page.getByRole("button", { name: /add.*annotation|annotate/i }).first();
  if (await annoAdd.isVisible().catch(() => false)) {
    await annoAdd.click();
    await page.waitForTimeout(1500);
    await page.screenshot({ path: "screenshots/persona-reluctant-2026-05-24/10-add-annotation.png", fullPage: true });
  } else {
    console.log("[persona] add-annotation button not visible from collection landing");
    await page.screenshot({ path: "screenshots/persona-reluctant-2026-05-24/10-no-anno-button.png", fullPage: true });
  }
});

test("Provenance / lineage tab on collection 42", async ({ page }) => {
  await loginAs(page, "alice", "alice-demo");
  await page.goto("/collections/42", { waitUntil: "networkidle" }).catch(() => {});
  await page.waitForTimeout(1500);
  const lineage = page.getByText(/dataset lineage|lineage/i).first();
  if (await lineage.isVisible().catch(() => false)) {
    await lineage.click();
    await page.waitForTimeout(2000);
    await page.screenshot({ path: "screenshots/persona-reluctant-2026-05-24/11-lineage.png", fullPage: true });
  }
});
