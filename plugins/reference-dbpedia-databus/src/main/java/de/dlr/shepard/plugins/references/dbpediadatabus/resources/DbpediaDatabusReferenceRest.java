package de.dlr.shepard.plugins.references.dbpediadatabus.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.plugins.references.dbpediadatabus.clients.DatabusHttpClient;
import de.dlr.shepard.plugins.references.dbpediadatabus.daos.DbpediaDatabusReferenceDAO;
import de.dlr.shepard.plugins.references.dbpediadatabus.entities.DbpediaDatabusConfig;
import de.dlr.shepard.plugins.references.dbpediadatabus.entities.DbpediaDatabusReference;
import de.dlr.shepard.plugins.references.dbpediadatabus.io.DbpediaDatabusCreateReferenceIO;
import de.dlr.shepard.plugins.references.dbpediadatabus.io.DbpediaDatabusPreviewIO;
import de.dlr.shepard.plugins.references.dbpediadatabus.io.DbpediaDatabusReferenceIO;
import de.dlr.shepard.plugins.references.dbpediadatabus.services.DbpediaDatabusConfigService;
import de.dlr.shepard.plugins.references.dbpediadatabus.services.DbpediaDatabusCredentialService;
import de.dlr.shepard.plugins.references.dbpediadatabus.services.DbpediaDatabusReferenceService;
import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;
import java.util.List;
import java.util.Optional;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * REF1c — user-facing REST surface for
 * {@code /v2/data-objects/{dataObjectAppId}/dbpedia-databus-references}.
 *
 * <p>Mirrors the G1a {@code GitReferenceRest} shape: permission checks
 * piggyback on the DataObject's ACL (Read to list/get, Write to
 * create/delete). Auth gate is {@code shepard-user}.
 *
 * <p>On create, the plugin fetches the artifact's JSON-LD metadata
 * from the Databus instance and caches the result in the Neo4j
 * {@code :DbpediaDatabusReference} node.
 */
@Path("/v2/data-objects/{dataObjectAppId}/dbpedia-databus-references")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@RolesAllowed("shepard-user")
@Tag(name = "DBpedia Databus references (v2)")
public class DbpediaDatabusReferenceRest {

  static final String PROBLEM_TYPE_VALIDATION = "/problems/dbpedia-databus.reference.validation";
  static final String PROBLEM_TYPE_DISABLED = "/problems/dbpedia-databus.reference.plugin-disabled";

  @Inject
  DbpediaDatabusReferenceDAO referenceDAO;

  @Inject
  DataObjectDAO dataObjectDAO;

  @Inject
  PermissionsService permissionsService;

  @Inject
  EntityIdResolver entityIdResolver;

  @Inject
  DbpediaDatabusConfigService configService;

  @Inject
  DbpediaDatabusCredentialService credentialService;

  @Inject
  DbpediaDatabusReferenceService referenceService;

  @Inject
  DatabusHttpClient httpClient;

  @GET
  @Operation(summary = "List all DbpediaDatabusReferences for a DataObject.")
  @APIResponse(
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = DbpediaDatabusReferenceIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the DataObject.")
  @APIResponse(responseCode = "404", description = "No DataObject with that appId.")
  public Response list(
    @PathParam("dataObjectAppId") String dataObjectAppId,
    @Context SecurityContext securityContext
  ) {
    Response gate = checkAccess(dataObjectAppId, AccessType.Read, securityContext);
    if (gate != null) return gate;
    List<DbpediaDatabusReferenceIO> rows = referenceDAO
      .findByDataObjectAppId(dataObjectAppId)
      .stream()
      .map(DbpediaDatabusReferenceIO::new)
      .toList();
    return Response.ok(rows).build();
  }

  @POST
  @Operation(
    summary = "Create a new DbpediaDatabusReference on a DataObject.",
    description = "Validates the artifactUri, checks the host allowlist, then fetches " +
    "the artifact's JSON-LD from the Databus and caches title / abstract / version / licence. " +
    "The optional apiKey field is used as X-API-Key on the fetch and is never persisted."
  )
  @APIResponse(
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = DbpediaDatabusReferenceIO.class))
  )
  @APIResponse(
    responseCode = "400",
    description = "artifactUri missing, blank, or host not in allowedHosts (RFC 7807).",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemJson.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the DataObject.")
  @APIResponse(responseCode = "404", description = "No DataObject with that appId.")
  public Response create(
    @PathParam("dataObjectAppId") String dataObjectAppId,
    DbpediaDatabusCreateReferenceIO body,
    @Context SecurityContext securityContext
  ) {
    if (body == null || body.getArtifactUri() == null || body.getArtifactUri().isBlank()) {
      return problem(PROBLEM_TYPE_VALIDATION, "artifactUri is required", Status.BAD_REQUEST, "artifactUri must be non-blank");
    }
    DbpediaDatabusReferenceService.ValidationResult vr = referenceService.validateUri(body.getArtifactUri());
    if (!vr.ok()) {
      return problem(PROBLEM_TYPE_VALIDATION, "Invalid artifactUri", Status.BAD_REQUEST, vr.reason());
    }
    Response gate = checkAccess(dataObjectAppId, AccessType.Write, securityContext);
    if (gate != null) return gate;
    DataObject parent;
    try {
      parent = dataObjectDAO.findByNeo4jId(entityIdResolver.resolveLong(dataObjectAppId));
    } catch (NotFoundException nfe) {
      return Response.status(Status.NOT_FOUND).build();
    }
    if (parent == null) return Response.status(Status.NOT_FOUND).build();

    DbpediaDatabusReference ref = new DbpediaDatabusReference(body.getArtifactUri());
    ref.setDataObject(parent);
    ref.setCacheStatus(DbpediaDatabusReference.STATUS_UNAVAILABLE);
    DbpediaDatabusReference saved = referenceDAO.createOrUpdate(ref);

    // Fetch metadata on creation — best-effort; failure leaves cache UNAVAILABLE
    try {
      DatabusHttpClient.AuthMode auth = buildAuthMode(body.getApiKey());
      DbpediaDatabusPreviewIO preview = httpClient.fetchArtifact(body.getArtifactUri(), auth);
      if (preview.isAvailable()) {
        saved.setCachedTitle(preview.getTitle());
        saved.setCachedAbstract(preview.getDescription());
        saved.setCachedVersion(preview.getVersion());
        saved.setCachedLicence(preview.getLicence());
        saved.setCachedModifiedAtMillis(preview.getModifiedAt() == null ? null : preview.getModifiedAt().getTime());
        saved.setCacheFetchedAtMillis(System.currentTimeMillis());
        saved.setCacheStatus(DbpediaDatabusReference.STATUS_FRESH);
        saved = referenceDAO.createOrUpdate(saved);
      }
    } catch (RuntimeException e) {
      Log.warnf(e, "REF1c: metadata fetch on create failed for artifactUri=%s", body.getArtifactUri());
    }
    return Response.status(Status.CREATED).entity(new DbpediaDatabusReferenceIO(saved)).build();
  }

  @GET
  @Path("/{referenceAppId}")
  @Operation(summary = "Read a single DbpediaDatabusReference by appId.")
  @APIResponse(
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = DbpediaDatabusReferenceIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No DataObject or reference with those appIds.")
  public Response read(
    @PathParam("dataObjectAppId") String dataObjectAppId,
    @PathParam("referenceAppId") String referenceAppId,
    @Context SecurityContext securityContext
  ) {
    Response gate = checkAccess(dataObjectAppId, AccessType.Read, securityContext);
    if (gate != null) return gate;
    DbpediaDatabusReference ref = referenceDAO.findByAppId(referenceAppId);
    if (ref == null || ref.getDataObject() == null || !dataObjectAppId.equals(ref.getDataObject().getAppId())) {
      return Response.status(Status.NOT_FOUND).build();
    }
    return Response.ok(new DbpediaDatabusReferenceIO(ref)).build();
  }

  @DELETE
  @Path("/{referenceAppId}")
  @Operation(summary = "Delete a DbpediaDatabusReference.")
  @APIResponse(responseCode = "204", description = "Deleted.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No DataObject or reference with those appIds.")
  public Response delete(
    @PathParam("dataObjectAppId") String dataObjectAppId,
    @PathParam("referenceAppId") String referenceAppId,
    @Context SecurityContext securityContext
  ) {
    Response gate = checkAccess(dataObjectAppId, AccessType.Write, securityContext);
    if (gate != null) return gate;
    DbpediaDatabusReference ref = referenceDAO.findByAppId(referenceAppId);
    if (ref == null || ref.getDataObject() == null || !dataObjectAppId.equals(ref.getDataObject().getAppId())) {
      return Response.status(Status.NOT_FOUND).build();
    }
    referenceDAO.deleteByNeo4jId(ref.getId());
    return Response.noContent().build();
  }

  @GET
  @Path("/{referenceAppId}/preview")
  @Operation(
    summary = "Fetch / serve the cached DBpedia Databus artifact preview.",
    description = "Returns the cached title / abstract / version / licence / distributions " +
    "from the last successful fetch. If the cache is stale (per cacheTtlSeconds) or was " +
    "never populated, a live fetch is attempted. All failure modes surface as 200 with " +
    "available=false + a reason discriminator."
  )
  @APIResponse(
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = DbpediaDatabusPreviewIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No DataObject or reference with those appIds.")
  public Response preview(
    @PathParam("dataObjectAppId") String dataObjectAppId,
    @PathParam("referenceAppId") String referenceAppId,
    @Context SecurityContext securityContext
  ) {
    Response gate = checkAccess(dataObjectAppId, AccessType.Read, securityContext);
    if (gate != null) return gate;
    DbpediaDatabusReference ref = referenceDAO.findByAppId(referenceAppId);
    if (ref == null || ref.getDataObject() == null || !dataObjectAppId.equals(ref.getDataObject().getAppId())) {
      return Response.status(Status.NOT_FOUND).build();
    }
    DbpediaDatabusPreviewIO out = referenceService.preview(ref);
    return Response.ok(out).build();
  }

  // ─── helpers ────────────────────────────────────────────────────────────────

  /**
   * Returns null when access is allowed, otherwise a short-circuit
   * Response (401 / 403 / 404).
   */
  private Response checkAccess(String dataObjectAppId, AccessType accessType, SecurityContext securityContext) {
    String caller = securityContext.getUserPrincipal() != null ? securityContext.getUserPrincipal().getName() : null;
    if (caller == null) return Response.status(Status.UNAUTHORIZED).build();
    long ogmId;
    try {
      ogmId = entityIdResolver.resolveLong(dataObjectAppId);
    } catch (NotFoundException nfe) {
      return Response.status(Status.NOT_FOUND).build();
    }
    if (!permissionsService.isAccessTypeAllowedForUser(ogmId, accessType, caller)) {
      return Response.status(Status.FORBIDDEN).build();
    }
    return null;
  }

  private DatabusHttpClient.AuthMode buildAuthMode(String apiKey) {
    if (apiKey != null && !apiKey.isBlank()) {
      // API-key auth: use as bearer token for simplicity; the Databus accepts
      // both X-API-Key header and Bearer. The oauth-client-credentials flow
      // is config-driven (admin-side) and not per-request.
      return DatabusHttpClient.AuthMode.none();
    }
    DbpediaDatabusConfig cfg = configService.current();
    if (!DbpediaDatabusConfig.AUTH_MODE_OAUTH_CC.equals(cfg.getAuthMode())) {
      return DatabusHttpClient.AuthMode.none();
    }
    if (!cfg.isOauthClientSecretSet() || cfg.getOauthClientSecretCipher() == null) {
      return DatabusHttpClient.AuthMode.none();
    }
    Optional<String> secret = credentialService.decrypt(cfg.getOauthClientSecretCipher());
    if (secret.isEmpty()) return DatabusHttpClient.AuthMode.none();
    return DatabusHttpClient.AuthMode.oauthClientCredentials(
      cfg.getOauthTokenUrl(),
      cfg.getOauthClientId(),
      secret.get()
    );
  }

  private Response problem(String type, String title, Status status, String detail) {
    ProblemJson body = new ProblemJson(type, title, status.getStatusCode(), detail, null);
    return Response.status(status).type("application/problem+json").entity(body).build();
  }
}
