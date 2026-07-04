/**
 * V2-LINKS — no numeric-Neo4j-id navigation requests.
 *
 * Regression guard for the recurring "/collections/<numeric>" 404 class
 * (operator-surfaced: https://shepard.nuclide.systems/collections/367014 →
 * "Couldn't load the DataObject tree" + stale-URL warning). The v2/appId-only
 * backend 404s on a numeric route param, so any navigation link that carries a
 * numeric Neo4j id instead of the UUID-v7 appId is a dead link.
 *
 * This spec walks the feature-showcase collection surface at 4K and FAILS if
 * the frontend ever issues a `GET /v2/collections/<digits>` (a numeric-keyed
 * detail fetch) — those only arise when a navigation route carried a numeric
 * id. UUID-v7 segments (`019eb019-...`) are the correct shape and are allowed.
 *
 * Login helper: loginAs(page, "admin", "admin-demo") per the task brief; falls
 * back to the demo user env overrides used by the other specs.
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "./helpers/auth";

const USER = process.env.DEMO_USER || "admin";
const PASSWORD = process.env.DEMO_PASSWORD || "admin-demo";

// A v2 collection/data-object detail fetch keyed by a NUMERIC id. A UUID v7
// has hyphens + hex; a bare run of digits is the numeric Neo4j id we forbid.
const NUMERIC_V2_COLLECTION = /\/v2\/collections\/\d+(?:[/?#]|$)/;

test.describe("V2-LINKS — navigation never uses numeric Neo4j ids", () => {
  test("walking the showcase collection issues zero numeric-id v2 requests", async ({
    page,
  }) => {
    await page.setViewportSize({ width: 3840, height: 2160 });

    // Record every numeric-keyed v2 collection request seen during the walk.
    const numericHits: string[] = [];
    page.on("request", req => {
      const url = req.url();
      if (NUMERIC_V2_COLLECTION.test(url)) numericHits.push(url);
    });

    await loginAs(page, USER, PASSWORD);

    // 1. Collections list → click into the first showcase collection.
    await page.goto("/collections", { waitUntil: "domcontentloaded" });
    await page.waitForTimeout(4000);
    await page
      .getByText(/LUMEN|MFFD|Hotfire|Microsection|showcase/i)
      .first()
      .click()
      .catch(() => {});
    await page.waitForTimeout(3000);

    // The detail URL itself must carry a UUID v7 — never a bare numeric id.
    const detailUrl = page.url();
    const seg = detailUrl.match(/\/collections\/([^/?#]+)/)?.[1] ?? "";
    expect(
      seg,
      `collection detail route segment "${seg}" must be a UUID v7, not a numeric id`,
    ).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[0-9a-f]{4}-[0-9a-f]{12}$/i);

    // The page must NOT show the stale-URL / load-failure shape.
    const failed = await page
      .getByText(/Couldn't load the DataObject tree|could not be fetched|stale/i)
      .first()
      .isVisible()
      .catch(() => false);
    expect(failed, "collection detail loaded without a stale-URL / 404 banner").toBe(
      false,
    );

    // 2. Open the home page (PersonalDigest shared-collection links) + the
    //    header-search dropdown, which both build collection routes.
    await page.goto("/", { waitUntil: "domcontentloaded" });
    await page.waitForTimeout(2000);
    const searchInput = page
      .getByTestId("header-search-input")
      .locator("input");
    if (await searchInput.isVisible().catch(() => false)) {
      await searchInput.click();
      await searchInput.fill("TR");
      await page.waitForTimeout(2500);
      // Click the first collection/dataobject result if present — exercises
      // onPickCollection / onPickDataObject (appId-routed).
      const pick = page
        .getByTestId("header-search-result-dataobject")
        .first();
      if (await pick.isVisible().catch(() => false)) {
        await pick.click().catch(() => {});
        await page.waitForTimeout(2500);
      }
    }

    // 3. Final assertion: not a single numeric-keyed v2 collection request.
    expect(
      numericHits,
      `numeric-id v2 collection requests detected (each is a dead-link bug):\n${numericHits.join("\n")}`,
    ).toEqual([]);
  });
});
