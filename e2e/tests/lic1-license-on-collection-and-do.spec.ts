/**
 * LIC1 e2e — license + accessRights persist + display on Collection + DataObject.
 *
 * Acceptance criteria (per the LIC1 task spec):
 *  - As alice: open a Collection, set `license = MIT` via the new UI, save,
 *    reload, assert it persists + displays.
 *  - Same for DataObject.
 *  - Set `accessRights = RESTRICTED` on the collection, assert persistence.
 *
 * Strategy: create a throw-away test collection (mirroring the pattern in
 * `collections.spec.ts`), then open its detail page and exercise the Edit
 * dialog twice — once setting license, once setting accessRights. We do the
 * DataObject leg by creating one inside the same collection via the Create
 * dialog and confirming the chips render after edit.
 *
 * The "save + reload + still shows" assertion is the load-bearing one — it
 * proves the wire round-trip (frontend → REST → Neo4j → REST → frontend) and
 * that the LIC1 V57 NOOP-migration + AbstractDataObject + AbstractDataObjectIO
 * + chip components are all wired together correctly.
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "./helpers/auth";

test.describe("LIC1 — license + accessRights on Collection + DataObject", () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, "alice", "alice-demo");
  });

  test("license + accessRights persist + display on a Collection", async ({
    page,
  }) => {
    // ── 1. create throw-away collection ───────────────────────────────────
    await page.goto("/collections");
    await page.waitForLoadState("networkidle");
    const collName = `lic1-e2e-coll-${Date.now()}`;
    const createDialog = page
      .locator(".v-overlay__content")
      .filter({ hasText: "Create Collection" });
    await page.getByRole("button", { name: /create new collection/i }).click();
    await expect(createDialog).toBeVisible({ timeout: 5_000 });
    await createDialog.locator("input").first().fill(collName);
    await createDialog.getByRole("button", { name: "Next", exact: true }).click();
    await createDialog.getByRole("button", { name: "Create", exact: true }).click();
    await expect(page.getByText(collName)).toBeVisible({ timeout: 10_000 });

    // ── 2. open the collection's detail page ─────────────────────────────
    await page.getByText(collName).first().click();
    await page.waitForLoadState("networkidle");
    // Collection detail URL pattern: /collections/<appId>
    await expect(page).toHaveURL(/\/collections\/[0-9a-f-]+/);
    const detailUrl = page.url();

    // ── 3. open Edit dialog and set license + accessRights ───────────────
    await page.getByRole("button", { name: /edit/i }).first().click();
    const editDialog = page.locator(".v-overlay__content").filter({
      hasText: /edit collection/i,
    });
    await expect(editDialog).toBeVisible({ timeout: 5_000 });

    // License input is a v-autocomplete; AccessRights is a v-select. Both are
    // identified by their visible label. The autocomplete accepts free-text
    // input so we simply type "MIT" then commit by pressing Enter (the SPDX
    // list is curated; MIT is on it).
    const licenseField = editDialog.getByLabel(/license/i);
    await licenseField.click();
    await licenseField.fill("MIT");
    // Wait for the autocomplete listbox + click the MIT option if rendered,
    // otherwise commit the typed value with Enter.
    const mitOption = page.locator(".v-list-item-title").filter({ hasText: /^MIT$/ });
    if (await mitOption.count()) {
      await mitOption.first().click();
    } else {
      await licenseField.press("Enter");
    }

    const accessField = editDialog.getByLabel(/access[- ]?rights/i);
    await accessField.click();
    await page
      .locator(".v-list-item-title")
      .filter({ hasText: /^RESTRICTED$/i })
      .first()
      .click();

    await editDialog.getByRole("button", { name: /save|update/i }).click();
    await expect(editDialog).toBeHidden({ timeout: 10_000 });

    // ── 4. reload and assert chips are visible ───────────────────────────
    await page.goto(detailUrl);
    await page.waitForLoadState("networkidle");
    // FAIR strip: LicenseChip shows the SPDX id; AccessRightsChip shows the
    // enum value verbatim (uppercase).
    await expect(page.getByText("MIT").first()).toBeVisible({ timeout: 10_000 });
    await expect(
      page.getByText(/RESTRICTED/i).first(),
    ).toBeVisible({ timeout: 10_000 });
  });

  test("license persists on a DataObject inside the collection", async ({
    page,
  }) => {
    // Reuse the same create-coll → create-DO → edit-DO flow.
    await page.goto("/collections");
    await page.waitForLoadState("networkidle");
    const collName = `lic1-e2e-do-${Date.now()}`;
    const createCollDialog = page
      .locator(".v-overlay__content")
      .filter({ hasText: "Create Collection" });
    await page.getByRole("button", { name: /create new collection/i }).click();
    await expect(createCollDialog).toBeVisible({ timeout: 5_000 });
    await createCollDialog.locator("input").first().fill(collName);
    await createCollDialog.getByRole("button", { name: "Next", exact: true }).click();
    await createCollDialog.getByRole("button", { name: "Create", exact: true }).click();
    await expect(page.getByText(collName)).toBeVisible({ timeout: 10_000 });
    await page.getByText(collName).first().click();
    await page.waitForLoadState("networkidle");

    // Create a DataObject inside this collection.
    const doName = `lic1-do-${Date.now()}`;
    await page
      .getByRole("button", { name: /create.*data.?object|new.*data.?object/i })
      .first()
      .click();
    const createDoDialog = page
      .locator(".v-overlay__content")
      .filter({ hasText: /create.*data.?object/i });
    await expect(createDoDialog).toBeVisible({ timeout: 5_000 });
    await createDoDialog.locator("input").first().fill(doName);
    await createDoDialog
      .getByRole("button", { name: /next|create/i })
      .first()
      .click();
    // Two-step wizard mirrors collection create; if a second "Create" button
    // is exposed, click it.
    const finalCreate = createDoDialog.getByRole("button", {
      name: "Create",
      exact: true,
    });
    if (await finalCreate.count()) {
      await finalCreate.click();
    }

    // Navigate to the new DataObject detail page.
    await expect(page.getByText(doName).first()).toBeVisible({ timeout: 10_000 });
    await page.getByText(doName).first().click();
    await page.waitForURL(/\/dataobjects\/[0-9a-f-]+/, { timeout: 10_000 });
    const doUrl = page.url();

    // Edit and set license.
    await page.getByRole("button", { name: /edit/i }).first().click();
    const editDoDialog = page
      .locator(".v-overlay__content")
      .filter({ hasText: /edit.*data.?object/i });
    await expect(editDoDialog).toBeVisible({ timeout: 5_000 });
    const licField = editDoDialog.getByLabel(/license/i);
    await licField.click();
    await licField.fill("MIT");
    const mitOpt = page.locator(".v-list-item-title").filter({ hasText: /^MIT$/ });
    if (await mitOpt.count()) {
      await mitOpt.first().click();
    } else {
      await licField.press("Enter");
    }
    await editDoDialog.getByRole("button", { name: /save|update/i }).click();
    await expect(editDoDialog).toBeHidden({ timeout: 10_000 });

    // Reload and assert.
    await page.goto(doUrl);
    await page.waitForLoadState("networkidle");
    await expect(page.getByText("MIT").first()).toBeVisible({ timeout: 10_000 });
  });
});
