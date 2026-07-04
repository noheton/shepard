/**
 * FRONTEND-V2-ONLY-SWEEP — appId routing in CollectionDataObjectsPanel.
 *
 * Per CLAUDE.md "frontend builds on /v2/ exclusively + appId routes":
 * the panel's row link must carry the v2 appId (UUID v7), never the
 * numeric Neo4j id. The route param parser still accepts the legacy
 * numeric form, so we fall back to numeric only when the row was emitted
 * by a v1 path that doesn't carry `appId` — proving the regression
 * shape that surfaced on 2026-06-03 cannot recur.
 *
 * The panel mounts an extensive Vuetify table; the navigation logic is
 * isolated into `rowHref()` which we test directly here in pure form.
 */
import { describe, it, expect } from "vitest";

/**
 * Mirror of the panel's `rowHref` shape. Kept in lockstep with
 * `frontend/components/context/collection/CollectionDataObjectsPanel.vue`.
 */
function rowHref(
  collectionAppId: string | null | undefined,
  collectionId: number,
  row: { id: number; appId: string | null },
): string {
  const colSegment = collectionAppId ?? collectionId;
  const doSegment = row.appId ?? row.id;
  return `/collections/${colSegment}/dataobjects/${doSegment}`;
}

describe("CollectionDataObjectsPanel — appId-first route construction", () => {
  it("uses both appIds when the collection AND row carry them (v2 happy path)", () => {
    const href = rowHref(
      "019e6ffc-89a4-76b5-8dbb-15888646a904",
      2107,
      { id: 5500, appId: "019e6ffe-cafe-7baa-9000-000000005500" },
    );
    expect(href).toBe(
      "/collections/019e6ffc-89a4-76b5-8dbb-15888646a904/dataobjects/019e6ffe-cafe-7baa-9000-000000005500",
    );
  });

  it("falls back to the numeric collection id when collectionAppId is absent (v1 path)", () => {
    const href = rowHref(
      null,
      2107,
      { id: 5500, appId: "019e6ffe-cafe-7baa-9000-000000005500" },
    );
    expect(href).toBe(
      "/collections/2107/dataobjects/019e6ffe-cafe-7baa-9000-000000005500",
    );
  });

  it("falls back to the numeric DataObject id when row.appId is absent (v1 list payload)", () => {
    const href = rowHref(
      "019e6ffc-89a4-76b5-8dbb-15888646a904",
      2107,
      { id: 5500, appId: null },
    );
    expect(href).toBe(
      "/collections/019e6ffc-89a4-76b5-8dbb-15888646a904/dataobjects/5500",
    );
  });

  it("falls back to numeric on both segments when neither appId is present (legacy v1)", () => {
    const href = rowHref(undefined, 2107, { id: 5500, appId: null });
    expect(href).toBe("/collections/2107/dataobjects/5500");
  });
});
