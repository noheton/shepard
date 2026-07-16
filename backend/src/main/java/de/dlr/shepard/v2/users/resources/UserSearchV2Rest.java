package de.dlr.shepard.v2.users.resources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.dlr.shepard.auth.users.io.UserIO;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.search.io.UserSearchBody;
import de.dlr.shepard.common.search.io.UserSearchParams;
import de.dlr.shepard.common.search.services.UserSearchService;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
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
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.headers.Header;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import static de.dlr.shepard.v2.common.ProblemResponse.problem;

/**
 * SEARCH-V2-4-PRE — user search on the v2 surface.
 *
 * <p>{@code GET /v2/users?q=<text>} searches users by username / firstName /
 * lastName / email with an OR-contains query. No numeric Neo4j id is exposed.
 * Replaces the v1 {@code POST /shepard/api/search/users} for this fork's own
 * callers (migrated in SEARCH-V2-4).
 *
 * <p>The frozen v1 search surface ({@code /shepard/api/search}) is unchanged.
 */
@Path("/v2/users")
@RequestScoped
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Users")
public class UserSearchV2Rest {

  @Inject
  UserSearchService userSearchService;

  static final ObjectMapper MAPPER = new ObjectMapper();

  @GET
  @Operation(
    operationId = "searchUsersV2",
    summary = "Search users by text (v2)",
    description = "Searches users by username, firstName, lastName and email with a case-insensitive OR-contains query. Returns a paginated envelope. No numeric Neo4j id is exposed. Requires authentication."
  )
  @APIResponse(
    responseCode = "200",
    description = "Paginated list of matching users.",
    content = @Content(schema = @Schema(implementation = PagedResponseIO.class)),
    headers = @Header(name = "X-Total-Count", description = "Total count before paging.", schema = @Schema(implementation = Long.class))
  )
  @APIResponse(responseCode = "400", description = "Query parameter 'q' is blank or missing.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @Parameter(name = "q", description = "Search string (required). OR-matched across username, firstName, lastName, email.", required = true)
  @Parameter(name = "page", description = "Zero-based page index (default 0).")
  @Parameter(name = "pageSize", description = "Items per page, 1–100 (default 50).")
  public Response searchUsers(
    @QueryParam("q") String q,
    @QueryParam("page") @DefaultValue("0") @PositiveOrZero int page,
    @QueryParam("pageSize") @DefaultValue("50") @Min(1) @Max(100) int pageSize
  ) {
    if (q == null || q.isBlank()) {
      return problem(
        "/problems/users.bad-request",
        "Bad Request",
        Response.Status.BAD_REQUEST,
        "Query parameter 'q' is required and must be non-blank."
      );
    }
    String jsonQuery = buildOrContainsQuery(q, "username", "firstName", "lastName", "email");
    var body = new UserSearchBody(new UserSearchParams(jsonQuery));
    PagedResponseIO<UserIO> paged = userSearchService.searchPaged(body, page, pageSize);
    return Response.ok(paged)
        .header("X-Total-Count", paged.total())
        .build();
  }

  /**
   * Builds an OR-contains JSON query accepted by {@link UserSearchService} over the given
   * property names. Uses Jackson serialization so the user-supplied value is safely escaped.
   */
  static String buildOrContainsQuery(String value, String... properties) {
    try {
      ArrayNode conditions = MAPPER.createArrayNode();
      for (String prop : properties) {
        ObjectNode cond = MAPPER.createObjectNode();
        cond.put("property", prop);
        cond.put("value", value);
        cond.put("operator", "contains");
        conditions.add(cond);
      }
      ObjectNode root = MAPPER.createObjectNode();
      root.set("OR", conditions);
      return MAPPER.writeValueAsString(root);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize search query", e);
    }
  }

}
