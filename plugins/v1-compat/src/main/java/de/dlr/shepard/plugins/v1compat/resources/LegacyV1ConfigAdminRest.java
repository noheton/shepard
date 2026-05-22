package de.dlr.shepard.plugins.v1compat.resources;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.plugins.v1compat.entities.LegacyV1Config;
import de.dlr.shepard.plugins.v1compat.io.LegacyV1ConfigIO;
import de.dlr.shepard.plugins.v1compat.io.LegacyV1ConfigPatchIO;
import de.dlr.shepard.plugins.v1compat.services.LegacyV1ConfigService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * V1COMPAT.0 — admin REST surface for the v1 compat marker plugin
 * (Phase 1). Lives under {@code /v2/admin/legacy/v1/...}, every
 * endpoint gated {@code @RolesAllowed("instance-admin")} per the
 * UH1a / N1c2 precedent.
 *
 * <p>Two operations:
 *
 * <ul>
 *   <li>{@code GET /v2/admin/legacy/v1/config} — read the singleton
 *       (seeds it on first read if absent).</li>
 *   <li>{@code PATCH /v2/admin/legacy/v1/config} — RFC 7396 merge-
 *       patch the {@code enabled} field. Other fields are read-only
 *       in Phase 1 (per clarification 2 lean A; the only field).</li>
 * </ul>
 *
 * <p>Mutations land in the {@code :Activity} audit table via the
 * existing {@code ProvenanceCaptureFilter} (PROV1a) — no additional
 * audit wiring needed here. The caller's sub is taken from
 * {@link SecurityContext#getUserPrincipal()} and stamped on the
 * row's {@code updatedBy} field for human-readable audit.
 */
@Path("/v2/admin/legacy/v1/config")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
@Tag(name = "Admin")
public class LegacyV1ConfigAdminRest {

  @Inject
  LegacyV1ConfigService service;

  @GET
  @Operation(
    summary = "Read the :LegacyV1Config singleton.",
    description = "Returns the current state of the v1 compat surface toggle. " +
    "Seeds the singleton from the deploy-time shepard.legacy.v1.enabled default if no " +
    "row exists yet (defence-in-depth against the V63 migration having been skipped)."
  )
  @APIResponse(
    responseCode = "200",
    description = "Current v1 compat config.",
    content = @Content(schema = @Schema(implementation = LegacyV1ConfigIO.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response getConfig() {
    LegacyV1Config cfg = service.current();
    return Response.ok(LegacyV1ConfigIO.from(cfg)).build();
  }

  @PATCH
  @Consumes({ Constants.APPLICATION_MERGE_PATCH_JSON, MediaType.APPLICATION_JSON })
  @Operation(
    summary = "RFC 7396 merge-patch the :LegacyV1Config singleton.",
    description = "Phase 1 minimal shape: only the `enabled` field is patchable. Absent " +
    "field = leave alone; explicit true/false = flip. Flipping `enabled=false` makes " +
    "every /shepard/api/... request return HTTP 410 Gone with an RFC 7807 problem-" +
    "detail body. The runtime value wins over the deploy-time install default forever " +
    "after the first PATCH."
  )
  @APIResponse(
    responseCode = "200",
    description = "Post-patch :LegacyV1Config.",
    content = @Content(schema = @Schema(implementation = LegacyV1ConfigIO.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response patchConfig(LegacyV1ConfigPatchIO patch, @Context SecurityContext sc) {
    if (patch == null || patch.enabled() == null) {
      // No-op patch — return the current state (RFC 7396 "absent =
      // leave alone" — an empty body is a no-op merge-patch).
      LegacyV1Config current = service.current();
      return Response.ok(LegacyV1ConfigIO.from(current)).build();
    }
    String actor = sc != null && sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
    LegacyV1Config post = service.setEnabled(patch.enabled(), actor);
    return Response.ok(LegacyV1ConfigIO.from(post)).build();
  }
}
