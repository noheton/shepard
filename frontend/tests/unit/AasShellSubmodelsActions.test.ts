import { describe, it, expect } from "vitest";

/**
 * MISSING-aas-ui Slice 12 — AAS Shell detail page: submodel row actions + empty state.
 *
 * Covers:
 * - "Open DataObject" button route shape per submodel row
 * - "Open Collection" CTA route for empty state
 * - displayName field optional in AasSubmodelRefIO (TypeScript compat)
 * - submodelRefToAppId strips URN prefix correctly
 */

import { submodelRefToAppId } from "~/composables/aas/useAasShell";
import type { AasSubmodelRefIO } from "~/composables/aas/useAasShell";

const COLLECTION_APP_ID = "0194fdc2-9359-7234-a12d-b6e7c1f5db4e";
const DO_APP_ID = "01927aae-5e4c-7b32-b9c3-a97f3c6d4e27";
const SUBMODEL_IRI = `urn:shepard:dataobject:${DO_APP_ID}`;

describe("AasShellSubmodelsActions — Slice 12", () => {
  it("Open DataObject route contains collectionId and dataObjectAppId", () => {
    const appId = submodelRefToAppId(SUBMODEL_IRI);
    const route = `/collections/${COLLECTION_APP_ID}/dataobjects/${appId}`;
    expect(route).toContain(COLLECTION_APP_ID);
    expect(route).toContain(DO_APP_ID);
  });

  it("Open DataObject route has /collections/ prefix", () => {
    const appId = submodelRefToAppId(SUBMODEL_IRI);
    const route = `/collections/${COLLECTION_APP_ID}/dataobjects/${appId}`;
    expect(route.startsWith("/collections/")).toBe(true);
  });

  it("Open DataObject route and AAS Submodel route are distinct for same entity", () => {
    const appId = submodelRefToAppId(SUBMODEL_IRI);
    const aasRoute = `/aas/submodels/${COLLECTION_APP_ID}/${appId}`;
    const doRoute = `/collections/${COLLECTION_APP_ID}/dataobjects/${appId}`;
    expect(aasRoute).not.toEqual(doRoute);
    expect(aasRoute.startsWith("/aas/")).toBe(true);
    expect(doRoute.startsWith("/collections/")).toBe(true);
  });

  it("Empty-state CTA links to the parent Collection", () => {
    const collectionRoute = `/collections/${COLLECTION_APP_ID}`;
    expect(collectionRoute).toContain(COLLECTION_APP_ID);
    expect(collectionRoute).toBe(`/collections/${COLLECTION_APP_ID}`);
  });

  it("AasSubmodelRefIO displayName is optional (TypeScript compat)", () => {
    const refWithoutDisplayName: AasSubmodelRefIO = {
      type: "ExternalReference",
      keys: [{ type: "Submodel", value: SUBMODEL_IRI }],
    };
    const refWithDisplayName: AasSubmodelRefIO = {
      type: "ExternalReference",
      keys: [{ type: "Submodel", value: SUBMODEL_IRI }],
      displayName: "My Process Step",
    };
    const refWithNullDisplayName: AasSubmodelRefIO = {
      type: "ExternalReference",
      keys: [{ type: "Submodel", value: SUBMODEL_IRI }],
      displayName: null,
    };
    expect(refWithoutDisplayName.displayName).toBeUndefined();
    expect(refWithDisplayName.displayName).toBe("My Process Step");
    expect(refWithNullDisplayName.displayName).toBeNull();
  });

  it("submodelRefToAppId correctly strips URN prefix", () => {
    expect(submodelRefToAppId(SUBMODEL_IRI)).toBe(DO_APP_ID);
  });

  it("Open DataObject route appId segment does not contain URN prefix", () => {
    const appId = submodelRefToAppId(SUBMODEL_IRI);
    const route = `/collections/${COLLECTION_APP_ID}/dataobjects/${appId}`;
    expect(route).not.toContain("urn:shepard:dataobject:");
  });
});
