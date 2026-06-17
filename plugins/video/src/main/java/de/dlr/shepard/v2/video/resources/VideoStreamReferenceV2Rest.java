package de.dlr.shepard.v2.video.resources;

import de.dlr.shepard.common.exceptions.ProblemJson;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

/**
 * APISIMP-VIDEO-STREAMREF-PATH — 410 tombstone surface for the retired
 * per-DataObject video upload and download paths.
 *
 * <p>Both operations have moved to the unified {@code /v2/references} surface:
 * <ul>
 *   <li>Upload: {@code POST /v2/references?kind=video} (metadata) +
 *       {@code PUT /v2/references/{appId}/content} (bytes)</li>
 *   <li>Download: {@code GET /v2/references/{appId}/download}</li>
 * </ul>
 *
 * <p>Every method in this class returns 410 Gone with a machine-readable
 * {@code Link} header pointing to the replacement endpoint.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/data-objects/{dataObjectAppId}/video-stream-references")
@RequestScoped
@Tag(name = "Video stream references (retired paths)")
public class VideoStreamReferenceV2Rest {

  private static final String SUCCESSOR_UPLOAD =
    "</v2/references>; rel=\"successor-version\"";
  private static final String SUCCESSOR_DOWNLOAD_TEMPLATE =
    "/v2/references/{appId}/download";

  // ─── retired upload ──────────────────────────────────────────────────────

  @POST
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @Operation(
    operationId = "uploadVideoStreamReference",
    summary = "RETIRED — use POST /v2/references?kind=video + PUT /v2/references/{appId}/content.",
    description =
      "This path has been retired (APISIMP-VIDEO-STREAMREF-PATH). " +
      "Create a VideoStreamReference via the two-step pattern: " +
      "(1) POST /v2/references?kind=video&dataObjectAppId=<doAppId> with JSON body {\"name\":\"<name>\"} → 201 with ReferenceV2IO; " +
      "(2) PUT /v2/references/<appId>/content?filename=<original-name> with application/octet-stream body → 200."
  )
  @APIResponse(responseCode = "410", description = "Gone — see POST /v2/references?kind=video + PUT /v2/references/{appId}/content.")
  public Response upload(
    @PathParam("dataObjectAppId") String dataObjectAppId,
    @QueryParam("name") String name,
    @RestForm("file") FileUpload upload
  ) {
    return gone(
      "POST /v2/data-objects/{dataObjectAppId}/video-stream-references has moved. " +
      "Step 1: POST /v2/references?kind=video&dataObjectAppId=" + dataObjectAppId +
      " with JSON body {\"name\":\"<name>\"} → 201 with ReferenceV2IO containing appId. " +
      "Step 2: PUT /v2/references/<appId>/content?filename=<original-name> with application/octet-stream body.",
      SUCCESSOR_UPLOAD
    );
  }

  // ─── retired download ────────────────────────────────────────────────────

  @GET
  @Path("/{appId}/download")
  @Produces({ MediaType.APPLICATION_OCTET_STREAM, MediaType.APPLICATION_JSON })
  @Operation(
    operationId = "downloadVideoStreamReference",
    summary = "RETIRED — use GET /v2/references/{appId}/download.",
    description = "This path has been retired (APISIMP-VIDEO-STREAMREF-PATH). Use GET /v2/references/{appId}/download instead."
  )
  @APIResponse(responseCode = "410", description = "Gone — use GET /v2/references/{appId}/download.")
  public Response download(
    @PathParam("dataObjectAppId") String dataObjectAppId,
    @PathParam("appId") String appId,
    @HeaderParam("Range") String rangeHeader
  ) {
    String successor = "<" + SUCCESSOR_DOWNLOAD_TEMPLATE.replace("{appId}", appId) + ">; rel=\"successor-version\"";
    return gone(
      "GET /v2/data-objects/{dataObjectAppId}/video-stream-references/{appId}/download has moved. " +
      "Use GET /v2/references/" + appId + "/download instead.",
      successor
    );
  }

  // ─── helpers ─────────────────────────────────────────────────────────────

  private static Response gone(String detail, String linkHeader) {
    return Response.status(Response.Status.GONE)
      .type("application/problem+json")
      .header("Link", linkHeader)
      .entity(new ProblemJson(
        "urn:shepard:error:gone",
        "Gone",
        Response.Status.GONE.getStatusCode(),
        detail,
        null))
      .build();
  }
}
