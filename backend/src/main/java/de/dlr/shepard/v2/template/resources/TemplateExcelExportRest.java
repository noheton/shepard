package de.dlr.shepard.v2.template.resources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.template.daos.ShepardTemplateDAO;
import de.dlr.shepard.template.entities.ShepardTemplate;
import de.dlr.shepard.template.services.TemplateInheritanceResolver;
import de.dlr.shepard.v2.template.io.TemplateFormDescriptorIO;
import de.dlr.shepard.v2.template.services.CellMappingExcelExporter;
import de.dlr.shepard.v2.template.services.FormDescriptorCompiler;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.Optional;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import static de.dlr.shepard.v2.common.ProblemResponse.problem;

/**
 * {@code GET /v2/templates/{appId}/export?dataObjectAppId=…} —
 * shape-driven Excel export (BTKVS-C1-EXCEL-EXPORT, doc 125 §6/D5).
 *
 * <p><b>Seam reasoning (BTKVS-C1 reconciliation, aidocs/platform/191).</b>
 * The export is the <em>read-direction spreadsheet projection</em> of the
 * same data-kind shape that {@code GET …/form} projects write-direction:
 * one template surface, two projections. It therefore lives as a sibling on
 * the generic {@code /v2/templates/{appId}} namespace — no plugin-minted
 * top-level {@code /v2/btkvs/…} resource (illegal per 191 Tier-2/Tier-3;
 * same dissolution the svdx precedent applied 2026-06-12). A
 * {@code ViewRecipeRenderer} through {@code POST /v2/shapes/render} was
 * rejected: a STRUCTURED_RECIPE is a data kind, not a VIEW_RECIPE — routing
 * it through the render dispatcher would distort the template kind model
 * (doc 125 D1).
 *
 * <p><b>What it does.</b> Flattens the template's inheritance chain,
 * compiles the {@code shapeGraph} via {@link FormDescriptorCompiler} (the
 * same field extraction the form descriptor uses — the cell-mapping logic
 * exists in exactly one place), reads the focused DataObject's attributes,
 * and writes each mapped value into the mapped cell/sheet of a generated
 * workbook ({@link CellMappingExcelExporter}). Fields without cell mappings
 * are skipped silently.
 *
 * <p><b>Status codes.</b> 401 unauthenticated · 404 unknown template or
 * unknown/deleted DataObject · 403 caller lacks Read on the DataObject's
 * Collection · 409 retired template, or template whose shape carries no
 * {@code urn:btkvs:cell-mapping} annotations · 422 template not
 * form-renderable / no shapeGraph / unparseable Turtle (mirrors the form
 * descriptor's contract).
 */
@Produces(CellMappingExcelExporter.XLSX_MEDIA_TYPE)
@Path("/v2/templates/{appId}/export")
@RequestScoped
@Tag(name = "Templates")
public class TemplateExcelExportRest {

  private static final String PT_NOT_FOUND    = "/problems/templates.not-found";
  private static final String PT_CONFLICT     = "/problems/templates.conflict";
  private static final String PT_UNPROCESSABLE = "/problems/templates.unprocessable";
  private static final String PT_UNAUTHORIZED = "/problems/templates.unauthorized";
  private static final String PT_FORBIDDEN    = "/problems/templates.forbidden";

  @Inject
  ShepardTemplateDAO templateDAO;

  @Inject
  TemplateInheritanceResolver inheritanceResolver;

  @Inject
  FormDescriptorCompiler compiler;

  @Inject
  CellMappingExcelExporter exporter;

  @Inject
  DataObjectDAO dataObjectDAO;

  @Inject
  PermissionsService permissionsService;

  @Inject
  ObjectMapper objectMapper;

  @GET
  @Operation(
    operationId = "exportTemplateExcel",
    summary = "Export a DataObject as an Excel workbook driven by its template's SHACL cell-mappings.",
    description = "The read-direction spreadsheet projection of a data-kind template's shape (doc 125 §6/D5): " +
    "walks the urn:btkvs:cell-mapping / urn:btkvs:sheet annotations on the flattened shapeGraph and writes " +
    "the focused DataObject's attribute values into the mapped cells of a generated xlsx workbook. " +
    "Fields without cell-mappings are skipped silently. The same shape drives the web form " +
    "(GET …/form), server-side validation (422 violations[]), and — with BTKVS-C2 — the Excel import."
  )
  @APIResponse(
    responseCode = "200",
    description = "The generated workbook.",
    content = @Content(mediaType = CellMappingExcelExporter.XLSX_MEDIA_TYPE)
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the DataObject's Collection.")
  @APIResponse(responseCode = "404", description = "No template or no DataObject with that appId.")
  @APIResponse(
    responseCode = "409",
    description = "Template is retired, or its shape carries no urn:btkvs:cell-mapping annotations (nothing to export)."
  )
  @APIResponse(
    responseCode = "422",
    description = "Template is not form-renderable: no shapeGraph in the flattened body, unparseable Turtle, or a non-data templateKind."
  )
  public Response exportExcel(
    @PathParam("appId") String appId,
    @Parameter(
      description = "appId of the focused DataObject whose attributes fill the mapped cells.",
      required = true
    ) @QueryParam("dataObjectAppId") String dataObjectAppId,
    @Context SecurityContext securityContext
  ) {
    if (securityContext.getUserPrincipal() == null) {
      return problem(PT_UNAUTHORIZED, "Authentication required", Response.Status.UNAUTHORIZED.getStatusCode(), null);
    }
    String caller = securityContext.getUserPrincipal().getName();

    Optional<ShepardTemplate> templateOpt = templateDAO.findByAppId(appId);
    if (templateOpt.isEmpty()) {
      return problem(PT_NOT_FOUND, "Template not found", Response.Status.NOT_FOUND.getStatusCode(),
        "No template with appId " + appId);
    }
    ShepardTemplate template = templateOpt.get();

    if (template.isRetired()) {
      return problem(PT_CONFLICT, "Template retired", Response.Status.CONFLICT.getStatusCode(),
        "Template " + appId + " is retired; pick a non-retired version");
    }

    if (template.getTemplateKind() == null ||
        !FormDescriptorCompiler.FORM_RENDERABLE_KINDS.contains(template.getTemplateKind())) {
      return problem(PT_UNPROCESSABLE, "Template not exportable", 422,
        "templateKind " + template.getTemplateKind() + " is not a data kind — the Excel export is the " +
        "read-direction projection of DATAOBJECT_RECIPE / COLLECTION_RECIPE / STRUCTURED_RECIPE shapes " +
        "(doc 125 §6)");
    }

    String effectiveBody = inheritanceResolver.flattenBody(template);
    String shapeGraph = extractShapeGraph(effectiveBody);
    if (shapeGraph == null || shapeGraph.isBlank()) {
      return problem(PT_UNPROCESSABLE, "Template carries no shapeGraph", 422,
        "The flattened body of template " + appId + " has no shapeGraph Turtle — author one " +
        "via POST /v2/shapes/build (the JSON-DSL shape builder) and store it on the template body");
    }

    TemplateFormDescriptorIO descriptor;
    try {
      descriptor = compiler.compile(template, shapeGraph);
    } catch (IllegalArgumentException ex) {
      return problem(PT_UNPROCESSABLE, "Shape graph not compilable", 422,
        "Template " + appId + ": " + ex.getMessage());
    }

    if (!exporter.hasCellMappings(descriptor.fields())) {
      return problem(PT_CONFLICT, "Template carries no cell-mappings", Response.Status.CONFLICT.getStatusCode(),
        "No property shape on template " + appId + " carries a urn:btkvs:cell-mapping annotation — " +
        "there is nothing to place in a workbook. Author cell mappings via the shape builder's " +
        "hints.cellMapping (doc 125 §4.2)");
    }

    DataObject dataObject = dataObjectAppId == null || dataObjectAppId.isBlank()
      ? null
      : dataObjectDAO.findByAppId(dataObjectAppId);
    if (dataObject == null || dataObject.isDeleted()) {
      return problem(PT_NOT_FOUND, "DataObject not found", Response.Status.NOT_FOUND.getStatusCode(),
        "No DataObject with appId " + dataObjectAppId);
    }

    if (!permissionsService.isAccessAllowedForDataObjectAppId(dataObjectAppId, AccessType.Read, caller)) {
      return problem(PT_FORBIDDEN, "Insufficient permission", Response.Status.FORBIDDEN.getStatusCode(),
          "Caller lacks Read on the DataObject's Collection");
    }

    byte[] workbook = exporter.export(descriptor.fields(), dataObject.getAttributes());
    return Response.ok(workbook, CellMappingExcelExporter.XLSX_MEDIA_TYPE)
      .header("Content-Disposition", "attachment; filename=\"" + filenameFor(template, dataObject) + "\"")
      .build();
  }

  // ─── helpers ───────────────────────────────────────────────────────

  /** Extract the top-level {@code shapeGraph} Turtle string from the flattened body. */
  String extractShapeGraph(String body) {
    if (body == null || body.isBlank()) return null;
    try {
      JsonNode root = objectMapper.readTree(body);
      JsonNode sg = root.path("shapeGraph");
      if (!sg.isMissingNode() && !sg.isNull() && sg.isTextual()) {
        return sg.textValue();
      }
    } catch (JsonProcessingException e) {
      Log.warnf("TemplateExcelExportRest: could not parse template body when extracting shapeGraph: %s",
        e.getMessage());
    }
    return null;
  }

  /** {@code <template-name-slug>-<dataObjectAppId>.xlsx}, ASCII-safe for the Content-Disposition header. */
  static String filenameFor(ShepardTemplate template, DataObject dataObject) {
    String name = template.getName() == null || template.getName().isBlank() ? "export" : template.getName();
    String slug = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
    if (slug.isBlank()) {
      slug = "export";
    }
    return slug + "-" + dataObject.getAppId() + ".xlsx";
  }

}
