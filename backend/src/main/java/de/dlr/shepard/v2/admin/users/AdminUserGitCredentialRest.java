package de.dlr.shepard.v2.admin.users;

import de.dlr.shepard.auth.users.daos.GitCredentialDAO;
import de.dlr.shepard.auth.users.entities.GitCredential;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.crypto.AesGcmCipher;
import de.dlr.shepard.common.util.Constants;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Admin preseed endpoint for git credentials — lets an instance-admin set a
 * PAT for another user (e.g. the demo showcase user "flo") without that user
 * needing to log in and navigate to Profile → Git credentials.
 *
 * <p>POST is idempotent for the same host: if a credential for that host
 * already exists under the user, it is replaced in-place (same node,
 * updated encryptedPat + username). This matches the seed script's
 * "idempotent re-run" contract.
 *
 * <p>Auth: instance-admin role only. The PAT is never returned on the wire
 * (the response carries only the credential's appId and host). Encryption
 * uses the same AES-256-GCM cipher as the self-service endpoint so the PAT
 * is decryptable by {@link de.dlr.shepard.auth.users.services.GitCredentialService}.
 */
@Path("/v2/admin/users/{" + Constants.USERNAME + "}/git-credentials")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
@Tag(name = "Admin — git credential preseed")
public class AdminUserGitCredentialRest {

  @Inject
  UserService userService;

  @Inject
  GitCredentialDAO gitCredentialDAO;

  @ConfigProperty(name = "shepard.secrets.encryption-key")
  Optional<String> encryptionKey;

  public record AdminGitCredentialIO(
    String host,
    String username,
    String pat,
    String displayName
  ) {}

  public record AdminGitCredentialResultIO(String appId, String host, String username) {}

  @POST
  @Operation(
    summary = "Preseed a git credential for another user (admin-only).",
    description = "Creates or replaces a git credential for the named user. " +
    "If a credential for the same host already exists, it is overwritten. " +
    "The PAT is encrypted with AES-256-GCM using the instance encryption key " +
    "(`shepard.secrets.encryption-key`) and never returned on the wire. " +
    "The target user must already exist (i.e. they have logged in at least once). " +
    "Returns 503 when the encryption key is not configured."
  )
  @APIResponse(
    responseCode = "201",
    description = "Credential created (or replaced).",
    content = @Content(schema = @Schema(implementation = AdminGitCredentialResultIO.class))
  )
  @APIResponse(responseCode = "400", description = "Missing required field (host, username, or pat).")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks instance-admin role.")
  @APIResponse(responseCode = "404", description = "No user with that username.")
  @APIResponse(responseCode = "503", description = "Encryption key not configured.")
  public Response post(
    @PathParam(Constants.USERNAME) String targetUsername,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = AdminGitCredentialIO.class))
    ) AdminGitCredentialIO body
  ) {
    if (body == null || body.host() == null || body.host().isBlank()) {
      return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\":\"host is required\"}").build();
    }
    if (body.username() == null || body.username().isBlank()) {
      return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\":\"username is required\"}").build();
    }
    if (body.pat() == null || body.pat().isBlank()) {
      return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\":\"pat is required\"}").build();
    }

    if (userService.getUserOptional(targetUsername).isEmpty()) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    byte[] key = resolveKey();
    if (key == null) {
      return Response.status(Response.Status.SERVICE_UNAVAILABLE)
        .entity("{\"error\":\"shepard.secrets.encryption-key is not configured — git credentials cannot be stored\"}")
        .build();
    }

    String encryptedPat = AesGcmCipher.encrypt(body.pat(), key);
    String host = body.host().toLowerCase(java.util.Locale.ROOT).replaceAll("^https?://", "").replaceAll("/.*", "");
    String displayName = body.displayName() != null ? body.displayName() : host;

    // Idempotent: replace existing credential for the same host.
    List<GitCredential> existing = gitCredentialDAO.findAllByUser(targetUsername);
    for (GitCredential c : existing) {
      if (host.equals(c.getHost())) {
        GitCredential update = new GitCredential();
        update.setUsername(body.username());
        update.setEncryptedPat(encryptedPat);
        GitCredential updated = gitCredentialDAO.updateByUserAndAppId(targetUsername, c.getAppId(), update);
        String appId = updated != null ? updated.getAppId() : c.getAppId();
        return Response.status(Response.Status.CREATED)
          .entity(new AdminGitCredentialResultIO(appId, host, body.username()))
          .build();
      }
    }

    // Create new.
    GitCredential cred = new GitCredential();
    cred.setHost(host);
    cred.setUsername(body.username());
    cred.setDisplayName(displayName);
    cred.setEncryptedPat(encryptedPat);
    GitCredential created = gitCredentialDAO.createForUser(targetUsername, cred);
    return Response.status(Response.Status.CREATED)
      .entity(new AdminGitCredentialResultIO(created.getAppId(), host, body.username()))
      .build();
  }

  private byte[] resolveKey() {
    if (encryptionKey == null || encryptionKey.isEmpty() || encryptionKey.get().isBlank()) return null;
    try {
      return Base64.getDecoder().decode(encryptionKey.get().trim());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
