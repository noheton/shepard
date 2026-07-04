/**
 * LAYOUT-4K-CENTERED-EMPTY-001 — Playwright 4K screenshot evidence pass.
 *
 * Captures the 9 hub/tool pages flagged by the 2026-05-30 UI scrutinizer
 * (rows L1–L8 in `aidocs/agent-findings/ui-scrutinizer-2026-05-30.md`)
 * at the user's actual 3840×2160 viewport so the fluid/2400px-cap fix
 * can be eyeballed without re-running the audit.
 *
 * Per `feedback_validate_user_viewport.md` — 4K is the user's native
 * resolution; 1440/1920 alone is insufficient.
 *
 * Outputs land in `aidocs/agent-findings/screenshots/layout-4k-2026-05-31/`.
 * The spec is intentionally screenshot-only (no assertions beyond load
 * confirmation) — the human eye is the gate. Re-run pre/post and diff.
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "./helpers/auth";
import path from "path";

const USER = process.env.DEMO_USER || "flodemo";
const PASSWORD = process.env.DEMO_PASSWORD || "flo-demo";

const OUT_DIR = path.resolve(
  __dirname,
  "../../aidocs/agent-findings/screenshots/layout-4k-2026-05-31",
);

const PAGES: { name: string; url: string }[] = [
  { name: "L1-home",             url: "/" },
  { name: "L2-tools",            url: "/tools" },
  { name: "L3-me",               url: "/me" },
  { name: "L3-me-semantic",      url: "/me#semantic" },
  { name: "L4-semantic-landing", url: "/semantic" },
  { name: "L5-shapes-render",    url: "/shapes/render" },
  { name: "L5-shapes-validate",  url: "/shapes/validate" },
  { name: "L5-snapshots-diff",   url: "/snapshots/diff" },
  { name: "L6-search",           url: "/search" },
  { name: "L7-admin-gate",       url: "/admin" },
];

test.use({ viewport: { width: 3840, height: 2160 } });

test.describe("LAYOUT-4K-CENTERED-EMPTY-001 — 4K hub/tool screenshots", () => {
  // Login is best-effort — even an unauthenticated capture is useful evidence
  // for layout, since the layout containers render regardless of session.
  test.beforeEach(async ({ page }) => {
    await loginAs(page, USER, PASSWORD).catch(() => {
      // swallow: we still capture whatever state the page lands in
    });
  });

  for (const { name, url } of PAGES) {
    test(`${name} (${url}) at 3840×2160`, async ({ page }) => {
      await page.goto(url).catch(() => {});
      // Give Vue + the slow data fetches a moment to settle. We don't
      // assert on content — the screenshot is the evidence.
      await page.waitForLoadState("networkidle", { timeout: 8_000 }).catch(() => {});
      await page.waitForTimeout(1500);
      await page.screenshot({
        path: path.join(OUT_DIR, `${name}.png`),
        fullPage: false, // viewport-sized so empty grey is visible
      });
      // Sanity check the page rendered something.
      expect(await page.title()).not.toBe("");
    });
  }
});
