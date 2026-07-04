package de.dlr.shepard.v2.template.resources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.identifier.AppIdGenerator;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.daos.CollectionPropertiesDAO;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.template.daos.ShepardTemplateDAO;
import de.dlr.shepard.template.entities.ShepardTemplate;
import de.dlr.shepard.template.services.TemplateInheritanceResolver;
import de.dlr.shepard.v2.annotations.daos.SemanticAnnotationV2DAO;
import de.dlr.shepard.v2.shapes.validator.JenaShaclValidator;
import de.dlr.shepard.v2.template.io.TemplateInstantiateRequestIO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * {@code POST /v2/collections/{collectionAppId}/data-objects/from-template/{templateAppId}}
 *
 * <p>Server-side DataObject instantiation from a {@code :ShepardTemplate} per T1e
 * ({@code aidocs/16-dispatcher-backlog.md §T1e}).
 *
 * <p>Steps:
 * <ol>
 *   <li>401 when the caller is unauthenticated.</li>
 *   <li>404 when the Collection is not found.</li>
 *   <li>403 when the caller lacks Write on the Collection.</li>
 *   <li>404 when the template is not found.</li>
 *   <li>409 when the template is retired.</li>
 *   <li>403 when the Collection has a non-empty {@code :ALLOWS_TEMPLATE} allow-list
 *       and the template is not in it (empty list = unrestricted).</li>
 *   <li>Flatten the inherited body ({@code aidocs/integrations/123}).</li>
 *   <li>If the flattened body carries a {@code shapeGraph} Turtle string, validate the
 *       to-be-created DataObject against it. 422 on violation (V2CONV-B2, closes the
 *       dormant seam at {@code aidocs/platform/191 §3}).</li>
 *   <li>Create the DataObject with attributes extracted from the template body
 *       ({@code dataobjects[0].attributes}).</li>
 *   <li>Record the {@code :CREATED_FROM_TEMPLATE} relationship.</li>
 *   <li>Seed {@code :SemanticAnnotation} nodes from {@code dataObject.annotations[]}
 *       in the flattened template body (fire-and-forget secondary writes per CLAUDE.md).
 *       UI-GAP-4 / TPL-INSTANTIATE-ANNOTATIONS-1.</li>
 *   <li>Return 201 + the new {@link DataObjectIO}.</li>
 * </ol>
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/collections/{collectionAppId}/data-objects/from-template")
@RequestScoped
@Tag(name = "Collections")
public class TemplateInstantiationRest {

  /**
   * Prefix for the synthetic instance node used when building the data Turtle
   * for SHACL validation. The URI is local to the validation call and never
   * persisted; it just needs to be stable within one validation invocation.
   */
  private static final String PT_NOT_FOUND     = "/problems/template-instantiation.not-found";
  private static final String PT_FORBIDDEN     = "/problems/template-instantiation.forbidden";
  private static final String PT_CONFLICT      = "/problems/template-instantiation.conflict";
  private static final String PT_UNPROCESSABLE = "/problems/template-instantiation.unprocessable";
  private static final String PT_UNAUTHORIZED  = "/problems/template-instantiation.unauthorized";

  static final String INSTANCE_URI = "urn:shepard:instance:candidate";

  /**
   * Predicate namespace for DataObject attributes in the data Turtle graph.
   * Mirrors the {@code urn:shepard:attribute:} namespace used in the default
   * organizing ontology so shapes authored against that namespace work here.
   * Public because the form-descriptor compiler (FORM-DESCRIPTOR-1) derives
   * each field's {@code attributeKey} from the same namespace.
   */
  public static final String ATTR_NS = "urn:shepard:attribute:";

  @Inject
  ShepardTemplateDAO templateDAO;

  @Inject
  CollectionPropertiesDAO collectionPropsDAO;

  @Inject
  PermissionsService permissionsService;

  @Inject
  DataObjectService dataObjectService;

  @Inject
  ObjectMapper objectMapper;

  @Inject
  TemplateInheritanceResolver inheritanceResolver;

  @Inject
  JenaShaclValidator shaclValidator;

  @Inject
  SemanticAnnotationV2DAO annotationDAO;

  @POST
  @Path("/{templateAppId}")
  @Operation(
    operationId = "instantiateDataObject",
    summary = "Create a DataObject from a ShepardTemplate (server-side instantiation).",
    description = "Parses the template body's `dataobjects[0].attributes` and applies them to the new " +
    "DataObject. Records a `:CREATED_FROM_TEMPLATE` relationship back to the template. " +
    "Requires Write permission on the Collection. The template must be non-retired; " +
    "if the Collection has a non-empty allow-list the template must appear in it. " +
    "When the flattened template body includes a `shapeGraph` (Turtle) field, the " +
    "candidate DataObject's attributes are validated against it before creation — " +
    "422 is returned on violation (V2CONV-B2 / aidocs/platform/191 §3)."
  )
  @APIResponse(
    responseCode = "201",
    description = "DataObject created from the template.",
    content = @Content(schema = @Schema(implementation = DataObjectIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write on the Collection, or the template is not in the Collection's allow-list.")
  @APIResponse(responseCode = "404", description = "Collection or template not found.")
  @APIResponse(responseCode = "409", description = "Template is retired and cannot be used for new instantiations.")
  @APIResponse(
    responseCode = "422",
    description = "The candidate DataObject violates the template's SHACL shape graph. The problem-JSON " +
    "carries a structured violations[] extension ({path, value?, constraint?, message} per finding; " +
    "path = the SHACL resultPath predicate IRI, matching the form descriptor's fields[].path)."
  )
  public Response instantiateDataObject(
    @PathParam("collectionAppId") String collectionAppId,
    @PathParam("templateAppId") String templateAppId,
    @RequestBody(
      required = false,
      description = "Optional name override + caller-supplied attribute values (form input; merged over " +
      "the template's defaults before SHACL validation). All fields are optional.",
      content = @Content(schema = @Schema(implementation = TemplateInstantiateRequestIO.class))
    ) TemplateInstantiateRequestIO body,
    @Context SecurityContext securityContext
  ) {
    // Step 1: authentication
    if (securityContext.getUserPrincipal() == null) {
      return problem(PT_UNAUTHORIZED, "Authentication required", Response.Status.UNAUTHORIZED, null);
    }

    // Step 2 + 3: resolve collection and check Write permission
    Optional<Long> ogmId = collectionPropsDAO.findCollectionIdByAppId(collectionAppId);
    if (ogmId.isEmpty()) {
      return problem(PT_NOT_FOUND, "Collection not found", Response.Status.NOT_FOUND,
        "No Collection with appId " + collectionAppId);
    }
    String caller = securityContext.getUserPrincipal().getName();
    if (!permissionsService.isAccessTypeAllowedForUser(ogmId.get(), AccessType.Write, caller, 0L)) {
      return problem(PT_FORBIDDEN, "Insufficient permission", Response.Status.FORBIDDEN,
        "Caller lacks Write on Collection " + collectionAppId);
    }

    // Step 4: resolve template
    Optional<ShepardTemplate> templateOpt = templateDAO.findByAppId(templateAppId);
    if (templateOpt.isEmpty()) {
      return problem(PT_NOT_FOUND, "Template not found", Response.Status.NOT_FOUND,
        "No template with appId " + templateAppId);
    }
    ShepardTemplate template = templateOpt.get();

    // Step 5: 409 if retired
    if (template.isRetired()) {
      return problem(PT_CONFLICT, "Template retired", Response.Status.CONFLICT,
        "Template " + templateAppId + " is retired; pick a non-retired version");
    }

    // Step 6: allow-list guard (empty list = unrestricted)
    List<ShepardTemplate> allowed = templateDAO.listAllowedForCollection(collectionAppId);
    if (!allowed.isEmpty()) {
      boolean inList = allowed.stream().anyMatch(t -> templateAppId.equals(t.getAppId()));
      if (!inList) {
        return problem(PT_FORBIDDEN, "Template not in allow-list", Response.Status.FORBIDDEN,
          "Template " + templateAppId + " is not in the allow-list for Collection " + collectionAppId);
      }
    }

    // Flatten the inheritance chain (aidocs/integrations/123): a child template's
    // effective body is the parent's fields merged with the child's overrides.
    // The merger in TemplateInheritanceResolver.deepMerge() concatenates parent + child
    // shapeGraph strings (parent ahead), so the result is the full inherited shape.
    String effectiveBody = inheritanceResolver.flattenBody(template);

    // Step 7: extract attributes from the flattened template body dataobjects[0].attributes,
    // then merge caller-supplied attributes over them (caller wins per key — the form-submit
    // leg, BTKVS-B2 / doc 125 §5.2).
    Map<String, String> attributes = new HashMap<>(extractAttributes(effectiveBody));
    if (body != null && body.getAttributes() != null) {
      body.getAttributes().forEach((k, v) -> {
        if (k != null && v != null) {
          attributes.put(k, v);
        }
      });
    }

    // Step 8: SHACL shape validation — V2CONV-B2 / aidocs/platform/191 §3.
    // If the flattened body carries a top-level "shapeGraph" Turtle string, validate the
    // candidate DataObject's attributes against it.  A missing or blank shapeGraph means
    // the template carries no constraints → pass through.  A parse error in either graph
    // is logged as WARN and skipped (defensive: a malformed shape must not break creation).
    String shapeGraph = extractShapeGraph(effectiveBody);
    if (shapeGraph != null && !shapeGraph.isBlank()) {
      String dataTurtle = buildDataTurtle(attributes);
      JenaShaclValidator.Report report = shaclValidator.validate(dataTurtle, shapeGraph);

      if (report.parseError() != null || report.engineError() != null) {
        // Malformed shape or engine error: log and skip validation rather than blocking
        Log.warnf(
          "SHACL validation skipped for template %s due to error: %s%s",
          templateAppId,
          report.parseError() != null ? "parseError=" + report.parseError() : "",
          report.engineError() != null ? "engineError=" + report.engineError() : ""
        );
      } else if (!report.conforms()) {
        // Shape violation: 422 with the structured violations[] extension
        // (FORM-422-FIELDS, doc 125 §5.2) — additive next to the prose detail
        // so pre-existing consumers keep parsing the same fields.
        List<Map<String, Object>> violations = report.findings().stream()
          .map(TemplateInstantiationRest::violationEntry)
          .collect(Collectors.toList());
        String prose = report.findings().stream()
          .map(f -> {
            StringBuilder sb = new StringBuilder();
            if (f.resultPath() != null) sb.append("path=").append(normalizePathIri(f.resultPath())).append(" ");
            if (f.value() != null) sb.append("value=").append(f.value()).append(" ");
            if (f.message() != null && !f.message().isBlank()) sb.append(f.message());
            return sb.toString().trim();
          })
          .collect(Collectors.joining("; "));
        ProblemJson problem = new ProblemJson(
          PT_UNPROCESSABLE,
          "SHACL validation failed",
          422,
          "DataObject violates the template's SHACL shape. Violations: " + prose,
          null,
          Map.of("violations", violations)
        );
        return Response.status(422).type("application/problem+json").entity(problem).build();
      }
    }

    // Step 9: resolve DataObject name from: request body → (flattened) template body → fallback
    String dataObjectName = resolveDataObjectName(body, template, effectiveBody);

    // Step 10: create the DataObject
    DataObjectIO doIO = new DataObjectIO();
    doIO.setName(dataObjectName);
    doIO.setAttributes(attributes.isEmpty() ? null : attributes);

    DataObject created = dataObjectService.createDataObject(ogmId.get(), doIO);

    // Step 11: record :CREATED_FROM_TEMPLATE edge
    templateDAO.recordCreatedFrom(created.getShepardId(), template);

    // Step 12: seed :SemanticAnnotation nodes from dataObject.annotations[] in the
    // (flattened) template body. Fire-and-forget — a secondary write must not block
    // or fail the primary DataObject creation (CLAUDE.md: secondary writes are fail-soft).
    List<Map.Entry<String, String>> annotationSeeds = extractAnnotationSeeds(effectiveBody);
    for (Map.Entry<String, String> seed : annotationSeeds) {
      try {
        SemanticAnnotation ann = new SemanticAnnotation();
        ann.setAppId(AppIdGenerator.next());
        ann.setSubjectKind("DataObject");
        ann.setSubjectAppId(created.getAppId());
        ann.setPropertyIRI(seed.getKey());
        ann.setValueName(seed.getValue());
        ann.setSourceMode("system");
        ann.setAgentUsername(caller);
        ann.setConfidence(1.0);
        annotationDAO.createOrUpdate(ann);
      } catch (Exception ex) {
        Log.warnf("Failed to seed annotation predicate=%s for DataObject %s: %s",
          seed.getKey(), created.getShepardId(), ex.getMessage());
      }
    }

    return Response.status(Response.Status.CREATED).entity(new DataObjectIO(created)).build();
  }

  // ─── helpers ───────────────────────────────────────────────────────────────

  /**
   * One structured {@code violations[]} entry (FORM-422-FIELDS, doc 125 §5.2):
   * {@code path} is the SHACL {@code resultPath} normalised to a bare predicate
   * IRI — byte-identical to the form descriptor's {@code fields[].path}, so the
   * client's field mapping is a dictionary lookup, no heuristics.
   */
  static Map<String, Object> violationEntry(JenaShaclValidator.Finding f) {
    Map<String, Object> entry = new LinkedHashMap<>();
    if (f.resultPath() != null) entry.put("path", normalizePathIri(f.resultPath()));
    if (f.value() != null) entry.put("value", f.value());
    if (f.constraint() != null) entry.put("constraint", f.constraint());
    entry.put("message", f.message() == null ? "" : f.message());
    return entry;
  }

  /**
   * Jena stringifies a predicate-only SPARQL path as {@code <iri>}; strip the
   * angle brackets so the wire value matches the descriptor's bare IRI.
   */
  static String normalizePathIri(String resultPath) {
    if (resultPath == null) return null;
    String p = resultPath.strip();
    if (p.startsWith("<") && p.endsWith(">") && p.length() > 1) {
      return p.substring(1, p.length() - 1);
    }
    return p;
  }

  private static Response problem(String type, String title, Response.Status status, String detail) {
    return Response.status(status).type("application/problem+json")
      .entity(new ProblemJson(type, title, status.getStatusCode(), detail, null)).build();
  }

  private static Response problem(String type, String title, int status, String detail) {
    return Response.status(status).type("application/problem+json")
      .entity(new ProblemJson(type, title, status, detail, null)).build();
  }

  /**
   * Extract the top-level {@code shapeGraph} Turtle string from the flattened
   * template body. The field is optional; returns {@code null} when absent.
   *
   * <p>The {@code shapeGraph} convention is described in
   * {@link de.dlr.shepard.v2.template.io.ShepardTemplateIO} (SHAPES-V-PREFILL-3-EXTRACT-SHACL)
   * and used by {@code TemplateInheritanceResolver.deepMerge()} to concatenate parent +
   * child graphs (parent ahead) during inheritance flattening.
   */
  String extractShapeGraph(String body) {
    if (body == null || body.isBlank()) return null;
    try {
      JsonNode root = objectMapper.readTree(body);
      JsonNode sg = root.path("shapeGraph");
      if (!sg.isMissingNode() && !sg.isNull() && sg.isTextual()) {
        return sg.textValue();
      }
    } catch (JsonProcessingException e) {
      Log.warnf("Could not parse template body when extracting shapeGraph: %s", e.getMessage());
    }
    return null;
  }

  /**
   * Build a minimal Turtle data graph representing the candidate DataObject's
   * attributes. The instance is given a stable local URI ({@value #INSTANCE_URI})
   * and each attribute is expressed as a property in the {@value #ATTR_NS} namespace.
   *
   * <p>Example for {@code {color: "red", count: "3"}}:
   * <pre>{@code
   * <urn:shepard:instance:candidate>
   *   <urn:shepard:attribute:color> "red" ;
   *   <urn:shepard:attribute:count> "3" .
   * }</pre>
   *
   * <p>Shapes authored against {@code urn:shepard:attribute:*} predicates can therefore
   * constrain DataObject attributes at instantiation time.
   *
   * <p>An empty attribute map produces a minimal valid Turtle (the node with no properties).
   */
  String buildDataTurtle(Map<String, String> attributes) {
    StringBuilder sb = new StringBuilder();
    sb.append("<").append(INSTANCE_URI).append(">");
    if (attributes.isEmpty()) {
      sb.append(" .\n");
    } else {
      Iterator<Map.Entry<String, String>> it = attributes.entrySet().iterator();
      boolean first = true;
      while (it.hasNext()) {
        Map.Entry<String, String> entry = it.next();
        String pred = "<" + ATTR_NS + entry.getKey() + ">";
        // Escape the value as a Turtle string literal
        String lit = "\"" + entry.getValue().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\"";
        if (first) {
          sb.append("\n  ").append(pred).append(" ").append(lit);
          first = false;
        } else {
          sb.append(" ;\n  ").append(pred).append(" ").append(lit);
        }
      }
      sb.append(" .\n");
    }
    return sb.toString();
  }

  /**
   * Resolve the DataObject name in priority order:
   * <ol>
   *   <li>Request body {@code name} (non-null, non-blank).</li>
   *   <li>{@code dataobjects[0].name} from the template body.</li>
   *   <li>Template {@code name} + {@code "-"} + current millis.</li>
   * </ol>
   */
  private String resolveDataObjectName(TemplateInstantiateRequestIO body, ShepardTemplate template, String effectiveBody) {
    if (body != null && body.getName() != null && !body.getName().isBlank()) {
      return body.getName();
    }
    // Try (flattened) template body dataobjects[0].name
    if (effectiveBody != null) {
      try {
        JsonNode root = objectMapper.readTree(effectiveBody);
        JsonNode firstDo = root.path("dataobjects").path(0);
        JsonNode nameNode = firstDo.path("name");
        if (!nameNode.isMissingNode() && !nameNode.isNull() && nameNode.isTextual() && !nameNode.textValue().isBlank()) {
          return nameNode.textValue();
        }
      } catch (JsonProcessingException e) {
        Log.warnf("Could not parse template body for DataObject name fallback (templateAppId=%s): %s", template.getAppId(), e.getMessage());
      }
    }
    return template.getName() + "-" + System.currentTimeMillis();
  }

  /**
   * Extract {@code dataObject.annotations[]} from the (flattened) template body as
   * a list of predicate → value pairs. Each entry must carry both {@code predicate}
   * and {@code value} (non-null, non-blank). Returns an empty list when the path is
   * absent, the body is null, or parsing fails.
   *
   * <p>Template body shape (from V100 MFFD migration, UI-GAP-4):
   * <pre>{@code
   * {
   *   "dataObject": {
   *     "annotations": [
   *       { "predicate": "urn:shepard:domain", "value": "aerospace-manufacturing" }
   *     ]
   *   }
   * }
   * }</pre>
   *
   * <p>Note: {@code dataObject} (singular) is the schema description key; distinct
   * from {@code dataobjects} (plural) which carries per-instance attribute defaults.
   */
  List<Map.Entry<String, String>> extractAnnotationSeeds(String body) {
    if (body == null || body.isBlank()) return List.of();
    JsonNode root;
    try {
      root = objectMapper.readTree(body);
    } catch (JsonProcessingException e) {
      Log.warnf("Could not parse template body for annotation seed extraction: %s", e.getMessage());
      return List.of();
    }
    JsonNode annotations = root.path("dataObject").path("annotations");
    if (annotations.isMissingNode() || annotations.isNull() || !annotations.isArray()) return List.of();

    List<Map.Entry<String, String>> result = new ArrayList<>();
    for (JsonNode entry : annotations) {
      JsonNode predNode = entry.path("predicate");
      JsonNode valNode = entry.path("value");
      if (!predNode.isTextual() || predNode.textValue().isBlank()) continue;
      if (!valNode.isTextual() || valNode.textValue().isBlank()) continue;
      result.add(Map.entry(predNode.textValue(), valNode.textValue()));
    }
    return result;
  }

  /**
   * Extract {@code dataobjects[0].attributes} from the template body as a
   * {@code Map<String,String>}. Returns an empty map when the path is absent,
   * the body is null, or parsing fails. Tolerates missing keys gracefully.
   */
  private Map<String, String> extractAttributes(String body) {
    if (body == null || body.isBlank()) return Map.of();
    JsonNode root;
    try {
      root = objectMapper.readTree(body);
    } catch (JsonProcessingException e) {
      Log.warnf("Could not parse template body for attribute extraction: %s", e.getMessage());
      return Map.of();
    }
    JsonNode attrs = root.path("dataobjects").path(0).path("attributes");
    if (attrs.isMissingNode() || attrs.isNull() || !attrs.isObject()) return Map.of();

    Map<String, String> result = new HashMap<>();
    Iterator<Map.Entry<String, JsonNode>> fields = attrs.fields();
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> entry = fields.next();
      JsonNode v = entry.getValue();
      if (v != null && !v.isNull()) {
        result.put(entry.getKey(), v.isTextual() ? v.textValue() : v.toString());
      }
    }
    return result;
  }
}
