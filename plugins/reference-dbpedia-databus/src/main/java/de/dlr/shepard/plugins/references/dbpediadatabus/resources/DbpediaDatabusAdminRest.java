package de.dlr.shepard.plugins.references.dbpediadatabus.resources;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.plugins.references.dbpediadatabus.clients.DatabusHttpClient;
import de.dlr.shepard.plugins.references.dbpediadatabus.entities.DbpediaDatabusConfig;
import de.dlr.shepard.plugins.references.dbpediadatabus.io.DbpediaDatabusConfigIO;
import de.dlr.shepard.plugins.references.dbpediadatabus.io.DbpediaDatabusConfigPatchIO;
import de.dlr.shepard.plugins.references.dbpediadatabus.io.DbpediaDatabusCredentialPatchIO;
import de.dlr.shepard.plugins.references.dbpediadatabus.services.DbpediaDatabusConfigService;
import de.dlr.shepard.plugins.references.dbpediadatabus.services.DbpediaDatabusConfigService.DatabusPatch;
import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * REF1c — admin REST surface for the DBpedia Databus reference plugin.
 *
 * <p>All endpoints live under {@code /v2/admin/references/dbpedia-databus/}
 * and require the {@code instance-admin} role per the CLAUDE.md
 * "admin-configurable at runtime" rule.
 *
 * <p>RFC 7807 problem responses on all non-2xx paths.
 */
@Path("/v2/admin/references/dbpedia-databus")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
@Tag(name = "Admin")
public class DbpediaDatabusAdminRest {

  static final String PROBLEM_TYPE_DISABLED_ENDPOINT = "/problems/dbpedia-databus.config.encryption-unavailable";
  static final String PROBLEM_TYPE_VALIDATION = "/problems/dbpedia-databus.config.validation";

  @Inject
  DbpediaDatabusConfigService configService;

  @Inject
  DatabusHttpClient httpClient;

  @GET
  @Path("/config")
  @Operation(
    summary = "Read the current :DbpediaDatabusConfig singleton.",
    description = "Returns the runtime-mutable DBpedia-Databus-integration config — " +
    "enabled, defaultEndpoint, allowedHosts, cacheTtlSeconds, authMode, " +
    "oauthTokenUrl, oauthClientId, oauthClientSecretSet + masked fingerprint. " +
    "The raw AES-GCM cipher is never returned."
  )
  @APIResponse(
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = DbpediaDatabusConfigIO.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response getConfig() {
    DbpediaDatabusConfig cfg = configService.current();
    return Response.ok(DbpediaDatabusConfigIO.from(cfg)).build();
  }

  @PATCH
  @Path("/config")
  @Operation(
    summary = "RFC 7396 merge-patch the :DbpediaDatabusConfig singleton.",
    description = "Patchable fields: enabled, defaultEndpoint, allowedHosts, cacheTtlSeconds, " +
    "authMode, oauthTokenUrl, oauthClientId. RFC 7396 semantics — absent = leave alone, " +
    "null = clear (where applicable). The OAuth client secret is write-only via " +
    "POST /v2/admin/references/dbpedia-databus/credential and is never returned here."
  )
  @APIResponse(
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = DbpediaDatabusConfigIO.class))
  )
  @APIResponse(
    responseCode = "400",
    description = "Validation failed (RFC 7807).",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemJson.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response patchConfig(DbpediaDatabusConfigPatchIO body, @Context SecurityContext securityContext) {
    DbpediaDatabusConfigPatchIO patch = body == null ? new DbpediaDatabusConfigPatchIO() : body;
    DatabusPatch svcPatch = new DatabusPatch();
    svcPatch.enabled = patch.getEnabled();
    svcPatch.cacheTtlSeconds = patch.getCacheTtlSeconds();
    svcPatch.authMode = patch.getAuthMode();
    svcPatch.defaultEndpointTouched = patch.isDefaultEndpointTouched();
    svcPatch.defaultEndpoint = patch.getDefaultEndpoint();
    svcPatch.allowedHostsTouched = patch.isAllowedHostsTouched();
    svcPatch.allowedHosts = patch.getAllowedHosts();
    svcPatch.oauthTokenUrlTouched = patch.isOauthTokenUrlTouched();
    svcPatch.oauthTokenUrl = patch.getOauthTokenUrl();
    svcPatch.oauthClientIdTouched = patch.isOauthClientIdTouched();
    svcPatch.oauthClientId = patch.getOauthClientId();

    String actor = securityContext.getUserPrincipal() != null ? securityContext.getUserPrincipal().getName() : null;
    try {
      DbpediaDatabusConfig saved = configService.patch(svcPatch, actor);
      return Response.ok(DbpediaDatabusConfigIO.from(saved)).build();
    } catch (IllegalArgumentException iae) {
      Log.warnf("REF1c: PATCH /v2/admin/references/dbpedia-databus/config validation failed: %s", iae.getMessage());
      return problem(PROBLEM_TYPE_VALIDATION, "Config validation failed", Status.BAD_REQUEST, iae.getMessage());
    }
  }

  @POST
  @Path("/credential")
  @Operation(
    summary = "Set or update the OAuth client secret for DBpedia Databus auth.",
    description = "AES-GCM-encrypts the provided clientSecret and stores only the cipher on " +
    ":DbpediaDatabusConfig. The plaintext is never persisted or logged — only the " +
    "fingerprint (first 8 hex chars of SHA-256(cipher)) is visible in admin responses. " +
    "Requires shepard.secrets.encryption-key to be configured."
  )
  @APIResponse(
    responseCode = "200",
    description = "Credential set; updated config returned.",
    content = @Content(schema = @Schema(implementation = DbpediaDatabusConfigIO.class))
  )
  @APIResponse(
    responseCode = "400",
    description = "clientSecret blank or encryption unavailable (RFC 7807).",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemJson.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response setCredential(DbpediaDatabusCredentialPatchIO body, @Context SecurityContext securityContext) {
    if (body == null || body.getClientSecret() == null || body.getClientSecret().isBlank()) {
      return problem(PROBLEM_TYPE_VALIDATION, "clientSecret is required", Status.BAD_REQUEST, "clientSecret must be non-blank");
    }
    String actor = securityContext.getUserPrincipal() != null ? securityContext.getUserPrincipal().getName() : null;
    try {
      DbpediaDatabusConfig saved = configService.setOauthClientSecret(body.getClientSecret(), actor);
      return Response.ok(DbpediaDatabusConfigIO.from(saved)).build();
    } catch (IllegalStateException ise) {
      return problem(PROBLEM_TYPE_DISABLED_ENDPOINT, "Credential storage unavailable", Status.BAD_REQUEST, ise.getMessage());
    }
  }

  @DELETE
  @Path("/credential")
  @Operation(
    summary = "Clear the stored OAuth client secret.",
    description = "Removes the AES-GCM cipher from :DbpediaDatabusConfig. The authMode is " +
    "NOT automatically reset — an admin should also PATCH authMode=none if desired."
  )
  @APIResponse(
    responseCode = "200",
    description = "Credential cleared; updated config returned.",
    content = @Content(schema = @Schema(implementation = DbpediaDatabusConfigIO.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response clearCredential(@Context SecurityContext securityContext) {
    String actor = securityContext.getUserPrincipal() != null ? securityContext.getUserPrincipal().getName() : null;
    DbpediaDatabusConfig saved = configService.clearOauthClientSecret(actor);
    return Response.ok(DbpediaDatabusConfigIO.from(saved)).build();
  }

  @POST
  @Path("/test-connection")
  @Operation(
    summary = "Probe the configured defaultEndpoint.",
    description = "GETs {defaultEndpoint}/api/info with a 30s timeout; returns reachable + " +
    "statusCode + latencyMs + optional reason."
  )
  @APIResponse(
    responseCode = "200",
    description = "Connection test result (reachable may be false — check the payload)."
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response testConnection() {
    DbpediaDatabusConfig cfg = configService.current();
    String endpoint = cfg.getDefaultEndpoint() + "/api/info";
    DatabusHttpClient.ConnectionTestResult result = httpClient.testConnection(endpoint);
    return Response.ok(result).build();
  }

  // ─── helpers ─────────────────────────────────────────────────────────────

  private Response problem(String type, String title, Status status, String detail) {
    ProblemJson body = new ProblemJson(type, title, status.getStatusCode(), detail, null);
    return Response.status(status).type("application/problem+json").entity(body).build();
  }
}
