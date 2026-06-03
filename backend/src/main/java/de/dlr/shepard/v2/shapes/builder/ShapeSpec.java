package de.dlr.shepard.v2.shapes.builder;

import java.util.List;

/**
 * Typed input for one {@code sh:NodeShape} — the top-level authoring form a
 * {@code ShepardTemplate.body} carries (V2CONV-B1, see
 * {@code aidocs/platform/191-v2-surface-convergence.md §3}).
 *
 * <p>This is the deterministic compiler's <em>input</em>; the output is
 * canonical SHACL Turtle produced by {@link ShaclShapeBuilder}. It is the
 * inverse of {@code JenaShaclValidator}: that class consumes a shape graph,
 * this one produces one.
 *
 * @param shapeIri    the IRI of the {@code sh:NodeShape}; when {@code null} the
 *                    builder mints a deterministic local IRI so output stays
 *                    byte-stable and blank-node-free
 * @param targetClass {@code sh:targetClass} IRI; nullable (a shape may target by
 *                    other means or be referenced via {@code sh:node})
 * @param closed      {@code sh:closed} — when {@code true}, emits
 *                    {@code sh:closed true}; {@code false} omits it (the SHACL
 *                    default is open, so an omitted triple is the open shape)
 * @param properties  the property constraints; nullable / empty = a shape with
 *                    no property constraints (still a valid {@code sh:NodeShape})
 */
public record ShapeSpec(
  String shapeIri,
  String targetClass,
  boolean closed,
  List<PropertyShapeSpec> properties
) {}
