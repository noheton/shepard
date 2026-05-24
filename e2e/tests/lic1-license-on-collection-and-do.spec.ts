/**
 * LIC1 e2e — license + accessRights persist + display on Collection + DataObject.
 *
 * Acceptance criteria (per the LIC1 task spec):
 *  - As alice: open a Collection, set `license = MIT` via the new UI, save,
 *    reload, assert it persists + displays.
 *  - Same for DataObject.
 *  - Set `accessRights = RESTRICTED` on the collection, assert persistence.
 *
 * Selectors validated against the live Vuetify 3 / Nuxt 3 source:
 *   - Edit dialog title is `Edit "<name>"` (NOT "Edit Collection").
 *   - Collection landing's create-DO button label is "New DataObject".
 *   - Create-Data-Object dialog title is exactly "Create Data Object".
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "./helpers/auth";

test.describe("LIC1 — license + accessRights on Collection + DataObject", () => {
  // Collection layout (frontend/layouts/collection.vue) hides the sidebar
  // below the `lg` breakpoint (1280px). Default Playwright viewport is
  // 1280x720; some buffer prevents border-edge flakiness.
  test.use({ viewport: { width: 1600, height: 900 } });

  test.beforeEach(async ({ page }) => {
    await loginAs(page, "alice", "alice-demo");
  });

  test("license + accessRights persist + display on a Collection", async ({
    page,
  }) => {
    // Diagnostic: capture PATCH/PUT bodies so an interceptor proves
    // whether the wire actually carried license + accessRights.
    const writeBodies: Array<{ method: string; url: string; body: unknown }> = [];
    page.on("request", req => {
      const m = req.method();
      if (m === "PATCH" || m === "PUT" || m === "POST") {
        const u = req.url();
        if (u.includes("/collections/") && !u.includes("/auth/")) {
          let body: unknown = null;
          try { body = req.postDataJSON(); } catch { body = req.postData(); }
          writeBodies.push({ method: m, url: u, body });
        }
      }
    });

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
    await expect(page).toHaveURL(/\/collections\/[0-9a-f-]+/);
    const detailUrl = page.url();

    // ── 3. open Edit dialog (title is `Edit "<name>"`) and set license ───
    // The Collection Edit dialog is opened via the sidebar context menu
    // (`mdi-dots-horizontal`) → "Edit" item, not via a direct Edit button
    // on the landing (the visible "Edit" button on the landing belongs to
    // the inline description editor — UI-017).
    // The menu button is hover-revealed (showContextMenuButton flag); hover
    // the sidebar collection row first, then click.
    await page
      .locator(".sidebar-item, .sidebar-item-focused")
      .filter({ visible: true })
      .first()
      .hover();
    const collMenuBtn = page
      .locator("button:has(.mdi-dots-horizontal)")
      .filter({ visible: true })
      .first();
    await collMenuBtn.click({ force: true });
    await page
      .locator(".v-overlay__content .v-list-item:has-text('Edit')")
      .first()
      .click();
    const editDialog = page.locator(".v-overlay__content").filter({
      hasText: new RegExp(`Edit "${collName}"`, "i"),
    });
    await expect(editDialog).toBeVisible({ timeout: 5_000 });

    // The dialog is a stepper; the LIC1 fields land in the "Additional
    // Information" step (step 2). We click Next until license + accessRights
    // labels become visible (defensive; works whether they're step 2 or 3).
    const licenseField = editDialog.getByLabel(/license/i).first();
    for (let i = 0; i < 4 && !(await licenseField.isVisible().catch(() => false)); i++) {
      const nextBtn = editDialog.getByRole("button", { name: /^next$/i });
      if (await nextBtn.count()) await nextBtn.first().click();
      await page.waitForTimeout(200);
    }
    await expect(licenseField).toBeVisible({ timeout: 5_000 });

    // v-autocomplete commits on option-click, not on Enter alone. Type
    // char-by-char to trigger the filter (the dropdown opens as you type),
    // then click the matching .v-list-item-title. `.fill()` blasts the
    // value too quickly and the option list never updates.
    await licenseField.click();
    await licenseField.pressSequentially("MIT", { delay: 40 });
    await page
      .locator(".v-list-item-title")
      .filter({ hasText: /^MIT$/ })
      .first()
      .click();

    // Close any open autocomplete overlay from the license-pick by clicking
    // an empty area in the dialog before reaching for the next field.
    await page.keyboard.press("Escape");
    const accessField = editDialog.getByLabel(/access[- ]?rights/i).first();
    await accessField.click({ force: true });
    await page
      .locator(".v-list-item-title")
      .filter({ hasText: /^RESTRICTED$/i })
      .first()
      .click();

    // Save: dialog uses "Update" (common Vuetify edit-action label) or "Save".
    await editDialog
      .getByRole("button", { name: /save|update/i })
      .first()
      .click();
    await expect(editDialog).toBeHidden({ timeout: 10_000 });

    // Wire-shape assertion: the PUT body MUST carry license + accessRights.
    // Earlier discovery: the generated backend-client's CollectionToJSON
    // strip-allowlisted fields, dropping these silently (now patched in
    // backend-client/src/models/Collection.ts). This assertion pins that the
    // wire actually carries the values we set in the UI.
    const collectionPut = writeBodies.find(
      w => w.method === "PUT" && /\/collections\/\d+$/.test(w.url),
    );
    expect(collectionPut, "PUT /collections/{id} must have been observed").toBeDefined();
    expect(collectionPut?.body).toMatchObject({
      license: "MIT",
      accessRights: "RESTRICTED",
    });

    // ── 4. reload and assert chips are visible ───────────────────────────
    await page.goto(detailUrl);
    await page.waitForLoadState("networkidle");
    // FAIR strip: LicenseChip shows the SPDX id; AccessRightsChip shows the
    // enum value verbatim (uppercase).
    await expect(page.getByText("MIT", { exact: true }).first()).toBeVisible({
      timeout: 10_000,
    });
    await expect(
      page.getByText(/RESTRICTED/i).first(),
    ).toBeVisible({ timeout: 10_000 });
  });

  test("license persists on a DataObject inside the collection", async ({
    page,
  }) => {
    // Reuse the create-coll → create-DO → edit-DO flow.
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

    // Create a DataObject inside this collection (button label "New DataObject").
    const doName = `lic1-do-${Date.now()}`;
    await page
      .getByRole("button", { name: /new\s*data\s*object/i })
      .first()
      .click();
    const createDoDialog = page
      .locator(".v-overlay__content")
      .filter({ hasText: /Create Data Object/i });
    await expect(createDoDialog).toBeVisible({ timeout: 5_000 });
    await createDoDialog.locator("input").first().fill(doName);
    await createDoDialog
      .getByRole("button", { name: /next|create/i })
      .first()
      .click();
    const finalCreate = createDoDialog.getByRole("button", {
      name: "Create",
      exact: true,
    });
    if (await finalCreate.count()) {
      await finalCreate.click();
    }
    await expect(createDoDialog).toBeHidden({ timeout: 10_000 });

    // Navigate to the new DataObject. It appears in the panel below; click its name.
    await expect(page.getByText(doName).first()).toBeVisible({ timeout: 10_000 });
    await page.getByText(doName).first().click();
    await page.waitForURL(/\/dataobjects\/[0-9a-f-]+/, { timeout: 10_000 });
    const doUrl = page.url();

    // Edit the DO: the DataObject lives in the sidebar's tree (not the
    // .sidebar-item card). The CollectionSidebarItemContextMenu is wrapped
    // in DisplayChildrenOnHover — hover the selected treeitem row first,
    // then click the now-visible context-menu trigger.
    await page
      .getByRole("treeitem")
      .filter({ hasText: doName })
      .first()
      .hover();
    const doMenuBtns = page
      .locator("button:has(.mdi-dots-horizontal)")
      .filter({ visible: true });
    await doMenuBtns.last().click({ force: true });
    await page
      .locator(".v-overlay__content .v-list-item:has-text('Edit')")
      .first()
      .click();
    const editDoDialog = page.locator(".v-overlay__content").filter({
      hasText: new RegExp(`Edit "${doName}"`, "i"),
    });
    await expect(editDoDialog).toBeVisible({ timeout: 5_000 });

    // Step through to find the license field.
    const licField = editDoDialog.getByLabel(/license/i).first();
    for (let i = 0; i < 4 && !(await licField.isVisible().catch(() => false)); i++) {
      const nextBtn = editDoDialog.getByRole("button", { name: /^next$/i });
      if (await nextBtn.count()) await nextBtn.first().click();
      await page.waitForTimeout(200);
    }
    await expect(licField).toBeVisible({ timeout: 5_000 });

    await licField.click();
    await licField.pressSequentially("MIT", { delay: 40 });
    await page
      .locator(".v-list-item-title")
      .filter({ hasText: /^MIT$/ })
      .first()
      .click();
    await editDoDialog
      .getByRole("button", { name: /save|update/i })
      .first()
      .click();
    await expect(editDoDialog).toBeHidden({ timeout: 10_000 });

    // Reload and assert.
    await page.goto(doUrl);
    await page.waitForLoadState("networkidle");
    await expect(page.getByText("MIT", { exact: true }).first()).toBeVisible({
      timeout: 10_000,
    });
  });
});
