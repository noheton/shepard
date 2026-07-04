package de.dlr.shepard.v2.template.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Response body for {@code GET /v2/templates/{templateAppId}/form} — the
 * compiled, cacheable form descriptor (FORM-DESCRIPTOR-1, doc 125 §5.1).
 *
 * <p>A form is the <b>write-direction projection of a data-kind shape</b>
 * (doc 125 D1): the same flattened {@code shapeGraph} the V2CONV-B2
 * validate-on-instantiate seam enforces server-side is compiled here into a
 * renderer-friendly JSON so browser forms, the BT-KVS Streamlit frontend, and
 * MCP agents can render + submit without a client-side SHACL stack.
 *
 * <p>{@code fields[].path} carries the SHACL predicate IRI — byte-identical
 * to the {@code violations[].path} entries the submit leg's 422 problem-JSON
 * returns (FORM-422-FIELDS), so mapping a violation to a form field is a
 * dictionary lookup.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "TemplateFormDescriptor", description = "Compiled form descriptor for a data-kind ShepardTemplate (doc 125 §5.1).")
public record TemplateFormDescriptorIO(
  @Schema(description = "appId of the template this descriptor was compiled from.")
  String templateAppId,
  @Schema(description = "The template's kind (a data kind: DATAOBJECT_RECIPE / COLLECTION_RECIPE / STRUCTURED_RECIPE).")
  String templateKind,
  @Schema(description = "Form title — the template name.")
  String title,
  @Schema(description = "IRI of the compiled sh:NodeShape.", nullable = true)
  String shapeIri,
  @Schema(description = "Form sections (sh:PropertyGroup), ordered.")
  List<GroupIO> groups,
  @Schema(description = "Form fields, ordered by sh:order (unordered fields last, alphabetical — the DASH rule).")
  List<FieldIO> fields,
  @Schema(description = "Server-computed submit target. The client never chooses an endpoint.")
  SubmitIO submit
) {
  /** One form section. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(description = "One form section (sh:PropertyGroup).")
  public record GroupIO(
    @Schema(description = "Group IRI — referenced by fields[].group.")
    String id,
    @Schema(description = "Section label (rdfs:label).", nullable = true)
    String label,
    @Schema(description = "Section order (sh:order).", nullable = true)
    Double order
  ) {}

  /** One form field, compiled from one sh:PropertyShape. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(description = "One form field (compiled sh:PropertyShape + hints).")
  public record FieldIO(
    @Schema(description = "The SHACL predicate IRI (sh:path) — the key violations[].path maps back to.")
    String path,
    @Schema(
      description = "DataObject attribute key when the path lives in the urn:shepard:attribute: namespace — " +
      "the key a client puts in the instantiation request's attributes map. Null for non-attribute paths.",
      nullable = true
    )
    String attributeKey,
    @Schema(description = "Field label (sh:name; falls back to the path's local name).")
    String label,
    @Schema(description = "Help text (sh:description).", nullable = true)
    String description,
    @Schema(description = "IRI of the group this field belongs to (sh:group).", nullable = true)
    String group,
    @Schema(description = "Field order (sh:order).", nullable = true)
    Double order,
    @Schema(description = "Literal datatype IRI (sh:datatype).", nullable = true)
    String datatype,
    @Schema(description = "True when sh:minCount >= 1.", nullable = true)
    Boolean required,
    @Schema(description = "Regex the value must match (sh:pattern).", nullable = true)
    String pattern,
    @Schema(description = "Editor IRI — explicit dash:editor, else the DASH constraint-scoring default.")
    String editor,
    @Schema(description = "dash:singleLine.", nullable = true)
    Boolean singleLine,
    @Schema(description = "Input placeholder text (urn:shepard:form:placeholder).", nullable = true)
    String placeholder,
    @Schema(description = "Pre-filled value (sh:defaultValue).", nullable = true)
    String defaultValue,
    @Schema(description = "Enumeration options (sh:in lexical forms / IRIs).", nullable = true)
    List<String> options,
    @Schema(description = "Conditional-visibility JSON (urn:shepard:form:visibleWhen). Presentation-only.", nullable = true)
    String visibleWhen,
    @Schema(description = "Excel cell mapping (urn:btkvs:cell-mapping + urn:btkvs:sheet). Domain-side, BTKVS-C2.", nullable = true)
    CellMappingIO cellMapping
  ) {}

  /** Excel cell mapping surfaced from the domain-side annotations. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(description = "One Excel cell mapping.")
  public record CellMappingIO(
    @Schema(description = "Worksheet name.", nullable = true)
    String sheet,
    @Schema(description = "A1-style cell reference.")
    String cell
  ) {}

  /** The server-computed submit block. */
  @Schema(description = "Server-computed submit target (doc 125 §5.1).")
  public record SubmitIO(
    @Schema(description = "HTTP method.")
    String method,
    @Schema(description = "URI template of the submit endpoint. {collectionAppId} is filled by the client from context.")
    String href,
    @Schema(description = "Human-readable note on the 422 violation contract.")
    String violationContract
  ) {}
}
