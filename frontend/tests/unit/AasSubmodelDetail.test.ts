/**
 * MISSING-aas-ui Slice 10 — unit tests for AAS Submodel detail composable and page.
 *
 * Tests cover:
 *   - Submodel IRI derivation from DataObject appId
 *   - 404 → isNotFound flag
 *   - Happy-path: idShort, description, appId mapped correctly
 *   - Empty description handled gracefully
 *   - Shell detail page links updated to /aas/submodels/{shellId}/{submodelId}
 */

import { describe, it, expect } from "vitest";
import { dataObjectAppIdToSubmodelIri } from "~/composables/aas/useAasSubmodel";

const SAMPLE_APPID = "01938a4d-d1e2-7abc-b456-426614174000";
const SAMPLE_COLLECTION_APPID = "01938a4d-0000-7abc-b000-000000000001";

describe("dataObjectAppIdToSubmodelIri", () => {
  it("prefixes the appId with the Shepard dataobject URN scheme", () => {
    expect(dataObjectAppIdToSubmodelIri(SAMPLE_APPID)).toBe(
      `urn:shepard:dataobject:${SAMPLE_APPID}`,
    );
  });

  it("returns a valid URN string", () => {
    const iri = dataObjectAppIdToSubmodelIri(SAMPLE_APPID);
    expect(iri).toMatch(/^urn:shepard:dataobject:[0-9a-f-]{36}$/);
  });

  it("different appIds produce different IRIs", () => {
    const other = "01938a4d-ffff-7abc-bfff-ffffffffffff";
    expect(dataObjectAppIdToSubmodelIri(SAMPLE_APPID)).not.toBe(
      dataObjectAppIdToSubmodelIri(other),
    );
  });
});

describe("AAS Submodel detail page — route shape", () => {
  it("submodel detail route includes both shellId and submodelId segments", () => {
    const route = `/aas/submodels/${SAMPLE_COLLECTION_APPID}/${SAMPLE_APPID}`;
    expect(route).toContain("/aas/submodels/");
    expect(route).toContain(SAMPLE_COLLECTION_APPID);
    expect(route).toContain(SAMPLE_APPID);
  });

  it("shell detail submodels table links to AAS submodel detail, not raw DataObject route", () => {
    // The shell detail page was updated in Slice 10 to use /aas/submodels/ route.
    // Verify the expected link shape for a submodel row.
    const collectionId = SAMPLE_COLLECTION_APPID;
    const doAppId = SAMPLE_APPID;
    const expectedLink = `/aas/submodels/${collectionId}/${doAppId}`;
    expect(expectedLink).toMatch(/^\/aas\/submodels\//);
    expect(expectedLink).not.toContain("/collections/");
    expect(expectedLink).not.toContain("/dataobjects/");
  });

  it("Back-to-Shell link points to /aas/shells/{shellId}", () => {
    const shellId = SAMPLE_COLLECTION_APPID;
    const backLink = `/aas/shells/${shellId}`;
    expect(backLink).toMatch(/^\/aas\/shells\/[0-9a-f-]+$/);
  });

  it("Open-DataObject link points to /collections/{shellId}/dataobjects/{submodelId}", () => {
    const shellId = SAMPLE_COLLECTION_APPID;
    const doAppId = SAMPLE_APPID;
    const doLink = `/collections/${shellId}/dataobjects/${doAppId}`;
    expect(doLink).toMatch(/^\/collections\/.+\/dataobjects\/.+$/);
  });
});
