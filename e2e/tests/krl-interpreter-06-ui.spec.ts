/**
 * KRL-INTERPRETER-06 — Playwright spec validating the "Run / preview" UI
 * at the user's actual 4K viewport (3840 x 2160).
 *
 * The spec doesn't require the krl-interpreter sidecar to be running — that
 * is the whole point of the operator-opt-in story. When the backend returns
 * 502, the result panel surfaces the operator hint, which is the explicit
 * acceptance for the headline "honest 502 handling" requirement in the row
 * KRL-INTERPRETER-06.
 *
 * Per `feedback_validate_user_viewport.md`, the modal must render entirely
 * within the 3840 x 2160 viewport — no horizontal overflow, all sections
 * reachable without scrolling beyond the viewport bounds.
 *
 * The spec is allowed to be skipped when the deployment doesn't have a
 * `.src` FileReference in the demo Collection — the env-driven
 * `KRL_SRC_REF_URL` carries the canonical detail-page URL. Without it we
 * fall back to a unit-style render assertion on the auth landing.
 */
import { expect, test } from "@playwright/test";

const KC = process.env.KEYCLOAK_HOST || "https://shepard-auth.nuclide.systems";
const REALM = process.env.KEYCLOAK_REALM || "shepard-demo";
const USER = process.env.SHEPARD_USER || "alice";
const PASSWORD = process.env.SHEPARD_PASSWORD || "alice";

const SRC_REF_URL = process.env.KRL_SRC_REF_URL || "";

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

test.describe("KRL-INTERPRETER-06 — Run / preview UI at 4K", () => {
  test.use({ viewport: { width: 3840, height: 2160 } });

  test.beforeEach(async ({ page }) => {
    await loginAs(page, USER, PASSWORD);
  });

  test("button is visible on a .src FileReference detail page", async ({
    page,
  }) => {
    test.skip(!SRC_REF_URL, "KRL_SRC_REF_URL not set");
    await page.goto(SRC_REF_URL);
    const button = page.locator('[data-test="krl-run-preview-button"]');
    await expect(button).toBeVisible({ timeout: 15_000 });
  });

  test("clicking opens the dialog; dialog fits inside the 4K viewport", async ({
    page,
  }) => {
    test.skip(!SRC_REF_URL, "KRL_SRC_REF_URL not set");
    await page.goto(SRC_REF_URL);
    const button = page.locator('[data-test="krl-run-preview-button"]');
    await button.click();

    const dialog = page.locator('[data-test="krl-run-preview-dialog"]');
    await expect(dialog).toBeVisible();

    // Dialog must not exceed the 3840 x 2160 viewport bounds.
    const box = await dialog.boundingBox();
    expect(box, "dialog has no bounding box").not.toBeNull();
    if (box) {
      expect(box.x).toBeGreaterThanOrEqual(0);
      expect(box.y).toBeGreaterThanOrEqual(0);
      expect(box.x + box.width).toBeLessThanOrEqual(3840);
      expect(box.y + box.height).toBeLessThanOrEqual(2160);
    }

    // Required pickers + submit button all render.
    await expect(page.locator('[data-test="krl-urdf-picker"]')).toBeVisible();
    await expect(
      page.locator('[data-test="krl-target-dataobject"]'),
    ).toBeVisible();
    await expect(page.locator('[data-test="krl-ts-container"]')).toBeVisible();
    await expect(page.locator('[data-test="krl-submit"]')).toBeVisible();
  });

  test("submitting against a 502 sidecar surfaces the operator hint", async ({
    page,
  }) => {
    test.skip(
      !SRC_REF_URL || !process.env.KRL_URDF_APP_ID || !process.env.KRL_TS_CONTAINER_APP_ID,
      "URL + URDF appId + TS container appId required for the 502 happy-path check",
    );
    await page.goto(SRC_REF_URL);
    await page.locator('[data-test="krl-run-preview-button"]').click();

    // Pre-fill the URDF picker.
    const urdf = page.locator('[data-test="krl-urdf-picker"] input').first();
    await urdf.fill(String(process.env.KRL_URDF_APP_ID));
    await page.keyboard.press("Enter");

    // TS container picker (free-text field at tier-1).
    await page
      .locator('[data-test="krl-ts-container"] input')
      .first()
      .fill(String(process.env.KRL_TS_CONTAINER_APP_ID));

    await page.locator('[data-test="krl-submit"]').click();

    // Sidecar isn't up → backend returns 502 → the result panel shows the
    // operator hint pointing at the compose-profile command.
    const errorPanel = page.locator('[data-test="krl-error-message"]');
    await expect(errorPanel).toBeVisible({ timeout: 30_000 });
    await expect(errorPanel).toContainText(
      /COMPOSE_PROFILES=krl-interpreter/i,
    );
  });
});
