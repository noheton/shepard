import { test, expect } from "@playwright/test";
import { loginAs } from "./helpers/auth";

// Repro: operator reports "Error while fetching collection:" on the LUMEN
// collection page after the V2-SWEEP redeploy (2026-06-10). Captures every
// failed request + console error + visible toast text.
const LUMEN = "019eb019-d49b-7131-b2d2-3f3107d36a4f";

test("LUMEN collection page loads without fetch errors", async ({ page }) => {
  const failures: string[] = [];
  page.on("response", (r) => {
    if (r.status() >= 400) failures.push(`${r.status()} ${r.request().method()} ${r.url()}`);
  });
  page.on("console", (m) => {
    if (m.type() === "error") failures.push(`console: ${m.text().slice(0, 200)}`);
  });

  await loginAs(page, "admin", "admin-demo");
  await page.goto(`/collections/${LUMEN}`);
  await page.waitForTimeout(10_000);

  const toasts = await page
    .locator(".v-snackbar, .v-alert, [role=alert]")
    .allTextContents();
  console.log("TOASTS:", JSON.stringify(toasts));
  console.log("FAILURES:\n" + failures.join("\n"));

  await page.screenshot({ path: "screenshots/repro-lumen-collection.png", fullPage: false });
  expect(failures.filter((f) => !f.includes("favicon"))).toEqual([]);
});
