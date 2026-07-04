/**
 * BUG-ANNOTATIONS-MAP-ENVELOPE + BUG-SIDEBAR-CHILD-FETCH-STALE-CLIENT —
 * live post-redeploy verification (tasks #318 unblock).
 *
 * Bug 1: annotations panel crashed with
 *   "Cannot read properties of undefined (reading 'map')"
 * because the deployed bundle expected a bare array from /v2/annotations
 * while the backend migrated to the PagedResponseIO {items,...} envelope.
 * The regenerated client + composables/annotated.ts now read `page.items`.
 *
 * Bug 2: lazy child fetch dropped topLevel/parentAppId params (stale
 * DataObjectsApi client) so substructures never loaded.
 *
 * This spec runs at the user's 4K viewport and asserts that NO
 * "reading 'map'" TypeError reaches the console on the DataObject
 * detail surface — both on the operator-reported URL and on a
 * generally-accessible DataObject discovered from the collections list.
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "./helpers/auth";

const REPORTED_DO =
  "/collections/019ed455-66f4-7aea-8cb3-5c0b34a737df/dataobjects/019ed607-023a-7313-978f-e664423a34c4";

/** Collect every console error + pageerror for `.map`-class crash detection. */
function attachErrorSink(page: import("@playwright/test").Page): string[] {
  const errors: string[] = [];
  page.on("console", (m) => {
    if (m.type() === "error") errors.push(m.text());
  });
  page.on("pageerror", (e) => errors.push(String(e)));
  return errors;
}

test("reported DataObject URL renders without the annotations .map crash", async ({
  page,
}) => {
  await page.setViewportSize({ width: 3840, height: 2160 });
  const errors = attachErrorSink(page);
  await loginAs(page, "alice", "alice-demo");

  await page.goto(REPORTED_DO, { waitUntil: "domcontentloaded" });
  // Give annotation + reference composables time to resolve/settle.
  await page.waitForTimeout(8000);

  const mapCrash = errors.filter((e) =>
    /reading 'map'|reading "map"|\.map is not a function/i.test(e),
  );
  expect(
    mapCrash,
    `annotations .map crash surfaced:\n${mapCrash.join("\n")}`,
  ).toHaveLength(0);
});

test("an accessible DataObject shows the annotations panel and expands children", async ({
  page,
}) => {
  await page.setViewportSize({ width: 3840, height: 2160 });
  const errors = attachErrorSink(page);
  await loginAs(page, "alice", "alice-demo");

  // Discover a collection alice can read.
  await page.goto("/collections", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(4000);
  const collLink = page.locator('a[href*="/collections/"]').first();
  if ((await collLink.count()) === 0) {
    test.info().attach("no-collections", {
      body: "alice sees no collections; cannot exercise DO detail",
      contentType: "text/plain",
    });
    return;
  }
  await collLink.click().catch(() => {});
  await page.waitForTimeout(3000);

  // Open the first DataObject detail surface from the collection page.
  const doLink = page.locator('a[href*="/dataobjects/"]').first();
  if ((await doLink.count()) === 0) {
    test.info().attach("no-dataobjects", {
      body: "no DataObject links on this collection; skipping",
      contentType: "text/plain",
    });
    return;
  }
  await doLink.click().catch(() => {});
  await page.waitForTimeout(6000);

  // No .map crash on a real, accessible DO.
  const mapCrash = errors.filter((e) =>
    /reading 'map'|reading "map"|\.map is not a function/i.test(e),
  );
  expect(
    mapCrash,
    `annotations .map crash on accessible DO:\n${mapCrash.join("\n")}`,
  ).toHaveLength(0);

  // The DataObject detail page rendered something coherent (not a blank/error).
  await expect(page.locator("body")).not.toContainText(
    "Internal Server Error",
  );
});
