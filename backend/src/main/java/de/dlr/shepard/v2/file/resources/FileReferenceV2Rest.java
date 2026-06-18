package de.dlr.shepard.v2.file.resources;

import com.fasterxml.jackson.databind.JsonNode;
import de.dlr.shepard.common.util.HttpRangeUtil;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

/**
 * {@code /v2/files/...} REST surface — ALL ENDPOINTS RETIRED
 * (APISIMP-FILE-PATH-RETIRE-2).
 *
 * <p>The {@code POST /v2/files} multipart upload was retired first in
 * APISIMP-KIND-DISCRIMINATOR-2 (PR&nbsp;#1966). The remaining CRUD
 * endpoints are retired here in APISIMP-FILE-PATH-RETIRE-2. Every
 * method now returns HTTP 410 Gone with a {@code detail} field that
 * points to the unified {@code /v2/references/...} replacement.
 *
 * <p>Migration map:
 * <ul>
 *   <li>{@code POST   /v2/files} →
 *       {@code POST /v2/references?kind=file&dataObjectAppId=<doAppId>}
 *       then {@code PUT /v2/references/{appId}/content}</li>
 *   <li>{@code GET    /v2/files/by-data-object/{doAppId}} →
 *       {@code GET /v2/references?kind=file&dataObjectAppId={doAppId}}</li>
 *   <li>{@code GET    /v2/files/{appId}} →
 *       {@code GET /v2/references/{appId}}</li>
 *   <li>{@code GET    /v2/files/{appId}/content} →
 *       {@code GET /v2/references/{appId}/content}</li>
 *   <li>{@code PATCH  /v2/files/{appId}} →
 *       {@code PATCH /v2/references/{appId}}</li>
 *   <li>{@code DELETE /v2/files/{appId}} →
 *       {@code DELETE /v2/references/{appId}}</li>
 * </ul>
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/files")
@RequestScoped
@Tag(name = "File references")
public class FileReferenceV2Rest {

  // ─── POST /v2/files — RETIRED APISIMP-KIND-DISCRIMINATOR-2 ───────────────

  @POST
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @Operation(
    operationId = "createFileReferenceMultipartRetired",
    summary = "RETIRED — use POST /v2/references?kind=file + PUT /v2/references/{appId}/content.",
    description =
      "Retired in APISIMP-KIND-DISCRIMINATOR-2 (PR #1966). Migrate: " +
      "(1) POST /v2/references?kind=file&dataObjectAppId=<doAppId> with JSON body {\"name\":\"...\"}; " +
      "(2) PUT /v2/references/<appId>/content?filename=<name> with application/octet-stream body."
  )
  @APIResponse(responseCode = "410", description = "Gone — see POST /v2/references?kind=file + PUT /v2/references/{appId}/content.")
  public Response createSingleton(
    @QueryParam("parentDataObjectAppId") String parentDataObjectAppId,
    @QueryParam("name") String name,
    @RestForm("file") FileUpload upload,
    @Context SecurityContext securityContext
  ) {
    return gone(
      "/problems/file-references.multipart-upload-retired",
      "POST /v2/files multipart upload retired (APISIMP-KIND-DISCRIMINATOR-2). " +
      "Step 1: POST /v2/references?kind=file&dataObjectAppId=<doAppId> with JSON body {\"name\":\"<name>\"}. " +
      "Step 2: PUT /v2/references/<appId>/content?filename=<original-name> with Content-Type: application/octet-stream body."
    );
  }

  // ─── GET /v2/files/by-data-object/{doAppId} — RETIRED APISIMP-FILE-PATH-RETIRE-2 ──

  @GET
  @Path("/by-data-object/{dataObjectAppId}")
  @Operation(
    summary = "RETIRED — use GET /v2/references?kind=file&dataObjectAppId={dataObjectAppId}.",
    description = "Retired in APISIMP-FILE-PATH-RETIRE-2. Migrate to: GET /v2/references?kind=file&dataObjectAppId={dataObjectAppId}."
  )
  @APIResponse(responseCode = "410", description = "Gone — use GET /v2/references?kind=file&dataObjectAppId={dataObjectAppId}.")
  public Response listByDataObject(
    @PathParam("dataObjectAppId") String dataObjectAppId,
    @Context SecurityContext securityContext
  ) {
    return gone(
      "/problems/file-references.list-by-do-retired",
      "GET /v2/files/by-data-object/" + dataObjectAppId + " retired (APISIMP-FILE-PATH-RETIRE-2). " +
      "Migrate to: GET /v2/references?kind=file&dataObjectAppId=" + dataObjectAppId
    );
  }

  // ─── GET /v2/files/{appId} — RETIRED APISIMP-FILE-PATH-RETIRE-2 ──────────

  @GET
  @Path("/{appId}")
  @Operation(
    summary = "RETIRED — use GET /v2/references/{appId}.",
    description = "Retired in APISIMP-FILE-PATH-RETIRE-2. Migrate to: GET /v2/references/{appId}."
  )
  @APIResponse(responseCode = "410", description = "Gone — use GET /v2/references/{appId}.")
  public Response getSingleton(
    @PathParam("appId") String appId,
    @Context SecurityContext securityContext
  ) {
    return gone(
      "/problems/file-references.get-retired",
      "GET /v2/files/" + appId + " retired (APISIMP-FILE-PATH-RETIRE-2). " +
      "Migrate to: GET /v2/references/" + appId
    );
  }

  // ─── GET /v2/files/{appId}/content — RETIRED APISIMP-FILE-PATH-RETIRE-2 ──

  @GET
  @Path("/{appId}/content")
  @Produces({ MediaType.APPLICATION_OCTET_STREAM, MediaType.APPLICATION_JSON })
  @Operation(
    summary = "RETIRED — use GET /v2/references/{appId}/content.",
    description = "Retired in APISIMP-FILE-PATH-RETIRE-2. Migrate to: GET /v2/references/{appId}/content."
  )
  @APIResponse(responseCode = "410", description = "Gone — use GET /v2/references/{appId}/content.")
  public Response getContent(
    @PathParam("appId") String appId,
    @HeaderParam("Range") String rangeHeader,
    @Context SecurityContext securityContext
  ) {
    return gone(
      "/problems/file-references.content-retired",
      "GET /v2/files/" + appId + "/content retired (APISIMP-FILE-PATH-RETIRE-2). " +
      "Migrate to: GET /v2/references/" + appId + "/content"
    );
  }

  // ─── PATCH /v2/files/{appId} — RETIRED APISIMP-FILE-PATH-RETIRE-2 ────────

  @PATCH
  @Path("/{appId}")
  @Consumes({ "application/merge-patch+json", MediaType.APPLICATION_JSON })
  @Operation(
    summary = "RETIRED — use PATCH /v2/references/{appId}.",
    description = "Retired in APISIMP-FILE-PATH-RETIRE-2. Migrate to: PATCH /v2/references/{appId}."
  )
  @APIResponse(responseCode = "410", description = "Gone — use PATCH /v2/references/{appId}.")
  public Response patchSingleton(
    @PathParam("appId") String appId,
    JsonNode body,
    @Context SecurityContext securityContext
  ) {
    return gone(
      "/problems/file-references.patch-retired",
      "PATCH /v2/files/" + appId + " retired (APISIMP-FILE-PATH-RETIRE-2). " +
      "Migrate to: PATCH /v2/references/" + appId
    );
  }

  // ─── DELETE /v2/files/{appId} — RETIRED APISIMP-FILE-PATH-RETIRE-2 ───────

  @DELETE
  @Path("/{appId}")
  @Operation(
    summary = "RETIRED — use DELETE /v2/references/{appId}.",
    description = "Retired in APISIMP-FILE-PATH-RETIRE-2. Migrate to: DELETE /v2/references/{appId}."
  )
  @APIResponse(responseCode = "410", description = "Gone — use DELETE /v2/references/{appId}.")
  public Response deleteSingleton(
    @PathParam("appId") String appId,
    @Context SecurityContext securityContext
  ) {
    return gone(
      "/problems/file-references.delete-retired",
      "DELETE /v2/files/" + appId + " retired (APISIMP-FILE-PATH-RETIRE-2). " +
      "Migrate to: DELETE /v2/references/" + appId
    );
  }

  // ─── helpers kept for test-compat ─────────────────────────────────────────

  /**
   * @deprecated Delegates to {@link HttpRangeUtil#parseRange}; kept so
   *   existing {@code FileReferenceV2RestTest} tests continue to compile.
   *   New code should import {@link HttpRangeUtil} directly.
   */
  @Deprecated
  static long[] parseRange(String header, long total) {
    return HttpRangeUtil.parseRange(header, total);
  }

  /**
   * Convert a Jackson {@link JsonNode} object into a plain
   * {@code Map<String, Object>} preserving null values.
   * Kept so {@code FileReferenceV2RestTest.jsonNodeToMap_preservesScalars}
   * continues to compile without changes.
   */
  Map<String, Object> jsonNodeToMap(JsonNode node) {
    Map<String, Object> out = new LinkedHashMap<>();
    var fields = node.fields();
    while (fields.hasNext()) {
      var e = fields.next();
      JsonNode v = e.getValue();
      if (v == null || v.isNull()) {
        out.put(e.getKey(), null);
      } else if (v.isTextual()) {
        out.put(e.getKey(), v.asText());
      } else if (v.isInt() || v.isLong()) {
        out.put(e.getKey(), v.asLong());
      } else if (v.isNumber()) {
        out.put(e.getKey(), v.asDouble());
      } else if (v.isBoolean()) {
        out.put(e.getKey(), v.asBoolean());
      } else if (v.isObject()) {
        Map<String, Object> sub = new HashMap<>();
        var inner = v.fields();
        while (inner.hasNext()) {
          var ie = inner.next();
          sub.put(ie.getKey(), ie.getValue() == null || ie.getValue().isNull() ? null : ie.getValue().asText());
        }
        out.put(e.getKey(), sub);
      } else {
        out.put(e.getKey(), v.toString());
      }
    }
    return out;
  }

  private static Response gone(String type, String detail) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("status", 410);
    body.put("title", "Gone");
    body.put("type", type);
    body.put("detail", detail);
    return Response.status(Response.Status.GONE)
      .type(MediaType.APPLICATION_JSON)
      .entity(body)
      .build();
  }
}
