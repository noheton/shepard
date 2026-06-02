package de.dlr.shepard.v2.admin.krl.resources;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.v2.admin.krl.entities.KrlInterpreterConfigSingleton;
import de.dlr.shepard.v2.admin.krl.io.KrlInterpreterConfigIO;
import de.dlr.shepard.v2.admin.krl.io.KrlInterpreterConfigPatchIO;
import de.dlr.shepard.v2.admin.krl.services.KrlInterpreterConfigService;
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
 * KRL-CONFIG-1 — admin REST surface for the KRL interpreter config singleton,
 * at {@code /v2/admin/krl/config}.
 *
 * <p>Exclusively {@code @RolesAllowed("instance-admin")}. All response
 * bodies are {@code application/json} except error paths.
 *
 * <p>No upstream {@code /shepard/api/} surface is touched.
 * PROV1a's {@code ProvenanceCaptureFilter} automatically captures the
 * PATCH as an {@code :Activity} row.
 *
 * <p>The canonical endpoint shape mirrors {@link
 * de.dlr.shepard.v2.admin.jupyter.resources.JupyterConfigPluginRest}
 * (J1e-PR-07) with additional integer fields for timeout and body-size.
 *
 * @see KrlInterpreterConfigService
 * @see KrlInterpreterConfigSingleton
 */
@Path("/v2/admin/krl/config")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
@Tag(name = "Admin")
public class KrlAdminConfigRest {

  @Inject KrlInterpreterConfigService service;

  @GET
  @Operation(
      summary = "Read the current :KrlInterpreterConfigSingleton.",
      description =
          "Returns the runtime-mutable KRL interpreter config — `enabled` (boolean), "
              + "`sidecarUrl` (string, nullable), `timeoutSeconds` (int), `maxBodySizeMb` (int). "
              + "All values reflect the effective setting: runtime singleton wins over deploy-time "
              + "`shepard.krl.sidecar.*` defaults. Gated on the instance-admin role.")
  @APIResponse(
      responseCode = "200",
      description = "Current KRL interpreter config (singleton).",
      content = @Content(schema = @Schema(implementation = KrlInterpreterConfigIO.class)))
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response getConfig() {
    KrlInterpreterConfigSingleton cfg = service.current();
    return Response.ok(toIO(cfg)).build();
  }

  @PATCH
  @Consumes({"application/merge-patch+json", MediaType.APPLICATION_JSON})
  @Operation(
      summary = "RFC 7396 merge-patch the :KrlInterpreterConfigSingleton.",
      description =
          "Patchable fields: `enabled` (boolean), `sidecarUrl` (string), "
              + "`timeoutSeconds` (int), `maxBodySizeMb` (int). "
              + "RFC 7396 semantics — absent = leave alone, null on `sidecarUrl` = clear "
              + "(revert to deploy-time default), zero on integer fields = revert to default, "
              + "non-null/non-zero value = replace. An explicit null on `enabled` is treated "
              + "as 'leave alone' since the entity field is a primitive boolean. "
              + "PROV1a's ProvenanceCaptureFilter captures this PATCH as an "
              + ":Activity row (targetKind=KrlInterpreterConfig).")
  @APIResponse(
      responseCode = "200",
      description = "Updated config returned in the same shape as GET.",
      content = @Content(schema = @Schema(implementation = KrlInterpreterConfigIO.class)))
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response patchConfig(KrlInterpreterConfigPatchIO body) {
    KrlInterpreterConfigPatchIO patch = body == null ? new KrlInterpreterConfigPatchIO() : body;

    // Resolve effective values per RFC 7396: absent = use current value.
    KrlInterpreterConfigSingleton current = service.current();

    // enabled: absent or explicit null = leave alone (primitive boolean).
    Boolean effectiveEnabled = null; // null = don't change in service.patch()
    if (patch.isEnabledTouched() && patch.getEnabled() != null) {
      effectiveEnabled = patch.getEnabled();
    }

    // sidecarUrl: touched + null = clear (revert to deploy-time default).
    String effectiveSidecarUrl;
    if (patch.isSidecarUrlTouched()) {
      effectiveSidecarUrl =
          (patch.getSidecarUrl() == null || patch.getSidecarUrl().isBlank())
              ? null
              : patch.getSidecarUrl();
    } else {
      effectiveSidecarUrl = current.getSidecarUrl();
    }

    // timeoutSeconds: touched → take wire value (0 / null = revert to default).
    Integer effectiveTimeout;
    if (patch.isTimeoutSecondsTouched()) {
      effectiveTimeout = patch.getTimeoutSeconds();
    } else {
      effectiveTimeout = current.getTimeoutSeconds() > 0 ? current.getTimeoutSeconds() : null;
    }

    // maxBodySizeMb: touched → take wire value (0 / null = revert to default).
    Integer effectiveMaxBody;
    if (patch.isMaxBodySizeMbTouched()) {
      effectiveMaxBody = patch.getMaxBodySizeMb();
    } else {
      effectiveMaxBody = current.getMaxBodySizeMb() > 0 ? current.getMaxBodySizeMb() : null;
    }

    KrlInterpreterConfigSingleton saved =
        service.patch(effectiveEnabled, effectiveSidecarUrl, effectiveTimeout, effectiveMaxBody);
    return Response.ok(toIO(saved)).build();
  }

  // ─── helpers ─────────────────────────────────────────────────────────────

  private KrlInterpreterConfigIO toIO(KrlInterpreterConfigSingleton cfg) {
    return KrlInterpreterConfigIO.from(
        cfg,
        service.getDefaultSidecarUrl(),
        service.getDefaultTimeoutSeconds(),
        service.getDefaultMaxBodySizeMb());
  }
}
