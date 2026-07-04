package de.dlr.shepard.v2.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.spi.transform.TransformException;
import de.dlr.shepard.spi.transform.TransformExecutor;
import de.dlr.shepard.spi.transform.TransformExecutorRegistry;
import de.dlr.shepard.spi.transform.TransformRequest;
import de.dlr.shepard.spi.transform.TransformResult;
import de.dlr.shepard.template.daos.ShepardTemplateDAO;
import de.dlr.shepard.template.entities.ShepardTemplate;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MCP-MAPPING-RECIPE-1 — MAPPING_RECIPE MCP tools.
 *
 * <p>Two tools wrapping the {@code /v2/mappings/*} REST surface so an MCP
 * caller (typically an AI agent) can discover and run MAPPING_RECIPE templates
 * without HTTP round-trips:
 *
 * <ul>
 *   <li>{@code mapping_list} — list all non-retired MAPPING_RECIPE templates
 *       with their appId, name, shapeIri, and a flag indicating whether the
 *       executor plugin is installed. Replaces the per-vertical
 *       {@code scene_graph_list} tool deleted in V2CONV-B4.</li>
 *   <li>{@code mapping_materialize} — bind input reference appIds and run the
 *       registered {@link TransformExecutor}, returning either a derived
 *       reference appId (REFERENCE) or a view-model (VIEW). Replaces the
 *       per-vertical {@code materialize_scene_graph} tool deleted in V2CONV-B4.
 *       Covers both scene-graph (B4) and KRL trajectory (B5) verticals.</li>
 * </ul>
 */
@ApplicationScoped
public class MappingsMcpTools {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String MAPPING_RECIPE_KIND = "MAPPING_RECIPE";

  @Inject
  ShepardTemplateDAO templateDAO;

  @Inject
  TransformExecutorRegistry executorRegistry;

  @Inject
  McpContextBridge contextBridge;

  @Inject
  McpToolSupport support;

  // ─── mapping_list ──────────────────────────────────────────────────────────

  @Tool(
    name = "mapping_list",
    description =
      "List all non-retired MAPPING_RECIPE templates (scene-graph plays, KRL trajectory " +
      "interpreters, and any other transform-recipe shapes installed by plugins).\n\n" +
      "No parameters required.\n\n" +
      "Returns: array of\n" +
      "  appId             — UUID v7, used as templateAppId in mapping_materialize.\n" +
      "  name              — human-readable template name.\n" +
      "  description       — nullable description.\n" +
      "  mappingRecipeShape— the SHACL shape IRI that identifies which TransformExecutor\n" +
      "                      runs this recipe (null when the body is not yet configured).\n" +
      "  executorAvailable — true when the recipe's TransformExecutor plugin is installed\n" +
      "                      and can materialize; false means the plugin is absent.\n\n" +
      "Use mapping_materialize to run a recipe once you know its appId and the input " +
      "reference appIds to bind."
  )
  public String mappingList() {
    return support.run("mapping_list", () -> {
      contextBridge.bind();
      List<ShepardTemplate> templates = templateDAO.list(MAPPING_RECIPE_KIND, false);
      List<Map<String, Object>> rows = new ArrayList<>();
      for (ShepardTemplate t : templates) {
        String shapeIri = parseMappingRecipeShape(t.getBody());
        boolean executorAvailable = shapeIri != null &&
          executorRegistry != null &&
          executorRegistry.resolve(shapeIri).isPresent();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("appId", t.getAppId());
        row.put("name", t.getName());
        row.put("description", t.getDescription());
        row.put("mappingRecipeShape", shapeIri);
        row.put("executorAvailable", executorAvailable);
        rows.add(row);
      }
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("count", rows.size());
      result.put("recipes", rows);
      return support.toJson(result);
    });
  }

  // ─── mapping_materialize ───────────────────────────────────────────────────

  @Tool(
    name = "mapping_materialize",
    description =
      "Run a MAPPING_RECIPE template — bind input reference appIds and invoke the " +
      "registered TransformExecutor to produce a derived output.\n\n" +
      "Parameters:\n" +
      "  templateAppId          — UUID v7 of the MAPPING_RECIPE template " +
      "(from mapping_list).\n" +
      "  inputReferenceAppIds   — JSON object mapping binding-role names declared by " +
      "the recipe's shape to their input reference appIds. Example:\n" +
      "    {\"urdfFileAppId\":\"018f...\",\"jointTimeseriesAppId\":\"018f...\"}\n" +
      "  Pass an empty object {} when the recipe has no required inputs.\n\n" +
      "Returns on success:\n" +
      "  outputKind             — REFERENCE | VIEW\n" +
      "  derivedReferenceAppId  — appId of the produced reference (REFERENCE output).\n" +
      "  viewModel              — projection envelope (VIEW output).\n" +
      "  executor               — name of the TransformExecutor that ran.\n\n" +
      "Errors:\n" +
      "  -32602 — template not found, not a MAPPING_RECIPE, no shape IRI configured, " +
      "or the executor plugin is not installed (error message says which).\n" +
      "  -32603 — executor threw an unexpected internal error."
  )
  public String mappingMaterialize(
    @ToolArg(description = "UUID v7 of the MAPPING_RECIPE template to run.") String templateAppId,
    @ToolArg(
      name = "inputReferenceAppIds",
      description = "Binding-role → input reference appId map. Keys match the roles the recipe's shape declares.",
      required = false
    ) Map<String, String> inputReferenceAppIds
  ) {
    return support.run("mapping_materialize", () -> {
      contextBridge.bind();
      if (templateAppId == null || templateAppId.isBlank()) {
        throw McpToolSupport.invalidParams("templateAppId is required.");
      }
      Optional<ShepardTemplate> tplOpt = templateDAO.findByAppId(templateAppId);
      if (tplOpt.isEmpty()) {
        throw McpToolSupport.invalidParams("No MAPPING_RECIPE template with appId " + templateAppId);
      }
      ShepardTemplate template = tplOpt.get();
      if (!MAPPING_RECIPE_KIND.equals(template.getTemplateKind())) {
        throw McpToolSupport.invalidParams(
          "mapping_materialize requires a MAPPING_RECIPE template; got templateKind=" +
          template.getTemplateKind() +
          ". Use mapping_list to discover available recipes."
        );
      }
      String shapeIri = parseMappingRecipeShape(template.getBody());
      if (shapeIri == null) {
        throw McpToolSupport.invalidParams(
          "MAPPING_RECIPE template '" + template.getName() +
          "' has no mappingRecipeShape IRI configured — cannot resolve a TransformExecutor. " +
          "Edit the template body to add a mappingRecipeShape field."
        );
      }
      if (executorRegistry == null) {
        throw McpToolSupport.invalidParams(
          "TransformExecutorRegistry is not available (plugin infrastructure not initialised)."
        );
      }
      Optional<TransformExecutor> executorOpt = executorRegistry.resolve(shapeIri);
      if (executorOpt.isEmpty()) {
        throw McpToolSupport.invalidParams(
          "No TransformExecutor is registered for shape <" + shapeIri + ">. " +
          "The plugin that implements this recipe may not be installed or enabled. " +
          "Check /v2/admin/plugins and ensure the relevant plugin is active."
        );
      }
      TransformExecutor executor = executorOpt.get();
      Map<String, String> inputs = inputReferenceAppIds != null
        ? new java.util.HashMap<>(inputReferenceAppIds)
        : Map.of();

      TransformRequest req = new TransformRequest(templateAppId, shapeIri, inputs, null, template.getBody());
      try {
        TransformResult result = executor.materialize(req);
        if (result == null) {
          throw new IllegalStateException(
            "TransformExecutor '" + executor.name() + "' returned null for shape <" + shapeIri + ">"
          );
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("templateAppId", templateAppId);
        body.put("outputKind", result.kind().name());
        body.put("derivedReferenceAppId", result.derivedReferenceAppId());
        body.put("viewModel", result.viewModel());
        body.put("executor", result.executorName());
        return support.toJson(body);
      } catch (TransformException ex) {
        throw McpToolSupport.invalidParams(
          "Transform failed [" + (ex.code() != null ? ex.code() : "transform.unknown-error") +
          "]: " + ex.getMessage()
        );
      }
    });
  }

  // ─── helper ────────────────────────────────────────────────────────────────

  /** Read the template body's {@code mappingRecipeShape} IRI; null when absent/blank/unparseable. */
  static String parseMappingRecipeShape(String bodyJson) {
    if (bodyJson == null) return null;
    try {
      JsonNode root = MAPPER.readTree(bodyJson);
      JsonNode s = root.path("mappingRecipeShape");
      if (!s.isTextual()) return null;
      String iri = s.asText();
      return (iri == null || iri.isBlank()) ? null : iri;
    } catch (JsonProcessingException e) {
      return null;
    }
  }
}
