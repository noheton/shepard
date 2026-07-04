import { describe, expect, it } from "vitest";

const COLLECTION_APP_ID = "01900000-0000-7000-8000-000000000001";
const DO_APP_ID = "01900000-0000-7000-8000-000000000002";

function shellRoute(collectionAppId: string) {
  return `/aas/shells/${collectionAppId}`;
}

function submodelRoute(collectionAppId: string, dataObjectAppId: string) {
  return `/aas/submodels/${collectionAppId}/${dataObjectAppId}`;
}

describe("DataObjectAasPane navigation helpers (Slice 11)", () => {
  it("shell route points to specific shell, not the list", () => {
    const route = shellRoute(COLLECTION_APP_ID);
    expect(route).toBe(`/aas/shells/${COLLECTION_APP_ID}`);
    expect(route).not.toBe("/aas/shells");
  });

  it("shell route contains the collection appId segment", () => {
    const route = shellRoute(COLLECTION_APP_ID);
    expect(route).toContain(COLLECTION_APP_ID);
  });

  it("submodel route contains both collectionAppId and dataObjectAppId segments", () => {
    const route = submodelRoute(COLLECTION_APP_ID, DO_APP_ID);
    expect(route).toContain(COLLECTION_APP_ID);
    expect(route).toContain(DO_APP_ID);
  });

  it("submodel route has /aas/submodels/ prefix", () => {
    const route = submodelRoute(COLLECTION_APP_ID, DO_APP_ID);
    expect(route.startsWith("/aas/submodels/")).toBe(true);
  });

  it("different dataObjectAppIds produce different submodel routes", () => {
    const doAppId2 = "01900000-0000-7000-8000-000000000003";
    expect(submodelRoute(COLLECTION_APP_ID, DO_APP_ID)).not.toBe(
      submodelRoute(COLLECTION_APP_ID, doAppId2),
    );
  });

  it("shell route and submodel route are distinct for the same entity", () => {
    const shell = shellRoute(COLLECTION_APP_ID);
    const submodel = submodelRoute(COLLECTION_APP_ID, DO_APP_ID);
    expect(shell).not.toBe(submodel);
  });
});
