/**
 * PROJ-NAV-1 — Playwright 4K screenshot evidence pass for the Projects surface.
 *
 * Captures:
 *   - the top-nav /projects route
 *   - the CollectionList view (so the Project chip is visible on row-level)
 *
 * at the user's actual 3840×2160 viewport so layout regressions in the new
 * surface can be caught without re-running the audit.
 *
 * Per `feedback_validate_user_viewport.md` — 4K is the user's native
 * resolution; 1440/1920 alone is insufficient.
 *
 * Outputs land in `aidocs/agent-findings/screenshots/proj-nav-1-4k-2026-06-02/`.
 * Screenshot-only: the human eye is the gate, and at the time of writing
 * the new surface had not yet been redeployed to the live host — so the
 * captures are baseline-only. Re-run after redeploy to verify the new
 * surface is reachable from the top-nav and that the CollectionList chip
 * renders.
 */
import { test } from "@playwright/test";
import { loginAs } from "./helpers/auth";
import path from "path";

const USER = process.env.DEMO_USER || "flodemo";
const PASSWORD = process.env.DEMO_PASSWORD || "flo-demo";

const OUT_DIR = path.resolve(
  __dirname,
  "../../aidocs/agent-findings/screenshots/proj-nav-1-4k-2026-06-02",
);

const PAGES: { name: string; url: string }[] = [
  // Top-nav reachability: home shows the Projects button in the nav.
  { name: "P1-home-with-projects-nav",  url: "/" },
  // The /projects index — the canonical entrypoint.
  { name: "P2-projects-index",          url: "/projects" },
  // The Collections list — Project chips should appear on rows whose
  // Collection carries urn:shepard:project = "true".
  { name: "P3-collections-with-chip",   url: "/collections" },
];

test.use({ viewport: { width: 3840, height: 2160 } });

test.describe("PROJ-NAV-1 — 4K screenshots", () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, USER, PASSWORD).catch(() => {
      // best-effort login; layout captures unauth pages too
    });
  });

  for (const p of PAGES) {
    test(`@4k ${p.name}`, async ({ page }) => {
      await page.goto(p.url);
      // Wait for either main content or the body, whichever lands first.
      await page
        .waitForSelector("main, .v-application, body", { timeout: 10_000 })
        .catch(() => {
          // swallow — we still capture whatever state the page lands in
        });
      // Settle animations.
      await page.waitForTimeout(800);
      await page.screenshot({
        path: path.join(OUT_DIR, `${p.name}.png`),
        fullPage: true,
      });
    });
  }
});
