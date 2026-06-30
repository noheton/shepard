package de.dlr.shepard.plugins.video.transcode;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.references.videostreamreference.daos.VideoStreamReferenceDAO;
import de.dlr.shepard.context.references.videostreamreference.model.VideoStreamReference;
import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * VIDEO-HEVC-TRANSCODE-BACKFILL-2026-06-30 — admin endpoint that re-submits
 * pre-feature {@code :VideoStreamReference} rows (HEVC source uploaded before
 * the on-upload transcode pipeline shipped) to the {@link
 * VideoTranscodeOrchestrator}. Without this, MFFD welding HEVC uploads
 * continue to fail browser playback even after PR-2a's pipeline shipped.
 *
 * <p>{@code POST /v2/admin/video/transcode-backfill} with optional JSON body
 * {@code {"filter":{"codec":"hevc"}, "limit":100, "dryRun":false}}. Returns
 * {@code {submitted, skipped, jobs:[{appId, status}, …]}}.
 *
 * <p>{@code @RolesAllowed("instance-admin")} per the CLAUDE.md admin-knobs rule.
 * Mutations are captured automatically by {@code ProvenanceCaptureFilter}.
 */
@Path("/v2/admin/video/transcode-backfill")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
@Tag(name = "Admin")
public class VideoTranscodeBackfillRest {

  @Inject
  VideoStreamReferenceDAO videoStreamReferenceDAO;

  @Inject
  VideoTranscodeOrchestrator orchestrator;

  @POST
  @Operation(
    operationId = "transcodeBackfill",
    summary = "VIDEO-HEVC-TRANSCODE-BACKFILL — re-submit existing video references for proxy transcode.",
    description =
      "Queries every non-deleted :VideoStreamReference whose proxyStatus IS NULL OR FAILED, " +
      "optionally narrowed by codec, and submits each to the VideoTranscodeOrchestrator. " +
      "Body (all optional): {\"filter\":{\"codec\":\"hevc\"}, \"limit\":100, \"dryRun\":false}. " +
      "Returns {submitted, skipped, jobs:[{appId, status}]}."
  )
  @APIResponse(responseCode = "200", description = "Backfill submitted (or dry-run preview).")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "instance-admin role required.")
  public Response backfill(Map<String, Object> body) {
    String codec = null;
    int limit = 0;
    boolean dryRun = false;
    if (body != null) {
      Object filter = body.get("filter");
      if (filter instanceof Map<?, ?> f) {
        Object c = f.get("codec");
        if (c instanceof String s && !s.isBlank()) codec = s.trim();
      }
      Object l = body.get("limit");
      if (l instanceof Number n) limit = n.intValue();
      Object d = body.get("dryRun");
      if (Boolean.TRUE.equals(d)) dryRun = true;
    }

    List<VideoStreamReference> candidates;
    try {
      candidates = videoStreamReferenceDAO.findBackfillCandidates(codec, limit);
    } catch (Exception ex) {
      Log.errorf("VIDEO-HEVC-TRANSCODE-BACKFILL: query failed: %s", ex.getMessage());
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
        .type("application/problem+json")
        .entity(new ProblemJson(
          "urn:shepard:error:internal",
          Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase(),
          Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
          ex.getMessage(), null))
        .build();
    }

    int submitted = 0;
    int skipped = 0;
    List<Map<String, Object>> jobs = new ArrayList<>(candidates.size());
    for (VideoStreamReference ref : candidates) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("appId", ref.getAppId());
      row.put("name", ref.getName());
      row.put("videoCodec", ref.getVideoCodec());
      row.put("priorProxyStatus", ref.getProxyStatus());
      if (dryRun) {
        row.put("status", "dryRun");
        skipped++;
      } else {
        try {
          VideoStreamReference after = orchestrator.submit(ref);
          row.put("status", after != null ? after.getProxyStatus() : "skipped");
          submitted++;
        } catch (RuntimeException ex) {
          Log.warnf("VIDEO-HEVC-TRANSCODE-BACKFILL: submit failed for appId=%s: %s",
            ref.getAppId(), ex.getMessage());
          row.put("status", "error");
          row.put("error", ex.getMessage());
          skipped++;
        }
      }
      jobs.add(row);
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("submitted", submitted);
    result.put("skipped", skipped);
    result.put("total", candidates.size());
    result.put("dryRun", dryRun);
    if (codec != null) result.put("codecFilter", codec);
    result.put("jobs", jobs);
    Log.infof("VIDEO-HEVC-TRANSCODE-BACKFILL: submitted=%d skipped=%d total=%d dryRun=%s",
      submitted, skipped, candidates.size(), dryRun);
    return Response.ok(result).build();
  }
}
