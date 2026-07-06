package de.dlr.shepard.v2.users.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.users.io.UserIO;
import de.dlr.shepard.common.search.io.UserSearchBody;
import de.dlr.shepard.common.search.services.UserSearchService;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** SEARCH-V2-4-PRE / APISIMP-USER-SEARCH-NO-PAGINATION — unit tests for {@link UserSearchV2Rest}. No CDI, no Neo4j. */
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
  void searchUsers_returnsPaginatedEnvelope_whenServiceHasResults() {
    UserIO userIO = new UserIO();
    when(userSearchService.searchPaged(any(UserSearchBody.class), anyInt(), anyInt()))
      .thenReturn(new PagedResponseIO<>(List.of(userIO), 1L, 0, 20));

    Response response = resource.searchUsers("alice", 0, 20);

    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<UserIO> body = (PagedResponseIO<UserIO>) response.getEntity();
    assertNotNull(body);
    assertEquals(1, body.items().size());
    assertEquals(1L, body.total());
    assertEquals(0, body.page());
    assertEquals(20, body.pageSize());
  }

  @Test
  void searchUsers_returnsEmptyPage_whenNoMatches() {
    when(userSearchService.searchPaged(any(UserSearchBody.class), anyInt(), anyInt()))
      .thenReturn(new PagedResponseIO<>(List.of(), 0L, 0, 20));

    Response response = resource.searchUsers("nomatch", 0, 20);

    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<UserIO> body = (PagedResponseIO<UserIO>) response.getEntity();
    assertEquals(0, body.items().size());
    assertEquals(0L, body.total());
  }

  @Test
  void searchUsers_returnsCorrectPageMetadata_withCustomPage() {
    UserIO u1 = new UserIO();
    when(userSearchService.searchPaged(any(UserSearchBody.class), anyInt(), anyInt()))
      .thenReturn(new PagedResponseIO<>(List.of(u1), 5L, 1, 2));

    Response response = resource.searchUsers("bob", 1, 2);

    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<UserIO> body = (PagedResponseIO<UserIO>) response.getEntity();
    assertEquals(1, body.items().size());
    assertEquals(5L, body.total());
    assertEquals(1, body.page());
    assertEquals(2, body.pageSize());
  }

  @Test
  void searchUsers_returns400_whenQIsNull() {
    Response response = resource.searchUsers(null, 0, 20);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
  }

  @Test
  void searchUsers_returns400_whenQIsBlank() {
    Response response = resource.searchUsers("   ", 0, 20);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
  }
}
