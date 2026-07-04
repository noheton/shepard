package de.dlr.shepard.v2.video.resources;

import de.dlr.shepard.common.exceptions.ProblemJson;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

/**
 * APISIMP-VIDEO-STREAMREF-PATH tombstone — this class now contains only
 * 410 Gone stubs for the two paths that migrated to the unified
 * {@code /v2/references} surface.
 *
 * <ul>
 *   <li>{@code POST /v2/data-objects/{doId}/video-stream-references} →
 *       use two-step: {@code POST /v2/references?kind=video&dataObjectAppId=…}
 *       then {@code PUT /v2/references/{appId}/content?filename=…}.</li>
 *   <li>{@code GET /v2/data-objects/{doId}/video-stream-references/{appId}/download} →
 *       use {@code GET /v2/references/{appId}/content} (range-aware).</li>
 * </ul>
 *
 * <p>CRUD (list / get-one / patch / delete) was removed by PLUGIN-PERKIND-CRUD-CLEANUP
 * and is served by {@code ReferencesV2Rest} + {@code VideoStreamReferenceKindHandler}.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/data-objects/{dataObjectAppId}/video-stream-references")
@RequestScoped
@Tag(name = "Video stream references")
public class VideoStreamReferenceV2Rest {

  // No injections needed — this class only serves 410 Gone tombstone responses.

  // ─── upload — 410 tombstone ───────────────────────────────────────────────

  @POST
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @Operation(
    operationId = "uploadVideoStreamReference",
    summary = "[GONE] Multipart video upload — use two-step pattern instead.",
    description =
      "**APISIMP-VIDEO-STREAMREF-PATH**: this endpoint has been replaced by the unified two-step pattern:\n\n" +
      "1. `POST /v2/references?kind=video&dataObjectAppId={doAppId}` with JSON `{\"name\":\"video.mp4\"}` → returns `{appId}`\n" +
      "2. `PUT /v2/references/{appId}/content?filename=video.mp4` with raw `application/octet-stream` body\n\n" +
      "Returns **410 Gone** permanently."
  )
  @APIResponse(responseCode = "410", description = "Gone — use two-step POST /v2/references?kind=video + PUT /v2/references/{appId}/content.")
  public Response upload(
    @PathParam("dataObjectAppId") String dataObjectAppId,
    @QueryParam("name") String name,
    @RestForm("file") FileUpload upload,
    @Context SecurityContext sc
  ) {
    return Response.status(Response.Status.GONE)
      .type("application/problem+json")
      .header("Location", "/v2/references")
      .entity(new ProblemJson(
        "urn:shepard:error:gone",
        "Gone",
        Response.Status.GONE.getStatusCode(),
        "POST /v2/data-objects/{doId}/video-stream-references has been removed. " +
        "Use two-step: POST /v2/references?kind=video&dataObjectAppId=… then PUT /v2/references/{appId}/content?filename=…",
        null))
      .build();
  }

  // ─── download — 410 tombstone ─────────────────────────────────────────────

  @GET
  @Path("/{appId}/download")
  @Produces({ MediaType.APPLICATION_OCTET_STREAM, MediaType.APPLICATION_JSON })
  @Operation(
    operationId = "downloadVideoStreamReference",
    summary = "[GONE] Range-aware video download — use GET /v2/references/{appId}/content instead.",
    description =
      "**APISIMP-VIDEO-STREAMREF-PATH**: this path has been replaced by the unified " +
      "`GET /v2/references/{appId}/content` endpoint (range-aware, same 206 semantics).\n\n" +
      "Returns **410 Gone** permanently."
  )
  @APIResponse(responseCode = "410", description = "Gone — use GET /v2/references/{appId}/content.")
  public Response download(
    @PathParam("dataObjectAppId") String dataObjectAppId,
    @PathParam("appId") String appId,
    @HeaderParam("Range") String rangeHeader,
    @Context SecurityContext sc
  ) {
    return Response.status(Response.Status.GONE)
      .type("application/problem+json")
      .header("Location", "/v2/references/" + appId + "/content")
      .entity(new ProblemJson(
        "urn:shepard:error:gone",
        "Gone",
        Response.Status.GONE.getStatusCode(),
        "GET /v2/data-objects/{doId}/video-stream-references/{appId}/download has been removed. " +
        "Use GET /v2/references/" + appId + "/content (same range semantics).",
        null))
      .build();
  }

}
