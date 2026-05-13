package de.dlr.shepard.plugins.unhide.resources;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.plugins.unhide.entities.UnhideConfig;
import de.dlr.shepard.plugins.unhide.io.FeedIO;
import de.dlr.shepard.plugins.unhide.services.UnhideConfigService;
import de.dlr.shepard.plugins.unhide.services.UnhideFeedService;
import io.quarkus.logging.Log;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * UH1a — public feed endpoint for the Helmholtz Unhide harvester.
 *
 * <p>Path: {@code GET /v2/unhide/feed.jsonld}. Listed in
 * {@link de.dlr.shepard.common.filters.PublicEndpointRegistry} so
 * the {@code JWTFilter} doesn't auto-reject — the access model is
 * runtime-mutable on {@code :UnhideConfig.feedPublic}, which a
 * static registry can't express.
 *
 * <p>Auth precedence inside the resource:
 *
 * <ol>
 *   <li>{@code :UnhideConfig.enabled=false} → 503 RFC 7807
 *       {@code unhide.feed.disabled} (no auth attempted).</li>
 *   <li>{@code feedPublic=true} → 200 (no auth required).</li>
 *   <li>{@code feedPublic=false} AND X-API-KEY header present AND
 *       it hashes to {@code :UnhideConfig.harvestApiKeyHash} → 200.</li>
 *   <li>Otherwise → 401 RFC 7807 {@code unhide.harvest-key.absent}.</li>
 * </ol>
 *
 * <p><b>Auth-fallback simplification.</b> The {@code aidocs/67} design
 * mentions "OR caller has instance-admin role" as a private-feed
 * fallback. Phase 1 omits the instance-admin shortcut and surfaces
 * the harvest key as the sole non-public auth path — operationally
 * cleaner (one auth model for the harvest path) and the admin
 * inspection path is "mint a harvest key, then curl it" which is
 * what the CLI does anyway. The instance-admin fallback can be
 * grafted on in UH1b if operator feedback wants it.
 *
 * @see UnhideConfigService
 * @see UnhideFeedService
 */
@Path("/v2/unhide")
@Produces({ "application/ld+json", "application/json" })
@RequestScoped
@PermitAll
@Tag(name = "Unhide")
public class UnhideFeedRest {

  /** RFC 7807 type URI for the master-toggle-off path. */
  static final String PROBLEM_TYPE_FEED_DISABLED = "/problems/unhide.feed.disabled";

  /** RFC 7807 type URI for the missing-/wrong-API-key path. */
  static final String PROBLEM_TYPE_HARVEST_KEY_ABSENT = "/problems/unhide.harvest-key.absent";

  @Inject
  UnhideConfigService configService;

  @Inject
  UnhideFeedService feedService;

  @GET
  @Path("/feed.jsonld")
  @Operation(
    summary = "Helmholtz Unhide harvest feed (schema.org + metadata4ing JSON-LD).",
    description = "Lists every Collection on this shepard instance in the schema.org / " +
    "metadata4ing JSON-LD shape that Unhide's inward-mappings consume. Cursor-paginated " +
    "via ?page=N&page-size=N (page-size capped at 1000). When :UnhideConfig.enabled=false " +
    "the endpoint returns 503 unhide.feed.disabled; when feedPublic=false, a matching " +
    "X-API-KEY header is required (use POST /v2/admin/unhide/harvest-key/rotate to mint)."
  )
  @APIResponse(
    responseCode = "200",
    description = "The current feed page.",
    content = @Content(schema = @Schema(implementation = FeedIO.class))
  )
  @APIResponse(
    responseCode = "401",
    description = "Missing or invalid X-API-KEY when feedPublic=false (RFC 7807).",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemJson.class))
  )
  @APIResponse(
    responseCode = "503",
    description = "Unhide integration is disabled (master toggle off) (RFC 7807).",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemJson.class))
  )
  public Response feed(
    @QueryParam("page") Integer page,
    @QueryParam("page-size") Integer pageSize,
    @Context HttpHeaders headers,
    @Context UriInfo uriInfo
  ) {
    UnhideConfig cfg = configService.current();
    if (!cfg.isEnabled()) {
      Log.debugf("UH1a: feed request rejected — :UnhideConfig.enabled=false");
      return problem(
        PROBLEM_TYPE_FEED_DISABLED,
        "Unhide integration is disabled",
        Status.SERVICE_UNAVAILABLE,
        "This shepard instance has the Helmholtz Unhide integration turned off. " +
        "An instance-admin can enable it via PATCH /v2/admin/unhide/config or " +
        "`shepard-admin unhide enable`."
      );
    }

    if (!cfg.isFeedPublic()) {
      String apiKey = headers == null ? null : headers.getHeaderString(Constants.API_KEY_HEADER);
      if (!configService.verifyHarvestKey(apiKey)) {
        Log.debugf("UH1a: feed request rejected — feedPublic=false, X-API-KEY %s",
          apiKey == null ? "absent" : "did not match harvest hash");
        return problem(
          PROBLEM_TYPE_HARVEST_KEY_ABSENT,
          "Valid harvest API key required",
          Status.UNAUTHORIZED,
          "This feed is non-public. Set X-API-KEY to the harvest API key (mint via " +
          "POST /v2/admin/unhide/harvest-key/rotate) or flip feedPublic=true via " +
          "PATCH /v2/admin/unhide/config."
        );
      }
    }

    int p = page == null ? 0 : page;
    int ps = pageSize == null ? UnhideFeedService.DEFAULT_PAGE_SIZE : pageSize;
    String baseUrl = uriInfo == null ? "" : uriInfo.getBaseUri().toString();
    FeedIO body = feedService.buildFeed(cfg, baseUrl, p, ps);
    return Response.ok(body).type(MediaType_JSON_LD).build();
  }

  // ─── constants + helpers ────────────────────────────────────────────────

  /** JSON-LD media type. */
  static final String MediaType_JSON_LD = "application/ld+json";

  /** Plain JSON fallback for clients that don't negotiate ld+json. */
  static final String MediaType_APPLICATION_JSON = "application/json";

  private Response problem(String type, String title, Status status, String detail) {
    ProblemJson body = new ProblemJson(type, title, status.getStatusCode(), detail, null);
    return Response.status(status).type("application/problem+json").entity(body).build();
  }
}
