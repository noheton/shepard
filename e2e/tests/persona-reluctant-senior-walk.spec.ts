import { test, expect } from "@playwright/test";
import { loginAs } from "./helpers/auth";

/**
 * Persona: Reluctant Senior Researcher — live walk on shepard.nuclide.systems.
 * Re-run dated 2026-05-24. Observation-only spec; takes screenshots that the
 * persona findings reference. See:
 *   aidocs/agent-findings/persona-reluctant-senior-2026-05-24.md
 */

const OUT = "screenshots/persona-reluctant-2026-05-24";

test.describe("Reluctant Senior — live walk", () => {
  test("home + login lands on a recognisable page", async ({ page }) => {
    await loginAs(page, "alice", "alice-demo");
    await page.waitForLoadState("networkidle").catch(() => {});
    await page.screenshot({ path: `${OUT}/01-home.png`, fullPage: true });
    await expect(page.getByText(/sign out/i).first()).toBeVisible();
  });

  test("collections list — new # DOs column hydration", async ({ page }) => {
    await loginAs(page, "alice", "alice-demo");
    await page.goto("/collections", { waitUntil: "networkidle" }).catch(() => {});
    await page.waitForTimeout(1500);
    await page.screenshot({ path: `${OUT}/02-collections-list.png`, fullPage: true });
  });

  test("collection 42 landing — Cite card + Metadata Completeness widget", async ({ page }) => {
    await loginAs(page, "alice", "alice-demo");
    await page.goto("/collections/42", { waitUntil: "networkidle" }).catch(() => {});
    await page.waitForTimeout(2000);
    await page.screenshot({ path: `${OUT}/03-collection-42-landing.png`, fullPage: true });

    const citeCard = page.getByText(/cite this dataset/i).first();
    const hasCite = await citeCard.isVisible().catch(() => false);
    console.log(`[persona] cite-this card visible: ${hasCite}`);

    const metaWidget = page.getByText(/metadata completeness/i).first();
    const hasMeta = await metaWidget.isVisible().catch(() => false);
    console.log(`[persona] metadata-completeness widget visible: ${hasMeta}`);
  });

  test("DataObject detail — count badges on reference panels", async ({ page }) => {
    await loginAs(page, "alice", "alice-demo");
    await page.goto("/collections/42", { waitUntil: "networkidle" }).catch(() => {});
    await page.waitForTimeout(1500);
    const tr001 = page.getByText(/TR-001/i).first();
    if (await tr001.isVisible().catch(() => false)) {
      await tr001.click();
      await page.waitForLoadState("networkidle").catch(() => {});
      await page.waitForTimeout(2000);
    }
    await page.screenshot({ path: `${OUT}/04-dataobject-detail.png`, fullPage: true });
  });

  test("header search — typing 'TR-04'", async ({ page }) => {
    await loginAs(page, "alice", "alice-demo");
    await page.waitForTimeout(1000);
    const search = page.getByPlaceholder(/search/i).first();
    if (await search.isVisible().catch(() => false)) {
      await search.click();
      await search.fill("TR-04");
      await page.waitForTimeout(1500);
      await page.screenshot({ path: `${OUT}/05-header-search.png`, fullPage: true });
    } else {
      await page.screenshot({ path: `${OUT}/05-header-search-missing.png`, fullPage: true });
    }
  });

  test("/me landing — section cards + ORCID input on profile", async ({ page }) => {
    await loginAs(page, "alice", "alice-demo");
    await page.goto("/me", { waitUntil: "networkidle" }).catch(() => {});
    await page.waitForTimeout(1500);
    await page.screenshot({ path: `${OUT}/06-me-landing.png`, fullPage: true });

    const profile = page.getByText(/^profile$/i).first();
    if (await profile.isVisible().catch(() => false)) {
      await profile.click();
      await page.waitForLoadState("networkidle").catch(() => {});
      await page.waitForTimeout(1500);
      await page.screenshot({ path: `${OUT}/07-me-profile.png`, fullPage: true });
    }
  });

  test("/admin as alice — should be Unauthorized", async ({ page }) => {
    await loginAs(page, "alice", "alice-demo");
    await page.goto("/admin", { waitUntil: "networkidle" }).catch(() => {});
    await page.waitForTimeout(1500);
    await page.screenshot({ path: `${OUT}/08-admin-as-alice.png`, fullPage: true });
    const restricted = await page
      .getByText(/administration is restricted|required role/i)
      .first()
      .isVisible()
      .catch(() => false);
    console.log(`[persona] /admin shows restricted view to alice: ${restricted}`);
  });
});
