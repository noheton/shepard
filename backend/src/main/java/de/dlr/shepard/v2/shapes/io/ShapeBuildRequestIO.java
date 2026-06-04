package de.dlr.shepard.v2.shapes.io;

import de.dlr.shepard.v2.shapes.builder.InMember;
import de.dlr.shepard.v2.shapes.builder.PropertyShapeSpec;
import de.dlr.shepard.v2.shapes.builder.ShapeSpec;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * V2CONV-B6 — request body for {@code POST /v2/shapes/build}.
 *
 * <p>The wire-level twin of {@link ShapeSpec} / {@link PropertyShapeSpec} /
 * {@link InMember}: the visual template editor serialises its editor state to
 * this JSON DSL and the endpoint compiles it to canonical SHACL Turtle via
 * {@link de.dlr.shepard.v2.shapes.builder.ShaclShapeBuilder}. The "author once,
 * used four ways" spine — see {@code aidocs/platform/191-v2-surface-convergence.md §3}.
 *
 * <p>Why a separate IO rather than binding {@code ShapeSpec} directly: the
 * builder records are {@code @ApplicationScoped}-adjacent domain types that
 * carry a non-default constructor / factory helpers and an enum nested type
 * ({@link InMember.Kind}) whose JSON binding we want to pin explicitly (a
 * lower-case-tolerant enum string). Keeping the wire shape in the {@code io}
 * package mirrors {@link ShapeValidationRequestIO} and keeps the OpenAPI schema
 * stable independently of internal builder refactors.
 */
@Schema(
  description = "JSON DSL for a SHACL NodeShape. Compiled to canonical Turtle by POST /v2/shapes/build."
)
public record ShapeBuildRequestIO(
  @Schema(
    description = "IRI of the sh:NodeShape. When null/blank the builder mints a deterministic local IRI.",
    nullable = true,
    example = "urn:shepard:shape:mffd-ncr"
  )
  String shapeIri,
  @Schema(
    description = "sh:targetClass IRI. Nullable — a shape may target by other means or be referenced via sh:node.",
    nullable = true,
    example = "http://semantics.dlr.de/shepard#DataObject"
  )
  String targetClass,
  @Schema(description = "sh:closed — when true, emits sh:closed true.", defaultValue = "false")
  boolean closed,
  @Schema(description = "The property constraints. Null/empty = a shape with no property constraints.", nullable = true)
  List<PropertyIO> properties
) {
  /** Wire shape for one {@code sh:PropertyShape}. Mirrors {@link PropertyShapeSpec}. */
  @Schema(description = "One SHACL property constraint, keyed by predicate IRI.")
  public record PropertyIO(
    @Schema(required = true, description = "The predicate IRI (sh:path).", example = "http://semantics.dlr.de/shepard#status")
    String path,
    @Schema(description = "Literal datatype IRI (sh:datatype).", nullable = true)
    String datatype,
    @Schema(description = "sh:minCount.", nullable = true)
    Integer minCount,
    @Schema(description = "sh:maxCount.", nullable = true)
    Integer maxCount,
    @Schema(description = "sh:in membership list. Null/empty = no sh:in.", nullable = true)
    List<InMemberIO> in,
    @Schema(description = "Nested sh:NodeShape IRI (sh:node).", nullable = true)
    String node
  ) {}

  /** Wire shape for one {@code sh:in} member. Mirrors {@link InMember}. */
  @Schema(description = "One sh:in member, with its RDF kind stated explicitly.")
  public record InMemberIO(
    @Schema(required = true, description = "Lexical value: the literal lexical form, or the IRI string.")
    String value,
    @Schema(description = "IRI or LITERAL. Null defaults to LITERAL.", nullable = true, example = "LITERAL")
    String kind,
    @Schema(description = "Literal datatype IRI (LITERAL only). Null defaults to xsd:string.", nullable = true)
    String datatype
  ) {}

  /**
   * Convert this wire shape to the builder's {@link ShapeSpec}. Null-tolerant:
   * a null {@code properties} list compiles to a constraint-free node shape.
   */
  public ShapeSpec toSpec() {
    List<PropertyShapeSpec> props = new ArrayList<>();
    if (properties != null) {
      for (PropertyIO p : properties) {
        if (p == null) continue;
        props.add(
          new PropertyShapeSpec(p.path(), p.datatype(), p.minCount(), p.maxCount(), toMembers(p.in()), p.node())
        );
      }
    }
    return new ShapeSpec(shapeIri, targetClass, closed, props);
  }

  private static List<InMember> toMembers(List<InMemberIO> raw) {
    if (raw == null || raw.isEmpty()) {
      return null;
    }
    List<InMember> out = new ArrayList<>();
    for (InMemberIO m : raw) {
      if (m == null) continue;
      InMember.Kind kind = parseKind(m.kind());
      out.add(new InMember(m.value(), kind, m.datatype()));
    }
    return out.isEmpty() ? null : out;
  }

  /** Lower-case tolerant; anything not recognised as IRI falls back to LITERAL. */
  private static InMember.Kind parseKind(String raw) {
    if (raw != null && "iri".equalsIgnoreCase(raw.trim())) {
      return InMember.Kind.IRI;
    }
    return InMember.Kind.LITERAL;
  }
}
