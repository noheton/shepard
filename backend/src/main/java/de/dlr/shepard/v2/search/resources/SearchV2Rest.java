package de.dlr.shepard.v2.search.resources;

import de.dlr.shepard.common.search.endpoints.BasicCollectionAttributes;
import de.dlr.shepard.common.search.io.QueryType;
import de.dlr.shepard.common.search.io.ResponseBody;
import de.dlr.shepard.common.search.io.ResultTriple;
import de.dlr.shepard.common.search.io.SearchBody;
import de.dlr.shepard.common.search.io.SearchParams;
import de.dlr.shepard.common.search.io.SearchScope;
import de.dlr.shepard.common.search.services.CollectionSearchService;
import de.dlr.shepard.common.search.services.DataObjectSearchService;
import de.dlr.shepard.common.util.TraversalRules;
import de.dlr.shepard.context.collection.daos.CollectionDAO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.v2.search.io.SearchV2ItemIO;
import de.dlr.shepard.v2.search.io.SearchV2ResultIO;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import static de.dlr.shepard.v2.common.ProblemResponse.problem;

/**
 * MISSING-V2-APPID-IN-SEARCH — global full-text search on the v2 surface.
 *
 * <p>The legacy {@code POST /shepard/api/search} endpoint returns results that
 * include numeric Neo4j node ids alongside {@code appId}. This endpoint exposes
 * only the stable {@code appId} (UUID v7), which the frontend and MCP tools use
 * for navigation.
 *
 * <p>Results are interleaved (collections first, then DataObjects) and paged
 * via a single {@code page} / {@code pageSize} window on the combined set —
 * no per-kind secondary pagination axes.
 *
 * <p>APISIMP-SEARCH-IN-MEMORY-MERGE-PAGE (option-b): pagination is pushed to the
 * DAO layer — only the items in the requested page window are materialised.
 * The combined result set is still capped at {@link #SEARCH_RESULT_CAP} for the
 * reported {@code total}; queries exceeding the cap should use {@code collectionAppId}
 * to narrow scope.
 */
@Path("/v2/search")
@RequestScoped
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Search")
public class SearchV2Rest {

  /** Maximum combined results reported in {@code total}. Documented in OpenAPI. */
  static final int SEARCH_RESULT_CAP = 1000;

  @Inject
  CollectionSearchService collectionSearchService;

  @Inject
  DataObjectSearchService dataObjectSearchService;

  @Inject
  CollectionDAO collectionDAO;

  @GET
  @Operation(
    operationId = "searchV2",
    summary = "Full-text search returning appId-keyed results",
    description =
      "Searches Collections and DataObjects by name / description and returns a unified " +
      "list where every item carries its stable `appId` (UUID v7). Unlike the legacy " +
      "`POST /shepard/api/search` endpoint, no internal Neo4j node id is exposed. " +
      "Results are interleaved (collections first, then DataObjects) and paged via a " +
      "single `page` / `pageSize` window on the combined set — no per-kind secondary " +
      "pagination. Only items in the requested page window are fetched from the database. " +
      "When `collectionAppId` is supplied, DataObject results are narrowed " +
      "to that collection and no collection items are returned (scoped DO search). " +
      "The combined result set is capped at 1000 items; use `collectionAppId` to narrow " +
      "scope when a broader query would exceed this limit. " +
      "Requires authentication."
  )
  @APIResponse(
    responseCode = "200",
    description =
      "Search results — items ordered collections-first, then dataobjects. " +
      "The combined set is capped at 1000 items; `total` reflects the capped count " +
      "when the cap is reached.",
    content = @Content(
      mediaType = MediaType.APPLICATION_JSON,
      schema = @Schema(implementation = SearchV2ResultIO.class)
    )
  )
  @APIResponse(responseCode = "400", description = "Query parameter `q` is blank/absent, or `collectionAppId` is not found.")
  @APIResponse(responseCode = "401", description = "Caller is not authenticated.")
  public Response search(
    @Parameter(description = "Full-text search query (required).", required = true)
    @QueryParam("q") String q,
    @Parameter(
      description = "Zero-based page index for the combined result set.",
      schema = @Schema(minimum = "0", defaultValue = "0")
    )
    @QueryParam("page") @DefaultValue("0") @PositiveOrZero int page,
    @Parameter(
      description = "Page size for the combined result set (1–200).",
      schema = @Schema(minimum = "1", maximum = "200", defaultValue = "50")
    )
    @QueryParam("pageSize") @DefaultValue("50") @Min(1) @Max(200) int pageSize,
    @Parameter(
      description =
        "Optional collection appId (UUID v7) to scope DataObject results to a single " +
        "collection. When present, collection items are omitted from the response and " +
        "only DataObjects belonging to the specified collection are returned. Returns " +
        "400 if the appId does not match an existing collection."
    )
    @QueryParam("collectionAppId") String collectionAppId
  ) {
    if (q == null || q.isBlank()) {
      return problem("/problems/search.bad-request", "Bad Request", Response.Status.BAD_REQUEST,
          "Query parameter 'q' is required and must be non-blank.");
    }

    // Resolve optional collection scope (appId → Neo4j Long for DataObjectSearchService).
    Long scopeCollectionId = null;
    if (collectionAppId != null && !collectionAppId.isBlank()) {
      Collection scopeCol = collectionDAO.findByAppId(collectionAppId);
      if (scopeCol == null) {
        return problem("/problems/search.bad-request", "Bad Request", Response.Status.BAD_REQUEST,
            "Collection with appId '" + collectionAppId + "' not found.");
      }
      scopeCollectionId = scopeCol.getId();
    }

    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(pageSize, 1), 200);

    // Search body shared by all DataObject queries below.
    SearchBody body = new SearchBody(
      new SearchScope[] { new SearchScope(scopeCollectionId, null, new TraversalRules[0]) },
      new SearchParams(q, QueryType.DataObject)
    );

    // Phase 1: count-only (cheap COUNT queries — no items materialised yet).
    long colTotal = scopeCollectionId == null ? collectionSearchService.count(q) : 0L;
    long doTotal = dataObjectSearchService.count(body);
    long rawTotal = colTotal + doTotal;
    long total = Math.min(rawTotal, SEARCH_RESULT_CAP);

    // Collections fill positions [0, effectiveColTotal); DOs fill the rest.
    long effectiveColTotal = Math.min(colTotal, total);

    // Phase 2: fetch only the items inside the requested page window.
    long from = Math.min((long) safePage * safeSize, total);
    long to = Math.min(from + safeSize, total);

    List<SearchV2ItemIO> items = new ArrayList<>();

    // Collection slice — only when the page window overlaps [0, effectiveColTotal).
    if (scopeCollectionId == null && from < effectiveColTotal) {
      int colSkip = (int) from;
      int colLimit = (int) (Math.min(to, effectiveColTotal) - colSkip);
      if (colLimit > 0) {
        collectionSearchService
          .searchSlice(q, colSkip, colLimit, BasicCollectionAttributes.createdAt, true)
          .forEach(c -> items.add(new SearchV2ItemIO(c.getAppId(), c.getName(), "collection", null)));
      }
    }

    // DataObject slice — only when the page window overlaps [effectiveColTotal, total).
    if (to > effectiveColTotal) {
      int doSkip = (int) Math.max(0L, from - effectiveColTotal);
      int doLimit = (int) (to - Math.max(from, effectiveColTotal));
      if (doLimit > 0) {
        ResponseBody doSlice = dataObjectSearchService.searchPaged(body, doSkip, doLimit);
        var dos = doSlice.getResults();
        ResultTriple[] triples = doSlice.getResultSet();
        // Cache collection appId lookups so N+1 cost stays bounded.
        Map<Long, String> collectionAppIdCache = new HashMap<>();
        for (int i = 0; i < dos.length; i++) {
          Long colId = (triples != null && i < triples.length) ? triples[i].getCollectionId() : null;
          String colAppId = colId == null ? null : collectionAppIdCache.computeIfAbsent(colId, id -> {
            Collection col = collectionDAO.findLightByNeo4jId(id);
            return col != null ? col.getAppId() : null;
          });
          items.add(new SearchV2ItemIO(dos[i].getAppId(), dos[i].getName(), "dataobject", colAppId));
        }
      }
    }

    return Response.ok(new SearchV2ResultIO(items, total, safePage, safeSize, q))
        .build();
  }

}
