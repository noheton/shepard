package de.dlr.shepard.v2.admin.sqltimeseries.resources;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.v2.admin.sqltimeseries.entities.SqlTimeseriesConfig;
import de.dlr.shepard.v2.admin.sqltimeseries.io.SqlTimeseriesConfigIO;
import de.dlr.shepard.v2.admin.sqltimeseries.io.SqlTimeseriesConfigPatchIO;
import de.dlr.shepard.v2.admin.sqltimeseries.services.SqlTimeseriesConfigService;
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
import java.time.Duration;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * P10c — admin REST surface for the SQL timeseries config singleton.
 *
 * <p>Lives under {@code /v2/admin/sql-timeseries/config} — exclusively
 * {@code @RolesAllowed("instance-admin")}. All response bodies are
 * {@code application/json} except the {@code application/problem+json}
 * envelopes on error paths.
 *
 * <p>The endpoint is purely additive (new path on the {@code /v2/}
 * development surface); no upstream {@code /shepard/api/} surface is
 * touched. PROV1a's {@code ProvenanceCaptureFilter} automatically
 * captures the PATCH as an {@code :Activity} row (admin mutations are
 * captured by default).
 *
 * @see SqlTimeseriesConfigService
 * @see SqlTimeseriesConfig
 */
@Path("/v2/admin/sql-timeseries/config")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
@Tag(name = "Admin")
public class SqlTimeseriesConfigRest {

  /** RFC 7807 type URI for invalid maxRows. */
  static final String PROBLEM_TYPE_INVALID_MAX_ROWS = "/problems/sql-timeseries.config.invalid-max-rows";

  /** RFC 7807 type URI for invalid maxDuration. */
  static final String PROBLEM_TYPE_INVALID_MAX_DURATION = "/problems/sql-timeseries.config.invalid-max-duration";

  @Inject
  SqlTimeseriesConfigService service;

  @GET
  @Operation(
    summary = "Read the current :SqlTimeseriesConfig singleton.",
    description = "Returns the runtime-mutable SQL timeseries config — maxRows and maxDuration " +
    "(ISO-8601). Values are always resolved: if a field has been cleared (set to null via PATCH), " +
    "the deploy-time default is returned. Gated on the instance-admin role."
  )
  @APIResponse(
    responseCode = "200",
    description = "Current SQL timeseries config (singleton).",
    content = @Content(schema = @Schema(implementation = SqlTimeseriesConfigIO.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response getConfig() {
    SqlTimeseriesConfig cfg = service.current();
    return Response.ok(toIO(cfg)).build();
  }

  @PATCH
  @Operation(
    summary = "RFC 7396 merge-patch the :SqlTimeseriesConfig singleton.",
    description = "Patchable fields: maxRows (Long, must be > 0), maxDuration (ISO-8601 duration string). " +
    "RFC 7396 semantics — absent = leave alone, null = clear (revert to deploy-time default), " +
    "value = replace. PROV1a's ProvenanceCaptureFilter captures this PATCH as an :Activity row " +
    "(targetKind=SqlTimeseriesConfig) so the audit trail records who changed the config and when."
  )
  @APIResponse(
    responseCode = "200",
    description = "Updated config returned in the same shape as GET.",
    content = @Content(schema = @Schema(implementation = SqlTimeseriesConfigIO.class))
  )
  @APIResponse(
    responseCode = "400",
    description = "maxRows is <= 0, or maxDuration is not a valid ISO-8601 duration (RFC 7807).",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemJson.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response patchConfig(SqlTimeseriesConfigPatchIO body) {
    SqlTimeseriesConfigPatchIO patch = body == null ? new SqlTimeseriesConfigPatchIO() : body;

    // Validate maxRows when it was explicitly provided and is non-null.
    if (patch.isMaxRowsTouched() && patch.getMaxRows() != null) {
      if (patch.getMaxRows() <= 0) {
        Log.warnf("P10c: rejected PATCH — invalid maxRows %d (must be > 0)", patch.getMaxRows());
        return problem(
          PROBLEM_TYPE_INVALID_MAX_ROWS,
          "Invalid maxRows",
          Status.BAD_REQUEST,
          "maxRows must be greater than 0. Set to null to revert to the deploy-time default " +
          "(shepard.timeseries.sql.max-rows). Current deploy-time default: " + service.getDefaultMaxRows() + "."
        );
      }
    }

    // Validate maxDuration when it was explicitly provided and is non-null.
    if (patch.isMaxDurationTouched() && patch.getMaxDurationIso() != null) {
      try {
        Duration.parse(patch.getMaxDurationIso());
      } catch (Exception e) {
        Log.warnf("P10c: rejected PATCH — invalid maxDuration '%s' (not ISO-8601)", patch.getMaxDurationIso());
        return problem(
          PROBLEM_TYPE_INVALID_MAX_DURATION,
          "Invalid maxDuration",
          Status.BAD_REQUEST,
          "maxDuration must be a valid ISO-8601 duration (e.g. 'PT60S', 'PT2M30S', 'PT1H'). " +
          "Set to null to revert to the deploy-time default " +
          "(shepard.timeseries.sql.max-duration). Current deploy-time default: " +
          service.getDefaultMaxDuration() + "."
        );
      }
    }

    // Resolve effective values per RFC 7396: absent = use current value.
    SqlTimeseriesConfig current = service.current();
    Long effectiveMaxRows = patch.isMaxRowsTouched() ? patch.getMaxRows() : current.getMaxRows();
    String effectiveMaxDuration = patch.isMaxDurationTouched() ? patch.getMaxDurationIso() : current.getMaxDurationIso();

    SqlTimeseriesConfig saved = service.patch(effectiveMaxRows, effectiveMaxDuration);
    return Response.ok(toIO(saved)).build();
  }

  // ─── helpers ─────────────────────────────────────────────────────────────

  private SqlTimeseriesConfigIO toIO(SqlTimeseriesConfig cfg) {
    return SqlTimeseriesConfigIO.from(cfg, service.getDefaultMaxRows(), service.getDefaultMaxDuration());
  }

  private Response problem(String type, String title, Status status, String detail) {
    ProblemJson body = new ProblemJson(type, title, status.getStatusCode(), detail, null);
    return Response.status(status).type("application/problem+json").entity(body).build();
  }
}
