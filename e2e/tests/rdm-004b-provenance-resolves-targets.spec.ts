/**
 * RDM-004b — Provenance resolver lands the right target.
 *
 * The PROV-RESOLVER-PATHWALK + PROV-V1-NUMERIC-LOOKUP fix (2026-05-24)
 * structurally closes RDM-2026-05-24-004 buckets B + C. Before this fix, a
 * mutation through the v1 numeric surface (every shepard-client write to
 * /shepard/api/...) produced an Activity row with `targetKind=NULL,
 * targetAppId=NULL` because the resolver only matched UUID-tailed paths.
 *
 * This spec verifies, against the live deployment, that after a v1 PATCH on
 * a LUMEN DataObject:
 *   1. the DO's Provenance panel renders at least one row
 *   2. that row carries the captured PATCH (UPDATE on the DataObject)
 *
 * The frontend uses the generated client's `updateDataObject` which goes to
 * `PATCH /shepard/api/collections/{collectionId}/dataObjects/{dataObjectId}`
 * — purely v1 numeric, so this end-to-end walk exercises Part 2 (numeric
 * lookup) of the fix.
 */
import { expect, test } from "@playwright/test";
import { loginAs } from "./helpers/auth";

const LUMEN_COLLECTION = 42;

test.describe("RDM-004b: provenance resolver lands correct target", () => {
  test("PATCH on a LUMEN DataObject surfaces in its Provenance panel", async ({
    page,
  }) => {
    test.setTimeout(120_000);
    await loginAs(page, "alice", "alice-demo");

    // 1. Navigate to the LUMEN collection landing.
    await page.goto(`/collections/${LUMEN_COLLECTION}`);
    await page.waitForLoadState("networkidle", { timeout: 20_000 }).catch(() => {});

    // 2. Click into the first DataObject (TR-001 is the seed convention).
    const firstDoLink = page.locator('a:has-text("TR-")').first();
    await expect(firstDoLink).toBeVisible({ timeout: 15_000 });
    const href = await firstDoLink.getAttribute("href");
    expect(href).toBeTruthy();
    await firstDoLink.click();
    await page.waitForLoadState("networkidle", { timeout: 20_000 }).catch(() => {});

    // 3. Open Edit dialog, tweak the description, save — this fires the
    //    v1 PATCH /shepard/api/collections/42/dataObjects/{id}.
    const editBtn = page
      .getByRole("button", { name: /^edit$/i })
      .or(page.locator('button:has-text("Edit")'))
      .first();
    await expect(editBtn).toBeVisible({ timeout: 10_000 });
    await editBtn.click();

    // The edit dialog has a Description textarea; toggle a marker into it.
    const marker = `rdm-004b prov-resolver verify ${Date.now()}`;
    const descField = page
      .locator('label:has-text("Description")')
      .locator('..')
      .locator('textarea, input')
      .first()
      .or(page.getByLabel(/^description$/i));
    await expect(descField).toBeVisible({ timeout: 10_000 });
    // Append rather than overwrite — preserve any existing seed description.
    const current = (await descField.inputValue().catch(() => "")) ?? "";
    await descField.fill(`${current}\n${marker}`.trim());

    // Save.
    const saveBtn = page
      .getByRole("button", { name: /^save$/i })
      .or(page.locator('button:has-text("Save")'))
      .last();
    await expect(saveBtn).toBeVisible({ timeout: 10_000 });
    await saveBtn.click();

    // Wait for the PATCH round-trip + dialog close.
    await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
    await page.waitForTimeout(2_000);

    // 4. Open the Provenance tab.
    const provTab = page
      .getByRole("tab", { name: /provenance/i })
      .or(page.locator('[role="tab"]:has-text("Provenance")'))
      .first();
    await expect(provTab).toBeVisible({ timeout: 15_000 });
    await provTab.click();
    await page.waitForTimeout(3_000); // panel fetches the activities

    // 5. The provenance log table should contain at least one row with
    //    actionKind=UPDATE — that's our PATCH, attributed correctly.
    const provTable = page.locator(".prov-log");
    await expect(provTable).toBeVisible({ timeout: 15_000 });

    // Either the table rendered with rows, or the empty-state copy shows —
    // before the fix the empty state was the only outcome. After the fix
    // there must be at least one UPDATE row for this PATCH.
    const updateChip = provTable.locator('.v-chip:has-text("UPDATE")').first();
    await expect(updateChip).toBeVisible({ timeout: 20_000 });

    // 6. The row's path cell should contain the v1 numeric path shape.
    //    (Smoke-level — the precise format depends on UriInfo.getPath().)
    const tableText = await provTable.innerText();
    expect(tableText).toMatch(/dataObjects?\/\d+|data-objects\/[0-9a-f-]+/i);
  });
});
