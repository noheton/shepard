/**
 * V2CONV-B6 — unit tests for the visual template editor's pure helpers.
 *
 * Covers:
 *   1. editorStateToBuildRequest — drops blank fields/rows, encodes sh:in.
 *   2. cardinality mapping (min/max).
 *   3. propertyRowFromPalette — palette → row defaults.
 *   4. buildTemplateBody / editorStateFromTemplateBody — round-trip + prior-key
 *      preservation + non-editor body returns null.
 *   5. palette filter + merge + cardinality hint (useShapePalette pure fns).
 */
import { describe, it, expect } from "vitest";

import {
  editorStateToBuildRequest,
  cardinalityToMin,
  cardinalityToMax,
  propertyRowFromPalette,
  buildTemplateBody,
  editorStateFromTemplateBody,
  emptyShapeEditorState,
  XSD,
  type ShapeEditorState,
} from "../../utils/templateShapeDsl";
import {
  filterPalette,
  mergePaletteSources,
  vocabCardinalityHint,
  type PalettePredicate,
} from "../../composables/semantic/useShapePalette";

const SHEPARD = "http://semantics.dlr.de/shepard#";

describe("editorStateToBuildRequest", () => {
  it("drops blank optional fields and rows without a path", () => {
    const state: ShapeEditorState = {
      shapeIri: "  ",
      targetClass: SHEPARD + "DataObject",
      closed: false,
      properties: [
        { path: "  ", datatype: XSD + "string", minCount: 1, maxCount: 1 },
        { path: SHEPARD + "name", datatype: "", minCount: 1, maxCount: null, in: [], node: "  " },
      ],
    };

    const dsl = editorStateToBuildRequest(state);

    expect(dsl.shapeIri).toBeNull();
    expect(dsl.targetClass).toBe(SHEPARD + "DataObject");
    expect(dsl.properties).toHaveLength(1);
    expect(dsl.properties[0].path).toBe(SHEPARD + "name");
    expect(dsl.properties[0].datatype).toBeNull();
    expect(dsl.properties[0].node).toBeNull();
    expect(dsl.properties[0].in).toBeNull();
    expect(dsl.properties[0].minCount).toBe(1);
  });

  it("encodes sh:in members, dropping the datatype for IRI kind", () => {
    const state: ShapeEditorState = {
      shapeIri: SHEPARD + "S",
      targetClass: "",
      closed: true,
      properties: [
        {
          path: SHEPARD + "status",
          datatype: XSD + "string",
          minCount: 1,
          maxCount: 1,
          in: [
            { value: "DRAFT", kind: "LITERAL", datatype: XSD + "string" },
            { value: SHEPARD + "Term", kind: "IRI", datatype: XSD + "string" },
            { value: "  ", kind: "LITERAL" },
          ],
        },
      ],
    };

    const dsl = editorStateToBuildRequest(state);

    expect(dsl.closed).toBe(true);
    const members = dsl.properties[0].in!;
    expect(members).toHaveLength(2); // blank dropped
    expect(members[0]).toEqual({ value: "DRAFT", kind: "LITERAL", datatype: XSD + "string" });
    expect(members[1]).toEqual({ value: SHEPARD + "Term", kind: "IRI", datatype: null });
  });
});

describe("cardinality mapping", () => {
  it("maps min counts", () => {
    expect(cardinalityToMin("1")).toBe(1);
    expect(cardinalityToMin("required")).toBe(1);
    expect(cardinalityToMin("1..*")).toBe(1);
    expect(cardinalityToMin("0..*")).toBe(0);
    expect(cardinalityToMin("optional")).toBe(0);
    expect(cardinalityToMin(null)).toBeNull();
    expect(cardinalityToMin("2..5")).toBe(2);
  });

  it("maps max counts", () => {
    expect(cardinalityToMax("1")).toBe(1);
    expect(cardinalityToMax("0..1")).toBe(1);
    expect(cardinalityToMax("0..*")).toBeNull();
    expect(cardinalityToMax("1..n")).toBeNull();
    expect(cardinalityToMax("2..5")).toBe(5);
    expect(cardinalityToMax(null)).toBeNull();
  });
});

describe("propertyRowFromPalette", () => {
  it("pre-fills path, label, datatype and cardinality-derived counts", () => {
    const row = propertyRowFromPalette({
      uri: SHEPARD + "mass",
      label: "Mass",
      datatype: XSD + "decimal",
      cardinality: "1",
    });
    expect(row.path).toBe(SHEPARD + "mass");
    expect(row.label).toBe("Mass");
    expect(row.datatype).toBe(XSD + "decimal");
    expect(row.minCount).toBe(1);
    expect(row.maxCount).toBe(1);
  });
});

describe("template body round-trip", () => {
  it("buildTemplateBody stores editorState + shapeGraph and preserves prior keys", () => {
    const state = emptyShapeEditorState();
    state.targetClass = SHEPARD + "DataObject";
    const prior = JSON.stringify({ legacyKey: "keep-me", shapeGraph: "old" });

    const body = buildTemplateBody(state, "@prefix sh: <x> .", prior);
    const parsed = JSON.parse(body);

    expect(parsed.legacyKey).toBe("keep-me");
    expect(parsed.shapeGraph).toBe("@prefix sh: <x> .");
    expect(parsed.editorState.targetClass).toBe(SHEPARD + "DataObject");
  });

  it("editorStateFromTemplateBody reopens its own body", () => {
    const state = emptyShapeEditorState();
    state.shapeIri = SHEPARD + "S";
    state.properties.push({ path: SHEPARD + "name", minCount: 1 });
    const body = buildTemplateBody(state, "ttl", null);

    const reopened = editorStateFromTemplateBody(body);
    expect(reopened).not.toBeNull();
    expect(reopened!.shapeIri).toBe(SHEPARD + "S");
    expect(reopened!.properties).toHaveLength(1);
  });

  it("returns null for non-editor bodies and garbage", () => {
    expect(editorStateFromTemplateBody(null)).toBeNull();
    expect(editorStateFromTemplateBody("")).toBeNull();
    expect(editorStateFromTemplateBody("{}")).toBeNull();
    expect(editorStateFromTemplateBody("not json")).toBeNull();
    expect(editorStateFromTemplateBody(JSON.stringify({ shapeGraph: "ttl" }))).toBeNull();
  });

  it("normalises a malformed editorState (missing arrays/flags)", () => {
    const body = JSON.stringify({ editorState: { shapeIri: SHEPARD + "S" } });
    const reopened = editorStateFromTemplateBody(body);
    expect(reopened).not.toBeNull();
    expect(reopened!.closed).toBe(false);
    expect(reopened!.properties).toEqual([]);
  });
});

describe("palette pure helpers", () => {
  const items: PalettePredicate[] = [
    { uri: SHEPARD + "status", label: "Status", description: "process status", cardinality: "0..1", datatype: null, source: "vocabulary" },
    { uri: SHEPARD + "mass", label: "Mass", description: null, cardinality: "0..1", datatype: null, source: "vocabulary" },
  ];

  it("filterPalette is case-insensitive over uri/label/description", () => {
    expect(filterPalette(items, "STATUS")).toHaveLength(1);
    expect(filterPalette(items, "process")).toHaveLength(1);
    expect(filterPalette(items, "shepard#mass")).toHaveLength(1);
    expect(filterPalette(items, "")).toHaveLength(2);
    expect(filterPalette(items, "nope")).toHaveLength(0);
  });

  it("mergePaletteSources de-dupes by uri, curated wins", () => {
    const search: PalettePredicate[] = [
      { uri: SHEPARD + "status", label: "dup", description: null, cardinality: null, datatype: null, source: "search" },
      { uri: SHEPARD + "owner", label: "Owner", description: null, cardinality: null, datatype: null, source: "search" },
    ];
    const merged = mergePaletteSources(items, search);
    expect(merged).toHaveLength(3);
    expect(merged.find((m) => m.uri === SHEPARD + "status")!.source).toBe("vocabulary");
    expect(merged.find((m) => m.uri === SHEPARD + "owner")!.source).toBe("search");
  });

  it("vocabCardinalityHint maps one/many", () => {
    expect(vocabCardinalityHint("one")).toBe("0..1");
    expect(vocabCardinalityHint("many")).toBe("0..*");
    expect(vocabCardinalityHint(null)).toBeNull();
    expect(vocabCardinalityHint("weird")).toBeNull();
  });
});
