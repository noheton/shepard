/**
 * V2CONV-B5 — Playwright spec for the "Interpret as trajectory" UI at the
 * user's actual 4K viewport (3840 x 2160).
 *
 * History: KRL interpret was dissolved (V2CONV-B5) from a bespoke
 * `/v2/krl/interpret` "Run / preview" dialog into a MAPPING_RECIPE transform —
 * a `.src`/`.krl` FileReference + a URDF FileReference materialise (via
 * `POST /v2/mappings/{templateAppId}/materialize`) into a derived joint-
 * trajectory TimeseriesReference. The in-context affordance is now the
 * "Interpret as trajectory" button on a `.src`/`.krl` FileReference detail page.
 * This spec replaces the old `krl-run-preview-*` selectors with the new
 * `interpret-as-trajectory-*` / `trajectory-*` ones.
 *
 * Per `feedback_validate_user_viewport.md`, the dialog must render entirely
 * within the 3840 x 2160 viewport. The spec skips when the deployment has no
 * `.src`/`.krl` FileReference (env-driven `KRL_SRC_REF_URL`).
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

test.describe("V2CONV-B5 — Interpret-as-trajectory UI at 4K", () => {
  test.use({ viewport: { width: 3840, height: 2160 } });

  test.beforeEach(async ({ page }) => {
    await loginAs(page, USER, PASSWORD);
  });

  test("button is visible on a .src/.krl FileReference detail page", async ({
    page,
  }) => {
    test.skip(!SRC_REF_URL, "KRL_SRC_REF_URL not set");
    await page.goto(SRC_REF_URL);
    const button = page.locator('[data-test="interpret-as-trajectory-button"]');
    await expect(button).toBeVisible({ timeout: 15_000 });
  });

  test("clicking opens the dialog; dialog fits inside the 4K viewport", async ({
    page,
  }) => {
    test.skip(!SRC_REF_URL, "KRL_SRC_REF_URL not set");
    await page.goto(SRC_REF_URL);
    await page.locator('[data-test="interpret-as-trajectory-button"]').click();

    const dialog = page.locator('[data-test="interpret-as-trajectory-dialog"]');
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

    // The URDF picker + the trajectory-target pickers + submit all render.
    await expect(
      page.locator('[data-test="trajectory-urdf-picker"]'),
    ).toBeVisible();
    await expect(
      page.locator('[data-test="trajectory-target-dataobject"]'),
    ).toBeVisible();
    await expect(
      page.locator('[data-test="trajectory-ts-container"]'),
    ).toBeVisible();
    await expect(page.locator('[data-test="trajectory-submit"]')).toBeVisible();
  });

  test("submitting without the sidecar surfaces an error (no crash)", async ({
    page,
  }) => {
    test.skip(
      !SRC_REF_URL ||
        !process.env.KRL_URDF_APP_ID ||
        !process.env.KRL_TS_CONTAINER_APP_ID,
      "URL + URDF appId + TS container appId required for the submit-path check",
    );
    await page.goto(SRC_REF_URL);
    await page.locator('[data-test="interpret-as-trajectory-button"]').click();

    const urdf = page
      .locator('[data-test="trajectory-urdf-picker"] input')
      .first();
    await urdf.fill(String(process.env.KRL_URDF_APP_ID));
    await page.keyboard.press("Enter");

    await page
      .locator('[data-test="trajectory-ts-container"] input')
      .first()
      .fill(String(process.env.KRL_TS_CONTAINER_APP_ID));

    await page.locator('[data-test="trajectory-submit"]').click();

    // With the krl-interpreter sidecar absent the materialize fails; the dialog
    // must surface the error inline (data-test="trajectory-error") rather than
    // crash. Success (data-test="trajectory-success") is also acceptable when a
    // sidecar IS configured — either way the flow resolves without an unhandled
    // exception.
    const outcome = page.locator(
      '[data-test="trajectory-error"], [data-test="trajectory-success"]',
    );
    await expect(outcome.first()).toBeVisible({ timeout: 30_000 });
  });
});
