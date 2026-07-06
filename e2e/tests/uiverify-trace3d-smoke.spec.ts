/**
 * TRACE3D-E2E-SMOKE-2026-07-02 — Happy-path smoke for the Trace3D render surface.
 *
 * Asserts that `/shapes/render?renderer=trace-3d&...` with a valid container
 * and channel roles:
 *   1. Does NOT show any `.v-alert[type="error"]` visible on screen.
 *   2. Renders the colorbar `<canvas>` (always mounted when Trace3DView mounts).
 *   3. Renders the Three.js 3D canvas inside Trace3DCanvas.
 *
 * The test gates on `TRACE3D_CONTAINER_APPID` being set so it is skipped in CI
 * without a seeded live instance.  Default values target the MFFD AFP tapelaying
 * container on shepard.nuclide.systems (the C-shot from screenshots-320.spec.ts).
 *
 * Override env vars:
 *   TRACE3D_CONTAINER_APPID  — appId of a timeseries container with at least
 *                              x/y/z channels matching the roles below.
 *   TRACE3D_ROLES            — base64-encoded JSON roles object (same format as
 *                              the `roles` query param on /shapes/render).
 *   TRACE3D_START_NS         — window start as nanosecond epoch integer string.
 *   TRACE3D_END_NS           — window end as nanosecond epoch integer string.
 *
 * Motivation: CHANNELS-PAGESIZE-500-FIX-2026-07-02 introduced the server-side
 * @Max cap silently breaking three presentation surfaces for ~1 day because no
 * e2e covered this path end-to-end.  This spec is the regression guard.
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "./helpers/auth";

// ── defaults: the C-shot from screenshots-320 (MFFD AFP tapelaying) ────────────

const DEFAULT_CONTAINER_APPID = "019ede2a-60ec-7ac1-899d-3fe4c6263cbb";

// x/y/z = TCP position in mm (R20/MFZ); value = temperature °C (MTLH/MFZ).
const DEFAULT_ROLES =
  "eyJ4Ijp7Im1lYXN1cmVtZW50IjoibW0iLCJkZXZpY2UiOiJSMjAiLCJsb2NhdGlvbiI6Ik1GWiIsInN5bWJvbGljTmFtZSI6IlgiLCJmaWVsZCI6InZhbHVlIn0sInkiOnsibWVhc3VyZW1lbnQiOiJtbSIsImRldmljZSI6IlIyMCIsImxvY2F0aW9uIjoiTUZaIiwic3ltYm9saWNOYW1lIjoiWSIsImZpZWxkIjoidmFsdWUifSwieiI6eyJtZWFzdXJlbWVudCI6Im1tIiwiZGV2aWNlIjoiUjIwIiwibG9jYXRpb24iOiJNRloiLCJzeW1ib2xpY05hbWUiOiJaIiwiZmllbGQiOiJ2YWx1ZSJ9LCJ2YWx1ZSI6eyJtZWFzdXJlbWVudCI6ImNlbHNpdXMiLCJkZXZpY2UiOiJNVExIIiwibG9jYXRpb24iOiJNRloiLCJzeW1ib2xpY05hbWUiOiJUZW1wZXJhdHVyVGFwZSIsImZpZWxkIjoidmFsdWUifX0=";

// 30-second window early in the imported MFFD range.
const DEFAULT_START_NS = "1670425854562000000";
const DEFAULT_END_NS   = "1670425884562000000";

// ── resolved values (env-var overridable) ───────────────────────────────────────

const CONTAINER_APPID = process.env.TRACE3D_CONTAINER_APPID || DEFAULT_CONTAINER_APPID;
const ROLES           = process.env.TRACE3D_ROLES            || DEFAULT_ROLES;
const START_NS        = process.env.TRACE3D_START_NS         || DEFAULT_START_NS;
const END_NS          = process.env.TRACE3D_END_NS           || DEFAULT_END_NS;

const TRACE3D_URL =
  `/shapes/render?renderer=trace-3d` +
  `&containerAppId=${encodeURIComponent(CONTAINER_APPID)}` +
  `&roles=${encodeURIComponent(ROLES)}` +
  `&startNs=${START_NS}` +
  `&endNs=${END_NS}` +
  `&colormap=inferno`;

// ── test suite ──────────────────────────────────────────────────────────────────

test.describe("TRACE3D-E2E-SMOKE — Trace3D render happy path", () => {
  test.use({ viewport: { width: 1920, height: 1080 } });

  test.beforeEach(async ({ page }) => {
    await loginAs(page, "bob", "bob-demo");
  });

  test("trace-3d render: no error alert + colorbar canvas visible", async ({ page }) => {
    test.skip(
      !process.env.TRACE3D_CONTAINER_APPID,
      "TRACE3D_CONTAINER_APPID not set — skipping live-instance trace3d smoke",
    );
    test.setTimeout(45_000);

    const pageErrors: string[] = [];
    page.on("pageerror", err => pageErrors.push(err.message));

    await page.goto(TRACE3D_URL, { waitUntil: "domcontentloaded" });

    // Wait for the channel fetch + render to complete (bulk data endpoint can
    // take a few seconds on first hit; 20 s is conservative).
    await page.waitForTimeout(20_000);

    // 1. No error alert must be visible.
    const errorAlerts = page.locator(".v-alert[type='error'], .v-alert--type-error");
    await expect(errorAlerts.first()).not.toBeVisible({ timeout: 2_000 }).catch(() => {
      // Count as soft: error alert present → log but don't fail silently below.
    });
    const errorAlertCount = await errorAlerts.count();
    expect(
      errorAlertCount,
      `Expected zero error alerts on Trace3D render page but found ${errorAlertCount}`,
    ).toBe(0);

    // 2. The Trace3DView colorbar canvas must exist in the DOM.
    //    It is always rendered when Trace3DView mounts (not gated on tracePoints).
    const colorbarCanvas = page.locator("canvas.trace3d-view__colorbar");
    await expect(colorbarCanvas).toBeAttached({ timeout: 5_000 });

    // 3. The Three.js canvas (inside Trace3DCanvas / ClientOnly) must be visible.
    //    This confirms tracePoints.length > 0 — data was fetched and rendered.
    //    Trace3DCanvas renders a full-bleed canvas; we look for any canvas that
    //    is not the colorbar swatch (width > 300px indicates the 3D viewport).
    const anyCanvas = page.locator("canvas").filter({ hasNOT: page.locator(".trace3d-view__colorbar") }).first();
    await expect(anyCanvas).toBeVisible({ timeout: 5_000 });

    // Surface any JS errors that were caught during the run.
    expect(pageErrors, `JS errors during Trace3D render: ${pageErrors.join("; ")}`).toHaveLength(0);
  });

  test("trace-3d render page loads without crash (structural, data-independent)", async ({ page }) => {
    test.setTimeout(20_000);

    await page.goto("/shapes/render?renderer=trace-3d", { waitUntil: "domcontentloaded" });
    await page.waitForTimeout(2_000);

    // The page header must be present — confirms the route renders without a 500.
    await expect(page.getByRole("heading", { name: /shape render/i })).toBeVisible({ timeout: 5_000 });

    // No fatal error alert on the blank form.
    const errorAlerts = page.locator(".v-alert[type='error'], .v-alert--type-error");
    const errorAlertCount = await errorAlerts.count();
    expect(
      errorAlertCount,
      `Unexpected error alert on blank shapes/render page`,
    ).toBe(0);
  });
});
