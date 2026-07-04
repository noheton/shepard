/**
 * BUG-COLL-APPID-ROUTE-002 — live verification that the regression is
 * documented. This spec is intentionally non-asserting on the bug-state
 * capture; it loads the LUMEN Collection detail page on the live
 * deployment via the UUID v7 route and screenshots the rendered state
 * for the agent-findings audit trail.
 *
 * Until the worktree's frontend image is redeployed, the live state
 * will still show the v1-path 404 toast (the bug). Post-deploy, the
 * same spec — re-run unmodified — will show the DataObjects panel
 * populated.
 *
 * Artefacts land in:
 *   aidocs/agent-findings/screenshots/bug-coll-appid-route-002-fix/
 */
import { test } from "@playwright/test";
import { loginAs } from "./helpers/auth";
import * as path from "path";

const OUT_DIR = path.resolve(
  __dirname,
  "../../aidocs/agent-findings/screenshots/bug-coll-appid-route-002-fix",
);

test("BUG-COLL-APPID-ROUTE-002 — Collection detail loads via UUID v7 route", async ({
  page,
}) => {
  await page.setViewportSize({ width: 3840, height: 2160 });
  await loginAs(page, "alice", "alice-demo");

  // First navigate to the list to discover an extant LUMEN collection appId.
  await page.goto("/collections", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(6000);
  await page.screenshot({
    path: path.join(OUT_DIR, "01-collections-list.png"),
    fullPage: true,
  });

  // Click the first non-header row (Vuetify v-data-table rows are navigable
  // via row-click, not via an explicit <a href> on the cell).
  let href: string | null = null;
  await page
    .getByText(/LUMEN.*Hotfire|MFFD.*Upper.*Shell|MFFD.*RDK/)
    .first()
    .click()
    .catch(() => {});
  await page.waitForTimeout(2000);
  const url = page.url();
  const m = url.match(/\/collections\/([^/?#]+)/);
  if (m) href = `/collections/${m[1]}`;
  if (!href) {
    test.info().attach("no-collection-link", {
      body: "No /collections/{id} links found on the list page",
      contentType: "text/plain",
    });
    return;
  }

  // Navigate to the detail page.
  await page.goto(href, { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(4000);
  await page.screenshot({
    path: path.join(OUT_DIR, "02-collection-detail.png"),
    fullPage: true,
  });

  // Capture whether the "DataObjects" section is populated. We do not
  // assert — pre-deploy this will be empty/red, post-deploy populated.
  const hasError = await page
    .getByText(/(could not be fetched|Not found)/i)
    .first()
    .isVisible()
    .catch(() => false);
  test.info().attach("detail-state.txt", {
    body: `URL: ${href}\nError visible: ${hasError}\n`,
    contentType: "text/plain",
  });
});
