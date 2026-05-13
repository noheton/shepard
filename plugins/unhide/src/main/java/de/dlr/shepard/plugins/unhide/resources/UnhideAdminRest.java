package de.dlr.shepard.plugins.unhide.resources;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.plugins.unhide.entities.UnhideConfig;
import de.dlr.shepard.plugins.unhide.io.HarvestKeyMintedIO;
import de.dlr.shepard.plugins.unhide.io.UnhideConfigIO;
import de.dlr.shepard.plugins.unhide.io.UnhideConfigPatchIO;
import de.dlr.shepard.plugins.unhide.services.UnhideConfigService;
import de.dlr.shepard.plugins.unhide.services.UnhideConfigService.MintResult;
import de.dlr.shepard.plugins.unhide.services.UnhideConfigService.ReadOnlyFieldException;
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
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.util.Date;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * UH1a — admin REST surface for the Helmholtz Unhide publish plugin.
 *
 * <p>Lives under {@code /v2/admin/unhide/...} — exclusively
 * {@code @RolesAllowed("instance-admin")} (the role guard runs
 * unconditionally, and the harvest-key minting path is one of the
 * highest-trust admin paths in the codebase). All response bodies
 * are {@code application/json} except the
 * {@code application/problem+json} envelopes wired through
 * {@link ProblemJson} on the error paths.
 *
 * <p>Wired entirely via CDI ({@code @Inject UnhideConfigService}) —
 * no static lookups, no hardcoded backend references. Per ADR-0023
 * + CLAUDE.md "plugin-first": when the {@code PluginManifest}
 * SPI lands and this plugin moves to drop-in-JAR distribution, the
 * REST resource binds via the same CDI scan with zero source changes
 * (only the build path flips, per the comment block on
 * {@code plugins/unhide/pom.xml}).
 *
 * @see UnhideConfigService
 * @see de.dlr.shepard.plugins.unhide.entities.UnhideConfig
 */
@Path("/v2/admin/unhide")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
@Tag(name = "Admin")
public class UnhideAdminRest {

  /** RFC 7807 type URI for the read-only-field-patched path. */
  static final String PROBLEM_TYPE_READ_ONLY_FIELD = "/problems/unhide.config.read-only-field";

  @Inject
  UnhideConfigService service;

  @GET
  @Path("/config")
  @Operation(
    summary = "Read the current :UnhideConfig singleton.",
    description = "Returns the runtime-mutable Unhide-integration config — enabled, " +
    "feedPublic, contactEmail, plus a masked fingerprint of the harvest API key (first " +
    "8 hex chars of the SHA-256) and its mint timestamp. The harvest-key hash itself is " +
    "never returned; the plaintext is shown exactly once at mint-time via the rotate " +
    "endpoint."
  )
  @APIResponse(
    responseCode = "200",
    description = "Current Unhide config (singleton).",
    content = @Content(schema = @Schema(implementation = UnhideConfigIO.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response getConfig() {
    UnhideConfig cfg = service.current();
    return Response.ok(UnhideConfigIO.from(cfg)).build();
  }

  @PATCH
  @Path("/config")
  @Operation(
    summary = "RFC 7396 merge-patch the :UnhideConfig singleton.",
    description = "Patchable fields: enabled, feedPublic, contactEmail. RFC 7396 " +
    "semantics — absent = leave alone, null = clear (contactEmail only), value = " +
    "replace. The harvest-key hash is read-only via this path; use POST " +
    "/v2/admin/unhide/harvest-key/rotate to mint a fresh key. PROV1a's " +
    "ProvenanceCaptureFilter captures this PATCH as an :Activity row " +
    "(targetKind=UnhideConfig) so the audit trail records who flipped what when."
  )
  @APIResponse(
    responseCode = "200",
    description = "Updated config returned in the same shape as GET.",
    content = @Content(schema = @Schema(implementation = UnhideConfigIO.class))
  )
  @APIResponse(
    responseCode = "400",
    description = "Caller tried to patch the read-only harvestApiKeyHash field (RFC 7807).",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemJson.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response patchConfig(UnhideConfigPatchIO body) {
    UnhideConfigPatchIO patch = body == null ? new UnhideConfigPatchIO() : body;

    UnhideConfigService.UnhidePatch svcPatch = new UnhideConfigService.UnhidePatch();
    svcPatch.enabled = patch.getEnabled();
    svcPatch.feedPublic = patch.getFeedPublic();
    svcPatch.contactEmail = patch.getContactEmail();
    svcPatch.contactEmailTouched = patch.isContactEmailTouched();
    svcPatch.harvestApiKeyHashTouched = patch.isHarvestApiKeyHashTouched();

    final UnhideConfig saved;
    try {
      saved = service.patch(svcPatch);
    } catch (ReadOnlyFieldException denied) {
      Log.warnf("UH1a: rejected PATCH /v2/admin/unhide/config — read-only field '%s' touched", denied.field());
      return problem(
        PROBLEM_TYPE_READ_ONLY_FIELD,
        "Field is read-only via PATCH",
        Status.BAD_REQUEST,
        "Field '" + denied.field() + "' cannot be set via PATCH. Use the dedicated harvest-key " +
        "rotate / revoke endpoints to mutate it."
      );
    }
    return Response.ok(UnhideConfigIO.from(saved)).build();
  }

  @POST
  @Path("/harvest-key/rotate")
  @Operation(
    summary = "Mint a fresh harvest API key.",
    description = "Generates a UUID v4 (SecureRandom-backed), stores its SHA-256 hex on " +
    ":UnhideConfig.harvestApiKeyHash, and returns the plaintext exactly once. Rotates if a " +
    "key already exists (the prior hash is replaced, the prior plaintext becomes unusable). " +
    "The response body is NOT recorded in :Activity — PROV1a's ProvenanceCaptureFilter " +
    "captures only the request method + path + status, never response bodies, so the " +
    "plaintext never enters the audit trail. The plaintext is also never logged server-side; " +
    "only the masked fingerprint (first 8 hex chars of the SHA-256) appears in INFO logs."
  )
  @APIResponse(
    responseCode = "200",
    description = "Fresh harvest API key. Save it immediately — it is not retrievable later.",
    content = @Content(schema = @Schema(implementation = HarvestKeyMintedIO.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response rotateHarvestKey() {
    MintResult result = service.rotateHarvestKey();
    HarvestKeyMintedIO body = new HarvestKeyMintedIO(
      result.plaintext(),
      UnhideConfigService.fingerprint(result.config().getHarvestApiKeyHash()),
      result.config().getHarvestApiKeyLastRotatedAt() == null
        ? null
        : new Date(result.config().getHarvestApiKeyLastRotatedAt()),
      HarvestKeyMintedIO.WARNING
    );
    return Response.ok(body).build();
  }

  @POST
  @Path("/harvest-key/revoke")
  @Operation(
    summary = "Revoke the current harvest API key.",
    description = "Clears :UnhideConfig.harvestApiKeyHash. When feedPublic=false (the " +
    "default), the feed becomes reachable only by instance-admin callers until a fresh " +
    "key is minted. The revoke is captured as an :Activity row via PROV1a."
  )
  @APIResponse(
    responseCode = "200",
    description = "Updated config after revoke (no harvest key present).",
    content = @Content(schema = @Schema(implementation = UnhideConfigIO.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response revokeHarvestKey() {
    UnhideConfig saved = service.revokeHarvestKey();
    return Response.ok(UnhideConfigIO.from(saved)).build();
  }

  /**
   * The HTTP DELETE verb on the same path is also wired so a
   * REST-purist operator can issue {@code DELETE
   * /v2/admin/unhide/harvest-key} — semantically equivalent to the
   * POST .../revoke variant. Both shapes are documented in the
   * design (aidocs/67 §6) so the CLI + curl pathways stay flexible.
   */
  @DELETE
  @Path("/harvest-key")
  @Operation(summary = "Revoke the current harvest API key (DELETE-verb variant of revoke).")
  @APIResponse(
    responseCode = "200",
    description = "Updated config after revoke.",
    content = @Content(schema = @Schema(implementation = UnhideConfigIO.class))
  )
  public Response deleteHarvestKey() {
    return revokeHarvestKey();
  }

  // ─── helpers ────────────────────────────────────────────────────────────

  private Response problem(String type, String title, Status status, String detail) {
    ProblemJson body = new ProblemJson(type, title, status.getStatusCode(), detail, null);
    return Response.status(status).type("application/problem+json").entity(body).build();
  }
}
