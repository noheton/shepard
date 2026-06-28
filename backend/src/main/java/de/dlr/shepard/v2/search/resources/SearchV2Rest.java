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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.microprofile.openapi.annotations.Operation;
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
 * <p>Collection results are paginated via {@code page} / {@code pageSize}.
 * DataObject results are returned inline (no per-kind secondary pagination in
 * this slice; the underlying Neo4j query naturally caps the result set).
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
    summary = "Full-text search returning appId-keyed results",
    description =
      "Searches Collections and DataObjects by name / description and returns a unified " +
      "list where every item carries its stable `appId` (UUID v7). Unlike the legacy " +
      "`POST /shepard/api/search` endpoint, no internal Neo4j node id is exposed. " +
      "Collection results are paginated via `page` / `pageSize`; DataObject results are " +
      "returned inline alongside them. Requires authentication."
  )
  @APIResponse(
    responseCode = "200",
    description = "Search results — items ordered collections-first, then dataobjects.",
    content = @Content(
      mediaType = MediaType.APPLICATION_JSON,
      schema = @Schema(implementation = SearchV2ResultIO.class)
    )
  )
  @APIResponse(responseCode = "400", description = "Query parameter `q` is blank or absent.")
  @APIResponse(responseCode = "401", description = "Caller is not authenticated.")
  public Response search(
    @Parameter(description = "Full-text search query (required).", required = true)
    @QueryParam("q") String q,
    @Parameter(
      description = "Zero-based page index for collection results.",
      schema = @Schema(minimum = "0", defaultValue = "0")
    )
    @QueryParam("page") @DefaultValue("0") @PositiveOrZero int page,
    @Parameter(
      description = "Page size (1–200).",
      schema = @Schema(minimum = "1", maximum = "200", defaultValue = "50")
    )
    @QueryParam("pageSize") @DefaultValue("50") @Min(1) @Max(200) int pageSize
  ) {
    if (q == null || q.isBlank()) {
      var problem = new ProblemJson("/problems/search.bad-request", "Bad Request",
          Response.Status.BAD_REQUEST.getStatusCode(), "Query parameter 'q' is required.", null);
      return Response.status(Response.Status.BAD_REQUEST).type("application/problem+json").entity(problem).build();
    }
    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(pageSize, 1), 200);

    List<SearchV2ItemIO> items = new ArrayList<>();
    long total = 0;

    // Collections — paginated
    PaginatedCollectionList colPage = collectionSearchService.search(
      q,
      Optional.of(safePage),
      Optional.of(safeSize),
      BasicCollectionAttributes.createdAt,
      true
    );
    colPage.getResults().forEach(c -> items.add(new SearchV2ItemIO(c.getAppId(), c.getName(), "collection", null)));
    total += colPage.getTotalResults() != null ? colPage.getTotalResults() : 0L;

    // DataObjects — global scope (no collection/dataobject constraint)
    SearchBody body = new SearchBody(
      new SearchScope[] { new SearchScope(null, null, new TraversalRules[0]) },
      new SearchParams(q, QueryType.DataObject)
    );
    ResponseBody doResult = dataObjectSearchService.search(body);
    ResultTriple[] triples = doResult.getResultSet();
    // Cache collection appId lookups; the result set is small so N+1 is fine.
    Map<Long, String> collectionAppIdCache = new HashMap<>();
    for (int i = 0; i < doResult.getResults().length; i++) {
      var r = doResult.getResults()[i];
      Long colId = (triples != null && i < triples.length) ? triples[i].getCollectionId() : null;
      String colAppId = null;
      if (colId != null) {
        colAppId = collectionAppIdCache.computeIfAbsent(colId, id -> {
          Collection col = collectionDAO.findLightByNeo4jId(id);
          return col != null ? col.getAppId() : null;
        });
      }
      items.add(new SearchV2ItemIO(r.getAppId(), r.getName(), "dataobject", colAppId));
    }
    total += doResult.getResults().length;

    return Response.ok(new SearchV2ResultIO(items, total, safePage, safeSize, q))
        .header("X-Total-Count", total)
        .build();
  }
}
