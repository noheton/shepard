package de.dlr.shepard.v2.shapes.builder;

import java.util.List;

/**
 * Typed input for one {@code sh:PropertyShape} — the thin, vocabulary-IRI
 * authoring form a {@code ShepardTemplate.body} carries (V2CONV-B1, see
 * {@code aidocs/platform/191-v2-surface-convergence.md §3}).
 *
 * <p>Every field maps 1:1 onto a SHACL property constraint. {@code path} is
 * the only mandatory field — it is the vocabulary predicate IRI the constraint
 * targets ({@code sh:path}). All other fields are optional ({@code null} /
 * empty = "not constrained").
 *
 * @param path     the predicate IRI ({@code sh:path}); MUST be a non-blank
 *                 absolute IRI string
 * @param datatype the literal datatype IRI ({@code sh:datatype}), e.g.
 *                 {@code http://www.w3.org/2001/XMLSchema#string}; nullable
 * @param minCount {@code sh:minCount}; nullable
 * @param maxCount {@code sh:maxCount}; nullable
 * @param in       {@code sh:in} membership list — each entry is either a
 *                 literal (encoded as {@code xsd:string} unless {@code datatype}
 *                 is set, in which case the value is typed with it) or an IRI
 *                 (entries that parse as an absolute IRI are emitted as IRIs);
 *                 nullable / empty = no {@code sh:in}
 * @param node     a nested {@code sh:NodeShape} IRI ({@code sh:node}); nullable
 */
public record PropertyShapeSpec(
  String path,
  String datatype,
  Integer minCount,
  Integer maxCount,
  List<String> in,
  String node
) {
  /** Convenience factory for the common path-only constraint. */
  public static PropertyShapeSpec of(String path) {
    return new PropertyShapeSpec(path, null, null, null, null, null);
  }
}
