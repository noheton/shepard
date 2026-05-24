/**
 * UI-2026-05-24-019 — Create-DataObject dialog fill+cancel walk.
 *
 * Context: ux-auditor flagged HYPOTHESIS — the dialog might error mid-wizard
 * before the user reaches the Create step. The first walk only opened+escaped.
 * This spec fills the dialog through all wizard steps and Cancels — NO mutation.
 *
 * Hard rule: never click "Create". Cancel/X/Escape ONLY.
 *
 * Method:
 *   1. Auth as alice
 *   2. Visit LUMEN collection (42)
 *   3. Click "Add new data object" (sidebar)
 *   4. If template picker shows: click "Start from blank" or close (we want the
 *      blank-form wizard for the fill test)
 *   5. Fill name + description on step 1, click Next
 *   6. On step 2, optionally add an attribute, then click Cancel
 *   7. Verify dialog closed and no new DataObject was created
 *
 * Output: screenshots + console errors + network 4xx/5xx evidence file.
 */
import { test, expect, type Page, type ConsoleMessage, type Request, type Response } from "@playwright/test";
import * as fs from "fs";
import * as path from "path";

const KC = process.env.KEYCLOAK_HOST || "https://shepard-auth.nuclide.systems";
const REALM = "shepard-demo";
const OUT = "/opt/shepard/aidocs/agent-findings/ui-018-019-evidence-2026-05-24";
const LUMEN_COLL = 42;
const TEST_NAME = `UI-019-test-do-DELETE-ME-${Date.now()}`;

fs.mkdirSync(OUT, { recursive: true });

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

test("UI-019 — Create DataObject dialog: fill stepper, cancel, verify no write", async ({ page }) => {
  test.setTimeout(120_000);
  await page.setViewportSize({ width: 1920, height: 1080 });

  // Phase tracking: we want to separate page-load errors (pre-existing)
  // from dialog-interaction errors (the ones UI-019 is actually about).
  type Phased<T> = { phase: string; entry: T };
  const consoleErrors: Phased<string>[] = [];
  const networkErrors: Phased<{ url: string; status: number; method: string }>[] = [];
  const mutationCalls: { url: string; method: string }[] = [];
  let currentPhase = "page-load";

  page.on("console", (msg: ConsoleMessage) => {
    if (msg.type() === "error") consoleErrors.push({ phase: currentPhase, entry: msg.text() });
  });
  page.on("pageerror", (e: Error) => consoleErrors.push({ phase: currentPhase, entry: `PAGEERROR: ${e.message}` }));
  page.on("response", (resp: Response) => {
    if (resp.status() >= 400 && !resp.url().includes(".well-known")) {
      networkErrors.push({
        phase: currentPhase,
        entry: { url: resp.url(), status: resp.status(), method: resp.request().method() },
      });
    }
  });
  page.on("request", (req: Request) => {
    const m = req.method();
    if (m === "POST" || m === "PUT" || m === "PATCH" || m === "DELETE") {
      // log any potentially mutating request — flag if it hits the DataObject create endpoint
      if (/dataObjects|data-objects|dataobjects/i.test(req.url())) {
        mutationCalls.push({ url: req.url(), method: m });
      }
    }
  });

  await loginAs(page, "alice", "alice-demo");

  // Snapshot the DO list BEFORE the test
  await page.goto(`/collections/${LUMEN_COLL}`, { waitUntil: "networkidle" });
  await page.waitForTimeout(1500);
  const beforeTreeHTML = await page.locator(".sidebar-container, [class*=sidebar]").first().innerHTML().catch(() => "");
  await page.screenshot({ path: path.join(OUT, "ui-019-01-collection-landing.png"), fullPage: false });

  // ── Step A: open the dialog ─────────────────────────────────────────────
  currentPhase = "dialog-open";
  const addBtn = page.getByRole("button", { name: /add new data object/i });
  await expect(addBtn).toBeVisible({ timeout: 10_000 });
  await addBtn.click();
  await page.waitForTimeout(1000);
  await page.screenshot({ path: path.join(OUT, "ui-019-02-dialog-opened.png"), fullPage: false });

  // Dialog may open as a template picker if the collection has templates.
  // LUMEN may or may not have templates seeded. Detect via dialog title text.
  // If we see "Start from blank", click it to enter the form mode.
  const startBlankBtn = page.getByRole("button", { name: /start from blank|blank/i });
  if (await startBlankBtn.isVisible({ timeout: 2_000 }).catch(() => false)) {
    await startBlankBtn.click();
    await page.waitForTimeout(800);
    await page.screenshot({ path: path.join(OUT, "ui-019-03-blank-form.png"), fullPage: false });
  }

  // ── Step B: fill step 1 (Properties / Relationships) ────────────────────
  // Vuetify dialogs render under .v-overlay-container; the dialog body has
  // a v-card containing "Create Data Object" header.
  const dialog = page
    .locator(".v-overlay-container .v-overlay.v-dialog .v-card")
    .filter({ hasText: /create data object/i })
    .first();
  await expect(dialog).toBeVisible({ timeout: 5_000 });

  // First text input inside the dialog is the Name field
  const nameField = dialog.locator('input[type="text"]').first();
  currentPhase = "step1-fill";
  await nameField.fill(TEST_NAME);
  await page.waitForTimeout(500);
  await page.screenshot({ path: path.join(OUT, "ui-019-04-step1-filled.png"), fullPage: false });

  // ── Step C: click Next → go to step 2 ───────────────────────────────────
  currentPhase = "step1-next";
  const nextBtn = page.getByRole("button", { name: /^next$/i });
  await expect(nextBtn).toBeVisible({ timeout: 5_000 });
  await expect(nextBtn).toBeEnabled({ timeout: 5_000 });
  await nextBtn.click();
  await page.waitForTimeout(1000);
  await page.screenshot({ path: path.join(OUT, "ui-019-05-step2.png"), fullPage: false });

  // ── Step D: verify Create button is visible and Cancel is too ───────────
  const createBtn = page.getByRole("button", { name: /^create$/i });
  const cancelBtn = page.getByRole("button", { name: /^cancel$/i });
  await expect(createBtn).toBeVisible({ timeout: 5_000 });
  await expect(cancelBtn).toBeVisible({ timeout: 5_000 });

  // ── Step E: CANCEL — NEVER click Create ─────────────────────────────────
  currentPhase = "cancel";
  await cancelBtn.click();
  await page.waitForTimeout(800);
  await page.screenshot({ path: path.join(OUT, "ui-019-06-after-cancel.png"), fullPage: false });

  // Dialog should be gone
  await expect(dialog).not.toBeVisible({ timeout: 5_000 });

  // ── Step F: verify NO DataObject was created ────────────────────────────
  currentPhase = "post-cancel-verify";
  // Reload the collection and check that TEST_NAME is not in the sidebar tree
  await page.reload({ waitUntil: "networkidle" });
  await page.waitForTimeout(1500);
  const afterPageText = await page.locator("body").innerText();
  const newObjectAppeared = afterPageText.includes(TEST_NAME);

  await page.screenshot({ path: path.join(OUT, "ui-019-07-after-reload.png"), fullPage: false });

  // ── Step G: write report ────────────────────────────────────────────────
  // Errors caused by THE DIALOG are the ones that matter for UI-019.
  // Pre-existing page-load errors are out of scope (own findings).
  const dialogPhases = new Set(["dialog-open", "step1-fill", "step1-next", "cancel"]);
  const dialogConsoleErrors = consoleErrors.filter(e => dialogPhases.has(e.phase));
  const dialogNetworkErrors = networkErrors.filter(e => dialogPhases.has(e.phase));

  const report = {
    test: "UI-2026-05-24-019",
    runAt: new Date().toISOString(),
    testName: TEST_NAME,
    verdict:
      mutationCalls.length === 0 &&
      !newObjectAppeared &&
      dialogConsoleErrors.length === 0 &&
      dialogNetworkErrors.length === 0
        ? "CLEAN"
        : "FOUND-ISSUE",
    mutationCallsObserved: mutationCalls,
    newObjectAppearedAfterCancel: newObjectAppeared,
    dialogPhaseConsoleErrors: dialogConsoleErrors,
    dialogPhaseNetworkErrors: dialogNetworkErrors,
    allConsoleErrors: consoleErrors,
    allNetworkErrors: networkErrors,
  };
  fs.writeFileSync(
    path.join(OUT, "ui-019-report.json"),
    JSON.stringify(report, null, 2),
  );

  console.log("─".repeat(70));
  console.log(`UI-019 verdict: ${report.verdict}`);
  console.log(`  Mutation calls during fill+cancel: ${mutationCalls.length} (expected 0)`);
  console.log(`  New DO appeared after cancel: ${newObjectAppeared} (expected false)`);
  console.log(`  Console errors during dialog: ${dialogConsoleErrors.length} (total: ${consoleErrors.length})`);
  console.log(`  Network errors during dialog: ${dialogNetworkErrors.length} (total: ${networkErrors.length})`);
  if (dialogConsoleErrors.length) console.log(`  First dialog console error: [${dialogConsoleErrors[0].phase}] ${dialogConsoleErrors[0].entry}`);
  if (dialogNetworkErrors.length) console.log(`  First dialog network error: [${dialogNetworkErrors[0].phase}] ${dialogNetworkErrors[0].entry.status} ${dialogNetworkErrors[0].entry.url}`);
  console.log("─".repeat(70));

  // Hard assertions
  expect(mutationCalls, "no DataObject mutation calls should fire during fill+cancel").toEqual([]);
  expect(newObjectAppeared, "no new DataObject should appear in the collection after cancel").toBe(false);
});
