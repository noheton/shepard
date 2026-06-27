package de.dlr.shepard.v2.admin.ror.resources;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.v2.admin.ror.entities.InstanceRorConfig;
import de.dlr.shepard.v2.admin.ror.io.InstanceRorConfigIO;
import de.dlr.shepard.v2.admin.ror.io.InstanceRorConfigPatchIO;
import de.dlr.shepard.v2.admin.ror.services.InstanceRorConfigService;
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
 * ROR1 — admin REST surface for the instance-level Research
 * Organization Registry (ROR) config singleton.
 *
 * <p>Lives under {@code /v2/admin/instance/ror} — exclusively
 * {@code @RolesAllowed("instance-admin")}. All response bodies are
 * {@code application/json} except the {@code application/problem+json}
 * envelopes on error paths.
 *
 * <p>The endpoint is purely additive (new path on the {@code /v2/}
 * development surface); no upstream {@code /shepard/api/} surface is
 * touched. PROV1a's {@code ProvenanceCaptureFilter} automatically
 * captures the PATCH as an {@code :Activity} row (admin mutations
 * are captured by default).
 *
 * @see InstanceRorConfigService
 * @see InstanceRorConfig
 */
@Path("/v2/admin/instance/ror")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
@Tag(name = "Admin")
public class InstanceRorConfigRest {

  /** RFC 7807 type URI for the invalid-rorId path. */
  static final String PROBLEM_TYPE_INVALID_ROR_ID = "/problems/ror.config.invalid-ror-id";

  /**
   * Pattern for a valid ROR identifier suffix. Real ROR IDs are 9-char
   * Crockford base32 with a checksum, but the validation here is
   * deliberately lenient (1-9 alphanumeric chars) — canonicality
   * enforcement is deferred to a future tightening pass.
   */
  static final String ROR_ID_PATTERN = "[A-Za-z0-9]{1,9}";

  @Inject
  InstanceRorConfigService service;

  @GET
  @Operation(
    summary = "Read the current :InstanceRorConfig singleton.",
    description = "Returns the runtime-mutable ROR config — rorId, organizationName, " +
    "and the computed rorUrl (https://ror.org/<rorId>). All fields are null when " +
    "no ROR ID has been configured yet."
  )
  @APIResponse(
    responseCode = "200",
    description = "Current ROR config (singleton).",
    content = @Content(schema = @Schema(implementation = InstanceRorConfigIO.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response getConfig() {
    InstanceRorConfig cfg = service.current();
    return Response.ok(InstanceRorConfigIO.from(cfg)).build();
  }

  @PATCH
  @Operation(
    summary = "RFC 7396 merge-patch the :InstanceRorConfig singleton.",
    description = "Patchable fields: rorId, organizationName. RFC 7396 semantics — " +
    "absent = leave alone, null = clear, value = replace. rorId must be 1-9 " +
    "alphanumeric characters when non-null. The computed rorUrl is returned in " +
    "the response. PROV1a's ProvenanceCaptureFilter captures this PATCH as an " +
    ":Activity row (targetKind=InstanceRorConfig) so the audit trail records who " +
    "changed the ROR config and when."
  )
  @APIResponse(
    responseCode = "200",
    description = "Updated config returned in the same shape as GET.",
    content = @Content(schema = @Schema(implementation = InstanceRorConfigIO.class))
  )
  @APIResponse(
    responseCode = "400",
    description = "rorId is present but not 1-9 alphanumeric chars (RFC 7807).",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemJson.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response patchConfig(InstanceRorConfigPatchIO body) {
    InstanceRorConfigPatchIO patch = body == null ? new InstanceRorConfigPatchIO() : body;

    // Validate rorId when it was explicitly provided (including non-null value).
    if (patch.isRorIdTouched() && patch.getRorId() != null && !patch.getRorId().isBlank()) {
      if (!patch.getRorId().matches(ROR_ID_PATTERN)) {
        Log.warnf("ROR1: rejected PATCH — invalid rorId '%s' (must match %s)", patch.getRorId(), ROR_ID_PATTERN);
        return problem(
          PROBLEM_TYPE_INVALID_ROR_ID,
          "Invalid ROR identifier",
          Status.BAD_REQUEST,
          "rorId must be 1-9 alphanumeric characters (a-z, A-Z, 0-9). " +
          "Provide the ROR suffix only — not the full URL. " +
          "E.g. '04cvxnb49' for DLR, not 'https://ror.org/04cvxnb49'. " +
          "Omit or set to null to clear the existing value."
        );
      }
    }

    // Resolve effective values per RFC 7396: absent = use current value.
    InstanceRorConfig current = service.current();
    String effectiveRorId = patch.isRorIdTouched() ? patch.getRorId() : current.getRorId();
    String effectiveOrgName = patch.isOrganizationNameTouched() ? patch.getOrganizationName() : current.getOrganizationName();

    InstanceRorConfig saved = service.patch(effectiveRorId, effectiveOrgName);
    return Response.ok(InstanceRorConfigIO.from(saved)).build();
  }

  // ─── helpers ─────────────────────────────────────────────────────────────

  private Response problem(String type, String title, Status status, String detail) {
    ProblemJson body = new ProblemJson(type, title, status.getStatusCode(), detail, null);
    return Response.status(status).type("application/problem+json").entity(body).build();
  }
}
