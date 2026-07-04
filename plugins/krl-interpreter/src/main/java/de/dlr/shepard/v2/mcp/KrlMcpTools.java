package de.dlr.shepard.v2.mcp;

import de.dlr.shepard.v2.transform.krl.KrlTrajectoryTransformExecutor;
import de.dlr.shepard.v2.transform.krl.services.KrlTrajectoryParams;
import de.dlr.shepard.v2.transform.krl.services.KrlTrajectoryService;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP-COV-09-KRL — MCP tools for the KRL trajectory interpret capability.
 *
 * <p>Two tools:
 *
 * <ul>
 *   <li>{@code krl_interpret} — run a KRL {@code .src/.krl} program against a URDF
 *       robot model, persist the resulting joint trajectory as a new
 *       {@link de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference},
 *       and return the derived reference's appId.</li>
 *   <li>{@code krl_capabilities} — return the MAPPING_RECIPE shape IRI and the
 *       binding roles needed to run the same interpretation via the generic
 *       {@code mapping_materialize} MCP tool.</li>
 * </ul>
 *
 * <p>This is a plugin-local CDI bean (plugin-first rule). It drives the same
 * {@link KrlTrajectoryService} as the generic
 * {@link KrlTrajectoryTransformExecutor} (MAPPING_RECIPE path). Callers who have
 * already configured a MAPPING_RECIPE template can use the generic
 * {@code mapping_materialize} tool; this tool exists so an agent can perform a
 * one-shot KRL interpret without first creating a template.
 *
 * <p>Unlike {@link KrlTrajectoryTransformExecutor}, which is a ServiceLoader POJO
 * that cannot receive CDI injections and resolves the service lazily via
 * {@code CDI.current()}, this bean is {@code @ApplicationScoped} and wires the
 * service at startup via normal CDI injection.
 */
@ApplicationScoped
public class KrlMcpTools {

  @Inject KrlTrajectoryService krlTrajectoryService;
  @Inject McpContextBridge contextBridge;
  @Inject McpToolSupport support;

  // ─── krl_interpret ──────────────────────────────────────────────────────────

  @Tool(
    name = "krl_interpret",
    description =
      "Interpret a KRL robot program (.src / .krl file) against a URDF robot model " +
      "and persist the resulting joint trajectory as a new TimeseriesReference " +
      "under a target DataObject.\n\n" +
      "Required parameters:\n" +
      "  srcFileRefAppId           — appId of the FileReference pointing to the KRL " +
      ".src / .krl source file.\n" +
      "  urdfFileRefAppId          — appId of the FileReference pointing to the URDF " +
      "robot model file.\n" +
      "  targetDataObjectAppId     — appId of the DataObject the derived " +
      "TimeseriesReference will be attached to.\n" +
      "  timeseriesContainerAppId  — appId of the TimeseriesContainer the joint " +
      "trajectory channels will be written into.\n\n" +
      "Optional parameters:\n" +
      "  datFileRefAppIds          — list of companion .dat FileReference appIds " +
      "(auxiliary data the KRL program references). Defaults to empty.\n" +
      "  templateAppId             — appId of a MAPPING_RECIPE template to echo in " +
      "the provenance Activity. Pass null for one-shot invocations.\n\n" +
      "Returns:\n" +
      "  derivedReferenceAppId     — appId of the newly created TimeseriesReference " +
      "carrying the joint trajectory data.\n\n" +
      "Errors:\n" +
      "  -32602 — missing required parameter, bad appId, container not found, or " +
      "the KRL sidecar rejected the input.\n" +
      "  -32603 — sidecar unreachable or internal server error. " +
      "Bring up the sidecar with: COMPOSE_PROFILES=krl-interpreter docker compose up -d\n\n" +
      "Tip: call krl_capabilities() to discover the MAPPING_RECIPE shape IRI and " +
      "binding roles if you want to build a reusable recipe template instead."
  )
  public String krlInterpret(
    @ToolArg(
      name = "srcFileRefAppId",
      description = "UUID v7 appId of the KRL .src / .krl FileReference."
    ) String srcFileRefAppId,
    @ToolArg(
      name = "urdfFileRefAppId",
      description = "UUID v7 appId of the URDF robot model FileReference."
    ) String urdfFileRefAppId,
    @ToolArg(
      name = "targetDataObjectAppId",
      description = "UUID v7 appId of the DataObject the derived TimeseriesReference attaches to."
    ) String targetDataObjectAppId,
    @ToolArg(
      name = "timeseriesContainerAppId",
      description = "UUID v7 appId of the TimeseriesContainer for the joint trajectory channels."
    ) String timeseriesContainerAppId,
    @ToolArg(
      name = "datFileRefAppIds",
      description = "Optional list of companion .dat FileReference appIds.",
      required = false
    ) List<String> datFileRefAppIds,
    @ToolArg(
      name = "templateAppId",
      description = "Optional MAPPING_RECIPE template appId echoed in provenance. Null for one-shot runs.",
      required = false
    ) String templateAppId
  ) {
    return support.run("krl_interpret", () -> {
      contextBridge.bind();
      if (srcFileRefAppId == null || srcFileRefAppId.isBlank()) {
        throw McpToolSupport.invalidParams(
          "srcFileRefAppId is required (UUID v7 appId of the .src/.krl FileReference).");
      }
      if (urdfFileRefAppId == null || urdfFileRefAppId.isBlank()) {
        throw McpToolSupport.invalidParams(
          "urdfFileRefAppId is required (UUID v7 appId of the URDF FileReference).");
      }
      if (targetDataObjectAppId == null || targetDataObjectAppId.isBlank()) {
        throw McpToolSupport.invalidParams(
          "targetDataObjectAppId is required (UUID v7 appId of the target DataObject).");
      }
      if (timeseriesContainerAppId == null || timeseriesContainerAppId.isBlank()) {
        throw McpToolSupport.invalidParams(
          "timeseriesContainerAppId is required (UUID v7 appId of the TimeseriesContainer).");
      }

      KrlTrajectoryParams params = new KrlTrajectoryParams(
        templateAppId,
        srcFileRefAppId,
        urdfFileRefAppId,
        targetDataObjectAppId,
        timeseriesContainerAppId,
        datFileRefAppIds != null ? datFileRefAppIds : List.of()
      );

      String derivedRefAppId = krlTrajectoryService.interpret(params, null, null);

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("derivedReferenceAppId", derivedRefAppId);
      return support.toJson(result);
    });
  }

  // ─── krl_capabilities ───────────────────────────────────────────────────────

  @Tool(
    name = "krl_capabilities",
    description =
      "Return metadata describing the KRL interpreter plugin's MAPPING_RECIPE shape — " +
      "the shape IRI and the binding roles needed to run a KRL interpret via the " +
      "generic mapping_materialize tool.\n\n" +
      "No parameters required.\n\n" +
      "Returns:\n" +
      "  shapeIri         — the SHACL shape IRI for the KRL trajectory transform. " +
      "Use this as mappingRecipeShape in a MAPPING_RECIPE template body.\n" +
      "  bindingRoles     — the input-reference binding roles the TransformExecutor " +
      "reads. Supply these as the inputReferenceAppIds map in mapping_materialize.\n" +
      "  templateBodyFields — JSON fields the executor reads from the MAPPING_RECIPE " +
      "template body (complement to the binding roles).\n\n" +
      "Use this tool to construct a reusable MAPPING_RECIPE template (via " +
      "mapping_materialize) rather than calling krl_interpret for every run."
  )
  public String krlCapabilities() {
    return support.run("krl_capabilities", () -> {
      Map<String, Object> bindingRoles = new LinkedHashMap<>();
      bindingRoles.put(
        KrlTrajectoryTransformExecutor.ROLE_SRC_FILE,
        "appId of the KRL .src/.krl FileReference"
      );
      bindingRoles.put(
        KrlTrajectoryTransformExecutor.ROLE_URDF_FILE,
        "appId of the URDF robot model FileReference"
      );

      Map<String, Object> templateBodyFields = new LinkedHashMap<>();
      templateBodyFields.put("targetDataObjectAppId",
        "required — appId of the DataObject the derived TimeseriesReference attaches to");
      templateBodyFields.put("timeseriesContainerAppId",
        "required — appId of the TimeseriesContainer for trajectory channels");
      templateBodyFields.put("srcFileReferenceAppId",
        "fallback appId when the " + KrlTrajectoryTransformExecutor.ROLE_SRC_FILE +
        " binding role is not supplied in inputReferenceAppIds");
      templateBodyFields.put("urdfFileReferenceAppId",
        "fallback appId when the " + KrlTrajectoryTransformExecutor.ROLE_URDF_FILE +
        " binding role is not supplied in inputReferenceAppIds");
      templateBodyFields.put("datFileReferenceAppIds",
        "optional — JSON array of companion .dat FileReference appIds");
      templateBodyFields.put("aiAgent",
        "optional — AI agent identifier for EU AI Act Art. 50 provenance disclosure");

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("shapeIri", KrlTrajectoryTransformExecutor.KRL_TRAJECTORY_SHAPE_IRI);
      result.put("bindingRoles", bindingRoles);
      result.put("templateBodyFields", templateBodyFields);
      return support.toJson(result);
    });
  }
}
