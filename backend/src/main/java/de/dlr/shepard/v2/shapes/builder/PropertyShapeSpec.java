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
 * @param in       {@code sh:in} membership list — each entry is an explicit
 *                 {@link InMember} stating its own {@link InMember.Kind} (IRI or
 *                 LITERAL) and, for literals, its datatype; nullable / empty = no
 *                 {@code sh:in}. (Explicit per-member typing — operator decision
 *                 2026-06-03 — replaces the earlier IRI-vs-literal heuristic.)
 * @param node     a nested {@code sh:NodeShape} IRI ({@code sh:node}); nullable
 * @param pattern  a regular expression the literal value must match
 *                 ({@code sh:pattern}); nullable (BTKVS-B2 — validating
 *                 constraint, unlike the {@code hints} bag)
 * @param hints    non-validating form-presentation hints (DASH editor, label,
 *                 order, group, placeholder, cell mapping — doc 125 §4);
 *                 nullable / empty = no hints emitted
 */
public record PropertyShapeSpec(
  String path,
  String datatype,
  Integer minCount,
  Integer maxCount,
  List<InMember> in,
  String node,
  String pattern,
  FormHintSpec hints
) {
  /**
   * Pre-BTKVS-B2 compatibility constructor (no {@code sh:pattern}, no form
   * hints) — keeps the V2CONV-B1 call sites source-compatible.
   */
  public PropertyShapeSpec(
    String path,
    String datatype,
    Integer minCount,
    Integer maxCount,
    List<InMember> in,
    String node
  ) {
    this(path, datatype, minCount, maxCount, in, node, null, null);
  }

  /** Convenience factory for the common path-only constraint. */
  public static PropertyShapeSpec of(String path) {
    return new PropertyShapeSpec(path, null, null, null, null, null, null, null);
  }
}
