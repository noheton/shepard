package de.dlr.shepard.v2.search.resources;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.search.endpoints.BasicCollectionAttributes;
import de.dlr.shepard.common.search.io.QueryType;
import de.dlr.shepard.common.search.io.ResponseBody;
import de.dlr.shepard.common.search.io.ResultTriple;
import de.dlr.shepard.common.search.io.SearchBody;
import de.dlr.shepard.common.search.io.SearchParams;
import de.dlr.shepard.common.search.io.SearchScope;
import de.dlr.shepard.common.search.services.CollectionSearchService;
import de.dlr.shepard.common.search.services.DataObjectSearchService;
import de.dlr.shepard.common.search.services.PaginatedCollectionList;
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
import java.util.Optional;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.headers.Header;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

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
 */
@Path("/v2/search")
@RequestScoped
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Search")
public class SearchV2Rest {

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
      "pagination. When `collectionAppId` is supplied, DataObject results are narrowed " +
      "to that collection and no collection items are returned (scoped DO search). " +
      "Requires authentication."
  )
  @APIResponse(
    responseCode = "200",
    description = "Search results — items ordered collections-first, then dataobjects.",
    content = @Content(
      mediaType = MediaType.APPLICATION_JSON,
      schema = @Schema(implementation = SearchV2ResultIO.class)
    ),
    headers = @Header(
      name = "X-Total-Count",
      description = "Total element count before paging.",
      schema = @Schema(type = SchemaType.INTEGER)
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

    // Build combined list: all collections first, then all DataObjects.
    List<SearchV2ItemIO> combined = new ArrayList<>();
    long total = 0;

    // Collections — all matching results; skipped when a collection scope is active.
    if (scopeCollectionId == null) {
      PaginatedCollectionList colPage = collectionSearchService.search(
        q,
        Optional.empty(),
        Optional.empty(),
        BasicCollectionAttributes.createdAt,
        true
      );
      colPage.getResults().forEach(c -> combined.add(new SearchV2ItemIO(c.getAppId(), c.getName(), "collection", null)));
      total += colPage.getTotalResults() != null ? colPage.getTotalResults() : 0L;
    }

    // DataObjects — scoped to collection when collectionAppId provided, otherwise global.
    SearchBody body = new SearchBody(
      new SearchScope[] { new SearchScope(scopeCollectionId, null, new TraversalRules[0]) },
      new SearchParams(q, QueryType.DataObject)
    );
    ResponseBody doResult = dataObjectSearchService.search(body);
    var allDos = doResult.getResults();
    ResultTriple[] triples = doResult.getResultSet();
    // Cache collection appId lookups so N+1 cost stays bounded.
    Map<Long, String> collectionAppIdCache = new HashMap<>();
    for (int i = 0; i < allDos.length; i++) {
      var r = allDos[i];
      Long colId = (triples != null && i < triples.length) ? triples[i].getCollectionId() : null;
      String colAppId = null;
      if (colId != null) {
        colAppId = collectionAppIdCache.computeIfAbsent(colId, id -> {
          Collection col = collectionDAO.findLightByNeo4jId(id);
          return col != null ? col.getAppId() : null;
        });
      }
      combined.add(new SearchV2ItemIO(r.getAppId(), r.getName(), "dataobject", colAppId));
    }
    total += allDos.length;

    // Apply single page/pageSize window to the combined set.
    int from = (int) Math.min((long) safePage * safeSize, combined.size());
    int to = Math.min(from + safeSize, combined.size());
    List<SearchV2ItemIO> paged = combined.subList(from, to);

    return Response.ok(new SearchV2ResultIO(paged, total, safePage, safeSize, q))
        .header("X-Total-Count", total)
        .build();
  }

  private static Response problem(String type, String title, Response.Status status, String detail) {
    ProblemJson body = new ProblemJson(type, title, status.getStatusCode(), detail, null);
    return Response.status(status).type("application/problem+json").entity(body).build();
  }
}
