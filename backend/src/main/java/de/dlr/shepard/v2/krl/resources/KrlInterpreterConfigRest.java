package de.dlr.shepard.v2.krl.resources;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.v2.krl.entities.KrlInterpreterConfigEntity;
import de.dlr.shepard.v2.krl.io.KrlInterpreterConfigIO;
import de.dlr.shepard.v2.krl.io.KrlInterpreterConfigPatchIO;
import de.dlr.shepard.v2.krl.services.KrlInterpreterConfigService;
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
import jakarta.ws.rs.core.Response.Status;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * KRL-CONFIG-1 — admin REST surface for the KRL interpreter config singleton.
 *
 * <p>Path: {@code /v2/admin/plugins/krl/config} — follows the plugin SPI
 * convention path established by {@code JupyterConfigPluginRest}.
 *
 * <p>Exclusively {@code @RolesAllowed("instance-admin")}. All response
 * bodies are {@code application/json} except the
 * {@code application/problem+json} envelope on error paths.
 *
 * <p>No upstream {@code /shepard/api/} surface is touched.
 * PROV1a's {@code ProvenanceCaptureFilter} automatically captures the
 * PATCH as an {@code :Activity} row.
 *
 * @see KrlInterpreterConfigService
 * @see KrlInterpreterConfigEntity
 */
@Path("/v2/admin/plugins/krl/config")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
@Tag(name = "Admin")
public class KrlInterpreterConfigRest {

  /** RFC 7807 type URI for an invalid sidecar URL. */
  static final String PROBLEM_TYPE_INVALID_SIDECAR_URL =
    "/problems/krl.config.invalid-sidecar-url";

  /** RFC 7807 type URI for an invalid timeout value. */
  static final String PROBLEM_TYPE_INVALID_TIMEOUT =
    "/problems/krl.config.invalid-timeout";

  /** RFC 7807 type URI for an invalid max body size value. */
  static final String PROBLEM_TYPE_INVALID_MAX_BODY_SIZE =
    "/problems/krl.config.invalid-max-body-size";

  @Inject
  KrlInterpreterConfigService service;

  @GET
  @Operation(
    summary = "Read the current :KrlInterpreterConfig singleton.",
    description = "Returns the runtime-mutable KRL interpreter sidecar config — "
    + "`sidecarUrl` (string), `timeoutSeconds` (int), `maxBodySizeMb` (int). "
    + "All fields show the effective value: runtime override when set, "
    + "otherwise the deploy-time default from application.properties. "
    + "Gated on the instance-admin role."
  )
  @APIResponse(
    responseCode = "200",
    description = "Current KRL interpreter config (singleton).",
    content = @Content(schema = @Schema(implementation = KrlInterpreterConfigIO.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response getConfig() {
    KrlInterpreterConfigEntity cfg = service.current();
    return Response.ok(toIO(cfg)).build();
  }

  @PATCH
  @Consumes({ "application/merge-patch+json", MediaType.APPLICATION_JSON })
  @Operation(
    summary = "RFC 7396 merge-patch the :KrlInterpreterConfig singleton.",
    description = "Patchable fields: `sidecarUrl` (string), `timeoutSeconds` (int), "
    + "`maxBodySizeMb` (int). RFC 7396 semantics — absent = leave alone, "
    + "null = clear (revert to deploy-time default), value = replace. "
    + "When `sidecarUrl` is non-null it must be a non-blank string (format "
    + "not further validated; any URL the sidecar understands is accepted). "
    + "When `timeoutSeconds` is non-null it must be >= 1. "
    + "When `maxBodySizeMb` is non-null it must be >= 1. "
    + "PROV1a's ProvenanceCaptureFilter captures this PATCH as an :Activity row. "
    + "Gated on the instance-admin role."
  )
  @APIResponse(
    responseCode = "200",
    description = "Updated config returned in the same shape as GET.",
    content = @Content(schema = @Schema(implementation = KrlInterpreterConfigIO.class))
  )
  @APIResponse(
    responseCode = "400",
    description = "A field value failed validation (RFC 7807).",
    content = @Content(mediaType = "application/problem+json",
      schema = @Schema(implementation = ProblemJson.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response patchConfig(KrlInterpreterConfigPatchIO body) {
    KrlInterpreterConfigPatchIO patch = body == null ? new KrlInterpreterConfigPatchIO() : body;

    // Validate sidecarUrl when explicitly provided and non-null.
    if (patch.isSidecarUrlTouched() && patch.getSidecarUrl() != null
      && patch.getSidecarUrl().isBlank()) {
      Log.warnf("KRL-CONFIG-1: rejected PATCH — blank sidecarUrl");
      return problem(
        PROBLEM_TYPE_INVALID_SIDECAR_URL,
        "Invalid sidecarUrl",
        Status.BAD_REQUEST,
        "sidecarUrl must be non-blank when provided. Set to null to revert to the "
        + "deploy-time default (shepard.krl.sidecar.url)."
      );
    }

    // Validate timeoutSeconds when explicitly provided and non-null.
    if (patch.isTimeoutSecondsTouched() && patch.getTimeoutSeconds() != null
      && patch.getTimeoutSeconds() < 1) {
      Log.warnf("KRL-CONFIG-1: rejected PATCH — invalid timeoutSeconds %d",
        patch.getTimeoutSeconds());
      return problem(
        PROBLEM_TYPE_INVALID_TIMEOUT,
        "Invalid timeoutSeconds",
        Status.BAD_REQUEST,
        "timeoutSeconds must be >= 1 when provided. Set to null to revert to the "
        + "deploy-time default (shepard.krl.sidecar.timeout-seconds)."
      );
    }

    // Validate maxBodySizeMb when explicitly provided and non-null.
    if (patch.isMaxBodySizeMbTouched() && patch.getMaxBodySizeMb() != null
      && patch.getMaxBodySizeMb() < 1) {
      Log.warnf("KRL-CONFIG-1: rejected PATCH — invalid maxBodySizeMb %d",
        patch.getMaxBodySizeMb());
      return problem(
        PROBLEM_TYPE_INVALID_MAX_BODY_SIZE,
        "Invalid maxBodySizeMb",
        Status.BAD_REQUEST,
        "maxBodySizeMb must be >= 1 when provided. Set to null to revert to the "
        + "deploy-time default (shepard.krl.sidecar.max-body-size-mb)."
      );
    }

    // Resolve effective values per RFC 7396: absent = use current value.
    KrlInterpreterConfigEntity current = service.current();

    String effectiveSidecarUrl;
    if (patch.isSidecarUrlTouched()) {
      // Touched: take whatever was on the wire (null = clear to default).
      effectiveSidecarUrl = (patch.getSidecarUrl() != null && patch.getSidecarUrl().isBlank())
        ? null : patch.getSidecarUrl();
    } else {
      effectiveSidecarUrl = current.getSidecarUrl();
    }

    Integer effectiveTimeout;
    if (patch.isTimeoutSecondsTouched()) {
      effectiveTimeout = patch.getTimeoutSeconds();
    } else {
      effectiveTimeout = current.getTimeoutSeconds();
    }

    Integer effectiveMaxBody;
    if (patch.isMaxBodySizeMbTouched()) {
      effectiveMaxBody = patch.getMaxBodySizeMb();
    } else {
      effectiveMaxBody = current.getMaxBodySizeMb();
    }

    KrlInterpreterConfigEntity saved = service.patch(
      effectiveSidecarUrl, effectiveTimeout, effectiveMaxBody);
    return Response.ok(toIO(saved)).build();
  }

  // ─── helpers ─────────────────────────────────────────────────────────────

  private KrlInterpreterConfigIO toIO(KrlInterpreterConfigEntity cfg) {
    return KrlInterpreterConfigIO.from(
      cfg,
      service.getDefaultSidecarUrl(),
      service.getDefaultTimeoutSeconds(),
      service.getDefaultMaxBodySizeMb()
    );
  }

  private Response problem(String type, String title, Status status, String detail) {
    ProblemJson body = new ProblemJson(type, title, status.getStatusCode(), detail, null);
    return Response.status(status).type("application/problem+json").entity(body).build();
  }
}
