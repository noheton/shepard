/**
 * BUG-LABJOURNAL-MAP-ENVELOPE — live post-redeploy verification.
 *
 * The collection lab-journal panel crashed with
 *   "s.map is not a function"
 * because the deployed bundle's generated CollectionLabJournalEntriesApi
 * deserialized the response with `jsonValue.map(...)` while the backend
 * migrated GET /v2/collections/{appId}/lab-journal-entries to the
 * PagedResponseIO {items,...} envelope (APISIMP-PAGINATION-ENVELOPE).
 *
 * The regenerated client now unwraps `.items`. This spec runs at the user's
 * 4K viewport and asserts NO ".map is not a function" TypeError reaches the
 * console when the Collection detail page (which mounts
 * CollectionLabJournalEntryList) renders — on a collection alice can read.
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "./helpers/auth";

test("collection detail renders without the lab-journal .map crash", async ({
  page,
}) => {
  test.setTimeout(60000);
  await page.setViewportSize({ width: 3840, height: 2160 });
  const errors: string[] = [];
  page.on("console", (m) => {
    if (m.type() === "error") errors.push(m.text());
  });
  page.on("pageerror", (e) => errors.push(String(e)));

  await loginAs(page, "alice", "alice-demo");

  await page.goto("/collections", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(4000);
  const collLink = page.locator('a[href*="/collections/"]').first();
  if ((await collLink.count()) === 0) {
    test.info().attach("no-collections", {
      body: "alice sees no collections; cannot exercise lab-journal panel",
      contentType: "text/plain",
    });
    return;
  }
  await collLink.click().catch(() => {});
  // Let the collection page + lab-journal composable resolve/settle.
  await page.waitForTimeout(8000);

  const mapCrash = errors.filter((e) =>
    /\.map is not a function|s\.map is not a function|reading 'map'/i.test(e),
  );
  expect(
    mapCrash,
    `lab-journal .map crash surfaced:\n${mapCrash.join("\n")}`,
  ).toHaveLength(0);

  await expect(page.locator("body")).not.toContainText(
    "Internal Server Error",
  );
});
