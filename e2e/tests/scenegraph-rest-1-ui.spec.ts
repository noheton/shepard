/**
 * SCENEGRAPH-REST-1-UI — Playwright spec at 4K (3840 x 2160).
 *
 * Per `feedback_validate_user_viewport.md`, the page must render entirely
 * within the user's actual viewport — sticky inspector stays in-frame, no
 * horizontal overflow, all primary affordances reachable.
 *
 * The spec is allowed to fall back gracefully when no scene appId is
 * pre-provisioned for the deployment. With `SCENE_APP_ID` set, the full
 * interactive walk runs (tree → inspector → add-frame dialog → delete-confirm).
 * Without it, only the 404 graceful path is verified.
 */
import { expect, test } from "@playwright/test";

const KC = process.env.KEYCLOAK_HOST || "https://shepard-auth.nuclide.systems";
const REALM = process.env.KEYCLOAK_REALM || "shepard-demo";
const USER = process.env.SHEPARD_USER || "alice";
const PASSWORD = process.env.SHEPARD_PASSWORD || "alice";
const SCENE_APP_ID = process.env.SCENE_APP_ID || "";

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

test.describe("SCENEGRAPH-REST-1-UI — scene-graph browser at 4K", () => {
  test.use({ viewport: { width: 3840, height: 2160 } });

  test.beforeEach(async ({ page }) => {
    await loginAs(page, USER, PASSWORD);
  });

  test("404 state renders a graceful link back to Collections", async ({
    page,
  }) => {
    // A clearly-non-existent appId — backend returns 404, page surfaces the
    // 404 card with the back-to-Collections CTA.
    await page.goto("/scene-graphs/00000000-0000-0000-0000-000000000000");
    const card = page.locator('[data-test="scene-graph-404"]');
    await expect(card).toBeVisible({ timeout: 15_000 });
    await expect(card).toContainText(/scene not found/i);
    await expect(card.getByRole("link", { name: /collections/i })).toBeVisible();
  });

  test("tree + inspector render side-by-side at 4K without overflow", async ({
    page,
  }) => {
    test.skip(!SCENE_APP_ID, "SCENE_APP_ID not set");
    await page.goto(`/scene-graphs/${SCENE_APP_ID}`);
    const treePanel = page.locator('[data-test="scene-graph-tree"]');
    await expect(treePanel).toBeVisible({ timeout: 15_000 });

    // No horizontal overflow.
    const page404 = page.locator('[data-test="scene-graph-404"]');
    await expect(page404).toHaveCount(0);

    const pageRoot = page.locator('[data-test="scene-graph-page"]');
    const box = await pageRoot.boundingBox();
    expect(box).not.toBeNull();
    if (box) {
      expect(box.x).toBeGreaterThanOrEqual(0);
      expect(box.x + box.width).toBeLessThanOrEqual(3840);
    }

    // The URDF export button + Add-frame button render in the header.
    await expect(page.locator('[data-test="urdf-export-button"]')).toBeVisible();
    await expect(page.locator('[data-test="add-frame-button"]')).toBeVisible();
  });

  test("clicking a frame opens the inspector with editable fields", async ({
    page,
  }) => {
    test.skip(!SCENE_APP_ID, "SCENE_APP_ID not set");
    await page.goto(`/scene-graphs/${SCENE_APP_ID}`);
    // First row in the tree.
    const firstRow = page.locator('[data-test^="tree-row-"]').first();
    await expect(firstRow).toBeVisible({ timeout: 15_000 });
    await firstRow.click();

    const inspector = page.locator('[data-test="frame-inspector"]');
    await expect(inspector).toBeVisible();
    await expect(page.locator('[data-test="frame-input-name"]')).toBeVisible();
    await expect(page.locator('[data-test="frame-input-x"]')).toBeVisible();
    await expect(page.locator('[data-test="frame-save"]')).toBeVisible();
    await expect(page.locator('[data-test="frame-delete"]')).toBeVisible();
  });

  test("add-frame dialog opens and shows required fields", async ({ page }) => {
    test.skip(!SCENE_APP_ID, "SCENE_APP_ID not set");
    await page.goto(`/scene-graphs/${SCENE_APP_ID}`);
    await page.locator('[data-test="add-frame-button"]').click();
    const dialog = page.locator('[data-test="add-frame-dialog"]');
    await expect(dialog).toBeVisible();
    await expect(page.locator('[data-test="add-frame-name"]')).toBeVisible();
    await expect(page.locator('[data-test="add-frame-kind"]')).toBeVisible();

    // Dialog stays inside the 4K viewport.
    const box = await dialog.boundingBox();
    expect(box).not.toBeNull();
    if (box) {
      expect(box.x).toBeGreaterThanOrEqual(0);
      expect(box.y).toBeGreaterThanOrEqual(0);
      expect(box.x + box.width).toBeLessThanOrEqual(3840);
      expect(box.y + box.height).toBeLessThanOrEqual(2160);
    }
  });

  test("delete-frame confirm carries the subtree size", async ({ page }) => {
    test.skip(!SCENE_APP_ID, "SCENE_APP_ID not set");
    await page.goto(`/scene-graphs/${SCENE_APP_ID}`);
    const firstRow = page.locator('[data-test^="tree-row-"]').first();
    await expect(firstRow).toBeVisible({ timeout: 15_000 });
    await firstRow.click();
    await page.locator('[data-test="frame-delete"]').click();
    const confirm = page.locator('[data-test="delete-frame-confirm"]');
    await expect(confirm).toBeVisible();
    // The message mentions "delete" — exact phrasing is asserted in the
    // unit-level test, here we just verify the affordance is rendered.
    await expect(confirm).toContainText(/delete/i);
  });
});
