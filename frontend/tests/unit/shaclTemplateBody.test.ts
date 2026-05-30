/**
 * SHAPES-V-PREFILL-1 — unit tests for the optional SHACL shape-graph
 * extractor on ShepardTemplateIO.body.
 */
import { describe, it, expect } from "vitest";

import { extractShapeGraphFromTemplateBody } from "../../utils/shaclTemplateBody";

describe("extractShapeGraphFromTemplateBody", () => {
  it("returns null for null body", () => {
    expect(extractShapeGraphFromTemplateBody(null)).toBeNull();
  });

  it("returns null for undefined body", () => {
    expect(extractShapeGraphFromTemplateBody(undefined)).toBeNull();
  });

  it("returns null for empty string body", () => {
    expect(extractShapeGraphFromTemplateBody("")).toBeNull();
  });

  it("returns null when the body isn't valid JSON", () => {
    expect(extractShapeGraphFromTemplateBody("not json {")).toBeNull();
  });

  it("returns null when the JSON has no shapeGraph property", () => {
    expect(extractShapeGraphFromTemplateBody(JSON.stringify({ name: "x", body: "y" }))).toBeNull();
  });

  it("returns null when shapeGraph is present but not a string", () => {
    expect(extractShapeGraphFromTemplateBody(JSON.stringify({ shapeGraph: 42 }))).toBeNull();
    expect(extractShapeGraphFromTemplateBody(JSON.stringify({ shapeGraph: null }))).toBeNull();
    expect(extractShapeGraphFromTemplateBody(JSON.stringify({ shapeGraph: {} }))).toBeNull();
  });

  it("returns null when shapeGraph is an empty string", () => {
    expect(extractShapeGraphFromTemplateBody(JSON.stringify({ shapeGraph: "" }))).toBeNull();
  });

  it("returns the Turtle string when shapeGraph carries it", () => {
    const turtle = "@prefix sh: <http://www.w3.org/ns/shacl#> .\nex:S a sh:NodeShape .";
    const body = JSON.stringify({ shapeGraph: turtle, otherField: "ignored" });
    expect(extractShapeGraphFromTemplateBody(body)).toBe(turtle);
  });
});
