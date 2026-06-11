package de.dlr.shepard.v2.admin.thermography.resources;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.v2.admin.thermography.io.ThermographyConfigIO;
import de.dlr.shepard.v2.admin.thermography.io.ThermographyConfigPatchIO;
import de.dlr.shepard.v2.admin.thermography.services.ThermographyConfigService;
import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * MFFD-NDT-ADMIN-CONFIG-1 — admin REST surface for the thermography analysis
 * config singleton at {@code /v2/admin/thermography/config}.
 *
 * <p>Exclusively {@code @RolesAllowed("instance-admin")}. All response
 * bodies are {@code application/json}.
 *
 * <p>No upstream {@code /shepard/api/} surface is touched.
 * PROV1a's {@code ProvenanceCaptureFilter} automatically captures the
 * PATCH as an {@code :Activity} row.
 *
 * @see ThermographyConfigService
 * @see de.dlr.shepard.v2.admin.thermography.entities.ThermographyConfig
 */
@Path("/v2/admin/thermography/config")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
@Tag(name = "Admin")
public class ThermographyConfigAdminRest {

  @Inject
  ThermographyConfigService service;

  @GET
  @Operation(
    summary = "Read the current :ThermographyConfig singleton.",
    description = "Returns the runtime-mutable thermography analysis config — " +
    "`thresholdC` (quality-score denominator in °C), `gridWidth` and `gridHeight` " +
    "(plate-heatmap grid dimensions). Fields are always resolved: when the singleton " +
    "carries null for a field, the deploy-time default is returned in its place. " +
    "Precedence: per-request override > runtime singleton > deploy-time default. " +
    "Gated on the instance-admin role."
  )
  @APIResponse(
    responseCode = "200",
    description = "Current thermography analysis config (singleton).",
    content = @Content(schema = @Schema(implementation = ThermographyConfigIO.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response getConfig() {
    ThermographyConfigIO io = service.getConfig();
    Log.debugf("MFFD-NDT-ADMIN-CONFIG-1: GET /v2/admin/thermography/config → %s", io);
    return Response.ok(io).build();
  }

  @PATCH
  @Consumes({ "application/merge-patch+json", MediaType.APPLICATION_JSON })
  @Operation(
    summary = "RFC 7396 merge-patch the :ThermographyConfig singleton.",
    description = "Patchable fields: `thresholdC` (double), `gridWidth` (int), " +
    "`gridHeight` (int). RFC 7396 semantics — absent = leave the current value alone; " +
    "null = revert the field to its deploy-time default; non-null value = replace. " +
    "PROV1a's ProvenanceCaptureFilter captures this PATCH as an :Activity row " +
    "(targetKind=ThermographyConfig)."
  )
  @APIResponse(
    responseCode = "200",
    description = "Updated config returned in the same shape as GET.",
    content = @Content(schema = @Schema(implementation = ThermographyConfigIO.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response patchConfig(ThermographyConfigPatchIO body) {
    ThermographyConfigPatchIO patch = body == null ? new ThermographyConfigPatchIO() : body;

    // Resolve effective values per RFC 7396: absent = use current value.
    var current = service.current();

    Double effectiveThresholdC;
    if (patch.isThresholdCTouched()) {
      // Touched: null = revert to default (stored as null in entity),
      // non-null = replace (basic positive-value validation deferred to service).
      effectiveThresholdC = patch.getThresholdC();
    } else {
      effectiveThresholdC = current.getThresholdC();
    }

    Integer effectiveGridWidth;
    if (patch.isGridWidthTouched()) {
      effectiveGridWidth = patch.getGridWidth();
    } else {
      effectiveGridWidth = current.getGridWidth();
    }

    Integer effectiveGridHeight;
    if (patch.isGridHeightTouched()) {
      effectiveGridHeight = patch.getGridHeight();
    } else {
      effectiveGridHeight = current.getGridHeight();
    }

    ThermographyConfigIO updated = service.patchConfig(
      effectiveThresholdC, effectiveGridWidth, effectiveGridHeight);
    Log.infof("MFFD-NDT-ADMIN-CONFIG-1: PATCH /v2/admin/thermography/config → %s", updated);
    return Response.ok(updated).build();
  }
}
