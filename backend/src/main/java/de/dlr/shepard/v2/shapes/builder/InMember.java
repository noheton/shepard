package de.dlr.shepard.v2.shapes.builder;

/**
 * V2CONV-B1 — one explicit {@code sh:in} member, with its RDF kind <em>stated</em>
 * rather than guessed. Replaces the earlier IRI-vs-literal heuristic so a
 * controlled-term IRI and a string value that happens to contain a colon are never
 * confused (operator decision 2026-06-03).
 *
 * @param value    the lexical value — the literal lexical form, or the IRI string
 * @param kind     {@link Kind#IRI} or {@link Kind#LITERAL}; {@code null} defaults to
 *                 {@link Kind#LITERAL}
 * @param datatype literal datatype IRI (LITERAL only), e.g.
 *                 {@code http://www.w3.org/2001/XMLSchema#string}; {@code null} →
 *                 {@code xsd:string}. Ignored for {@link Kind#IRI}.
 */
public record InMember(String value, Kind kind, String datatype) {
  /** Whether an {@code sh:in} member is an IRI or a (typed) literal. */
  public enum Kind {
    IRI,
    LITERAL,
  }

  public InMember {
    if (kind == null) {
      kind = Kind.LITERAL;
    }
  }

  /** A controlled-term IRI member, e.g. a SKOS concept. */
  public static InMember iri(String iri) {
    return new InMember(iri, Kind.IRI, null);
  }

  /** An {@code xsd:string} literal member (e.g. a status enum value). */
  public static InMember literal(String value) {
    return new InMember(value, Kind.LITERAL, null);
  }

  /** A literal member with an explicit datatype IRI. */
  public static InMember literal(String value, String datatype) {
    return new InMember(value, Kind.LITERAL, datatype);
  }
}
