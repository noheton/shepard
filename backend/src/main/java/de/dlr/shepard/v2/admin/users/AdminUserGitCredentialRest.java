package de.dlr.shepard.v2.admin.users;

import de.dlr.shepard.auth.users.daos.GitCredentialDAO;
import de.dlr.shepard.auth.users.entities.GitCredential;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.crypto.AesGcmCipher;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import static de.dlr.shepard.v2.common.ProblemResponse.problem;

/**
 * Admin preseed endpoint for git credentials — lets an instance-admin set a
 * PAT for another user (e.g. the demo showcase user "flodemo") without that
 * user needing to log in and navigate to Profile → Git credentials.
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
@Tag(name = "Admin")
public class AdminUserGitCredentialRest {

  private static final String PROBLEM_TYPE_BAD_REQUEST = "/problems/admin-git-credentials.bad-request";
  private static final String PROBLEM_TYPE_NOT_FOUND = "/problems/admin-git-credentials.not-found";
  private static final String PROBLEM_TYPE_SERVICE_UNAVAILABLE = "/problems/admin-git-credentials.service-unavailable";

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

  /**
   * ADM-USR-GIT-BACKEND-1 — list-row view of a stored credential. The PAT
   * is intentionally absent (write-only credential rule); operators see
   * only the discovery metadata they need to decide whether to rotate.
   */
  public record AdminGitCredentialListItemIO(
    String appId,
    String host,
    String username,
    String displayName,
    Instant lastRotatedAt
  ) {
    public static AdminGitCredentialListItemIO from(GitCredential c) {
      Date rotated = c.getLastRotatedAt();
      return new AdminGitCredentialListItemIO(
        c.getAppId(),
        c.getHost(),
        c.getUsername(),
        c.getDisplayName(),
        rotated == null ? null : rotated.toInstant()
      );
    }
  }

  /** ADM-USR-GIT-BACKEND-1 — rotate body: caller supplies a fresh PAT. */
  public record AdminGitCredentialRotateIO(String newPat) {}

  @POST
  @Operation(
    operationId = "preseedGitCredential",
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
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Missing required field",
        Response.Status.BAD_REQUEST, "host is required");
    }
    if (body.username() == null || body.username().isBlank()) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Missing required field",
        Response.Status.BAD_REQUEST, "username is required");
    }
    if (body.pat() == null || body.pat().isBlank()) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Missing required field",
        Response.Status.BAD_REQUEST, "pat is required");
    }

    if (userService.getUserOptional(targetUsername).isEmpty()) {
      return problem(PROBLEM_TYPE_NOT_FOUND, "User not found",
          Response.Status.NOT_FOUND, "No user with username '" + targetUsername + "'.");
    }

    byte[] key = resolveKey();
    if (key == null) {
      return problem(PROBLEM_TYPE_SERVICE_UNAVAILABLE, "Encryption key not configured",
        Response.Status.SERVICE_UNAVAILABLE,
        "shepard.secrets.encryption-key is not configured — git credentials cannot be stored");
    }

    String encryptedPat = AesGcmCipher.encrypt(body.pat(), key);
    String host = body.host().toLowerCase(java.util.Locale.ROOT).replaceAll("^https?://", "").replaceAll("/.*", "");
    String displayName = body.displayName() != null ? body.displayName() : host;

    // Idempotent: replace existing credential for the same host. Use the
    // rotate path so lastRotatedAt is stamped (ADM-USR-GIT-BACKEND-1).
    List<GitCredential> existing = gitCredentialDAO.findAllByUser(targetUsername);
    for (GitCredential c : existing) {
      if (host.equals(c.getHost())) {
        // Username also needs to be updatable on the replace path; do that
        // via updateByUserAndAppId first (no rotation timestamp), then call
        // the rotate path to stamp the new ciphertext + lastRotatedAt.
        if (!body.username().equals(c.getUsername())) {
          GitCredential userOnly = new GitCredential();
          userOnly.setUsername(body.username());
          gitCredentialDAO.updateByUserAndAppId(targetUsername, c.getAppId(), userOnly);
        }
        GitCredential updated = gitCredentialDAO.rotateByUserAndAppId(
          targetUsername, c.getAppId(), encryptedPat
        );
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

  // ── ADM-USR-GIT-BACKEND-1 — read + rotate endpoints ──────────────────────

  @GET
  @Operation(
    operationId = "listGitCredentials",
    summary = "List a user's git credentials (admin-only).",
    description =
      "Returns the discovery metadata for every credential the named user " +
      "owns: appId, host, username, displayName, and lastRotatedAt. " +
      "The PAT itself is **never** returned on the wire (write-only " +
      "credential rule). Use `POST /v2/admin/users/{username}/git-credentials/" +
      "{appId}/rotate` to refresh a credential's PAT.\n\n" +
      "Pre-ADM-USR-GIT-BACKEND-1 credentials (rows persisted before the " +
      "`lastRotatedAt` field existed) return `null` for that field until " +
      "they are next rotated."
  )
  @APIResponse(
    responseCode = "200",
    description = "Paged list of credentials (may be empty).",
    content = @Content(schema = @Schema(implementation = PagedResponseIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks instance-admin role.")
  @APIResponse(responseCode = "404", description = "No user with that username.")
  public Response list(@PathParam(Constants.USERNAME) String targetUsername) {
    if (userService.getUserOptional(targetUsername).isEmpty()) {
      return problem(PROBLEM_TYPE_NOT_FOUND, "User not found",
          Response.Status.NOT_FOUND, "No user with username '" + targetUsername + "'.");
    }
    List<GitCredential> stored = gitCredentialDAO.findAllByUser(targetUsername);
    List<AdminGitCredentialListItemIO> items = new ArrayList<>(stored == null ? 0 : stored.size());
    if (stored != null) {
      for (GitCredential c : stored) {
        items.add(AdminGitCredentialListItemIO.from(c));
      }
    }
    return Response.ok(new PagedResponseIO<>(items, items.size(), 0, items.size()))
        .build();
  }

  @POST
  @Path("/{appId}/rotate")
  @Operation(
    operationId = "rotate",
    summary = "Rotate a git credential's PAT (admin-only).",
    description =
      "Replaces the encrypted PAT on the named credential and stamps " +
      "`lastRotatedAt = now`. Other fields (host, username, displayName) " +
      "are untouched. The new PAT is encrypted with AES-256-GCM using the " +
      "instance encryption key and never returned on the wire.\n\n" +
      "This is the explicit rotation path; the existing `POST /v2/admin/" +
      "users/{username}/git-credentials` keeps its idempotent " +
      "create-or-replace semantics for the same host."
  )
  @APIResponse(responseCode = "204", description = "Credential rotated.")
  @APIResponse(responseCode = "400", description = "newPat missing or blank.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks instance-admin role.")
  @APIResponse(responseCode = "404", description = "No user with that username or no credential with that appId under the user.")
  @APIResponse(responseCode = "503", description = "Encryption key not configured.")
  public Response rotate(
    @PathParam(Constants.USERNAME) String targetUsername,
    @PathParam("appId") String credAppId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = AdminGitCredentialRotateIO.class))
    ) AdminGitCredentialRotateIO body
  ) {
    if (body == null || body.newPat() == null || body.newPat().isBlank()) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Missing required field",
        Response.Status.BAD_REQUEST, "newPat is required");
    }
    if (userService.getUserOptional(targetUsername).isEmpty()) {
      return problem(PROBLEM_TYPE_NOT_FOUND, "User not found",
          Response.Status.NOT_FOUND, "No user with username '" + targetUsername + "'.");
    }
    GitCredential existing = gitCredentialDAO.findByUserAndAppId(targetUsername, credAppId);
    if (existing == null) {
      return problem(PROBLEM_TYPE_NOT_FOUND, "Credential not found",
          Response.Status.NOT_FOUND, "No git credential with appId '" + credAppId + "' under user '" + targetUsername + "'.");
    }
    byte[] key = resolveKey();
    if (key == null) {
      return problem(PROBLEM_TYPE_SERVICE_UNAVAILABLE, "Encryption key not configured",
        Response.Status.SERVICE_UNAVAILABLE,
        "shepard.secrets.encryption-key is not configured — git credentials cannot be stored");
    }
    String encrypted = AesGcmCipher.encrypt(body.newPat(), key);
    gitCredentialDAO.rotateByUserAndAppId(targetUsername, credAppId, encrypted);
    return Response.noContent().build();
  }

  private byte[] resolveKey() {
    if (encryptionKey == null || encryptionKey.isEmpty() || encryptionKey.get().isBlank()) return null;
    try {
      return Base64.getDecoder().decode(encryptionKey.get().trim());
    } catch (IllegalArgumentException e) {
      Log.warn("shepard.secrets.encryption-key is present but not valid base64 — treating as absent");
      return null;
    }
  }
}
