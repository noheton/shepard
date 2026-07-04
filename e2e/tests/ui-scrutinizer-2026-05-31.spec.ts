/**
 * UI Scrutinizer — second pass 2026-05-31.
 *
 * Verifies the 2026-05-30 audit's wave 1-9 fixes hold, hunts regressions,
 * new placeholders, in-context Tools menus, the archive flow, and 4K
 * layout on newly-shipped pages.
 *
 * Auth: alice / alice-demo (realm role `user`).
 * Viewport: 3840x2160 (operator's actual viewport, per
 *   feedback_validate_user_viewport.md).
 */
import { test, expect, Page } from "@playwright/test";
import * as fs from "node:fs";
import * as path from "node:path";
import { loginAs } from "./helpers/auth";

const OUT_DIR =
  "/opt/shepard/aidocs/agent-findings/screenshots/ui-scrutinizer-2026-05-31";
const MANIFEST = path.join(OUT_DIR, "_manifest.json");
fs.mkdirSync(OUT_DIR, { recursive: true });

const VIEWPORT = { width: 3840, height: 2160 };

// Known long-form ids (work post BUG-COLL-APPID-ROUTE-001 fix in both forms).
// LUMEN = 2107; MFFD = 1787 (from the 2026-05-30 manifest).
const LUMEN_LONG = "2107";
const LUMEN_APPID_GUESS = "019e6ffc-89a4-76b5-8dbb-15888646a904";
const MFFD_LONG = "1787";
const TR001_LONG = "2112";
const FILEREF_TR001 = "2256";
const SDREF_TR001 = "2261";
const FILE_CONT = "4277";
const TS_CONT = ""; // discovered later
const SD_CONT = "2156";

type Finding = {
  route: string;
  slug: string;
  screenshot: string;
  status: number | "n/a";
  title?: string;
  placeholderHits: string[];
  textPlaceholderHits: string[];
  consoleErrors: string[];
  redToastTexts: string[];
  notes: string[];
};

const findings: Finding[] = [];

async function visit(
  page: Page,
  slug: string,
  route: string,
  notes: string[] = [],
): Promise<Finding> {
  const consoleErrors: string[] = [];
  const errHandler = (m: any) => {
    if (m.type && m.type() === "error")
      consoleErrors.push(String(m.text()).slice(0, 240));
  };
  const errPageErr = (e: any) =>
    consoleErrors.push("PAGEERROR: " + String(e.message).slice(0, 240));
  page.on("console", errHandler);
  page.on("pageerror", errPageErr);

  let status: number | "n/a" = "n/a";
  try {
    const resp = await page.goto(route, {
      waitUntil: "domcontentloaded",
      timeout: 30_000,
    });
    status = resp?.status() ?? "n/a";
  } catch (e) {
    consoleErrors.push("GOTO_FAIL: " + String(e).slice(0, 240));
  }
  // Allow Vue + data fetch to settle.
  await page.waitForTimeout(4000);

  const placeholderHits: string[] = [];
  const html = await page.content().catch(() => "");
  for (const marker of [
    "PlaceholderImplStatus",
    "PlaceholderFragmentPane",
    "PlaceholderPageHeader",
    "PlaceholderRestDump",
    "placeholder-impl-status",
    "placeholder-fragment-pane",
    "placeholder-page-header",
    "placeholder-rest-dump",
    "Implementation status",
    "Raw REST response",
    "Page placeholder",
  ]) {
    if (html.includes(marker)) placeholderHits.push(marker);
  }

  const textPlaceholderHits: string[] = [];
  const bodyText = await page
    .locator("body")
    .innerText()
    .catch(() => "");
  for (const phrase of [
    "Coming soon",
    "Under construction",
    "Backend pending",
    "backend pending",
    "Not yet implemented",
    "TODO",
    "TBD",
    "queued",
  ]) {
    if (bodyText.includes(phrase)) textPlaceholderHits.push(phrase);
  }

  // Capture any red toast / snackbar texts (Vuetify v-snackbar / v-alert error).
  const redToastTexts: string[] = [];
  const toastNodes = await page
    .locator(
      ".v-snackbar.v-snackbar--error, .v-snackbar.error, [class*='error']:has-text('Error')",
    )
    .all()
    .catch(() => []);
  for (const t of toastNodes.slice(0, 5)) {
    const txt = await t.innerText().catch(() => "");
    if (txt) redToastTexts.push(txt.slice(0, 240));
  }

  const screenshot = path.join(OUT_DIR, `${slug}.png`);
  await page
    .screenshot({ path: screenshot, fullPage: false })
    .catch(() => {});

  const title = await page.title().catch(() => undefined);

  const f: Finding = {
    route,
    slug,
    screenshot,
    status,
    title,
    placeholderHits,
    textPlaceholderHits,
    consoleErrors: consoleErrors.slice(0, 20),
    redToastTexts,
    notes,
  };
  findings.push(f);

  page.off("console", errHandler);
  page.off("pageerror", errPageErr);
  return f;
}

test.use({ viewport: VIEWPORT });

test.describe.serial("UI Scrutinizer 2026-05-31 — verification walk", () => {
  test("login + main walk", async ({ page }) => {
    test.setTimeout(900_000);
    await loginAs(page, "alice", "alice-demo");

    // Hub pages
    await visit(page, "home", "/");
    await visit(page, "tools", "/tools");
    await visit(page, "me", "/me");
    await visit(page, "collections-list", "/collections");
    await visit(page, "containers-list", "/containers");
    await visit(page, "scene-graphs-list", "/scene-graphs");
    await visit(page, "search", "/search");
    await visit(page, "search-with-q", "/search?q=test");

    // Semantic
    await visit(page, "semantic-landing", "/semantic");
    await visit(page, "semantic-vocabularies", "/semantic/vocabularies");
    await visit(page, "semantic-sparql", "/semantic/sparql");

    // Shapes + snapshots
    await visit(page, "shapes-validate", "/shapes/validate");
    await visit(page, "shapes-render", "/shapes/render");
    await visit(page, "snapshots-diff", "/snapshots/diff");

    // Admin gates (alice is non-admin, expect access-denied)
    await visit(page, "admin", "/admin");
    await visit(page, "admin-instance-registry", "/admin/instance-registry");
    await visit(page, "admin-provenance", "/admin/provenance");

    // Collection by long-form (works) — LUMEN
    await visit(
      page,
      "collection-lumen-long",
      `/collections/${LUMEN_LONG}`,
      ["BUG-COLL-APPID-ROUTE-001 baseline path"],
    );

    // Collection by appId form — the wave-1 fix that we verify
    await visit(
      page,
      "collection-lumen-appid",
      `/collections/${LUMEN_APPID_GUESS}`,
      ["BUG-COLL-APPID-ROUTE-001 fix verification"],
    );

    // MFFD by long-form
    await visit(page, "collection-mffd-long", `/collections/${MFFD_LONG}`);

    // DataObject TR-001
    await visit(
      page,
      "dataobject-tr001",
      `/collections/${LUMEN_LONG}/dataobjects/${TR001_LONG}`,
    );

    // FileReference
    await visit(
      page,
      "fileref-tr001",
      `/collections/${LUMEN_LONG}/dataobjects/${TR001_LONG}/filereferences/${FILEREF_TR001}`,
    );

    // SD reference
    await visit(
      page,
      "sdref-tr001",
      `/collections/${LUMEN_LONG}/dataobjects/${TR001_LONG}/structureddatareferences/${SDREF_TR001}`,
    );

    // Containers detail
    await visit(page, "container-files", `/containers/files/${FILE_CONT}`);
    await visit(
      page,
      "container-structureddata",
      `/containers/structureddata/${SD_CONT}`,
    );

    // /me sub-fragments
    await visit(page, "me-mcp", "/me#mcp");
    await visit(page, "me-ai-settings", "/me#ai-settings");
    await visit(page, "me-git", "/me#git");
    await visit(page, "me-apikeys", "/me#api-keys");

    // Misc
    await visit(page, "about", "/about");
    await visit(page, "help", "/help");

    // Semantic predicate stats page (new in wave 1-9)
    await visit(
      page,
      "semantic-predicate-shepard",
      "/semantic/predicates/urn:shepard:lumen:test-engineer",
    );

    fs.writeFileSync(MANIFEST, JSON.stringify(findings, null, 2));
  });

  test("EntityToolsMenu walk on Collection detail", async ({ page }) => {
    test.setTimeout(300_000);
    await loginAs(page, "alice", "alice-demo");

    await page.goto(`/collections/${LUMEN_LONG}`, {
      waitUntil: "domcontentloaded",
    });
    await page.waitForTimeout(4000);

    // Look for the Tools menu button on the Collection detail header.
    const toolsBtn = page.getByRole("button", { name: /tools/i }).first();
    const visible = await toolsBtn.isVisible().catch(() => false);
    await page
      .screenshot({
        path: path.join(OUT_DIR, "coll-tools-before-click.png"),
      })
      .catch(() => {});
    if (visible) {
      await toolsBtn.click().catch(() => {});
      await page.waitForTimeout(1500);
      await page
        .screenshot({
          path: path.join(OUT_DIR, "coll-tools-menu-open.png"),
        })
        .catch(() => {});
      // Dump menu item text.
      const menuItems = await page
        .locator(".v-overlay__content .v-list-item-title")
        .allInnerTexts()
        .catch(() => []);
      fs.writeFileSync(
        path.join(OUT_DIR, "coll-tools-menu-items.json"),
        JSON.stringify(menuItems, null, 2),
      );
    } else {
      fs.writeFileSync(
        path.join(OUT_DIR, "coll-tools-menu-items.json"),
        JSON.stringify({ error: "Tools button not visible on Collection detail" }),
      );
    }
  });

  test("EntityToolsMenu walk on DataObject detail", async ({ page }) => {
    test.setTimeout(300_000);
    await loginAs(page, "alice", "alice-demo");

    await page.goto(
      `/collections/${LUMEN_LONG}/dataobjects/${TR001_LONG}`,
      { waitUntil: "domcontentloaded" },
    );
    await page.waitForTimeout(4000);

    const toolsBtn = page.getByRole("button", { name: /tools/i }).first();
    const visible = await toolsBtn.isVisible().catch(() => false);
    await page
      .screenshot({
        path: path.join(OUT_DIR, "do-tools-before-click.png"),
      })
      .catch(() => {});
    if (visible) {
      await toolsBtn.click().catch(() => {});
      await page.waitForTimeout(1500);
      await page
        .screenshot({
          path: path.join(OUT_DIR, "do-tools-menu-open.png"),
        })
        .catch(() => {});
      const menuItems = await page
        .locator(".v-overlay__content .v-list-item-title")
        .allInnerTexts()
        .catch(() => []);
      fs.writeFileSync(
        path.join(OUT_DIR, "do-tools-menu-items.json"),
        JSON.stringify(menuItems, null, 2),
      );

      // Click "Query annotations (SPARQL)" if present — verify pre-fill.
      const sparqlItem = page
        .locator(".v-overlay__content .v-list-item")
        .filter({ hasText: /sparql|query/i })
        .first();
      if (await sparqlItem.isVisible().catch(() => false)) {
        await sparqlItem.click().catch(() => {});
        await page.waitForTimeout(3500);
        await page
          .screenshot({
            path: path.join(OUT_DIR, "tools-context-do-sparql-after.png"),
            fullPage: false,
          })
          .catch(() => {});
        // Capture URL + visible query text.
        fs.writeFileSync(
          path.join(OUT_DIR, "tools-context-do-sparql-url.txt"),
          page.url(),
        );
      }
    } else {
      fs.writeFileSync(
        path.join(OUT_DIR, "do-tools-menu-items.json"),
        JSON.stringify({ error: "Tools button not visible on DataObject detail" }),
      );
    }
  });

  test("Archive flow walk on Collection", async ({ page }) => {
    test.setTimeout(300_000);
    await loginAs(page, "alice", "alice-demo");

    await page.goto(`/collections/${LUMEN_LONG}`, {
      waitUntil: "domcontentloaded",
    });
    await page.waitForTimeout(4000);
    await page
      .screenshot({
        path: path.join(OUT_DIR, "archive-coll-baseline.png"),
      })
      .catch(() => {});

    // Look for the Archive control / kebab menu.
    const archiveBtn = page
      .getByRole("button", { name: /archive/i })
      .first();
    const archiveVisible = await archiveBtn.isVisible().catch(() => false);
    fs.writeFileSync(
      path.join(OUT_DIR, "archive-coll-button-visible.txt"),
      String(archiveVisible),
    );

    // Look for ARCHIVED chip if already archived.
    const archivedChip = page
      .locator("text=ARCHIVED")
      .first();
    const chipVisible = await archivedChip.isVisible().catch(() => false);
    fs.writeFileSync(
      path.join(OUT_DIR, "archive-coll-chip-visible.txt"),
      String(chipVisible),
    );
  });
});
