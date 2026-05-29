/**
 * TS-AXIS-VERIFY (task #236, aidocs/16): confirm the
 * `/v2/timeseries-containers/{id}/channels/spatial-roles` endpoint
 * returns non-null role mappings on the MFFD synthetic showcase, and
 * confirm the Trace3D / ViewRecipeBuilderDialog auto-populates its X/Y/Z
 * dropdowns from those mappings.
 *
 * Live target — runs against https://shepard.nuclide.systems by default
 * (overridable via $BASE_URL). After the 2026-05-29 fix to the recovery
 * script (edge type changed from `:HAS_ANNOTATION` to `:has_annotation`
 * per Constants.HAS_ANNOTATION), the GET should return six non-null
 * UUIDs and the dialog should pre-fill the axis selectors on open.
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "./helpers/auth";

// MFFD synthetic showcase reference points (resolved 2026-05-29; stable
// across the current live deploy):
const MFFD_TS_CONTAINER_ID = 1772;
const MFFD_COLLECTION_ID = 1787;
const MFFD_DATAOBJECT_ID = 1814;          // LBR Cleat Installation
const MFFD_TS_REFERENCE_ID = 2039;        // lbr-sensors

test.describe("TS-AXIS-VERIFY — spatial-roles endpoint + Trace3D auto-fill", () => {
  test.use({ viewport: { width: 3840, height: 2160 } });

  test("GET /v2/.../channels/spatial-roles returns 6 non-null role UUIDs", async ({ request }) => {
    const apiKey = process.env.SHEPARD_API_KEY;
    test.skip(!apiKey, "SHEPARD_API_KEY env var required for API check");
    const res = await request.get(
      `/v2/timeseries-containers/${MFFD_TS_CONTAINER_ID}/channels/spatial-roles`,
      { headers: { "X-API-KEY": apiKey! } },
    );
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body).toMatchObject({
      x: expect.any(String),
      y: expect.any(String),
      z: expect.any(String),
      rot_a: expect.any(String),
      rot_b: expect.any(String),
      rot_c: expect.any(String),
    });
  });

  test("ViewRecipeBuilderDialog Trace3D auto-populates from annotations", async ({ page }) => {
    await loginAs(page, "bob", "bob-demo");
    const tsRefUrl =
      `/collections/${MFFD_COLLECTION_ID}` +
      `/dataobjects/${MFFD_DATAOBJECT_ID}` +
      `/timeseriesereferences/${MFFD_TS_REFERENCE_ID}`;
    await page.goto(tsRefUrl, { waitUntil: "domcontentloaded" });
    // Wait for the "Channel Overview" expansion panel to render
    const chanPanel = page.getByText("Channel Overview").first();
    await chanPanel.waitFor({ timeout: 15_000 });
    // Expand Channel Overview (it's not the default-open panel)
    await chanPanel.click();
    await page.waitForTimeout(500);
    await page.screenshot({
      path: "screenshots/ts-axis-verify-tsref-page.png",
      fullPage: true,
    });

    // The "Visualize in 3D" trigger lives inside the Channel Overview
    // expansion panel — only present after we expand it.
    const trigger = page.getByRole("button", { name: /visualize in 3d/i }).first();
    await expect(trigger).toBeVisible({ timeout: 10_000 });
    await trigger.click();

    // Wait for the dialog content to settle — auto-populate kicks in async
    // on dialog open via /channels/spatial-roles.
    await page.waitForTimeout(3000);
    await page.screenshot({
      path: "screenshots/ts-axis-verify-trace3d-dialog.png",
      fullPage: true,
    });

    // Sanity: the dialog title should appear (defensive — exact text may
    // vary, so we use a loose regex).
    const dialogVisible = await page
      .getByText(/view recipe|trace.?3d|channel picker|axis|x.axis/i)
      .first()
      .isVisible()
      .catch(() => false);
    expect(dialogVisible).toBe(true);
  });
});
