/**
 * BUG #139 Stage 7 — capture computed CSS at 4K to pin the layout-collapse root cause.
 *
 * Prior probes (stages 1-6) established the DOM IS present at 4K but the panel
 * is placed below the sidebar (y≈2380px) instead of beside it (expected y≈120px).
 * This probe captures the computed style of the v-container, v-row, both v-cols,
 * and the sidebar root, so the fix targets the right rule.
 */
import { test } from "@playwright/test";
import * as fs from "fs";
import * as path from "path";

const KC = process.env.KEYCLOAK_HOST || "https://shepard-auth.nuclide.systems";
const REALM = "shepard-demo";
const OUT = path.join(
  __dirname,
  "..",
  "..",
  "aidocs",
  "agent-findings",
  "bug-139-evidence-2026-05-24",
);

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

test("BUG #139 Stage 7 — computed CSS at 4K for v-row + v-cols + sidebar", async ({
  page,
}) => {
  await page.setViewportSize({ width: 3840, height: 2160 });
  await loginAs(page, "alice", "alice-demo");
  await page.goto("/collections/42/dataobjects/661928", {
    waitUntil: "networkidle",
  });
  await page.waitForTimeout(1500);

  const probe = await page.evaluate(() => {
    function snap(el: Element | null): unknown {
      if (!el) return null;
      const rect = el.getBoundingClientRect();
      const cs = getComputedStyle(el);
      return {
        rect: { x: rect.x, y: rect.y, w: rect.width, h: rect.height },
        display: cs.display,
        flexDirection: cs.flexDirection,
        flexWrap: cs.flexWrap,
        flexBasis: cs.flexBasis,
        flexGrow: cs.flexGrow,
        flexShrink: cs.flexShrink,
        width: cs.width,
        minWidth: cs.minWidth,
        maxWidth: cs.maxWidth,
        position: cs.position,
        cls: el.className,
      };
    }

    const main = document.querySelector("main");
    const container = main?.querySelector(".v-container");
    const row = container?.querySelector(".v-row");
    const cols = Array.from(row?.children ?? []);
    const sidebar = document.querySelector(".sidebar-container");

    return {
      viewport: { w: window.innerWidth, h: window.innerHeight },
      main: snap(main),
      container: snap(container ?? null),
      row: snap(row ?? null),
      cols: cols.map(snap),
      sidebar: snap(sidebar),
      // Useful breadcrumb: does Vuetify's useDisplay think we're mobile?
      vAppFontSize: getComputedStyle(document.documentElement).fontSize,
    };
  });

  if (!fs.existsSync(OUT)) fs.mkdirSync(OUT, { recursive: true });
  fs.writeFileSync(
    path.join(OUT, "stage7-computed-4k.json"),
    JSON.stringify(probe, null, 2),
  );
  await page.screenshot({
    path: path.join(OUT, "stage7-fullpage-4k.png"),
    fullPage: true,
  });
  await page.screenshot({
    path: path.join(OUT, "stage7-viewport-4k.png"),
    fullPage: false,
  });

  console.log(JSON.stringify(probe, null, 2));
});
