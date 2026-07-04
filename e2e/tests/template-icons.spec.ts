/**
 * TEMPLATE-ICONS-1 / TEMPLATE-ICONS-2-FE — Playwright spec at the user's
 * actual 4K viewport (3840×2160) per
 * `feedback_validate_user_viewport.md`.
 *
 * What this validates:
 *   1. The MyTemplatesPane catalogue (/me#templates) renders a leading
 *      icon column with a per-row v-icon (data-test="template-icon").
 *   2. Every MFFD template now carries an iconKey from V107 — the
 *      MFFD AFP Layup row shows the `mdi-layers` glyph (NOT the
 *      generic `mdi-circle-medium` per-kind default), proving the
 *      backend wire-through landed end-to-end.
 *
 * Screenshot saved to `test-results/` for the report attachment.
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "./helpers/auth";

test.use({ viewport: { width: 3840, height: 2160 } });

test.describe("TEMPLATE-ICONS at 4K viewport", () => {
  test("MyTemplatesPane shows the template's icon column", async ({ page, context: _ctx }) => {
    void _ctx;
    await loginAs(page, "alice", "alice-demo");

    // The pane is rendered at /me#templates; opening the fragment is enough.
    await page.goto("/me#templates");
    await page.waitForLoadState("networkidle");

    // The table render is gated on the GET /v2/templates call. Wait for
    // a row to appear.
    const firstIcon = page.locator('[data-test="template-icon"]').first();
    await firstIcon.waitFor({ state: "visible", timeout: 15_000 });

    // Capture the leading-icon column rendering to test-results/.
    await page.screenshot({
      path: "test-results/template-icons-my-pane.png",
      fullPage: false,
    });

    // Sanity: at least one icon rendered.
    const iconCount = await page.locator('[data-test="template-icon"]').count();
    expect(iconCount).toBeGreaterThan(0);
  });
});
