package de.dlr.shepard.v2.shapes.builder;

/**
 * Non-validating form-presentation hints on one property shape — the
 * FORM-DASH-VOCAB slice of the BTKVS-B1 design
 * ({@code aidocs/integrations/125-btkvs-shacl-form-templates.md §4}).
 *
 * <p>Three hint layers compile from this record:
 * <ul>
 *   <li><b>Core SHACL non-validating characteristics</b> — {@code sh:name},
 *       {@code sh:description}, {@code sh:order}, {@code sh:group},
 *       {@code sh:defaultValue}. Ignored by every SHACL validator per spec;
 *       consumed by the form-descriptor compiler.</li>
 *   <li><b>DASH</b> (adopted verbatim, never mutated) — {@code dash:editor}
 *       (an editor IRI from the <a href="https://datashapes.org/dash">DASH
 *       catalogue</a> or a {@code shepard-ui:*Editor} individual) and
 *       {@code dash:singleLine}.</li>
 *   <li><b>Minted residue</b> — {@code urn:shepard:form:placeholder} and
 *       {@code urn:shepard:form:visibleWhen} (the only two per-property
 *       predicates DASH lacks; doc 125 §4.2 Layer 3).</li>
 * </ul>
 *
 * <p>{@code cellMapping} compiles to the <em>domain-side</em>
 * {@code urn:btkvs:cell-mapping} / {@code urn:btkvs:sheet} annotations (doc
 * 125 §4.2 "Domain layer") that drive the Excel round-trip (BTKVS-C2). The
 * core builder emits them verbatim as opaque annotations; only the BTKVS
 * plugin interprets them.
 *
 * <p>All fields are optional — {@code null} means "no hint", and a fully-null
 * record emits nothing.
 *
 * @param name         field label ({@code sh:name})
 * @param description  help text ({@code sh:description})
 * @param order        field order ({@code sh:order}, decimal; DASH rule:
 *                     unordered fields sort last, alphabetically)
 * @param group        IRI of the {@code sh:PropertyGroup} this field belongs
 *                     to ({@code sh:group}); declare the group itself via
 *                     {@link GroupSpec} on the {@link ShapeSpec}
 * @param defaultValue pre-filled value ({@code sh:defaultValue}, string literal)
 * @param editor       explicit editor IRI ({@code dash:editor}); when absent
 *                     the descriptor compiler applies DASH constraint scoring
 * @param singleLine   {@code dash:singleLine} — {@code false} hints a textarea
 * @param placeholder  input placeholder text ({@code urn:shepard:form:placeholder})
 * @param visibleWhen  conditional-visibility JSON expression
 *                     ({@code urn:shepard:form:visibleWhen}; presentation-only,
 *                     never validation — doc 125 §4.2)
 * @param cellMapping  Excel cell mapping ({@code urn:btkvs:cell-mapping} +
 *                     {@code urn:btkvs:sheet}); nullable
 */
public record FormHintSpec(
  String name,
  String description,
  Double order,
  String group,
  String defaultValue,
  String editor,
  Boolean singleLine,
  String placeholder,
  String visibleWhen,
  CellMappingSpec cellMapping
) {
  /** One Excel cell mapping: optional worksheet name + A1-style cell ref. */
  public record CellMappingSpec(String sheet, String cell) {}

  /** True when every hint field is absent — the builder emits nothing then. */
  public boolean isEmpty() {
    return (
      isBlank(name) &&
      isBlank(description) &&
      order == null &&
      isBlank(group) &&
      defaultValue == null &&
      isBlank(editor) &&
      singleLine == null &&
      isBlank(placeholder) &&
      isBlank(visibleWhen) &&
      (cellMapping == null || (isBlank(cellMapping.sheet()) && isBlank(cellMapping.cell())))
    );
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }
}
