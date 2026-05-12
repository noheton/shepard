package de.dlr.shepard.v2.users.resources;

import de.dlr.shepard.auth.users.daos.GitCredentialDAO;
import de.dlr.shepard.auth.users.entities.GitCredential;
import de.dlr.shepard.common.crypto.AesGcmCipher;
import de.dlr.shepard.v2.users.io.CreateGitCredentialIO;
import de.dlr.shepard.v2.users.io.GitCredentialIO;
import de.dlr.shepard.v2.users.io.PatchGitCredentialIO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * {@code /v2/me/git-credentials} CRUD surface for per-host PAT storage
 * (U2a + G1-cred). Ownership is enforced by the DAO query
 * ({@code MATCH …-[:OWNS_CREDENTIAL]->(c) WHERE u.username=$caller}) —
 * not by {@code @RolesAllowed} — because these are user-scoped resources
 * with no admin override path.
 *
 * <p>The PAT is encrypted with AES-256-GCM before storage and never
 * returned on any response. When {@code shepard.secrets.encryption-key}
 * is absent, PAT storage is disabled and PATCH returns 501.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/me/git-credentials")
@RequestScoped
@Tag(name = "Git credentials (v2)")
public class MeCredentialsRest {

  @Inject
  GitCredentialDAO gitCredentialDAO;

  @ConfigProperty(name = "shepard.secrets.encryption-key")
  Optional<String> encryptionKey;

  @GET
  @Operation(summary = "List the caller's Git credentials.")
  @APIResponse(
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = GitCredentialIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  public Response list(@Context SecurityContext securityContext) {
    String caller = callerOrNull(securityContext);
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();

    List<GitCredentialIO> result = gitCredentialDAO.findAllByUser(caller).stream().map(GitCredentialIO::new).toList();
    return Response.ok(result).build();
  }

  @POST
  @Operation(summary = "Create a new Git credential for the caller.")
  @APIResponse(
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = GitCredentialIO.class))
  )
  @APIResponse(responseCode = "400", description = "host, username, or pat is missing/blank.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "501", description = "Encryption key not configured — PAT storage disabled.")
  public Response create(
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = CreateGitCredentialIO.class))) CreateGitCredentialIO body,
    @Context SecurityContext securityContext,
    @Context UriInfo uriInfo
  ) {
    String caller = callerOrNull(securityContext);
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();

    if (body == null) return Response.status(Response.Status.BAD_REQUEST).entity("Request body is required").build();
    if (body.getHost() == null || body.getHost().isBlank()) {
      return Response.status(Response.Status.BAD_REQUEST).entity("host is required and must be non-blank").build();
    }
    if (body.getUsername() == null || body.getUsername().isBlank()) {
      return Response.status(Response.Status.BAD_REQUEST).entity("username is required and must be non-blank").build();
    }
    if (body.getPat() == null || body.getPat().isBlank()) {
      return Response.status(Response.Status.BAD_REQUEST).entity("pat is required and must be non-blank").build();
    }

    byte[] key = resolveKey();
    if (key == null) return notImplementedNoKey();

    GitCredential cred = new GitCredential();
    cred.setHost(body.getHost().strip());
    cred.setDisplayName(body.getDisplayName());
    cred.setUsername(body.getUsername());
    cred.setEncryptedPat(AesGcmCipher.encrypt(body.getPat(), key));

    GitCredential saved = gitCredentialDAO.createForUser(caller, cred);
    var location = uriInfo.getAbsolutePathBuilder().path(saved.getAppId()).build();
    return Response.created(location).entity(new GitCredentialIO(saved)).build();
  }

  @GET
  @Path("/{appId}")
  @Operation(summary = "Get a single Git credential by appId.")
  @APIResponse(
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = GitCredentialIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "404", description = "Not found or not owned by caller.")
  public Response read(@PathParam("appId") String appId, @Context SecurityContext securityContext) {
    String caller = callerOrNull(securityContext);
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();

    GitCredential cred = gitCredentialDAO.findByUserAndAppId(caller, appId);
    if (cred == null) return Response.status(Response.Status.NOT_FOUND).build();
    return Response.ok(new GitCredentialIO(cred)).build();
  }

  @PATCH
  @Path("/{appId}")
  @Operation(summary = "Partially update a Git credential.")
  @APIResponse(
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = GitCredentialIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "404", description = "Not found or not owned by caller.")
  @APIResponse(responseCode = "501", description = "Encryption key not configured — PAT update disabled.")
  public Response patch(
    @PathParam("appId") String appId,
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = PatchGitCredentialIO.class))) PatchGitCredentialIO body,
    @Context SecurityContext securityContext
  ) {
    String caller = callerOrNull(securityContext);
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();

    if (body != null && body.getPat() != null && !body.getPat().isBlank()) {
      byte[] key = resolveKey();
      if (key == null) return notImplementedNoKey();
    }

    GitCredential existing = gitCredentialDAO.findByUserAndAppId(caller, appId);
    if (existing == null) return Response.status(Response.Status.NOT_FOUND).build();

    GitCredential delta = new GitCredential();
    if (body != null) {
      delta.setDisplayName(body.getDisplayName());
      delta.setUsername(body.getUsername());

      if (body.getPat() != null && !body.getPat().isBlank()) {
        byte[] key = resolveKey();
        // key non-null here: we already checked above
        delta.setEncryptedPat(AesGcmCipher.encrypt(body.getPat(), key));
      }
    }

    GitCredential updated = gitCredentialDAO.updateByUserAndAppId(caller, appId, delta);
    if (updated == null) return Response.status(Response.Status.NOT_FOUND).build();
    return Response.ok(new GitCredentialIO(updated)).build();
  }

  @DELETE
  @Path("/{appId}")
  @Operation(summary = "Delete a Git credential.")
  @APIResponse(responseCode = "204", description = "Deleted.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "404", description = "Not found or not owned by caller.")
  public Response delete(@PathParam("appId") String appId, @Context SecurityContext securityContext) {
    String caller = callerOrNull(securityContext);
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();

    boolean deleted = gitCredentialDAO.deleteByUserAndAppId(caller, appId);
    return deleted ? Response.noContent().build() : Response.status(Response.Status.NOT_FOUND).build();
  }

  private String callerOrNull(SecurityContext sc) {
    return sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
  }

  /**
   * @return 32-byte AES key decoded from config, or {@code null} when the
   *         key is absent. Absence is non-exceptional (feature disabled);
   *         callers that need the key should return 501.
   */
  private byte[] resolveKey() {
    if (encryptionKey.isEmpty() || encryptionKey.get().isBlank()) return null;
    try {
      return Base64.getDecoder().decode(encryptionKey.get().trim());
    } catch (IllegalArgumentException e) {
      // SecretsKeyValidator would have already aborted startup for a bad key;
      // this branch is unreachable in production but is a defensive guard for
      // environments that bypass the startup validator (e.g. test overrides).
      Log.error("shepard.secrets.encryption-key is present but not valid base64 — treating as absent");
      return null;
    }
  }

  private Response notImplementedNoKey() {
    return Response
      .status(Response.Status.NOT_IMPLEMENTED)
      .entity("PAT storage is disabled: shepard.secrets.encryption-key is not configured")
      .build();
  }
}
