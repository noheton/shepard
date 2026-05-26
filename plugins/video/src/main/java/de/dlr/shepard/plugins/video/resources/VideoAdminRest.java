package de.dlr.shepard.plugins.video.resources;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.plugins.video.entities.VideoConfig;
import de.dlr.shepard.plugins.video.io.VideoConfigIO;
import de.dlr.shepard.plugins.video.io.VideoConfigPatchIO;
import de.dlr.shepard.plugins.video.services.VideoConfigService;
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
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * VID1c — admin REST surface for the video plugin runtime config.
 *
 * <p>Lives under {@code /v2/admin/video/...} — exclusively
 * {@code @RolesAllowed("instance-admin")}. Implements the
 * operator-knob pattern from CLAUDE.md ("Always: surface operator
 * knobs in the admin config"), mirroring UH1a ({@code /v2/admin/unhide}),
 * N1c2 ({@code /v2/admin/semantic}), and AAS1c
 * ({@code /v2/admin/aas}).
 *
 * <p>Two endpoints:
 * <ul>
 *   <li>{@code GET  /v2/admin/video/config} — read the singleton.</li>
 *   <li>{@code PATCH /v2/admin/video/config} — RFC 7396 merge-patch.</li>
 * </ul>
 *
 * @see VideoConfigService
 * @see de.dlr.shepard.plugins.video.entities.VideoConfig
 */
@Path("/v2/admin/video")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
@Tag(name = "Admin")
public class VideoAdminRest {

  @Inject
  VideoConfigService service;

  @GET
  @Path("/config")
  @Operation(
    summary = "Read the current :VideoConfig singleton.",
    description = "Returns the runtime-mutable video-plugin config — ffprobeEnabled " +
    "(whether VideoProbeService.probe() runs on uploads) and maxFileSizeMb " +
    "(upload size cap in MiB; absent = unlimited)."
  )
  @APIResponse(
    responseCode = "200",
    description = "Current video plugin config (singleton).",
    content = @Content(schema = @Schema(implementation = VideoConfigIO.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response getConfig() {
    VideoConfig cfg = service.current();
    return Response.ok(VideoConfigIO.from(cfg)).build();
  }

  @PATCH
  @Path("/config")
  @Operation(
    summary = "RFC 7396 merge-patch the :VideoConfig singleton.",
    description = "Patchable fields: ffprobeEnabled, maxFileSizeMb. RFC 7396 " +
    "semantics — absent = leave alone, null = clear (maxFileSizeMb only, " +
    "removes the upload-size cap), value = replace. " +
    "PROV1a's ProvenanceCaptureFilter captures this PATCH as an :Activity row " +
    "(targetKind=VideoConfig) so the audit trail records who changed what when."
  )
  @APIResponse(
    responseCode = "200",
    description = "Updated config returned in the same shape as GET.",
    content = @Content(schema = @Schema(implementation = VideoConfigIO.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response patchConfig(VideoConfigPatchIO body) {
    VideoConfigPatchIO patch = body == null ? new VideoConfigPatchIO() : body;

    VideoConfigService.VideoPatch svcPatch = new VideoConfigService.VideoPatch();
    svcPatch.ffprobeEnabled = patch.getFfprobeEnabled();
    svcPatch.maxFileSizeMb = patch.getMaxFileSizeMb();
    svcPatch.maxFileSizeMbTouched = patch.isMaxFileSizeMbTouched();

    VideoConfig saved = service.patch(svcPatch);
    return Response.ok(VideoConfigIO.from(saved)).build();
  }
}
