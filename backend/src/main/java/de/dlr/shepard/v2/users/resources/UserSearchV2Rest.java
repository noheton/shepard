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
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Arrays;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

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
    description = "Searches users by username, firstName, lastName and email with a case-insensitive OR-contains query. Returns a flat list of matching users. No numeric Neo4j id is exposed. Requires authentication."
  )
  @APIResponse(
    responseCode = "200",
    description = "List of matching users.",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = UserIO.class))
  )
  @APIResponse(responseCode = "400", description = "Query parameter 'q' is blank or missing.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @Parameter(name = "q", description = "Search string (required). OR-matched across username, firstName, lastName, email.", required = true)
  public Response searchUsers(@QueryParam("q") String q) {
    if (q == null || q.isBlank()) {
      return problem(
        "/problems/users.bad-request",
        "Bad Request",
        Response.Status.BAD_REQUEST,
        "Query parameter 'q' is required and must be non-blank."
      );
    }
    String jsonQuery = buildOrContainsQuery(q, "username", "firstName", "lastName", "email");
    var result = userSearchService.search(new UserSearchBody(new UserSearchParams(jsonQuery)));
    List<UserIO> users = Arrays.asList(result.getResults());
    return Response.ok(users).build();
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

  private static Response problem(String type, String title, Response.Status status, String detail) {
    var body = new ProblemJson(type, title, status.getStatusCode(), detail, null);
    return Response.status(status).type("application/problem+json").entity(body).build();
  }
}
