package de.dlr.shepard.v2.publish.resources;

import de.dlr.shepard.v2.common.ProblemResponse;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * APISIMP-PUBLICATIONS-KIND-410 — tombstone.
 *
 * <p>{@code GET /v2/{kind}/{appId}/publications} is superseded by the
 * kind-agnostic flat alias {@code GET /v2/publications?entityAppId={appId}}
 * which does not require the caller to know the entity-kind URL segment.
 *
 * <p>All requests to this path return 410 Gone with a {@code Location} header
 * pointing to the canonical successor.
 */
@Produces(MediaType.APPLICATION_JSON)
@Path("/v2/{kind}/{appId}/publications")
@RequestScoped
@Tag(name = "Publish")
public class PublicationsListRest {

  private static final String SUCCESSOR = "/v2/publications";

  private static final String GONE_DETAIL =
    "This endpoint is deprecated and removed. " +
    "Use GET /v2/publications?entityAppId={appId} instead — it returns the same data " +
    "without requiring the entity-kind URL segment.";

  private static Response gone() {
    return ProblemResponse.problemBuilder(
        "urn:shepard:error:gone", "Gone", Response.Status.GONE.getStatusCode(), GONE_DETAIL)
      .header("Location", SUCCESSOR)
      .header("Link", "<" + SUCCESSOR + ">; rel=\"successor-version\"")
      .build();
  }

  @GET
  @Operation(
    operationId = "listPublicationsByKind",
    summary = "[GONE] List Publications by entity kind — use GET /v2/publications?entityAppId=…",
    deprecated = true
  )
  @APIResponse(responseCode = "410", description = "Endpoint removed. Use GET /v2/publications?entityAppId={appId}.")
  public Response list(
    @PathParam("kind") String kind,
    @PathParam("appId") String appId
  ) {
    return gone();
  }

  /**
   * Build a fully-qualified URL at the supplied application-relative path
   * using the request's own scheme + host + port.
   *
   * <p>Used by {@link FlatPublicationsRest} for KIP resolver URL construction.
   * Kept here during the tombstone phase; will move to a shared utility once
   * a logical home exists.
   */
  static String absoluteUrl(UriInfo uriInfo, String applicationPath) {
    if (uriInfo == null) return applicationPath;
    var base = uriInfo.getBaseUri();
    String scheme = base.getScheme();
    String host = base.getHost();
    int port = base.getPort();
    StringBuilder sb = new StringBuilder();
    sb.append(scheme).append("://").append(host);
    if (port > 0 && !((scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443))) {
      sb.append(":").append(port);
    }
    sb.append(applicationPath.startsWith("/") ? applicationPath : "/" + applicationPath);
    return sb.toString();
  }
}
