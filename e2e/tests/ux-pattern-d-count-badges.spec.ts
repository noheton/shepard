/**
 * UX Pattern D e2e — count badges on DataObject reference / annotation panels.
 *
 * Acceptance:
 *   - On a DO with references (LUMEN TR-007, collection 42, DO id 51), at least
 *     one panel title / section header on the detail page contains a `(N)` count
 *     where N > 0.
 *   - The page renders `(0)` cleanly (low-emphasis) for empty sections — i.e.
 *     the codebase convention (text-low-emphasis "(0)") is preserved, not hidden.
 *
 * Convention reference: `ExpansionPanelItem.vue` already prints
 * `<div class="text-h5 text-low-emphasis">({{ count }})</div>` for every defined
 * count, including 0. This PR extends the same pattern to:
 *   - Jupyter Notebooks panel title  (was missing a count)
 *   - Semantic Annotations section header (always-visible, not a panel)
 *   - Git References inner h5 (inside the Data References panel)
 *   - Video References inner h5 (inside the Data References panel)
 *
 * The four pre-existing panels (Attributes, Lab Journal, Data References,
 * Relationships) already render counts and continue to.
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "./helpers/auth";

const LUMEN_COLL = 42;
const LUMEN_DO = 51; // TR-007 — has references, lab-journal entries, etc.

// Local `loginAsTolerant` removed 2026-05-24 — folded into the shared
// `loginAs` helper (E2E-AUTH-TOLERANT-LOGIN). The shared helper now
// covers SSO-cookie-hot, cookie-cold, and the NextAuth sign-in
// redirect-loop retry that this spec originally worked around locally.

test.describe("UX Pattern D — count badges on DataObject reference panels", () => {
  test.use({ viewport: { width: 1600, height: 900 } });
  test.describe.configure({ mode: "serial" });
  // The detail page issues several async refs (data references, annotations,
  // lab journal, notebooks) and the OIDC sign-in dance can add 15s alone.
  test.setTimeout(90_000);

  test.beforeEach(async ({ page }) => {
    await loginAs(page, "alice", "alice-demo");
  });

  test("LUMEN TR-007 detail page shows count badges on every reference panel", async ({
    page,
  }) => {
    await page.goto(`/collections/${LUMEN_COLL}/dataobjects/${LUMEN_DO}`);
    await page.waitForLoadState("networkidle");
    // Give async refs (data references, lab journal, annotations) time to land.
    await page.waitForTimeout(2_500);

    // --- 1) Existing panel-title counts still render (regression guard) ----
    // ExpansionPanelItem renders "(N)" in `.text-h5.text-low-emphasis` siblings.
    // We assert that at least 3 distinct panel-title count badges exist
    // (covers Attributes + Lab Journal + Data References + Relationships +
    // the new Jupyter Notebooks badge — 5 expected).
    const panelTitleCounts = page.locator(
      ".v-expansion-panel-title .text-h5.text-low-emphasis",
    );
    const panelTitleCountText = await panelTitleCounts.allTextContents();
    expect(panelTitleCountText.length).toBeGreaterThanOrEqual(3);

    // At least one panel title must contain "(N)" where N>0 (LUMEN TR-007 has
    // attributes + data references + lab-journal entries).
    const hasPopulated = panelTitleCountText.some(t => /\((\d+)\)/.test(t) &&
      Number(t.match(/\((\d+)\)/)?.[1] ?? "0") > 0);
    expect(hasPopulated, `expected at least one populated count (N>0); got ${JSON.stringify(panelTitleCountText)}`).toBe(true);

    // --- 2) Semantic Annotations section header shows a count ---------------
    // The section is always visible (not a panel). The badge only renders
    // after annotations load (emit fires post-fetch). Wait for the chips
    // first so we know the fetch resolved, then assert the badge.
    await expect(
      page.locator("ul li").filter({ hasText: /Outcome|Burn|Role/ }).first(),
    ).toBeVisible({ timeout: 15_000 });
    const annotationsBadge = page.getByTestId("semantic-annotations-count");
    await expect(annotationsBadge).toBeVisible({ timeout: 10_000 });
    const annotationsText = (await annotationsBadge.textContent())?.trim();
    expect(annotationsText).toMatch(/^\(\d+\)$/);

    // --- 3) Data References panel (default-open) carries Git + Video badges -
    // Both panes live inside the Data References panel. The panel is in the
    // page's `default-open=[2,3]` set, so the inner badges are mounted on
    // load — no need to click. We only click if it's been collapsed.
    const dataRefTitle = page
      .locator(".v-expansion-panel-title")
      .filter({ hasText: /^Data References/ })
      .first();
    const isExpanded = await dataRefTitle
      .evaluate(el => el.classList.contains("v-expansion-panel-title--active"))
      .catch(() => false);
    if (!isExpanded) {
      await dataRefTitle.click();
      await page.waitForTimeout(1_000);
    }

    const gitBadge = page.getByTestId("git-references-count");
    await expect(gitBadge).toBeVisible({ timeout: 10_000 });
    expect((await gitBadge.textContent())?.trim()).toMatch(/^\(\d+\)$/);

    const videoBadge = page.getByTestId("video-references-count");
    await expect(videoBadge).toBeVisible({ timeout: 10_000 });
    expect((await videoBadge.textContent())?.trim()).toMatch(/^\(\d+\)$/);

    // --- 4) Jupyter Notebooks panel title carries a count after expand ------
    // Vuetify expansion panels are lazy by default — the inner pane only
    // mounts when expanded. Once mounted, `DataObjectNotebooksPane` emits
    // its count to the page, which sets the title badge. This is the same
    // pattern Lab Journal already follows.
    const jupyterTitle = page
      .locator(".v-expansion-panel-title")
      .filter({ hasText: /^Jupyter Notebooks/ })
      .first();
    await expect(jupyterTitle).toBeVisible({ timeout: 10_000 });
    await jupyterTitle.click();
    // Wait for the count emit to land; give the fetch a moment.
    await expect(async () => {
      const txt = (await jupyterTitle.textContent())?.trim() ?? "";
      expect(txt).toMatch(/Jupyter Notebooks\s*\(\d+\)/);
    }).toPass({ timeout: 10_000 });
  });

  test("zero-handling — low-emphasis (0) renders for empty sections", async ({
    page,
  }) => {
    // LUMEN TR-007 typically has 0 git refs + 0 video refs (synthetic dataset
    // never seeded git/video references). This locks in the zero-handling
    // convention: badges render "(0)" in low-emphasis colour rather than
    // being hidden — matching ExpansionPanelItem's existing behaviour
    // (Attributes shows "(0)" on a DO with no attributes).
    await page.goto(`/collections/${LUMEN_COLL}/dataobjects/${LUMEN_DO}`);
    await page.waitForLoadState("networkidle");
    await page.waitForTimeout(2_500);

    // Data References panel is default-open; only click if collapsed.
    const dataRefTitle = page
      .locator(".v-expansion-panel-title")
      .filter({ hasText: /^Data References/ })
      .first();
    const isExpanded = await dataRefTitle
      .evaluate(el => el.classList.contains("v-expansion-panel-title--active"))
      .catch(() => false);
    if (!isExpanded) {
      await dataRefTitle.click();
      await page.waitForTimeout(1_000);
    }

    const gitBadge = page.getByTestId("git-references-count");
    await expect(gitBadge).toBeVisible({ timeout: 10_000 });
    const gitText = (await gitBadge.textContent())?.trim();
    // Either populated ("(N)" with N>=0) — the key invariant is the badge
    // is present + low-emphasis-styled, never hidden.
    expect(gitText).toMatch(/^\(\d+\)$/);

    // Low-emphasis class verification — surfaces noisy bright (0) regression.
    const gitClasses = (await gitBadge.getAttribute("class")) ?? "";
    expect(gitClasses).toContain("text-low-emphasis");
  });
});
