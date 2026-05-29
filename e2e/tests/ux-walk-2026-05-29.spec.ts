/**
 * UX Walk 2026-05-29 — Live regression sweep after heavy build day.
 *
 * Captures evidence (screenshots) at each step at 4K viewport (3840x2160)
 * per `feedback_validate_user_viewport.md` + a 1440x900 comparison snapshot
 * for sidebar Bug U-01 regression check.
 *
 * Dispatch context: post Jandex-hang fix (48e1190ad), microsections seed
 * (f5775aa7e), TS-AXIS recovery fix (cce854736), PERM-INHERIT/PERM-REDESIGN
 * merges. J1e + REF-UNIFIED-TABLE-FR1B (c13464abf) landed.
 *
 * Findings flow to aidocs/agent-findings/ux-walk-2026-05-29.md.
 * Observations are soft (no hard assertions on UI state) so the walk
 * completes even when surfaces are partially broken — we WANT the
 * regression evidence captured.
 *
 * Credentials: demo realm uses `{username}-demo` convention. The dispatch
 * said `flo/flo` but the real keys are `flo/flo-demo`.
 */
import { test } from "@playwright/test";
import { loginAs } from "./helpers/auth";
import * as fs from "fs";
import * as path from "path";

const SCREENSHOT_DIR = "screenshots/ux-walk-2026-05-29";
const MICROSECTIONS_COLLECTION_APPID = "019e7243-f995-7914-be80-53e367aa5172";
const MICROSECTIONS_DO_APPID_PH2940_07 = "019e7244-06d0-7051-9add-f3f87fcf0f3b";

const VP_4K = { width: 3840, height: 2160 };
const VP_NARROW = { width: 1440, height: 900 };

const CREDS: ReadonlyArray<readonly [string, string]> = [
  ["flo", "flo-demo"],
  ["bob", "bob-demo"],
  ["alice", "alice-demo"],
];

async function tolerantLogin(page: import("@playwright/test").Page): Promise<string> {
  for (const [u, p] of CREDS) {
    try {
      await loginAs(page, u, p);
      return `${u}/${p}`;
    } catch {
      /* try next */
    }
  }
  return "";
}

function shotPath(name: string) {
  return path.join(SCREENSHOT_DIR, name);
}

const observations: string[] = [];
function obs(step: string, line: string) {
  observations.push(`- **${step}** — ${line}`);
}

test.use({ viewport: VP_4K });

test.describe.serial("UX walk 2026-05-29 @ 4K", () => {
  test.setTimeout(180_000);

  test("step 1: landing / home", async ({ page }) => {
    const cred = await tolerantLogin(page);
    obs("Step 1 auth", `signed in with cred=${cred || "NONE"}`);

    await page.goto("/", { waitUntil: "domcontentloaded" });
    await page.waitForTimeout(2_500);
    await page.screenshot({ path: shotPath("01-landing-4k.png"), fullPage: false });
    await page.screenshot({
      path: shotPath("01-landing-fullpage-4k.png"),
      fullPage: true,
    });
    const heroVisible = await page
      .locator("h1, h2")
      .first()
      .isVisible()
      .catch(() => false);
    const signOutVisible = await page
      .getByText(/sign out/i)
      .first()
      .isVisible()
      .catch(() => false);
    obs(
      "Step 1 landing",
      `hero=${heroVisible}, signed-in=${signOutVisible}`,
    );
  });

  test("step 2: collections list", async ({ page }) => {
    await tolerantLogin(page);
    await page.goto("/collections", { waitUntil: "domcontentloaded" });
    await page.waitForTimeout(3_500);
    await page.screenshot({
      path: shotPath("02-collections-list-4k.png"),
      fullPage: false,
    });
    await page.screenshot({
      path: shotPath("02-collections-list-fullpage-4k.png"),
      fullPage: true,
    });

    const bodyText = await page.locator("body").innerText().catch(() => "");
    obs(
      "Step 2 collections",
      `microsections=${/microsection|PH2940|composite/i.test(bodyText)}, MFFD=${/MFFD/i.test(
        bodyText,
      )}, LUMEN=${/LUMEN|TR-00/i.test(bodyText)}`,
    );
  });

  test("step 3: microsections collection landing", async ({ page }) => {
    await tolerantLogin(page);
    await page.goto(`/collections/${MICROSECTIONS_COLLECTION_APPID}`, {
      waitUntil: "domcontentloaded",
    });
    await page.waitForTimeout(5_000);
    await page.screenshot({
      path: shotPath("03-microsections-landing-4k.png"),
      fullPage: false,
    });
    await page.screenshot({
      path: shotPath("03-microsections-landing-fullpage-4k.png"),
      fullPage: true,
    });

    const text = await page.locator("body").innerText().catch(() => "");
    obs(
      "Step 3 microsections-landing",
      `cite-card=${/cite this/i.test(text)}, rdm-widget=${/metadata completeness|RDM/i.test(text)}, bob=${/bob/i.test(text)}, forbidden=${/forbidden|not authori[sz]ed|403/i.test(text)}`,
    );
  });

  test("step 4: microsections DO PH2940-07 — FR1b confirmation", async ({
    page,
  }) => {
    await tolerantLogin(page);
    await page.goto(
      `/collections/${MICROSECTIONS_COLLECTION_APPID}/dataobjects/${MICROSECTIONS_DO_APPID_PH2940_07}`,
      { waitUntil: "domcontentloaded" },
    );
    await page.waitForTimeout(6_000);
    await page.screenshot({
      path: shotPath("04-microsections-do-detail-4k.png"),
      fullPage: false,
    });
    await page.screenshot({
      path: shotPath("04-microsections-do-detail-fullpage-4k.png"),
      fullPage: true,
    });

    const text = await page.locator("body").innerText().catch(() => "");
    obs(
      "Step 4 microsections DO PH2940-07",
      `file-refs-rendered=${/file reference|fileReference|\.png|\.jpg|\.tiff|\.ipynb/i.test(text)}, empty-state=${/no references|0 references/i.test(text)}, notebooks-pane=${/notebook|jupyter|lab journal/i.test(text)}`,
    );
  });

  test("step 5: MFFD timeseries container — TS-AXIS chips", async ({
    page,
  }) => {
    await tolerantLogin(page);
    await page.goto("/containers/timeseries/1772", {
      waitUntil: "domcontentloaded",
    });
    await page.waitForTimeout(6_000);
    await page.screenshot({
      path: shotPath("05-mffd-ts-container-4k.png"),
      fullPage: false,
    });
    await page.screenshot({
      path: shotPath("05-mffd-ts-container-fullpage-4k.png"),
      fullPage: true,
    });

    const text = await page.locator("body").innerText().catch(() => "");
    obs(
      "Step 5 MFFD TS 1772",
      `spatial:axis-chips=${/spatial:axis|spatial-axis|axis\s+[xyz]\b/i.test(text)}, AFP=${/AFP/i.test(text)}, LBR=${/LBR/i.test(text)}, channels-present=${/channel/i.test(text)}`,
    );
  });

  test("step 6: Trace3D dropdown / annotation preselect", async ({ page }) => {
    await tolerantLogin(page);
    await page.goto("/containers/timeseries/1772", {
      waitUntil: "domcontentloaded",
    });
    await page.waitForTimeout(4_000);

    const trace3DBtn = page
      .getByRole("button", { name: /trace3d|3d|visuali[sz]e/i })
      .first();
    const visible = await trace3DBtn.isVisible().catch(() => false);
    if (visible) {
      await trace3DBtn.click().catch(() => {});
      await page.waitForTimeout(2_500);
      await page.screenshot({
        path: shotPath("06-trace3d-dialog-4k.png"),
        fullPage: false,
      });
    } else {
      await page.screenshot({
        path: shotPath("06-trace3d-not-found-4k.png"),
        fullPage: false,
      });
    }
    obs("Step 6 Trace3D", `affordance-visible=${visible}`);
  });

  test("step 7: LUMEN showcase (skip if absent)", async ({ page }) => {
    await tolerantLogin(page);
    await page.goto("/collections", { waitUntil: "domcontentloaded" });
    await page.waitForTimeout(2_000);
    const text = await page.locator("body").innerText().catch(() => "");
    const sawLUMEN = /LUMEN/i.test(text);
    await page.screenshot({
      path: shotPath("07-lumen-search-4k.png"),
      fullPage: false,
    });
    obs("Step 7 LUMEN", `LUMEN-listed=${sawLUMEN}`);
  });

  test("step 8: /help page", async ({ page }) => {
    await tolerantLogin(page);
    await page.goto("/help", { waitUntil: "domcontentloaded" });
    await page.waitForTimeout(3_000);
    await page.screenshot({
      path: shotPath("08-help-page-4k.png"),
      fullPage: false,
    });
    await page.screenshot({
      path: shotPath("08-help-page-fullpage-4k.png"),
      fullPage: true,
    });

    const text = await page.locator("body").innerText().catch(() => "");
    obs(
      "Step 8 help",
      `help-content=${/help|getting started|guide/i.test(text)}, word-count=${text.split(/\s+/).length}`,
    );
  });

  test("step 9: sidebar 4K + 1440 comparison", async ({ page }) => {
    await tolerantLogin(page);
    await page.goto(`/collections/${MICROSECTIONS_COLLECTION_APPID}`, {
      waitUntil: "domcontentloaded",
    });
    await page.waitForTimeout(5_000);
    const sidebar = page.locator(".v-navigation-drawer, aside, nav").first();
    const sidebarVisible = await sidebar.isVisible().catch(() => false);
    await page.screenshot({
      path: shotPath("09-sidebar-4k-zoom.png"),
      fullPage: false,
      clip: { x: 0, y: 0, width: 800, height: 1500 },
    });

    await page.setViewportSize(VP_NARROW);
    await page.waitForTimeout(1_500);
    await page.screenshot({
      path: shotPath("09-sidebar-1440-comparison.png"),
      fullPage: false,
    });
    obs("Step 9 sidebar", `sidebar-found=${sidebarVisible}; 4K+1440 captured`);
    await page.setViewportSize(VP_4K);
  });

  test("step 10: sign-out / sign-in cycle", async ({ page }) => {
    await tolerantLogin(page);
    await page.goto("/", { waitUntil: "domcontentloaded" });
    await page.waitForTimeout(1_500);

    const signOutBtn = page
      .getByRole("button", { name: /sign out/i })
      .first();
    const signOutLink = page.getByText(/sign out/i).first();

    let clicked = false;
    if (await signOutBtn.isVisible().catch(() => false)) {
      await signOutBtn.click().catch(() => {});
      clicked = true;
    } else if (await signOutLink.isVisible().catch(() => false)) {
      await signOutLink.click().catch(() => {});
      clicked = true;
    }
    await page.waitForTimeout(3_000);
    await page.screenshot({
      path: shotPath("10-after-signout-4k.png"),
      fullPage: false,
    });

    const url = page.url();
    const text = await page.locator("body").innerText().catch(() => "");
    obs(
      "Step 10 sign-out",
      `clicked=${clicked}, url=${url}, sign-in-visible=${/sign in|log in/i.test(text)}, errors=${/401|403|unauthor|forbidden/i.test(text)}`,
    );
  });

  test.afterAll(async () => {
    fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
    fs.writeFileSync(
      path.join(SCREENSHOT_DIR, "observations.md"),
      "# UX Walk 2026-05-29 — raw observations\n\n" +
        observations.join("\n") +
        "\n",
    );
  });
});
