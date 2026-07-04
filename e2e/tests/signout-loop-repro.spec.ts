/**
 * BUG-SIGNOUT-LOOP-1 regression (operator report 2026-05-31).
 *
 * Captures every navigation that happens after clicking "Sign Out". The fix
 * (`useAuthRefreshMiddleware` now suppresses re-auth when the session has no
 * access token, when status is "unauthenticated", or when on a public route)
 * keeps the post-signout chain short: a handful of navs and the browser
 * stabilises at "/" within 3 seconds. Before the fix the same probe captured
 * 339 navigations in 12 seconds (see signout-chain.json, pre-fix capture).
 *
 * The spec also serves as the canonical reproducer if the loop ever returns —
 * the dumped chain + screenshots make root-cause analysis a 5-minute exercise.
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "./helpers/auth";
import * as fs from "fs";
import * as path from "path";

const OUT = path.resolve(
  __dirname,
  "../../aidocs/agent-findings/screenshots/signout-loop-fix",
);

test("BUG-SIGNOUT-LOOP-1: clicking sign-out stabilises within 3s on '/'", async ({
  page,
}) => {
  fs.mkdirSync(OUT, { recursive: true });

  await loginAs(page, "alice", "alice-demo");
  await page.screenshot({
    path: path.join(OUT, "01-signed-in-before.png"),
    fullPage: false,
  });

  const navs: { url: string; status?: number; ts: number }[] = [];
  const t0 = Date.now();
  page.on("framenavigated", frame => {
    if (frame === page.mainFrame()) {
      navs.push({ url: frame.url(), ts: Date.now() - t0 });
    }
  });
  page.on("response", async resp => {
    const req = resp.request();
    if (req.isNavigationRequest() && req.frame() === page.mainFrame()) {
      navs.push({
        url: req.url(),
        status: resp.status(),
        ts: Date.now() - t0,
      });
    }
  });

  // Click sign out — header button uses mdi-logout icon and "SIGN OUT" text.
  const signOutBtn = page.getByRole("button", { name: /sign out/i }).first();
  await signOutBtn.click();

  // Generous window so we'd catch any residual loop activity. The assertion
  // below only counts navigations after the 3-second mark.
  await page.waitForTimeout(8_000);

  await page.screenshot({
    path: path.join(OUT, "02-after-signout-attempt.png"),
    fullPage: false,
  });

  fs.writeFileSync(
    path.join(OUT, "signout-chain.json"),
    JSON.stringify({ navs, finalUrl: page.url() }, null, 2),
  );

  console.log("FINAL URL:", page.url());
  console.log("NAV COUNT:", navs.length);

  // Regression assertion: after the loop window (~3 seconds is generous;
  // pre-fix the loop fired ~28 navs/second), things should be quiet.
  const lateNavs = navs.filter(n => n.ts > 3_000);
  expect(
    lateNavs.length,
    `expected ≤ 3 navigations after t=3s, got ${lateNavs.length}; chain:\n${lateNavs.map(n => `  +${n.ts}ms ${n.url}`).join("\n")}`,
  ).toBeLessThanOrEqual(3);

  // Final URL should be a public route — the landing page is the canonical
  // post-signout destination.
  expect(page.url()).toMatch(/\/(auth\/signIn)?$/);
});
