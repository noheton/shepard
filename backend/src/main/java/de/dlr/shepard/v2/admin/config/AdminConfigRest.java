package de.dlr.shepard.v2.admin.config;

import com.fasterxml.jackson.databind.JsonNode;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.Constants;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.util.Optional;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * V2CONV-A4 — generic registry-driven admin config endpoint.
 *
 * <p>Provides {@code GET|PATCH /v2/admin/config/{feature}} for any
 * {@link ConfigDescriptor} registered in {@link ConfigRegistry}. The per-feature
 * {@code *ConfigRest} classes remain in place (additive); this endpoint is the
 * convergence surface that admin frontends can migrate onto incrementally.
 *
 * <p>Auth: {@code instance-admin} role required on both methods.
 * ProvenanceCaptureFilter automatically captures PATCH mutations as
 * {@code :Activity} rows (same as per-feature endpoints).
 */
@Path("/v2/admin/config/{feature}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
@Tag(name = "Admin")
public class AdminConfigRest {

  static final String PROBLEM_TYPE_UNKNOWN_FEATURE = "/problems/admin.config.unknown-feature";

  @Inject
  ConfigRegistry registry;

  @GET
  @Operation(
    summary = "Read admin config for the named feature.",
    description =
      "Returns the current runtime-mutable config snapshot for the given feature " +
      "(e.g. 'sql-timeseries', 'jupyter', 'ror', 'semantic'). The JSON shape is " +
      "feature-specific; it matches the shape returned by the per-feature config " +
      "endpoint. A 404 is returned for unrecognised feature names."
  )
  @APIResponse(responseCode = "200", description = "Current config snapshot (feature-specific shape).")
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  @APIResponse(
    responseCode = "404",
    description = "No config registered for the given feature name (RFC 7807).",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemJson.class))
  )
  public Response getConfig(
    @Parameter(description = "Feature name, e.g. sql-timeseries, jupyter, ror, semantic")
    @PathParam("feature") String feature
  ) {
    Optional<ConfigDescriptor> opt = registry.find(feature);
    if (opt.isEmpty()) {
      return notFound(feature);
    }
    return Response.ok(opt.get().read()).build();
  }

  @PATCH
  @Operation(
    summary = "RFC 7396 merge-patch the admin config for the named feature.",
    description =
      "RFC 7396 semantics: absent = leave alone, null = clear to deploy-time default, " +
      "value = replace. The request body shape is feature-specific. A 404 is returned " +
      "for unrecognised feature names; 400 for invalid field values (RFC 7807)."
  )
  @APIResponse(responseCode = "200", description = "Updated config snapshot (same shape as GET).")
  @APIResponse(
    responseCode = "400",
    description = "One or more field values are invalid (RFC 7807).",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemJson.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  @APIResponse(
    responseCode = "404",
    description = "No config registered for the given feature name (RFC 7807).",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemJson.class))
  )
  public Response patchConfig(
    @Parameter(description = "Feature name, e.g. sql-timeseries, jupyter, ror, semantic")
    @PathParam("feature") String feature,
    JsonNode body
  ) {
    Optional<ConfigDescriptor> opt = registry.find(feature);
    if (opt.isEmpty()) {
      return notFound(feature);
    }
    try {
      Object result = opt.get().patch(body);
      return Response.ok(result).build();
    } catch (ConfigValidationException e) {
      return problem(e.getProblemType(), e.getTitle(), Status.BAD_REQUEST, e.getDetail());
    }
  }

  // ─── helpers ─────────────────────────────────────────────────────────────

  private Response notFound(String feature) {
    return problem(
      PROBLEM_TYPE_UNKNOWN_FEATURE,
      "Unknown feature",
      Status.NOT_FOUND,
      "No admin config registered for feature '" + feature + "'. " +
        "Registered features: " + registry.featureNames() + "."
    );
  }

  private Response problem(String type, String title, Status status, String detail) {
    ProblemJson body = new ProblemJson(type, title, status.getStatusCode(), detail, null);
    return Response.status(status).type("application/problem+json").entity(body).build();
  }
}
