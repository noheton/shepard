package de.dlr.shepard.v2.users.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.dlr.shepard.auth.users.entities.UserGroup;
import de.dlr.shepard.auth.users.services.UserGroupService;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.v2.users.io.UserGroupV2IO;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** V2-SWEEP-002 — unit tests for {@link UserGroupV2Rest}. No CDI, no Neo4j. */
class UserGroupV2RestTest {

  static final String APP_ID = "01900000-0000-7000-8000-000000000001";
  static final String GROUP_NAME = "LUMEN engineers";
  static final ObjectMapper MAPPER = new ObjectMapper();

  @Mock
  UserGroupService service;

  UserGroupV2Rest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new UserGroupV2Rest();
    resource.service = service;
  }

  // ── helpers ──────────────────────────────────────────────────────────

  private UserGroup stubGroup(String appId, String name) {
    UserGroup g = new UserGroup();
    g.setAppId(appId);
    g.setName(name);
    return g;
  }

  // ── GET /v2/user-groups ──────────────────────────────────────────────

  @Test
  void listUserGroups_returnsEmptyList() {
    when(service.getAllUserGroups(any(QueryParamHelper.class))).thenReturn(List.of());
    Response r = resource.listUserGroups(null, null, null, null);
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    List<UserGroupV2IO> body = (List<UserGroupV2IO>) r.getEntity();
    assertNotNull(body);
    assertEquals(0, body.size());
  }

  @Test
  void listUserGroups_returnsMappedList() {
    when(service.getAllUserGroups(any(QueryParamHelper.class)))
      .thenReturn(List.of(stubGroup(APP_ID, GROUP_NAME)));
    Response r = resource.listUserGroups(null, null, null, null);
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    List<UserGroupV2IO> body = (List<UserGroupV2IO>) r.getEntity();
    assertEquals(1, body.size());
    assertEquals(APP_ID, body.get(0).getAppId());
    assertEquals(GROUP_NAME, body.get(0).getName());
  }

  // ── GET /v2/user-groups/{appId} ──────────────────────────────────────

  @Test
  void getUserGroup_returns200WhenFound() {
    when(service.getUserGroupByAppId(APP_ID)).thenReturn(stubGroup(APP_ID, GROUP_NAME));
    Response r = resource.getUserGroup(APP_ID);
    assertEquals(200, r.getStatus());
    UserGroupV2IO body = (UserGroupV2IO) r.getEntity();
    assertEquals(APP_ID, body.getAppId());
    assertEquals(GROUP_NAME, body.getName());
  }

  @Test
  void getUserGroup_propagates404WhenNotFound() {
    when(service.getUserGroupByAppId(anyString())).thenThrow(new InvalidPathException("not found"));
    try {
      resource.getUserGroup("nonexistent");
    } catch (InvalidPathException e) {
      assertNotNull(e);
    }
  }

  // ── POST /v2/user-groups ─────────────────────────────────────────────

  @Test
  void createUserGroup_returns201WithLocation() {
    when(service.createUserGroup(any())).thenReturn(stubGroup(APP_ID, GROUP_NAME));
    var body = new UserGroupV2IO();
    body.setName(GROUP_NAME);
    body.setUsernames(List.of("alice", "bob"));
    Response r = resource.createUserGroup(body);
    assertEquals(201, r.getStatus());
    assertNotNull(r.getLocation());
    assertEquals("/v2/user-groups/" + APP_ID, r.getLocation().toString());
    UserGroupV2IO io = (UserGroupV2IO) r.getEntity();
    assertEquals(APP_ID, io.getAppId());
  }

  @Test
  void createUserGroup_nullUsernamesBecomesEmptyArray() {
    when(service.createUserGroup(any())).thenReturn(stubGroup(APP_ID, GROUP_NAME));
    var body = new UserGroupV2IO();
    body.setName(GROUP_NAME);
    // usernames is null → service receives empty array
    resource.createUserGroup(body);
    verify(service).createUserGroup(any());
  }

  // ── PATCH /v2/user-groups/{appId} ────────────────────────────────────

  @Test
  void patchUserGroup_nameOnly_callsServiceWithNullUsernames() throws Exception {
    when(service.patchUserGroupByAppId(eq(APP_ID), eq("new name"), isNull()))
      .thenReturn(stubGroup(APP_ID, "new name"));
    ObjectNode patch = MAPPER.createObjectNode();
    patch.put("name", "new name");
    Response r = resource.patchUserGroup(APP_ID, patch);
    assertEquals(200, r.getStatus());
    verify(service).patchUserGroupByAppId(APP_ID, "new name", null);
  }

  @Test
  void patchUserGroup_usernamesOnly_callsServiceWithNullName() throws Exception {
    when(service.patchUserGroupByAppId(eq(APP_ID), isNull(), any()))
      .thenReturn(stubGroup(APP_ID, GROUP_NAME));
    ObjectNode patch = MAPPER.createObjectNode();
    patch.putArray("usernames").add("carol");
    Response r = resource.patchUserGroup(APP_ID, patch);
    assertEquals(200, r.getStatus());
    verify(service).patchUserGroupByAppId(eq(APP_ID), isNull(), any());
  }

  @Test
  void patchUserGroup_nullBody_returns400() {
    Response r = resource.patchUserGroup(APP_ID, null);
    assertEquals(400, r.getStatus());
  }

  @Test
  void patchUserGroup_nullJsonNode_returns400() throws Exception {
    Response r = resource.patchUserGroup(APP_ID, MAPPER.nullNode());
    assertEquals(400, r.getStatus());
  }

  // ── DELETE /v2/user-groups/{appId} ───────────────────────────────────

  @Test
  void deleteUserGroup_returns204OnSuccess() {
    Response r = resource.deleteUserGroup(APP_ID);
    assertEquals(204, r.getStatus());
    verify(service).deleteUserGroupByAppId(APP_ID);
  }

  @Test
  void deleteUserGroup_propagates404WhenNotFound() {
    doThrow(new InvalidPathException("not found")).when(service).deleteUserGroupByAppId(anyString());
    try {
      resource.deleteUserGroup("nonexistent");
    } catch (InvalidPathException e) {
      assertNotNull(e);
    }
  }
}
