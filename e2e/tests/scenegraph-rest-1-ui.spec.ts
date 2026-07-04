/**
 * V2CONV-B4 — Playwright spec for the dissolved scene-graph at 4K (3840 x 2160).
 *
 * History: the bespoke `/v2/scene-graphs/*` editor (tree / inspector / frame
 * CRUD) was dissolved (V2CONV-B4) into a MAPPING_RECIPE view-shape. The scene
 * graph is now: an "Open in 3D view" button on a URDF FileReference detail page
 * creates a MAPPING_RECIPE template, then the played view lives at
 * `/scene-graphs/play/{templateAppId}` (URDF parsed on demand + joint TS →
 * Trace3D play/scrub). This spec replaces the old `scene-graph-tree` /
 * `frame-inspector` / `add-frame` selectors with the new
 * `open-in-3d-view-*` / `play-*` ones.
 *
 * Per `feedback_validate_user_viewport.md`, the play page + the create-view
 * dialog must render entirely within the 3840 x 2160 viewport. The interactive
 * walk runs when `URDF_REF_URL` (a URDF FileReference detail page) is set.
 */
import { expect, test } from "@playwright/test";

const KC = process.env.KEYCLOAK_HOST || "https://shepard-auth.nuclide.systems";
const REALM = process.env.KEYCLOAK_REALM || "shepard-demo";
const USER = process.env.SHEPARD_USER || "alice";
const PASSWORD = process.env.SHEPARD_PASSWORD || "alice";
const URDF_REF_URL = process.env.URDF_REF_URL || "";

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

test.describe("V2CONV-B4 — 3D-view (scene-graph MAPPING_RECIPE) at 4K", () => {
  test.use({ viewport: { width: 3840, height: 2160 } });

  test.beforeEach(async ({ page }) => {
    await loginAs(page, USER, PASSWORD);
  });

  test("play page for a non-existent template surfaces an error, not a crash", async ({
    page,
  }) => {
    await page.goto("/scene-graphs/play/00000000-0000-0000-0000-000000000000");
    // The page must resolve to its error affordance (data-test="play-error")
    // rather than throw — the materialise call 404s on the bogus template.
    await expect(page.locator('[data-test="play-error"]')).toBeVisible({
      timeout: 15_000,
    });
  });

  test('"Open in 3D view" button on a URDF FileReference opens the create dialog', async ({
    page,
  }) => {
    test.skip(!URDF_REF_URL, "URDF_REF_URL not set");
    await page.goto(URDF_REF_URL);
    const button = page.locator('[data-test="open-in-3d-view-button"]');
    await expect(button).toBeVisible({ timeout: 15_000 });
    await button.click();

    await expect(page.locator('[data-test="view-name-input"]')).toBeVisible();
    await expect(
      page.locator('[data-test="view-create-confirm"]'),
    ).toBeVisible();
  });

  test("creating the view navigates to the play page within the 4K viewport", async ({
    page,
  }) => {
    test.skip(!URDF_REF_URL, "URDF_REF_URL not set");
    await page.goto(URDF_REF_URL);
    await page.locator('[data-test="open-in-3d-view-button"]').click();
    await page
      .locator('[data-test="view-name-input"] input')
      .first()
      .fill(`e2e-3dview-${Date.now()}`);
    await page.locator('[data-test="view-create-confirm"]').click();

    // Lands on the play page.
    await page.waitForURL(/\/scene-graphs\/play\//, { timeout: 20_000 });
    const status = page.locator('[data-test="playback-status-chip"]');
    await expect(status).toBeVisible({ timeout: 20_000 });

    // The play summary card stays inside the 4K viewport.
    const summary = page.locator('[data-test="play-summary"]');
    const box = await summary.boundingBox();
    if (box) {
      expect(box.x).toBeGreaterThanOrEqual(0);
      expect(box.x + box.width).toBeLessThanOrEqual(3840);
    }
  });
});
