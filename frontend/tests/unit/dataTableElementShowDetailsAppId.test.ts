import { describe, it, expect, vi } from "vitest";
import { mapDataReferenceToDataTableElement } from "~/components/context/display-components/data-references/dataTableElementMappingUtil";

// Nuxt auto-imports used inside the mapper's container-meta path; stub them so
// the util can be exercised in isolation. Set on globalThis before the it block
// runs (the module reads them at call time, not import time).
vi.stubGlobal("toShortDateTimeString", (d: Date) => d.toISOString());
vi.stubGlobal("parseDateFromNanos", (n: number) => new Date(n / 1e6));
vi.stubGlobal("timeseriesReferencePathFragment", "/timeseriesereferences/");
vi.stubGlobal("fileReferencesPathFragment", "/filereferences/");
vi.stubGlobal("structuredDataReferencesPathFragment", "/structureddatareferences/");
vi.stubGlobal("videoStreamReferencesPathFragment", "/videostreamreferences/");

// Regression: a v2-fetched TimeseriesReference carries appId but no numeric id.
// The "show" button on the DataObject data-references table is guarded by
// showDetailsId(item) != null, which falls back to elementAppId. If the mapper
// omits elementAppId the button vanishes ("timeseries cannot be viewed — button
// missing"). Assert the appId is carried through as elementAppId.
describe("mapDataReferenceToDataTableElement — show-details id for v2 refs", () => {
  it("carries appId as elementAppId when the ref has no numeric id (timeseries)", () => {
    const ref = {
      __refKind: "timeseries",
      id: undefined,
      appId: "019ee497-ce7f-746f-b7da-6b2362b88e3a",
      name: "TPS timeseries — Track_1",
      createdAt: new Date(0),
      createdBy: "Demo Admin",
      start: 0,
      end: 0,
      timeseries: [],
      referencedContainerAvailability: "available",
      referencedContainerName: "mffd-afp-tapelaying-timeseries",
    } as unknown as Parameters<typeof mapDataReferenceToDataTableElement>[0];
    const el = mapDataReferenceToDataTableElement(ref);
    expect(el.actions.showDetails.enabled).toBe(true);
    expect(el.actions.elementId).toBeUndefined();
    expect(el.actions.elementAppId).toBe("019ee497-ce7f-746f-b7da-6b2362b88e3a");
  });
});
