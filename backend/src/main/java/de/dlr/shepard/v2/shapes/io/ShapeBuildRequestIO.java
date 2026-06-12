package de.dlr.shepard.v2.shapes.io;

import de.dlr.shepard.v2.shapes.builder.FormHintSpec;
import de.dlr.shepard.v2.shapes.builder.GroupSpec;
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
  List<PropertyIO> properties,
  @Schema(description = "sh:PropertyGroup declarations (form sections, doc 125 §4.2). Null/empty = none.", nullable = true)
  List<GroupIO> groups,
  @Schema(
    description = "sh:targetNode IRI. Shapes meant to fire on the validate-on-instantiate seam target " +
    "urn:shepard:instance:candidate (the stable candidate-graph URI).",
    nullable = true,
    example = "urn:shepard:instance:candidate"
  )
  String targetNode
) {
  /** Pre-BTKVS-B2 compatibility constructor (no group declarations, no targetNode). */
  public ShapeBuildRequestIO(String shapeIri, String targetClass, boolean closed, List<PropertyIO> properties) {
    this(shapeIri, targetClass, closed, properties, null, null);
  }

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
    String node,
    @Schema(description = "Regex the literal value must match (sh:pattern).", nullable = true, example = "^[A-Z][0-9]{3}$")
    String pattern,
    @Schema(description = "Non-validating form-presentation hints (DASH editor, label, order, group, placeholder, cell mapping — doc 125 §4).", nullable = true)
    HintsIO hints
  ) {
    /** Pre-BTKVS-B2 compatibility constructor (no pattern, no hints). */
    public PropertyIO(
      String path,
      String datatype,
      Integer minCount,
      Integer maxCount,
      List<InMemberIO> in,
      String node
    ) {
      this(path, datatype, minCount, maxCount, in, node, null, null);
    }
  }

  /** Wire shape for the non-validating hint bag. Mirrors {@link FormHintSpec}. */
  @Schema(description = "Non-validating form hints on one property shape (doc 125 §4).")
  public record HintsIO(
    @Schema(description = "Field label (sh:name).", nullable = true)
    String name,
    @Schema(description = "Help text (sh:description).", nullable = true)
    String description,
    @Schema(description = "Field order (sh:order).", nullable = true)
    Double order,
    @Schema(description = "IRI of the sh:PropertyGroup this field belongs to (sh:group).", nullable = true)
    String group,
    @Schema(description = "Pre-filled value (sh:defaultValue).", nullable = true)
    String defaultValue,
    @Schema(description = "Explicit editor IRI (dash:editor).", nullable = true, example = "http://datashapes.org/dash#TextFieldEditor")
    String editor,
    @Schema(description = "dash:singleLine — false hints a textarea.", nullable = true)
    Boolean singleLine,
    @Schema(description = "Input placeholder text (urn:shepard:form:placeholder).", nullable = true)
    String placeholder,
    @Schema(description = "Conditional-visibility JSON (urn:shepard:form:visibleWhen).", nullable = true)
    String visibleWhen,
    @Schema(description = "Excel cell mapping (urn:btkvs:cell-mapping + urn:btkvs:sheet).", nullable = true)
    CellMappingIO cellMapping
  ) {}

  /** Wire shape for one Excel cell mapping. */
  @Schema(description = "One Excel cell mapping: optional worksheet name + A1-style cell ref.")
  public record CellMappingIO(
    @Schema(description = "Worksheet name (urn:btkvs:sheet).", nullable = true)
    String sheet,
    @Schema(description = "A1-style cell reference (urn:btkvs:cell-mapping).", nullable = true)
    String cell
  ) {}

  /** Wire shape for one {@code sh:PropertyGroup}. Mirrors {@link GroupSpec}. */
  @Schema(description = "One sh:PropertyGroup declaration (a form section).")
  public record GroupIO(
    @Schema(required = true, description = "The group IRI.", example = "urn:btkvs:group:identity")
    String iri,
    @Schema(description = "Section label (rdfs:label).", nullable = true)
    String label,
    @Schema(description = "Section order (sh:order).", nullable = true)
    Double order
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
          new PropertyShapeSpec(
            p.path(),
            p.datatype(),
            p.minCount(),
            p.maxCount(),
            toMembers(p.in()),
            p.node(),
            p.pattern(),
            toHints(p.hints())
          )
        );
      }
    }
    List<GroupSpec> groupSpecs = null;
    if (groups != null && !groups.isEmpty()) {
      groupSpecs = new ArrayList<>();
      for (GroupIO g : groups) {
        if (g == null) continue;
        groupSpecs.add(new GroupSpec(g.iri(), g.label(), g.order()));
      }
    }
    return new ShapeSpec(shapeIri, targetClass, closed, props, groupSpecs, targetNode);
  }

  private static FormHintSpec toHints(HintsIO h) {
    if (h == null) return null;
    FormHintSpec.CellMappingSpec cm = h.cellMapping() == null
      ? null
      : new FormHintSpec.CellMappingSpec(h.cellMapping().sheet(), h.cellMapping().cell());
    return new FormHintSpec(
      h.name(),
      h.description(),
      h.order(),
      h.group(),
      h.defaultValue(),
      h.editor(),
      h.singleLine(),
      h.placeholder(),
      h.visibleWhen(),
      cm
    );
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
