/**
 * V2-SWEEP-004-3 — unit tests for URIReferenceV2 type guard and mapper.
 */
import { describe, it, expect } from "vitest";
import { isURIReferenceV2, type URIReferenceV2 } from "~/components/context/display-components/relationships/relatedEntity";
import { mapRelatedEntityToRelationshipTableElement } from "~/components/context/display-components/relationships/relationshipTableElementMappingUtil";

const makeUriV2 = (overrides: Partial<URIReferenceV2> = {}): URIReferenceV2 => ({
  id: 42,
  appId: "01926e8b-0000-7000-8000-000000000001",
  kind: "uri",
  name: "MFFD spec link",
  createdAt: new Date("2026-06-12T00:00:00Z"),
  createdBy: "fkrebs",
  payload: { uri: "https://example.com/spec", relationship: "seeAlso" },
  ...overrides,
});

describe("isURIReferenceV2", () => {
  it("returns true for a URIReferenceV2 object", () => {
    expect(isURIReferenceV2(makeUriV2())).toBe(true);
  });

  it("returns false for null / non-object", () => {
    expect(isURIReferenceV2(null)).toBe(false);
    expect(isURIReferenceV2("string")).toBe(false);
  });

  it("returns false for a different kind", () => {
    expect(isURIReferenceV2({ kind: "collection" })).toBe(false);
    expect(isURIReferenceV2({ kind: "file" })).toBe(false);
  });

  it("returns false when kind field is missing", () => {
    expect(isURIReferenceV2({ id: 1, name: "x" })).toBe(false);
  });
});

describe("mapper handles URIReferenceV2", () => {
  it("maps name and path from payload.uri", () => {
    const el = mapRelatedEntityToRelationshipTableElement(makeUriV2());
    expect(el.name).toEqual({ value: "MFFD spec link", path: "https://example.com/spec" });
  });

  it("maps information.type to Link", () => {
    const el = mapRelatedEntityToRelationshipTableElement(makeUriV2());
    expect(el.information.type).toEqual({ type: "Link" });
  });

  it("maps relationship from payload.relationship", () => {
    const el = mapRelatedEntityToRelationshipTableElement(makeUriV2());
    expect(el.relationship).toBe("seeAlso");
  });

  it("exposes uriRefAppId in actions", () => {
    const el = mapRelatedEntityToRelationshipTableElement(makeUriV2());
    expect(el.actions.uriRefAppId).toBe("01926e8b-0000-7000-8000-000000000001");
  });

  it("exposes uriRefEditData with name, uri, relationship", () => {
    const el = mapRelatedEntityToRelationshipTableElement(makeUriV2());
    expect(el.actions.uriRefEditData).toEqual({
      name: "MFFD spec link",
      uri: "https://example.com/spec",
      relationship: "seeAlso",
    });
  });

  it("marks as annotatable", () => {
    const el = mapRelatedEntityToRelationshipTableElement(makeUriV2());
    expect(el.information.annotatable).toBe(true);
  });

  it("handles null relationship", () => {
    const el = mapRelatedEntityToRelationshipTableElement(
      makeUriV2({ payload: { uri: "https://example.com", relationship: null } }),
    );
    expect(el.relationship).toBeUndefined();
    expect(el.actions.uriRefEditData?.relationship).toBeUndefined();
  });
});
