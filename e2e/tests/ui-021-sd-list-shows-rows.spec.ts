/**
 * UI-021 acceptance regression — alice can see at least one Structured Data
 * container row at the *real* containers list page.
 *
 * Closes UI-021 as WAI/misreport: the original report claimed
 * `/containers/structureddata` shows 0 rows. That path is a Nuxt 404 —
 * there is no list-page route per type. The actual list page is
 * `/containers` (query-param filtered). Full diagnosis:
 * `aidocs/agent-findings/ui-021-sd-list-fix-2026-05-24.md`.
 *
 * This spec guards the acceptance criteria the original task asked for:
 *   - alice opens the SD-filtered list → ≥ 1 row visible
 *   - alice opens the default list → ≥ 1 row visible
 *   - no console errors related to SD container listing
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "./helpers/auth";

const SEED_SD_CONTAINER = "lumen-inspired-runlogs";

test("alice sees ≥ 1 SD container row on /containers?selectedFilter=STRUCTUREDDATA", async ({
  page,
}) => {
  const consoleErrors: string[] = [];
  page.on("console", msg => {
    if (msg.type() === "error") consoleErrors.push(msg.text());
  });

  await loginAs(page, "alice", "alice-demo");
  await page.goto("/containers?selectedFilter=STRUCTUREDDATA");
  await page.waitForLoadState("networkidle", { timeout: 15_000 });

  const body = await page.locator("body").innerText();
  expect(body).toContain(SEED_SD_CONTAINER);

  const sdErrors = consoleErrors.filter(e =>
    /structureddata|structured-data|structuredDataContainer/i.test(e),
  );
  expect(sdErrors).toEqual([]);
});

test("alice sees ≥ 1 SD container row on default /containers list", async ({
  page,
}) => {
  await loginAs(page, "alice", "alice-demo");
  await page.goto("/containers");
  await page.waitForLoadState("networkidle", { timeout: 15_000 });

  const body = await page.locator("body").innerText();
  expect(body).toContain(SEED_SD_CONTAINER);
});

test("the original misreport URL /containers/structureddata is a Nuxt 404 (not an empty list)", async ({
  page,
}) => {
  await loginAs(page, "alice", "alice-demo");
  await page.goto("/containers/structureddata");
  await page.waitForLoadState("networkidle", { timeout: 10_000 });

  const body = await page.locator("body").innerText();
  // Sanity-check the diagnosis. If this assertion ever flips, the deep-link
  // affordance (UI-DEEP-LINK-TYPED-LISTS) has shipped and this spec should
  // be updated to assert the redirected list state instead.
  expect(body).toMatch(/Page not found|404/);
});
