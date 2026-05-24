/**
 * BUG #139 regression test — DataObject detail panel must render BESIDE the
 * collection sidebar at >=lg viewports, never below it.
 *
 * Root cause was an SSR/hydration mismatch in `layouts/collection.vue`: the
 * sidebar v-col used `v-if="!mobile"` (re-rendered on hydration) but the main
 * v-col used `:cols="mobile ? 12 : 9"` (a reactive prop that didn't re-resolve),
 * so at 4K the main panel ended up rendered with cols-12 + pa-3 (mobile branch)
 * even though the sidebar was rendered with cols-3 (desktop branch).
 * Fix: drop `useDisplay()` in favour of CSS-only Vuetify breakpoint utilities.
 *
 * This test asserts the post-fix invariant directly: at 4K the two v-cols sit
 * side-by-side, with the main panel x-offset > 0 and y aligned with the sidebar.
 */
import { expect, test } from "@playwright/test";

const KC = process.env.KEYCLOAK_HOST || "https://shepard-auth.nuclide.systems";
const REALM = "shepard-demo";

async function loginAs(
  page: import("@playwright/test").Page,
  username: string,
  password: string,
) {
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

/**
 * Probes the collection layout's v-row children and returns their bounding
 * rects + Vuetify col classes. Stable across viewports.
 */
async function readLayoutCols(page: import("@playwright/test").Page) {
  return page.evaluate(() => {
    const row = document.querySelector("main .v-container .v-row");
    if (!row) throw new Error("collection layout v-row not found");
    const cols = Array.from(row.children).map(el => {
      const rect = el.getBoundingClientRect();
      return {
        cls: (el as HTMLElement).className,
        // Visibility: a `d-none d-lg-block` element on a small viewport will
        // have display:none and a zero-rect; only count truthy children.
        visible: rect.width > 0 && rect.height > 0,
        x: rect.x,
        y: rect.y,
        w: rect.width,
      };
    });
    return cols;
  });
}

test.describe("BUG #139 — collection layout at large viewports", () => {
  test("at 4K (3840×2160), sidebar and main render side-by-side", async ({
    page,
  }) => {
    await page.setViewportSize({ width: 3840, height: 2160 });
    await loginAs(page, "alice", "alice-demo");
    // LUMEN TR-001 — a DO that always exists in the synthetic showcase.
    await page.goto("/collections/42/dataobjects/661928", {
      waitUntil: "networkidle",
    });
    await page.waitForTimeout(1000);

    const visibleCols = (await readLayoutCols(page)).filter(c => c.visible);
    expect(
      visibleCols.length,
      "exactly two visible v-cols at >=lg",
    ).toBe(2);

    const [sidebar, main] = visibleCols;

    // Both v-cols share the same top edge (within 4px) — they sit side-by-side,
    // not stacked. A stacked layout would put `main.y` ~equal to sidebar height.
    expect(
      Math.abs(sidebar.y - main.y),
      "sidebar and main v-cols share the same top edge (side-by-side)",
    ).toBeLessThan(4);

    // Main starts to the right of the sidebar.
    expect(
      main.x,
      "main v-col x-offset is > 0 (placed to the right of the sidebar)",
    ).toBeGreaterThan(0);

    // At cols=3 + cols=9 with a 3840px container, the split is 960/2880.
    expect(sidebar.w).toBeCloseTo(960, 0);
    expect(main.w).toBeCloseTo(2880, 0);

    // Main v-col must carry the lg=9 + pa-lg-8 classes (proves the CSS-driven
    // responsive path is engaged). `v-col-12` is also expected — it is the
    // base-breakpoint class that `v-col-lg-9` overrides via media query at
    // lg+, which is exactly what Vuetify emits for `cols="12" lg="9"`.
    expect(main.cls).toMatch(/v-col-lg-9/);
    expect(main.cls).toMatch(/pa-lg-8/);
  });

  test("at 1920×1080, sidebar and main also render side-by-side", async ({
    page,
  }) => {
    await page.setViewportSize({ width: 1920, height: 1080 });
    await loginAs(page, "alice", "alice-demo");
    await page.goto("/collections/42/dataobjects/661928", {
      waitUntil: "networkidle",
    });
    await page.waitForTimeout(1000);

    const visibleCols = (await readLayoutCols(page)).filter(c => c.visible);
    expect(visibleCols.length).toBe(2);
    const [sidebar, main] = visibleCols;
    expect(Math.abs(sidebar.y - main.y)).toBeLessThan(4);
    expect(main.x).toBeGreaterThan(0);
  });
});
