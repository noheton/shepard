/**
 * UI-020 — verifies the bulk lab-journal-entries endpoint replaced the
 * per-DataObject N+1 fan-out on the collection detail page.
 *
 * Before the fix, opening MFFD-Dropbox (~8500 DataObjects) issued ~8514
 * concurrent {@code GET /shepard/api/labJournalEntries?dataObjectId=N}
 * requests and produced ~8311 console errors as the browser refused
 * sockets. After the fix, the page issues at most a handful of
 * lab-journal requests (the new bulk endpoint at
 * {@code GET /v2/collections/{appId}/lab-journal-entries}, plus the
 * occasional follow-up if the expansion panel triggers a refetch).
 *
 * Assertion thresholds are deliberately generous (≤ 5 lab-journal
 * requests, ≤ 50 console errors of any kind) so noise from unrelated
 * UI elements never flakes this regression — the linear-scaling signal
 * we are guarding against is 1000× the threshold.
 */
import { test, expect, type Page } from "@playwright/test";

const MFFD_COLLECTION_ID = process.env.UI020_COLLECTION_ID || "661923";
const KC = process.env.KEYCLOAK_HOST || "https://shepard-auth.nuclide.systems";
const REALM = "shepard-demo";

/**
 * Local login helper — the shared {@code ./helpers/auth.ts#loginAs} defaults
 * KEYCLOAK_HOST to a stale internal IP. We hard-code the public Keycloak
 * hostname so this test runs against the live deploy without env tweaking.
 */
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
  await page.waitForURL(/shepard\.nuclide\.systems(?!.*error)/, { timeout: 20_000 });
  await page.waitForSelector("text=SIGN OUT", { timeout: 10_000 });
}

test.describe("UI-020 — lab-journal bulk endpoint replaces the N+1", () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, "alice", "alice-demo");
  });

  test("MFFD-Dropbox: ≤5 lab-journal requests and ≤50 console errors", async ({
    page,
  }) => {
    const labJournalRequests: string[] = [];
    page.on("request", req => {
      const url = req.url();
      if (
        url.includes("labJournalEntries") ||
        url.includes("lab-journal-entries")
      ) {
        labJournalRequests.push(`${req.method()} ${url}`);
      }
    });

    const consoleErrors: string[] = [];
    page.on("console", msg => {
      if (msg.type() === "error") consoleErrors.push(msg.text());
    });

    await page.goto(`/collections/${MFFD_COLLECTION_ID}`);

    // Wait for the page to settle. networkidle is more meaningful than a
    // fixed sleep at MFFD scale since the dataobjects panel triggers its
    // own paginated fetches.
    await page.waitForLoadState("networkidle", { timeout: 60_000 });

    // The expansion panel "Lab Journal" is collapsed by default in the
    // accordion shell. Open it explicitly so its child component mounts
    // and the bulk request fires.
    const labJournalToggle = page
      .locator(".v-expansion-panel-title")
      .filter({ hasText: "Lab Journal" })
      .first();
    if (await labJournalToggle.isVisible()) {
      await labJournalToggle.click();
      // Give the bulk fetch + render time to complete.
      await page.waitForLoadState("networkidle", { timeout: 30_000 });
    }

    // The pre-UI-020 baseline was ~8514 concurrent requests on MFFD-Dropbox.
    // Post-fix we expect 1 bulk request, with budget for pagination /
    // re-render edge cases. Anything in the thousands means the N+1
    // regression is back.
    expect(
      labJournalRequests.length,
      `lab-journal request count regression: ${labJournalRequests.length} requests, sample:\n` +
        labJournalRequests.slice(0, 10).join("\n"),
    ).toBeLessThanOrEqual(5);

    // Pre-UI-020 baseline was ~8311 console errors. Post-fix we expect
    // near-zero, but allow a generous noise budget so unrelated console
    // chatter (Vue dev-mode warnings, etc.) never flakes this guard.
    expect(
      consoleErrors.length,
      `console error count regression: ${consoleErrors.length} errors, sample:\n` +
        consoleErrors.slice(0, 5).join("\n"),
    ).toBeLessThanOrEqual(50);

    // Positive assertion: the bulk endpoint was actually hit
    // (vs. some unrelated page change disabling lab-journal entirely).
    const hitBulkEndpoint = labJournalRequests.some(r =>
      r.includes("/v2/collections/") && r.includes("/lab-journal-entries"),
    );
    expect(
      hitBulkEndpoint,
      `expected at least one request to /v2/collections/*/lab-journal-entries; got:\n${labJournalRequests.join("\n")}`,
    ).toBe(true);
  });
});
