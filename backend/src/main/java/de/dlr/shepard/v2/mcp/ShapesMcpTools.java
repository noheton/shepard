package de.dlr.shepard.v2.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.daos.CollectionPropertiesDAO;
import de.dlr.shepard.template.daos.ShepardTemplateDAO;
import de.dlr.shepard.template.entities.ShepardTemplate;
import de.dlr.shepard.v2.export.rep.RepExportIO;
import de.dlr.shepard.v2.export.rep.RepExportService;
import de.dlr.shepard.v2.shapes.validator.JenaShaclValidator;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MCP-COV-10 — SHAPES + REP MCP tools.
 *
 * <p>Three tools wrapping the {@code /v2/shapes/*} and
 * {@code /v2/collections/{appId}/export/regulatory-evidence} REST
 * endpoints so an MCP caller (typically an AI agent assembling a
 * candidate payload) can validate / render / package without round-
 * tripping through HTTP.
 *
 * <ul>
 *   <li>{@code shape_render} — project a {@code VIEW_RECIPE} template's
 *       channel bindings onto a focus DataObject. Mirrors the in-tree
 *       {@code DECLARED}-status projection from
 *       {@code ShapesRenderRest}; renderer-SPI dispatch is left to the
 *       REST resource (plugins are URL-bound, not MCP-bound).</li>
 *   <li>{@code shape_validate} — SHACL validation of a candidate data
 *       payload against a candidate shape graph. Stateless,
 *       no-side-effects, reads no stored data. Designed by the
 *       Digital Native review #4 specifically for MCP agents
 *       constructing JSON-LD before a write.</li>
 *   <li>{@code rep_export} — synchronous Regulatory Evidence Pack
 *       export for a Collection. TPL14 ships the build path inline; an
 *       async variant (with {@code rep_poll}) is reserved at TPL14b but
 *       not yet implemented. No {@code rep_poll} tool exists because
 *       there is nothing to poll yet.</li>
 * </ul>
 */
@ApplicationScoped
public class ShapesMcpTools {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String STATUS_DECLARED = "DECLARED";

  @Inject
  ShepardTemplateDAO templateDAO;

  @Inject
  JenaShaclValidator shaclValidator;

  @Inject
  RepExportService repExportService;

  @Inject
  CollectionPropertiesDAO collectionPropertiesDAO;

  @Inject
  PermissionsService permissionsService;

  @Inject
  AuthenticationContext authenticationContext;

  @Inject
  McpContextBridge contextBridge;

  @Inject
  McpToolSupport support;

  // ─── shape_render ───────────────────────────────────────────────────────────

  @Tool(
    name = "shape_render",
    description =
      "Project a VIEW_RECIPE template's channel bindings onto a focus DataObject.\n\n" +
      "Parameters:\n" +
      "  templateAppId    — UUID v7 of a VIEW_RECIPE template (from /v2/templates?kind=view).\n" +
      "  focusShepardId   — appId of the focus DataObject the bindings resolve against.\n\n" +
      "Returns:\n" +
      "  templateAppId, focusShepardId, renderer, channelBindings[]\n\n" +
      "Each binding row carries role / channelSelector / unit / required / status.\n" +
      "Beta (TPL2b): bindings ship with status='DECLARED'. Live channel resolution " +
      "(OK / MISSING / UNIT_MISMATCH) requires the renderer-SPI dispatch which is " +
      "URL-bound only — call POST /v2/shapes/render when live resolution is needed.\n\n" +
      "Errors:\n" +
      "  -32602 — template not found, or templateKind != VIEW_RECIPE."
  )
  public String shapeRender(
    @ToolArg(description = "UUID v7 of the VIEW_RECIPE template.") String templateAppId,
    @ToolArg(description = "appId of the focus DataObject.") String focusShepardId,
    @ToolArg(
      name = "params",
      description = "Optional render parameters (reserved for plugin-supplied renderers; ignored by the in-tree projection).",
      required = false
    ) Map<String, Object> params
  ) {
    return support.run("shape_render", () -> {
      contextBridge.bind();
      if (templateAppId == null || templateAppId.isBlank()) {
        throw McpToolSupport.invalidParams("templateAppId is required.");
      }
      if (focusShepardId == null || focusShepardId.isBlank()) {
        throw McpToolSupport.invalidParams("focusShepardId is required.");
      }
      Optional<ShepardTemplate> tplOpt = templateDAO.findByAppId(templateAppId);
      if (tplOpt.isEmpty()) {
        throw McpToolSupport.invalidParams("Template not found: " + templateAppId);
      }
      ShepardTemplate template = tplOpt.get();
      if (!"VIEW_RECIPE".equals(template.getTemplateKind())) {
        throw McpToolSupport.invalidParams(
          "shape_render only supports templateKind=VIEW_RECIPE; got " + template.getTemplateKind()
        );
      }

      List<Map<String, Object>> bindings = parseChannelBindings(template.getBody());
      String renderer = parseRenderer(template.getBody());

      Map<String, Object> response = new LinkedHashMap<>();
      response.put("templateAppId", templateAppId);
      response.put("focusShepardId", focusShepardId);
      response.put("renderer", renderer);
      response.put("channelBindings", bindings);
      // params is reserved for plugin-supplied renderers; echoed back to acknowledge.
      if (params != null && !params.isEmpty()) {
        response.put("paramsEcho", params);
      }
      return support.toJson(response);
    });
  }

  // ─── shape_validate ─────────────────────────────────────────────────────────

  @Tool(
    name = "shape_validate",
    description =
      "Validate a candidate RDF payload against a SHACL shape graph. Stateless and " +
      "read-only — reads no stored data.\n\n" +
      "Parameters:\n" +
      "  dataTurtle  — Turtle-serialised data graph.\n" +
      "  shapeTurtle — Turtle-serialised SHACL shape graph.\n\n" +
      "Returns: {conforms, parseError, engineError, findings[]}\n" +
      "Each finding: {focusNode, resultPath, value, severity, message}\n\n" +
      "Malformed Turtle in either input yields conforms=false with a non-null " +
      "parseError, NOT an MCP error — so an agent can branch on the validation " +
      "report shape rather than handling an exception. This mirrors the REST " +
      "endpoint shape (see ShapesValidateRest)."
  )
  public String shapeValidate(
    @ToolArg(description = "Turtle-serialised candidate data graph.") String dataTurtle,
    @ToolArg(description = "Turtle-serialised SHACL shape graph.") String shapeTurtle
  ) {
    return support.run("shape_validate", () -> {
      contextBridge.bind();
      JenaShaclValidator.Report report = shaclValidator.validate(dataTurtle, shapeTurtle);

      Map<String, Object> body = new LinkedHashMap<>();
      body.put("conforms", report.conforms());
      body.put("parseError", report.parseError());
      body.put("engineError", report.engineError());

      List<Map<String, Object>> findings = new ArrayList<>();
      if (report.findings() != null) {
        for (JenaShaclValidator.Finding f : report.findings()) {
          Map<String, Object> row = new LinkedHashMap<>();
          row.put("focusNode", f.focusNode());
          row.put("resultPath", f.resultPath());
          row.put("value", f.value());
          row.put("severity", f.severity());
          row.put("message", f.message());
          findings.add(row);
        }
      }
      body.put("findings", findings);
      return support.toJson(body);
    });
  }

  // ─── rep_export ─────────────────────────────────────────────────────────────

  @Tool(
    name = "rep_export",
    description =
      "Build a Regulatory Evidence Pack (REP) for a Collection — a BagIt 1.0 bag " +
      "containing RO-Crate 1.1 metadata + a PROV-O provenance graph, returned " +
      "inline (Base64) for bags ≤ 1 MB.\n\n" +
      "Parameters:\n" +
      "  collectionAppId — UUID v7 of the Collection to export.\n" +
      "  profile         — reserved for future profile selection (ISO-AP242, " +
      "                    EN-9100, DCAT3); currently ignored. TPL14b will wire it.\n\n" +
      "Returns:\n" +
      "  exportId        — UUID v4 stable identifier for this artefact.\n" +
      "  status          — always 'READY' in the synchronous build path.\n" +
      "  bagBase64       — inline bag (null when bag > 1 MB).\n" +
      "  downloadUrl     — TPL14b out-of-band delivery (currently null).\n" +
      "  fileName        — suggested filename.\n" +
      "  exportedAt, dataObjectCount, bagSizeBytes — metadata.\n\n" +
      "Auth: Read on the Collection. Forbidden → -32002.\n\n" +
      "Note: TPL14 is synchronous — there is no separate `rep_poll` tool because " +
      "the build completes inline. An async variant + poll handle ships at TPL14b."
  )
  public String repExport(
    @ToolArg(description = "UUID v7 of the Collection to export.") String collectionAppId,
    @ToolArg(
      name = "profile",
      description = "Reserved for future profile selection (ISO-AP242 / EN-9100 / DCAT3); currently ignored.",
      required = false
    ) String profile
  ) {
    return support.run("rep_export", () -> {
      contextBridge.bind();
      if (collectionAppId == null || collectionAppId.isBlank()) {
        throw McpToolSupport.invalidParams("collectionAppId is required.");
      }
      String caller = authenticationContext.getCurrentUserName();
      if (caller == null || caller.isBlank()) {
        throw new jakarta.ws.rs.NotAuthorizedException("Authentication required.");
      }
      Optional<Long> ogmId = collectionPropertiesDAO.findCollectionIdByAppId(collectionAppId);
      if (ogmId.isEmpty()) {
        throw McpToolSupport.invalidParams("No Collection with appId " + collectionAppId);
      }
      if (!permissionsService.isAccessTypeAllowedForUser(ogmId.get(), AccessType.Read, caller, 0L)) {
        throw new ForbiddenException("Caller lacks Read on collection " + collectionAppId);
      }

      RepExportIO io = repExportService.buildExport(collectionAppId, caller);

      Map<String, Object> body = new LinkedHashMap<>();
      body.put("exportId", io.getExportId());
      body.put("status", io.getStatus());
      body.put("bagBase64", io.getBagBase64());
      body.put("downloadUrl", io.getDownloadUrl());
      body.put("fileName", io.getFileName());
      body.put("exportedAt", io.getExportedAt() == null ? null : io.getExportedAt().toString());
      body.put("dataObjectCount", io.getDataObjectCount());
      body.put("bagSizeBytes", io.getBagSizeBytes());
      if (profile != null && !profile.isBlank()) {
        body.put("profileEcho", profile);
      }
      return support.toJson(body);
    });
  }

  // ─── helpers ────────────────────────────────────────────────────────────────

  /** Mirrors {@code ShapesRenderRest#parseRenderer}. */
  static String parseRenderer(String bodyJson) {
    if (bodyJson == null) return null;
    try {
      JsonNode root = MAPPER.readTree(bodyJson);
      JsonNode r = root.path("renderer");
      return r.isTextual() ? r.asText() : null;
    } catch (JsonProcessingException e) {
      return null;
    }
  }

  /** Mirrors {@code ShapesRenderRest#parseChannelBindings} (in-tree DECLARED projection). */
  static List<Map<String, Object>> parseChannelBindings(String bodyJson) {
    List<Map<String, Object>> out = new ArrayList<>();
    if (bodyJson == null) return out;
    try {
      JsonNode root = MAPPER.readTree(bodyJson);
      JsonNode bindingsNode = root.path("channelBindings");
      if (!bindingsNode.isArray()) return out;
      for (JsonNode b : bindingsNode) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("role", b.path("role").asText(null));
        row.put("channelSelector", b.path("channelSelector").asText(null));
        JsonNode unitNode = b.path("unit");
        row.put("unit", unitNode.isTextual() ? unitNode.asText() : null);
        row.put("required", b.path("required").asBoolean(true));
        row.put("status", STATUS_DECLARED);
        out.add(row);
      }
    } catch (JsonProcessingException e) {
      // malformed body — return what we parsed so far
    }
    return out;
  }
}
