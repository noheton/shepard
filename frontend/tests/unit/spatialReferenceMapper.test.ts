import { describe, it, expect } from "vitest";
import { mapSpatialReferenceToDataTableElement } from "~/components/context/display-components/data-references/dataTableElementMappingUtil";
import { isSpatialEligibleName } from "~/composables/context/usePromoteToSpatial";
import type { SpatialReferenceV2IO } from "~/composables/context/useFetchSpatialReferencesV2";

const baseRef = (overrides: Partial<SpatialReferenceV2IO> = {}): SpatialReferenceV2IO => ({
  appId: "019e7244-0000-7000-8000-000000000aaa",
  name: "TPS 3D pointclouds.0",
  createdAt: "2026-06-10T10:00:00Z",
  createdBy: "alice",
  spatialDataContainerAppId: "019e7244-0000-7000-8000-000000000ccc",
  promotionState: "pending",
  ...overrides,
});

describe("mapSpatialReferenceToDataTableElement", () => {
  it("maps to a 'Spatial' row addressed by appId", () => {
    const row = mapSpatialReferenceToDataTableElement(baseRef());
    expect(row.type).toBe("Spatial");
    expect(row.meta.appId).toBe("019e7244-0000-7000-8000-000000000aaa");
    expect(row.meta.spatialContainerAppId).toBe("019e7244-0000-7000-8000-000000000ccc");
    expect(row.meta.promotionState).toBe("pending");
  });

  it("falls back to appId when name is null", () => {
    const row = mapSpatialReferenceToDataTableElement(baseRef({ name: null }));
    expect(row.name).toBe(row.meta.appId);
  });

  it("plumbs createdAt + createdBy through", () => {
    const row = mapSpatialReferenceToDataTableElement(baseRef());
    expect(row.created.createdBy).toBe("alice");
    expect(row.created.createdAt).toBeInstanceOf(Date);
    expect(row.created.createdAt.getUTCFullYear()).toBe(2026);
  });

  it("never exposes a numeric detail-page nav", () => {
    const row = mapSpatialReferenceToDataTableElement(baseRef());
    expect(row.actions.showDetails.enabled).toBe(false);
    expect(row.actions.elementAppId).toBe(row.meta.appId);
  });

  it("tolerates a missing container (promotion not yet materialised)", () => {
    const row = mapSpatialReferenceToDataTableElement(
      baseRef({ spatialDataContainerAppId: null, promotionState: null }),
    );
    expect(row.meta.spatialContainerAppId).toBeNull();
    expect(row.meta.promotionState).toBeNull();
  });
});

describe("isSpatialEligibleName", () => {
  it.each([
    ["scan.las", true],
    ["scan.LAZ", true],
    ["mesh.ply", true],
    ["cloud.e57", true],
    ["points.pcd", true],
    ["raw.xyz", true],
    ["raw.pts", true],
    ["Track_66/TPS 3D pointclouds.0", true],
    ["FSD course 3D pointclouds", true],
    ["afp-trajectory.dat", true],
    ["results.csv", false],
    ["report.pdf", false],
    ["", false],
    [null, false],
    [undefined, false],
  ])("classifies %p as eligible=%s", (name, expected) => {
    expect(isSpatialEligibleName(name as string | null | undefined)).toBe(expected);
  });
});
