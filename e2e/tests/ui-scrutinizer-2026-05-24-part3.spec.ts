/**
 * UI Scrutinizer Part 3 — container views (phase 4).
 * Get container IDs by visiting LUMEN landing, expanding the Containers panel,
 * and harvesting link hrefs.
 */
import { test, expect, type Page, type ConsoleMessage, type Response, type Request } from "@playwright/test";
import * as fs from "fs";
import * as path from "path";

const KC = process.env.KEYCLOAK_HOST || "https://shepard-auth.nuclide.systems";
const REALM = "shepard-demo";
const OUT = "/opt/shepard/aidocs/agent-findings/ui-scrutinizer-2026-05-24-evidence";
const LUMEN_COLL = 42;
const MFFD_COLL = 661923;

async function loginAs(page: Page, username: string, password: string) {
  await page.goto("/auth/signIn");
  await page.getByRole("button", { name: /sign in|login/i }).first().click();
  await page.waitForURL(`${KC}/realms/${REALM}/**`, { timeout: 15_000 });
  await page.fill("#username", username);
  await page.fill("#password", password);
  await page.click('[type="submit"]');
  await page.waitForURL(/shepard\.nuclide\.systems(?!.*error)/, { timeout: 20_000 });
  await page.waitForSelector("text=SIGN OUT", { timeout: 15_000 });
}

function attachListeners(page: Page) {
  const errors: string[] = [];
  const warnings: string[] = [];
  const netErrors: { url: string; status: number }[] = [];
  page.on("console", (m: ConsoleMessage) => {
    if (m.type() === "error") errors.push(m.text());
    if (m.type() === "warning") warnings.push(m.text());
  });
  page.on("pageerror", (e: Error) => errors.push(`PAGEERROR: ${e.message}`));
  page.on("response", (r: Response) => {
    const s = r.status();
    if (s >= 400) netErrors.push({ url: r.url(), status: s });
  });
  return { errors, warnings, netErrors };
}

test("ui-scrutinizer part3 — containers + sidebar deep-dive", async ({ browser }) => {
  test.setTimeout(30 * 60_000);

  const ctx = await browser.newContext({
    viewport: { width: 1920, height: 1080 },
    ignoreHTTPSErrors: true,
  });
  const page = await ctx.newPage();
  await loginAs(page, "alice", "alice-demo");

  // Visit /containers, click into each row to capture container detail
  const containerLinks: string[] = [];
  await page.goto("/containers");
  await page.waitForTimeout(4000);

  // Extract the first row of each kind by clicking on a row
  const rows = await page.locator("tbody tr").all();
  for (const row of rows.slice(0, 4)) {
    try {
      const text = await row.textContent();
      const id = text?.match(/^\s*(\d+)/)?.[1];
      const kind = text?.toLowerCase().includes("structured") ? "structureddata"
        : text?.toLowerCase().includes("timeseries") ? "timeseries"
        : text?.toLowerCase().includes("file") ? "files"
        : undefined;
      if (id && kind) containerLinks.push(`/containers/${kind}/${id}`);
    } catch {}
  }
  fs.writeFileSync(path.join(OUT, "container-links-from-list.json"), JSON.stringify(containerLinks, null, 2));

  // Visit each
  for (let i = 0; i < containerLinks.length; i++) {
    const url = containerLinks[i];
    const slug = `p4-${String(i + 1).padStart(2, "0")}-${url.split("/")[2]}-${url.split("/")[3]}`;
    const { errors, warnings, netErrors } = attachListeners(page);
    const t0 = Date.now();
    try {
      await page.goto(url, { waitUntil: "domcontentloaded", timeout: 30_000 });
      await page.waitForTimeout(6000);
    } catch (e) {
      errors.push(`NAV_FAIL: ${(e as Error).message}`);
    }
    const dur = Date.now() - t0;
    try {
      await page.screenshot({ path: path.join(OUT, `${slug}-1920.png`), fullPage: true });
    } catch {}
    fs.writeFileSync(
      path.join(OUT, `${slug}-1920.json`),
      JSON.stringify({
        url,
        finalUrl: page.url(),
        title: await page.title(),
        durationMs: dur,
        consoleErrors: errors,
        consoleWarnings: warnings,
        networkErrors: netErrors,
      }, null, 2),
    );
  }

  // Also: capture a LUMEN DO at 4K (specifically TR-001=45) full-page to fully
  // characterize the bug — is the panel HIDDEN or RENDERED-WHITE-OFFSCREEN?
  await page.setViewportSize({ width: 3840, height: 2160 });
  await page.goto(`/collections/${LUMEN_COLL}/dataobjects/45`);
  await page.waitForTimeout(7000);
  await page.screenshot({ path: path.join(OUT, "bug139-tr001-4k-fullpage.png"), fullPage: true });
  // Snapshot the DOM for the main-content slot to see what's there
  const dom = await page.evaluate(() => {
    const out: Record<string, unknown> = {};
    const mains = document.querySelectorAll("main, .v-main, .main-content, [class*='detail']");
    out.mainsCount = mains.length;
    out.mains = Array.from(mains).slice(0, 5).map((el) => {
      const r = (el as HTMLElement).getBoundingClientRect();
      return {
        tag: el.tagName,
        cls: (el as HTMLElement).className,
        rect: { x: r.x, y: r.y, w: r.width, h: r.height },
        childCount: el.children.length,
        innerHTMLLen: el.innerHTML.length,
        textPreview: (el.textContent || "").slice(0, 200),
      };
    });
    // also explicitly grab elements that should contain DO detail
    const detail = document.querySelector("[class*='DataObject'], [class*='dataobject'], [class*='data-object']");
    if (detail) {
      const r = (detail as HTMLElement).getBoundingClientRect();
      out.detailEl = {
        tag: detail.tagName,
        cls: (detail as HTMLElement).className,
        rect: { x: r.x, y: r.y, w: r.width, h: r.height },
        innerHTMLLen: detail.innerHTML.length,
        textPreview: (detail.textContent || "").slice(0, 200),
      };
    }
    // window
    out.window = {
      innerWidth: window.innerWidth,
      innerHeight: window.innerHeight,
      scrollX: window.scrollX,
      scrollY: window.scrollY,
      docScrollWidth: document.documentElement.scrollWidth,
      docScrollHeight: document.documentElement.scrollHeight,
    };
    return out;
  });
  fs.writeFileSync(path.join(OUT, "bug139-tr001-4k-dom.json"), JSON.stringify(dom, null, 2));

  expect(true).toBe(true);
});
