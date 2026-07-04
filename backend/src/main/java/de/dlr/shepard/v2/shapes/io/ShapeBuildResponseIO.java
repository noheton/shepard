package de.dlr.shepard.v2.shapes.io;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * V2CONV-B6 — response body for {@code POST /v2/shapes/build}.
 *
 * <p>Carries the canonical SHACL Turtle the
 * {@link de.dlr.shepard.v2.shapes.builder.ShaclShapeBuilder} compiled from the
 * editor's JSON DSL, plus the (possibly builder-minted) shape IRI so the editor
 * can echo it back. The {@code error} field is set (and {@code shapeGraph}
 * left null) when the DSL was structurally invalid — a blank predicate path,
 * say. The endpoint returns a {@code 400} in that case; the field gives the
 * editor a human-readable reason to surface inline.
 */
@Schema(description = "Compiled SHACL shape graph (Turtle) for a JSON DSL.")
public record ShapeBuildResponseIO(
  @Schema(description = "The IRI of the compiled sh:NodeShape (builder-minted when the request omitted one).", nullable = true)
  String shapeIri,
  @Schema(description = "Canonical SHACL Turtle. Null when the DSL was invalid (see error).", nullable = true)
  String shapeGraph,
  @Schema(description = "Non-null when the DSL could not be compiled (e.g. a blank predicate path).", nullable = true)
  String error
) {
  public static ShapeBuildResponseIO ok(String shapeIri, String shapeGraph) {
    return new ShapeBuildResponseIO(shapeIri, shapeGraph, null);
  }

  public static ShapeBuildResponseIO invalid(String error) {
    return new ShapeBuildResponseIO(null, null, error);
  }
}
