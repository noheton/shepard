package de.dlr.shepard.plugins.unhide.resources;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.plugins.unhide.entities.UnhideConfig;
import de.dlr.shepard.plugins.unhide.io.HarvestKeyMintedIO;
import de.dlr.shepard.plugins.unhide.io.UnhideConfigIO;
import de.dlr.shepard.plugins.unhide.services.UnhideConfigService;
import de.dlr.shepard.plugins.unhide.services.UnhideConfigService.MintResult;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Date;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * UH1a — admin REST surface for the Helmholtz Unhide publish plugin:
 * harvest-key minting + revocation only.
 *
 * <p>Config read/write ({@code GET|PATCH /v2/admin/config/unhide}) moved
 * to the generic
 * {@link de.dlr.shepard.plugins.unhide.config.UnhideConfigDescriptor}
 * (V2CONV-A7). These harvest-key endpoints stay here because they are
 * credential operations (mint a UUID v4 secret, store its SHA-256 hash),
 * not config-field mutations — per CLAUDE.md §"Surface operator knobs in
 * the admin config": "Optional sister endpoints for mint-and-rotate of
 * feature-bound credentials."
 *
 * <p>Lives under {@code /v2/admin/unhide/...}, exclusively
 * {@code @RolesAllowed("instance-admin")}.
 *
 * @see UnhideConfigService
 * @see de.dlr.shepard.plugins.unhide.config.UnhideConfigDescriptor
 */
@Path("/v2/admin/unhide")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
@Tag(name = "Admin")
public class UnhideAdminRest {

  @Inject
  UnhideConfigService service;

  @POST
  @Path("/harvest-key/rotate")
  @Operation(
    operationId = "rotateUnhideHarvestKey",
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
    operationId = "revokeUnhideHarvestKey",
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
  @Operation(operationId = "deleteUnhideHarvestKey", summary = "Revoke the current harvest API key (DELETE-verb variant of revoke).")
  @APIResponse(
    responseCode = "200",
    description = "Updated config after revoke.",
    content = @Content(schema = @Schema(implementation = UnhideConfigIO.class))
  )
  public Response deleteHarvestKey() {
    return revokeHarvestKey();
  }

}
