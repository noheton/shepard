package de.dlr.shepard.v2.users.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.users.io.UserIO;
import de.dlr.shepard.common.search.io.UserSearchBody;
import de.dlr.shepard.common.search.io.UserSearchParams;
import de.dlr.shepard.common.search.io.UserSearchResult;
import de.dlr.shepard.common.search.services.UserSearchService;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** SEARCH-V2-4-PRE — unit tests for {@link UserSearchV2Rest}. No CDI, no Neo4j. */
class UserSearchV2RestTest {

  static final ObjectMapper MAPPER = new ObjectMapper();

  @Mock
  UserSearchService userSearchService;

  UserSearchV2Rest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new UserSearchV2Rest();
    resource.userSearchService = userSearchService;
  }

  // ── query building ────────────────────────────────────────────────────

  @Test
  void buildOrContainsQuery_producesValidOrJson() throws Exception {
    String json = UserSearchV2Rest.buildOrContainsQuery("alice", "username", "email");
    JsonNode root = MAPPER.readTree(json);
    assertTrue(root.has("OR"), "root should have OR key");
    assertEquals(2, root.get("OR").size(), "OR should have one condition per property");
    JsonNode first = root.get("OR").get(0);
    assertEquals("username", first.get("property").textValue());
    assertEquals("alice", first.get("value").textValue());
    assertEquals("contains", first.get("operator").textValue());
  }

  @Test
  void buildOrContainsQuery_escapesSpecialCharacters() throws Exception {
    // Jackson handles JSON escaping; quotes in the value must not break JSON structure
    String json = UserSearchV2Rest.buildOrContainsQuery("O'Brien \"test\"", "username");
    JsonNode root = MAPPER.readTree(json);
    assertEquals("O'Brien \"test\"", root.get("OR").get(0).get("value").textValue());
  }

  // ── searchUsers ───────────────────────────────────────────────────────

  @Test
  void searchUsers_returnsResults_whenServiceFindsMatches() {
    UserIO userIO = new UserIO();
    when(userSearchService.search(any(UserSearchBody.class)))
      .thenReturn(new UserSearchResult(new UserIO[] { userIO }, new UserSearchParams("q")));

    Response response = resource.searchUsers("alice");

    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    @SuppressWarnings("unchecked")
    List<UserIO> body = (List<UserIO>) response.getEntity();
    assertNotNull(body);
    assertEquals(1, body.size());
  }

  @Test
  void searchUsers_returnsEmptyList_whenNoMatches() {
    when(userSearchService.search(any(UserSearchBody.class)))
      .thenReturn(new UserSearchResult(new UserIO[0], new UserSearchParams("q")));

    Response response = resource.searchUsers("nomatch");

    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    @SuppressWarnings("unchecked")
    List<UserIO> body = (List<UserIO>) response.getEntity();
    assertEquals(0, body.size());
  }

  @Test
  void searchUsers_returns400_whenQIsNull() {
    Response response = resource.searchUsers(null);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
  }

  @Test
  void searchUsers_returns400_whenQIsBlank() {
    Response response = resource.searchUsers("   ");
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
  }
}
