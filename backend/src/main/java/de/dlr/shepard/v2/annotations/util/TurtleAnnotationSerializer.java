package de.dlr.shepard.v2.annotations.util;

import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import java.util.List;

/**
 * SHAPES-V-PREFILL-2-RDF-ENDPOINT — shared Turtle serializer for
 * {@link SemanticAnnotation} nodes.
 *
 * <p>Extracted from the private static helper in
 * {@link de.dlr.shepard.v2.annotations.resources.SemanticAnnotationV2Rest}
 * so that both the single-annotation export endpoint and the new
 * DataObject-scoped aggregation endpoint ({@code GET /v2/data-objects/{appId}/rdf})
 * can produce Turtle without duplicating the serialization logic.
 *
 * <p>The Turtle shape follows §3.3 of
 * {@code aidocs/semantics/100-consistent-semantic-annotation-design.md}:
 * one flat triple plus an OA-framed annotation block per annotation.
 * Prefix declarations are emitted once at the document head; annotation
 * blocks follow.
 */
public final class TurtleAnnotationSerializer {

  /** Standard prefix block emitted at the top of every Turtle document. */
  static final String PREFIXES =
    "@prefix oa: <http://www.w3.org/ns/oa#> .\n" +
    "@prefix prov: <http://www.w3.org/ns/prov#> .\n" +
    "@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .\n" +
    "@prefix sh: <http://www.w3.org/ns/shacl#> .\n" +
    "@prefix shepard: <https://shepard.dlr.de/v2/> .\n";

  private TurtleAnnotationSerializer() {
    // utility class — not instantiable
  }

  /**
   * Produce a Turtle document for a single annotation.
   *
   * <p>Includes the prefix block, one flat triple, and the OA-framed annotation.
   *
   * @param a the annotation to serialize; must not be {@code null}
   * @return a well-formed Turtle string (Content-Type {@code text/turtle})
   */
  public static String toTurtle(SemanticAnnotation a) {
    return PREFIXES + "\n" + annotationBlock(a);
  }

  /**
   * Produce a Turtle document aggregating all annotations for a single subject entity.
   *
   * <p>The prefix block is emitted once; then one flat triple + OA block per annotation.
   * When the list is empty, returns a document with only the prefix declarations
   * (valid, empty Turtle — still 200, not 404).
   *
   * @param annotations the annotations to include; may be empty; must not be {@code null}
   * @return a well-formed Turtle string aggregating all annotations
   */
  public static String toAggregatedTurtle(List<SemanticAnnotation> annotations) {
    if (annotations.isEmpty()) {
      return PREFIXES + "\n";
    }
    StringBuilder sb = new StringBuilder(PREFIXES);
    sb.append("\n");
    for (SemanticAnnotation a : annotations) {
      sb.append(annotationBlock(a));
      sb.append("\n");
    }
    return sb.toString();
  }

  // ── private helpers ──────────────────────────────────────────────────────

  /**
   * Builds the flat-triple + OA-frame block for one annotation.
   * Does NOT include prefix declarations (the caller emits those once).
   */
  static String annotationBlock(SemanticAnnotation a) {
    String subjectIri = shepardIri(a.getSubjectKind(), a.getSubjectAppId());
    String predicateIri = nvl(a.getPropertyIRI(),
      "http://www.w3.org/1999/02/22-rdf-syntax-ns#predicate");
    String annotationIri = "shepard:Annotation/" + nvl(a.getAppId(), "unknown");
    String activityIri = blank(a.getSourceActivityAppId())
      ? null
      : "shepard:Activity/" + a.getSourceActivityAppId();

    // object value: IRI or literal
    String objectValue = a.getValueIRI() != null
      ? "<" + a.getValueIRI() + ">"
      : "\"" + escapeTurtleLiteral(nvl(a.getValueName(), "")) + "\"";

    StringBuilder sb = new StringBuilder();

    // Flat triple (§3.3 line 1)
    sb.append("<").append(subjectIri).append("> <").append(predicateIri)
      .append("> ").append(objectValue).append(" .\n");
    sb.append("\n");

    // OA-shaped annotation (§3.3 lines 2-5)
    sb.append("<").append(annotationIri).append("> a oa:Annotation ;\n");
    sb.append("    oa:hasTarget <").append(subjectIri).append("> ;\n");
    sb.append("    oa:hasBody [ rdf:value ").append(objectValue)
      .append(" ; sh:path <").append(predicateIri).append("> ]");
    if (activityIri != null) {
      sb.append(" ;\n    prov:wasGeneratedBy <").append(activityIri).append(">");
    }
    sb.append(" .\n");

    return sb.toString();
  }

  private static String shepardIri(String kind, String appId) {
    if (blank(kind) || blank(appId)) {
      return "https://shepard.dlr.de/v2/entities/" + nvl(appId, "unknown");
    }
    return "https://shepard.dlr.de/v2/" + kind.toLowerCase() + "s/" + appId;
  }

  static String escapeTurtleLiteral(String s) {
    return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r");
  }

  private static boolean blank(String s) {
    return s == null || s.isBlank();
  }

  private static String nvl(String s, String fallback) {
    return s == null || s.isBlank() ? fallback : s;
  }
}
